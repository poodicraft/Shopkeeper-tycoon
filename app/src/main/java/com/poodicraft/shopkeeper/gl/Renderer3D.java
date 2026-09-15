package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES20;

import com.poodicraft.shopkeeper.math.Mat4;

/**
 * Thin draw service over GLES 2.0: one lit shader, a handful of light uniforms,
 * and a per-object tint. Everything in the game renders through {@link #draw}.
 */
public final class Renderer3D {

    private static final String VERTEX_SRC =
            "uniform mat4 uViewProj;\n" +
            "uniform mat4 uModel;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying vec3 vWorld;\n" +
            "void main() {\n" +
            "  vec4 world = uModel * vec4(aPos, 1.0);\n" +
            "  vWorld = world.xyz;\n" +
            // GLSL ES 1.00 has no matrix-from-matrix constructor, so the upper 3x3
            // is applied column by column instead of via mat3(uModel).
            "  vNormal = uModel[0].xyz * aNormal.x + uModel[1].xyz * aNormal.y\n" +
            "          + uModel[2].xyz * aNormal.z;\n" +
            "  vColor = aColor;\n" +
            "  gl_Position = uViewProj * world;\n" +
            "}\n";

    private static final String FRAGMENT_SRC =
            "precision mediump float;\n" +
            "uniform vec3 uSunDir;\n" +
            "uniform vec3 uSunColor;\n" +
            "uniform vec3 uSkyColor;\n" +
            "uniform vec3 uGroundColor;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform vec3 uEye;\n" +
            "uniform vec4 uTint;\n" +
            "uniform float uUnlit;\n" +
            "uniform float uFogDensity;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying vec3 vWorld;\n" +
            "void main() {\n" +
            "  vec3 n = normalize(vNormal);\n" +
            "  float ndl = dot(n, uSunDir);\n" +
            "  float direct = max(ndl, 0.0);\n" +
            "  float wrapped = ndl * 0.5 + 0.5;\n" +
            "  vec3 hemi = mix(uGroundColor, uSkyColor, n.y * 0.5 + 0.5);\n" +
            "  vec3 lit = vColor * (hemi + uSunColor * (direct * 0.85 + wrapped * 0.18));\n" +
            "  vec3 viewDir = normalize(uEye - vWorld);\n" +
            "  float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);\n" +
            "  lit += uSkyColor * rim * 0.22;\n" +
            "  vec3 base = mix(lit, vColor, uUnlit);\n" +
            "  base *= uTint.rgb;\n" +
            "  float dist = length(uEye - vWorld);\n" +
            "  float fog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);\n" +
            "  base = mix(base, uFogColor, clamp(fog, 0.0, 1.0) * (1.0 - uUnlit));\n" +
            "  gl_FragColor = vec4(base, uTint.a);\n" +
            "}\n";

    private ShaderProgram shader;
    private int aPos, aNormal, aColor;
    private Mesh boundMesh;

    private final float[] identityModel = new Mat4().m;

    public void init() {
        shader = new ShaderProgram(VERTEX_SRC, FRAGMENT_SRC);
        aPos = shader.attribute("aPos");
        aNormal = shader.attribute("aNormal");
        aColor = shader.attribute("aColor");

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public boolean isReady() { return shader != null; }

    public void clear(float r, float g, float b) {
        GLES20.glClearColor(r, g, b, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    /** Starts an opaque pass with the given camera and lighting environment. */
    public void beginFrame(Camera camera, Lighting lighting) {
        shader.use();
        boundMesh = null;
        shader.setMat4("uViewProj", camera.viewProjection.m);
        shader.setVec3("uEye", camera.eye.x, camera.eye.y, camera.eye.z);
        shader.setVec3("uSunDir", lighting.sunX, lighting.sunY, lighting.sunZ);
        shader.setVec3("uSunColor", lighting.sunR, lighting.sunG, lighting.sunB);
        shader.setVec3("uSkyColor", lighting.skyR, lighting.skyG, lighting.skyB);
        shader.setVec3("uGroundColor", lighting.groundR, lighting.groundG, lighting.groundB);
        shader.setVec3("uFogColor", lighting.fogR, lighting.fogG, lighting.fogB);
        shader.setFloat("uFogDensity", lighting.fogDensity);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** Switches to blended, depth-read-only drawing for shadows and ghost previews. */
    public void beginTransparentPass() {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDepthMask(false);
    }

    public void endTransparentPass() {
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void draw(Mesh mesh, Mat4 model, float r, float g, float b, float a, boolean unlit) {
        draw(mesh, model == null ? identityModel : model.m, r, g, b, a, unlit);
    }

    public void draw(Mesh mesh, float[] model, float r, float g, float b, float a, boolean unlit) {
        if (mesh == null || mesh.vertexCount() == 0) return;
        if (mesh != boundMesh) {
            mesh.bind(aPos, aNormal, aColor);
            boundMesh = mesh;
        }
        shader.setMat4("uModel", model);
        shader.setVec4("uTint", r, g, b, a);
        shader.setFloat("uUnlit", unlit ? 1f : 0f);
        mesh.draw();
    }

    /** Re-draws the currently bound mesh with a new transform, skipping the rebind. */
    public void drawAgain(float[] model, float r, float g, float b, float a, boolean unlit) {
        if (boundMesh == null) return;
        shader.setMat4("uModel", model);
        shader.setVec4("uTint", r, g, b, a);
        shader.setFloat("uUnlit", unlit ? 1f : 0f);
        boundMesh.draw();
    }

    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }

    /** Mutable lighting environment; the day/night cycle writes into this each frame. */
    public static final class Lighting {
        public float sunX = 0.45f, sunY = 0.78f, sunZ = 0.42f;
        public float sunR = 1.0f, sunG = 0.96f, sunB = 0.88f;
        public float skyR = 0.32f, skyG = 0.35f, skyB = 0.42f;
        public float groundR = 0.16f, groundG = 0.15f, groundB = 0.17f;
        public float fogR = 0.10f, fogG = 0.11f, fogB = 0.14f;
        public float fogDensity = 0.012f;

        public void normalizeSun() {
            float len = (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
            if (len > 1e-5f) { sunX /= len; sunY /= len; sunZ /= len; }
        }
    }
}
