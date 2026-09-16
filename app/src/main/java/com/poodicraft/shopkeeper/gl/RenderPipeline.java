package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES30;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.art.TextureFactory;
import com.poodicraft.shopkeeper.character.Skeleton;
import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;

import java.util.ArrayList;

/**
 * The frame pipeline: a shadow pass, a lit scene pass into an off-screen buffer,
 * then bloom and a composite that tonemaps, vignettes and anti-aliases.
 *
 * <p>Named for what it does rather than "Renderer", which would shadow
 * {@code GLSurfaceView.Renderer} wherever the two meet.
 *
 * <p>Callers submit draws for the whole frame first; the renderer then replays the
 * list into the shadow map and again into the scene, so shadow casters and lit
 * geometry can never drift apart.
 */
public final class RenderPipeline {

    /** Visual settings, stepped down automatically when frames get slow. */
    public enum Quality { LOW, MEDIUM, HIGH }

    /** Lighting environment for one frame. */
    public static final class Environment {
        public float sunX = 0.42f, sunY = 0.80f, sunZ = 0.42f;
        public float sunR = 1.05f, sunG = 0.98f, sunB = 0.90f;
        public float skyR = 0.40f, skyG = 0.44f, skyB = 0.52f;
        public float groundR = 0.18f, groundG = 0.17f, groundB = 0.16f;
        public float fogR = 0.62f, fogG = 0.68f, fogB = 0.76f;
        public float fogDensity = 0.0065f;
        public float exposure = 1.0f;
        public float shadowStrength = 1.0f;
        public float clearR = 0.55f, clearG = 0.66f, clearB = 0.80f;
        public float tintR = 1f, tintG = 1f, tintB = 1f;
        public float bloomThreshold = 0.72f;
        public float bloomIntensity = 0.45f;

        /** Up to eight lamps: xyz position with radius in w. */
        public final float[] pointPosition = new float[8 * 4];
        /** Matching rgb colour with intensity in w. */
        public final float[] pointColor = new float[8 * 4];
        public int pointCount = 0;

        public void clearPointLights() { pointCount = 0; }

        public void addPointLight(float x, float y, float z, float radius,
                                  float r, float g, float b, float intensity) {
            if (pointCount >= 8) return;
            int i = pointCount * 4;
            pointPosition[i] = x; pointPosition[i + 1] = y;
            pointPosition[i + 2] = z; pointPosition[i + 3] = radius;
            pointColor[i] = r; pointColor[i + 1] = g;
            pointColor[i + 2] = b; pointColor[i + 3] = intensity;
            pointCount++;
        }

        public void normalizeSun() {
            float len = (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
            if (len > 1e-5f) { sunX /= len; sunY /= len; sunZ /= len; }
        }
    }

    /** One queued draw. Pooled, so a frame allocates nothing. */
    private static final class DrawItem {
        GpuMesh mesh;
        final float[] model = new float[16];
        float[] bones;
        float[] tints;
        boolean skinned;
        boolean castsShadow;
    }

    private static final int SHADOW_LOW = 0;
    private static final int SHADOW_MEDIUM = 1024;
    private static final int SHADOW_HIGH = 2048;

    private Shader sceneStatic, sceneSkinned, depthStatic, depthSkinned;
    private Shader brightPass, blur, composite;

    private final TextureArray albedoArray = new TextureArray(TextureFactory.SIZE, Materials.COUNT);
    private final TextureArray normalArray = new TextureArray(TextureFactory.SIZE, Materials.COUNT);

    private final Framebuffer sceneBuffer = new Framebuffer(true, false);
    private final Framebuffer shadowBuffer = new Framebuffer(true, true);
    private final Framebuffer bloomA = new Framebuffer(false, false);
    private final Framebuffer bloomB = new Framebuffer(false, false);

    private final ArrayList<DrawItem> pool = new ArrayList<DrawItem>();
    private final ArrayList<DrawItem> queue = new ArrayList<DrawItem>();
    private int queued = 0;

    private final float[] identity = new Mat4().m;
    private final float[] defaultTints = new float[Materials.COUNT * 3];
    private final float[] materialParams = Materials.shadingParams();
    private float[] lastUploadedTints = null;

    private final Mat4 lightView = new Mat4();
    private final Mat4 lightProjection = new Mat4();
    private final Mat4 lightViewProjection = new Mat4();
    private final Vec3 lightEye = new Vec3();
    private final Vec3 lightCenter = new Vec3();
    private final Vec3 lightUp = new Vec3(0f, 1f, 0f);
    private final Vec3 scratch = new Vec3();

    private int screenWidth = 1, screenHeight = 1;
    private Quality quality = Quality.HIGH;
    private boolean texturesUploaded = false;
    private int[][] pendingAlbedo, pendingNormal;

    /** Rolling average frame time in milliseconds, used to pick a quality level. */
    private float smoothedFrameMs = 16f;
    private float qualityHoldSeconds = 0f;

    public RenderPipeline() {
        for (int i = 0; i < defaultTints.length; i++) defaultTints[i] = 1f;
    }

    public Quality quality() { return quality; }

    public void setQuality(Quality value) {
        quality = value;
        qualityHoldSeconds = 4f;
    }

    public float smoothedFrameMs() { return smoothedFrameMs; }

    // -------------------------------------------------------------- lifecycle

    public void init() {
        sceneStatic = new Shader("scene", Shaders.sceneVertex(false), Shaders.sceneFragment());
        sceneSkinned = new Shader("scene-skinned", Shaders.sceneVertex(true), Shaders.sceneFragment());
        depthStatic = new Shader("depth", Shaders.depthVertex(false), Shaders.depthFragment());
        depthSkinned = new Shader("depth-skinned", Shaders.depthVertex(true), Shaders.depthFragment());
        brightPass = new Shader("bright", Shaders.fullscreenVertex(), Shaders.brightPassFragment());
        blur = new Shader("blur", Shaders.fullscreenVertex(), Shaders.blurFragment());
        composite = new Shader("composite", Shaders.fullscreenVertex(), Shaders.compositeFragment());

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glFrontFace(GLES30.GL_CCW);

        texturesUploaded = false;
        lastUploadedTints = null;
        if (pendingAlbedo != null) flushTextures();
    }

    /** Hands over generated pixels; they upload on the next GL frame. */
    public void setTextures(int[][] albedo, int[][] normal) {
        pendingAlbedo = albedo;
        pendingNormal = normal;
        texturesUploaded = false;
    }

    private void flushTextures() {
        if (pendingAlbedo == null || pendingNormal == null) return;
        albedoArray.upload(pendingAlbedo, true);
        normalArray.upload(pendingNormal, false);
        texturesUploaded = true;
    }

    public boolean texturesReady() { return texturesUploaded; }

    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        allocateTargets();
    }

    private void allocateTargets() {
        float scale = quality == Quality.LOW ? 0.72f : 1f;
        int w = Math.max(1, (int) (screenWidth * scale));
        int h = Math.max(1, (int) (screenHeight * scale));
        sceneBuffer.resize(w, h);

        int shadowSize = shadowResolution();
        if (shadowSize > 0) shadowBuffer.resize(shadowSize, shadowSize);
        else shadowBuffer.dispose();

        if (quality != Quality.LOW) {
            bloomA.resize(Math.max(1, w / 4), Math.max(1, h / 4));
            bloomB.resize(Math.max(1, w / 4), Math.max(1, h / 4));
        } else {
            bloomA.dispose();
            bloomB.dispose();
        }
    }

    private int shadowResolution() {
        switch (quality) {
            case HIGH: return SHADOW_HIGH;
            case MEDIUM: return SHADOW_MEDIUM;
            default: return SHADOW_LOW;
        }
    }

    /** Forgets every GPU name after a context loss; geometry and pixels survive. */
    public void invalidate() {
        albedoArray.invalidate();
        normalArray.invalidate();
        sceneBuffer.invalidate();
        shadowBuffer.invalidate();
        bloomA.invalidate();
        bloomB.invalidate();
        texturesUploaded = false;
        lastUploadedTints = null;
    }

    // ------------------------------------------------------------ submission

    public void beginFrame() {
        queued = 0;
    }

    private DrawItem next() {
        if (queued < queue.size()) return queue.get(queued++);
        DrawItem item = new DrawItem();
        queue.add(item);
        queued++;
        return item;
    }

    /** Queues a static mesh. Pass null for {@code tints} to use plain material colours. */
    public void submitStatic(GpuMesh mesh, float[] model, float[] tints, boolean castsShadow) {
        if (mesh == null) return;
        DrawItem item = next();
        item.mesh = mesh;
        System.arraycopy(model == null ? identity : model, 0, item.model, 0, 16);
        item.bones = null;
        item.tints = tints;
        item.skinned = false;
        item.castsShadow = castsShadow;
    }

    /**
     * Queues a skinned mesh.
     *
     * <p>{@code bones} must stay valid until the frame is rendered; callers keep one
     * buffer per character rather than allocating each frame.
     */
    public void submitSkinned(GpuMesh mesh, float[] bones, float[] tints, boolean castsShadow) {
        if (mesh == null) return;
        DrawItem item = next();
        item.mesh = mesh;
        System.arraycopy(identity, 0, item.model, 0, 16);
        item.bones = bones;
        item.tints = tints;
        item.skinned = true;
        item.castsShadow = castsShadow;
    }

    // ---------------------------------------------------------------- render

    public void render(Camera camera, Environment env, float dt) {
        if (!texturesUploaded) flushTextures();
        adaptQuality(dt);
        env.normalizeSun();

        boolean shadows = shadowResolution() > 0 && env.shadowStrength > 0.01f;
        if (shadows) {
            updateLightMatrices(camera, env);
            renderShadowPass();
        } else {
            lightViewProjection.identity();
        }

        renderScenePass(camera, env, shadows);

        if (quality != Quality.LOW && env.bloomIntensity > 0.001f) {
            renderBloom(env);
        }
        renderComposite(env);
    }

    private void adaptQuality(float dt) {
        float frameMs = MathUtil.clamp(dt * 1000f, 1f, 200f);
        smoothedFrameMs += (frameMs - smoothedFrameMs) * 0.05f;
        if (qualityHoldSeconds > 0f) {
            qualityHoldSeconds -= dt;
            return;
        }
        // Step down when frames are consistently long; never step back up on its
        // own, so the picture does not oscillate while the player is looking at it.
        if (smoothedFrameMs > 27f && quality != Quality.LOW) {
            quality = quality == Quality.HIGH ? Quality.MEDIUM : Quality.LOW;
            qualityHoldSeconds = 6f;
            smoothedFrameMs = 16f;
            allocateTargets();
        }
    }

    /**
     * Builds an orthographic frustum around the player for the sun.
     *
     * <p>The centre is snapped to shadow-map texels: without that, moving the camera
     * makes every shadow edge crawl.
     */
    private void updateLightMatrices(Camera camera, Environment env) {
        final float halfSize = 11f;
        final float depth = 40f;

        lightCenter.set(camera.target.x, 0.9f, camera.target.z);
        lightEye.set(lightCenter.x + env.sunX * depth * 0.5f,
                lightCenter.y + env.sunY * depth * 0.5f,
                lightCenter.z + env.sunZ * depth * 0.5f);
        lightUp.set(0f, 1f, 0f);
        if (Math.abs(env.sunY) > 0.985f) lightUp.set(0f, 0f, 1f);
        lightView.setLookAt(lightEye, lightCenter, lightUp);

        // Snap in light space so the texel grid stays put as the player walks.
        scratch.set(lightCenter.x, lightCenter.y, lightCenter.z);
        lightView.transformPoint(scratch, scratch);
        float texelWorld = 2f * halfSize / shadowResolution();
        float snappedX = (float) Math.floor(scratch.x / texelWorld) * texelWorld;
        float snappedY = (float) Math.floor(scratch.y / texelWorld) * texelWorld;
        float offsetX = snappedX - scratch.x;
        float offsetY = snappedY - scratch.y;

        lightProjection.setOrtho(-halfSize + offsetX, halfSize + offsetX,
                -halfSize + offsetY, halfSize + offsetY, 0.5f, depth);
        Mat4.multiply(lightProjection, lightView, lightViewProjection);
    }

    private void renderShadowPass() {
        shadowBuffer.bind();
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glColorMask(false, false, false, false);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        // Drawing back faces into the shadow map keeps acne off the lit surfaces.
        GLES30.glCullFace(GLES30.GL_FRONT);

        Shader active = null;
        for (int i = 0; i < queued; i++) {
            DrawItem item = queue.get(i);
            if (!item.castsShadow) continue;
            Shader shader = item.skinned ? depthSkinned : depthStatic;
            if (shader != active) {
                shader.use();
                shader.setMat4("uLightViewProj", lightViewProjection.m);
                active = shader;
            }
            shader.setMat4("uModel", item.model);
            if (item.skinned && item.bones != null) {
                shader.setMat4Array("uBones", item.bones, Skeleton.BONE_COUNT);
            }
            item.mesh.draw();
        }

        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glColorMask(true, true, true, true);
    }

    private void renderScenePass(Camera camera, Environment env, boolean shadows) {
        sceneBuffer.bind();
        GLES30.glClearColor(env.clearR, env.clearG, env.clearB, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);

        albedoArray.bind(0);
        normalArray.bind(1);
        if (shadows) shadowBuffer.bindDepth(2);

        lastUploadedTints = null;
        Shader active = null;
        for (int i = 0; i < queued; i++) {
            DrawItem item = queue.get(i);
            Shader shader = item.skinned ? sceneSkinned : sceneStatic;
            if (shader != active) {
                shader.use();
                applyEnvironment(shader, camera, env, shadows);
                lastUploadedTints = null;
                active = shader;
            }
            float[] tints = item.tints == null ? defaultTints : item.tints;
            if (tints != lastUploadedTints) {
                shader.setVec3Array("uMaterialTint", tints, Materials.COUNT);
                lastUploadedTints = tints;
            }
            shader.setMat4("uModel", item.model);
            if (item.skinned && item.bones != null) {
                shader.setMat4Array("uBones", item.bones, Skeleton.BONE_COUNT);
            }
            item.mesh.draw();
        }
    }

    private void applyEnvironment(Shader shader, Camera camera, Environment env, boolean shadows) {
        shader.setMat4("uViewProj", camera.viewProjection.m);
        shader.setMat4("uLightViewProj", lightViewProjection.m);
        shader.setInt("uAlbedo", 0);
        shader.setInt("uNormalMap", 1);
        shader.setInt("uShadowMap", 2);
        shader.setVec4Array("uMaterialParams", materialParams, Materials.COUNT);
        shader.setVec3("uSunDir", env.sunX, env.sunY, env.sunZ);
        shader.setVec3("uSunColor", env.sunR, env.sunG, env.sunB);
        shader.setVec3("uSkyColor", env.skyR, env.skyG, env.skyB);
        shader.setVec3("uGroundColor", env.groundR, env.groundG, env.groundB);
        shader.setVec3("uFogColor", env.fogR, env.fogG, env.fogB);
        shader.setFloat("uFogDensity", env.fogDensity);
        shader.setVec3("uEye", camera.eye.x, camera.eye.y, camera.eye.z);
        int shadowSize = Math.max(1, shadowResolution());
        shader.setFloat("uShadowTexel", 1f / shadowSize);
        shader.setFloat("uShadowStrength", shadows ? env.shadowStrength : 0f);
        shader.setVec4Array("uPointPos", env.pointPosition, 8);
        shader.setVec4Array("uPointColor", env.pointColor, 8);
        shader.setInt("uPointCount", env.pointCount);
        shader.setFloat("uExposure", env.exposure);
        shader.setVec3("uGlobalTint", env.tintR, env.tintG, env.tintB);
    }

    private void renderBloom(Environment env) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        bloomA.bind();
        brightPass.use();
        sceneBuffer.bindColor(0);
        brightPass.setInt("uScene", 0);
        brightPass.setFloat("uThreshold", env.bloomThreshold);
        drawFullscreen();

        // Two separable passes; the second reads what the first wrote.
        bloomB.bind();
        blur.use();
        bloomA.bindColor(0);
        blur.setInt("uSource", 0);
        blur.setVec2("uDirection", 1f / bloomA.width(), 0f);
        drawFullscreen();

        bloomA.bind();
        bloomB.bindColor(0);
        blur.setInt("uSource", 0);
        blur.setVec2("uDirection", 0f, 1f / bloomB.height());
        drawFullscreen();
    }

    private void renderComposite(Environment env) {
        Framebuffer.bindDefault(screenWidth, screenHeight);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        composite.use();
        sceneBuffer.bindColor(0);
        composite.setInt("uScene", 0);
        boolean hasBloom = quality != Quality.LOW && env.bloomIntensity > 0.001f;
        if (hasBloom) {
            bloomA.bindColor(1);
            composite.setInt("uBloom", 1);
        } else {
            // Point the bloom sampler at the scene and scale it to nothing.
            sceneBuffer.bindColor(1);
            composite.setInt("uBloom", 1);
        }
        composite.setFloat("uBloomIntensity", hasBloom ? env.bloomIntensity : 0f);
        composite.setVec2("uInvResolution", 1f / sceneBuffer.width(), 1f / sceneBuffer.height());
        composite.setFloat("uVignette", 0.38f);
        composite.setFloat("uAntialias", quality == Quality.HIGH ? 1f : 0f);
        drawFullscreen();

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
    }

    /** One oversized triangle; the vertex shader builds it from gl_VertexID. */
    private void drawFullscreen() {
        GLES30.glBindVertexArray(0);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
    }

    public void dispose() {
        if (sceneStatic != null) sceneStatic.dispose();
        if (sceneSkinned != null) sceneSkinned.dispose();
        if (depthStatic != null) depthStatic.dispose();
        if (depthSkinned != null) depthSkinned.dispose();
        if (brightPass != null) brightPass.dispose();
        if (blur != null) blur.dispose();
        if (composite != null) composite.dispose();
        albedoArray.dispose();
        normalArray.dispose();
        sceneBuffer.dispose();
        shadowBuffer.dispose();
        bloomA.dispose();
        bloomB.dispose();
    }
}
