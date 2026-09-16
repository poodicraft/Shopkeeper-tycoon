import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.art.TextureFactory;
import com.poodicraft.shopkeeper.character.Animator;
import com.poodicraft.shopkeeper.character.CharacterMesh;
import com.poodicraft.shopkeeper.character.Skeleton;
import com.poodicraft.shopkeeper.game.Appearance;
import com.poodicraft.shopkeeper.gl.MeshData;
import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

/**
 * Offline software renderer for looking at the character without a device.
 *
 * <p>Rasterises the same meshes, the same skinning matrices and the same albedo
 * textures the phone will use, so texture density, proportions and clothing fit can
 * be judged from a PNG instead of guessed at. The shading is deliberately simple -
 * this is for looking at shape and scale, not for matching the real pipeline's
 * lighting.
 */
public final class Preview {

    private static final int WIDTH = 460;
    private static final int HEIGHT = 700;

    private final float[] depth = new float[WIDTH * HEIGHT];
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private final TextureFactory textures = new TextureFactory();
    MeshData apron;

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "build/preview";
        new File(outDir).mkdirs();

        Preview preview = new Preview();
        preview.textures.generateAll();

        CharacterMesh builder = new CharacterMesh();
        MeshData body = builder.buildBody();
        MeshData apron = builder.buildApron();
        MeshData[] hair = new MeshData[3];
        for (int i = 0; i < hair.length; i++) hair[i] = builder.buildHair(i);

        Appearance shopkeeper = new Appearance();
        shopkeeper.skin = 0xEEC5A2;
        shopkeeper.hair = 0x3E2A1B;
        shopkeeper.shirt = 0x2F6E8E;
        shopkeeper.trousers = 0x37414F;
        shopkeeper.shoes = 0x3A3230;
        shopkeeper.markDirty();

        // Front, three-quarter and side, plus a close-up of the head and a walk pose.
        preview.apron = apron;
        preview.render(body, hair[0], shopkeeper, "idle", 0f, 0f, 1.6f, 0.90f, 3.2f);
        preview.save(outDir + "/character-front.png");

        preview.render(body, hair[0], shopkeeper, "idle", 2.4f, 0f, 1.6f, 0.90f, 3.2f);
        preview.save(outDir + "/character-back.png");

        preview.render(body, hair[0], shopkeeper, "idle", 1.1f, 0f, 1.6f, 0.90f, 3.2f);
        preview.save(outDir + "/character-three-quarter.png");

        preview.render(body, hair[0], shopkeeper, "idle", 0.25f, -0.10f, 1.6f, 1.60f, 0.70f);
        preview.save(outDir + "/character-head.png");

        preview.render(body, hair[0], shopkeeper, "walk", 1.2f, 0f, 1.6f, 0.90f, 3.2f);
        preview.save(outDir + "/character-walk.png");

        preview.render(body, hair[0], shopkeeper, "carry", 1.2f, 0f, 1.6f, 0.90f, 3.2f);
        preview.save(outDir + "/character-carry.png");

        System.out.println("wrote previews to " + outDir);
    }

    private void render(MeshData body, MeshData hair, Appearance look, String pose,
                        float orbit, float tilt, float distanceScale,
                        float lookAtY, float frameHeight) {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF20242A;
            depth[i] = Float.MAX_VALUE;
        }

        Skeleton skeleton = new Skeleton();
        Animator animator = new Animator();
        animator.personalPhase = 0.4f;
        if ("walk".equals(pose)) {
            animator.locomotion = 1f;
            animator.speed = 2.3f;
            for (int i = 0; i < 9; i++) animator.update(1f / 24f);
        } else if ("carry".equals(pose)) {
            animator.carry = 1f;
            animator.update(0.2f);
        } else {
            animator.update(0.7f);
        }
        animator.pose(skeleton);
        skeleton.computePose(0f, 0f, 0f, orbit, look.height);

        // Camera framing the given height range from the given angle.
        float distance = frameHeight * distanceScale;
        Vec3 eye = new Vec3(
                (float) Math.sin(orbit * 0f) * 0f,
                lookAtY + frameHeight * tilt,
                distance);
        Vec3 target = new Vec3(0f, lookAtY, 0f);
        Mat4 view = new Mat4().setLookAt(eye, target, new Vec3(0f, 1f, 0f));
        Mat4 projection = new Mat4().setPerspective(34f, (float) WIDTH / HEIGHT, 0.05f, 40f);
        Mat4 viewProjection = new Mat4();
        Mat4.multiply(projection, view, viewProjection);

        float[] tints = look.materialTints();
        drawMesh(body, skeleton, viewProjection, tints, eye);
        drawMesh(hair, skeleton, viewProjection, tints, eye);
        if (apron != null) drawMesh(apron, skeleton, viewProjection, tints, eye);
    }

    private final Vec3 tmpIn = new Vec3();
    private final Vec3 tmpA = new Vec3();
    private final Vec3 tmpB = new Vec3();

    private void drawMesh(MeshData mesh, Skeleton skeleton, Mat4 viewProjection,
                          float[] tints, Vec3 eye) {
        int count = mesh.vertexCount();
        float[] sx = new float[count], sy = new float[count], sz = new float[count];
        float[] wz = new float[count];
        float[] nx = new float[count], ny = new float[count], nz = new float[count];

        for (int v = 0; v < count; v++) {
            tmpIn.set(mesh.get(v, 0), mesh.get(v, 1), mesh.get(v, 2));
            skinPoint(mesh, v, skeleton, tmpIn, tmpA);
            tmpIn.set(mesh.get(v, 3), mesh.get(v, 4), mesh.get(v, 5));
            skinDirection(mesh, v, skeleton, tmpIn, tmpB);
            nx[v] = tmpB.x; ny[v] = tmpB.y; nz[v] = tmpB.z;

            float[] m = viewProjection.m;
            float cx = m[0] * tmpA.x + m[4] * tmpA.y + m[8] * tmpA.z + m[12];
            float cy = m[1] * tmpA.x + m[5] * tmpA.y + m[9] * tmpA.z + m[13];
            float cz = m[2] * tmpA.x + m[6] * tmpA.y + m[10] * tmpA.z + m[14];
            float cw = m[3] * tmpA.x + m[7] * tmpA.y + m[11] * tmpA.z + m[15];
            if (cw < 1e-4f) cw = 1e-4f;
            sx[v] = (cx / cw * 0.5f + 0.5f) * WIDTH;
            sy[v] = (0.5f - cy / cw * 0.5f) * HEIGHT;
            sz[v] = cz / cw;
            wz[v] = cw;
        }

        for (int i = 0; i < mesh.indexCount; i += 3) {
            int a = mesh.indices[i], b = mesh.indices[i + 1], c = mesh.indices[i + 2];
            triangle(mesh, a, b, c, sx, sy, sz, wz, nx, ny, nz, tints);
        }
    }

    private void skinPoint(MeshData mesh, int v, Skeleton skeleton, Vec3 in, Vec3 out) {
        int b0 = (int) mesh.get(v, MeshData.OFFSET_BONE_INDEX);
        int b1 = (int) mesh.get(v, MeshData.OFFSET_BONE_INDEX + 1);
        float w0 = mesh.get(v, MeshData.OFFSET_BONE_WEIGHT);
        float w1 = mesh.get(v, MeshData.OFFSET_BONE_WEIGHT + 1);
        Vec3 p = new Vec3(), q = new Vec3();
        skeleton.skin[b0].transformPoint(in, p);
        skeleton.skin[b1].transformPoint(in, q);
        out.set(p.x * w0 + q.x * w1, p.y * w0 + q.y * w1, p.z * w0 + q.z * w1);
    }

    private void skinDirection(MeshData mesh, int v, Skeleton skeleton, Vec3 in, Vec3 out) {
        int b0 = (int) mesh.get(v, MeshData.OFFSET_BONE_INDEX);
        int b1 = (int) mesh.get(v, MeshData.OFFSET_BONE_INDEX + 1);
        float w0 = mesh.get(v, MeshData.OFFSET_BONE_WEIGHT);
        float w1 = mesh.get(v, MeshData.OFFSET_BONE_WEIGHT + 1);
        Vec3 p = new Vec3(), q = new Vec3();
        skeleton.skin[b0].transformDirection(in, p);
        skeleton.skin[b1].transformDirection(in, q);
        out.set(p.x * w0 + q.x * w1, p.y * w0 + q.y * w1, p.z * w0 + q.z * w1).normalize();
    }

    private void triangle(MeshData mesh, int a, int b, int c,
                          float[] sx, float[] sy, float[] sz, float[] wz,
                          float[] nx, float[] ny, float[] nz, float[] tints) {
        float x0 = sx[a], y0 = sy[a], x1 = sx[b], y1 = sy[b], x2 = sx[c], y2 = sy[c];
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (area >= -1e-6f) return; // back face, given the screen-space y flip

        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(WIDTH - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(HEIGHT - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        if (minX > maxX || minY > maxY) return;

        int material = (int) mesh.get(a, MeshData.OFFSET_MATERIAL);
        float invArea = 1f / area;

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float cxp = px + 0.5f, cyp = py + 0.5f;
                float w0 = ((x1 - cxp) * (y2 - cyp) - (x2 - cxp) * (y1 - cyp)) * invArea;
                float w1 = ((x2 - cxp) * (y0 - cyp) - (x0 - cxp) * (y2 - cyp)) * invArea;
                float w2 = 1f - w0 - w1;
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue;

                float z = sz[a] * w0 + sz[b] * w1 + sz[c] * w2;
                int index = py * WIDTH + px;
                if (z >= depth[index]) continue;
                depth[index] = z;

                // Perspective-correct interpolation of the attributes.
                float iw = w0 / wz[a] + w1 / wz[b] + w2 / wz[c];
                float pu = (mesh.get(a, MeshData.OFFSET_UV) * w0 / wz[a]
                        + mesh.get(b, MeshData.OFFSET_UV) * w1 / wz[b]
                        + mesh.get(c, MeshData.OFFSET_UV) * w2 / wz[c]) / iw;
                float pv = (mesh.get(a, MeshData.OFFSET_UV + 1) * w0 / wz[a]
                        + mesh.get(b, MeshData.OFFSET_UV + 1) * w1 / wz[b]
                        + mesh.get(c, MeshData.OFFSET_UV + 1) * w2 / wz[c]) / iw;

                float n0 = nx[a] * w0 + nx[b] * w1 + nx[c] * w2;
                float n1 = ny[a] * w0 + ny[b] * w1 + ny[c] * w2;
                float n2 = nz[a] * w0 + nz[b] * w1 + nz[c] * w2;
                float len = (float) Math.sqrt(n0 * n0 + n1 * n1 + n2 * n2);
                if (len > 1e-5f) { n0 /= len; n1 /= len; n2 /= len; }

                int texel = sample(material, pu, pv);
                float tr = ((texel >> 16) & 0xFF) / 255f;
                float tg = ((texel >> 8) & 0xFF) / 255f;
                float tb = (texel & 0xFF) / 255f;

                float vr = mesh.get(a, MeshData.OFFSET_TINT);
                float vg = mesh.get(a, MeshData.OFFSET_TINT + 1);
                float vb = mesh.get(a, MeshData.OFFSET_TINT + 2);
                tr *= vr * tints[material * 3];
                tg *= vg * tints[material * 3 + 1];
                tb *= vb * tints[material * 3 + 2];

                // Key light from the front-left with a soft fill, to show form.
                float key = Math.max(0f, n0 * -0.45f + n1 * 0.55f + n2 * 0.70f);
                float fill = 0.30f + 0.22f * (n1 * 0.5f + 0.5f);
                float light = 0.15f + key * 0.95f + fill;

                pixels[index] = 0xFF000000
                        | (clamp(tr * light) << 16)
                        | (clamp(tg * light) << 8)
                        | clamp(tb * light);
            }
        }
    }

    /** Samples an albedo layer with wrapping, the way the GPU will. */
    private int sample(int material, float u, float v) {
        int[] layer = material >= 0 && material < textures.albedo.length
                ? textures.albedo[material] : null;
        if (layer == null) return 0xFFB0B0B0;
        int size = TextureFactory.SIZE;
        int x = (int) Math.floor(u * size) % size;
        int y = (int) Math.floor(v * size) % size;
        if (x < 0) x += size;
        if (y < 0) y += size;
        return layer[y * size + x];
    }

    private static int clamp(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    private void save(String path) throws Exception {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
        ImageIO.write(image, "png", new File(path));
        System.out.println("  " + path);
    }
}
