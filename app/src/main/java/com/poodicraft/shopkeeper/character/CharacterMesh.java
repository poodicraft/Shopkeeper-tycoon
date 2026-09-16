package com.poodicraft.shopkeeper.character;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;

/**
 * Builds the skinned humanoid.
 *
 * <p>One continuous mesh over {@link Skeleton}: a torso lofted through real
 * cross-sections from seat to shoulders, a pelvis the legs grow out of, limbs swept
 * as tubes that swell at the muscle and narrow at the joints, a sculpted head with
 * eye sockets, brow, cheekbones and jaw, and hands with five separate digits.
 * Clothing is a shell over the body, which is why a sleeve moves with the arm
 * inside it.
 *
 * <p>UV scales here are in <em>texture tiles per metre</em>. Every generator in
 * {@link GeometryBuilder} measures UVs in metres, so a scale of 7 puts seven
 * repeats of a weave across a metre of cloth. Leaving these at 1 is what turns
 * fabric into masonry.
 */
public final class CharacterMesh {

    private static final int LIMB_SEGMENTS = 10;
    private static final int TORSO_SEGMENTS = 14;
    private static final int HEAD_SEGMENTS = 18;
    private static final int HEAD_RINGS = 13;

    /** Texture repeats per metre, chosen so each material's detail reads at arm's length. */
    private static final float UV_SKIN = 3.0f;
    private static final float UV_FABRIC = 7.0f;
    private static final float UV_DENIM = 7.0f;
    private static final float UV_APRON = 6.0f;
    private static final float UV_HAIR = 4.0f;
    private static final float UV_LEATHER = 5.0f;

    /** Head half-extents. The bone sits at the base of the skull, not its centre. */
    private static final float HEAD_WIDTH = 0.085f;
    private static final float HEAD_HEIGHT = 0.122f;
    private static final float HEAD_DEPTH = 0.102f;
    private static final float HEAD_RISE = 0.062f;

    private final GeometryBuilder b = new GeometryBuilder();

    /**
     * Torso cross-sections: height, half-width, half-depth, and how far the section
     * is pushed forward or back. The last column is what gives a chest and a seat
     * instead of a barrel.
     */
    private static final float[][] TORSO = {
            {0.900f, 0.150f, 0.106f, -0.014f},
            {0.950f, 0.163f, 0.114f, -0.012f},
            {1.010f, 0.158f, 0.109f, -0.006f},
            {1.070f, 0.148f, 0.100f,  0.000f},
            {1.120f, 0.146f, 0.099f,  0.002f},
            {1.190f, 0.156f, 0.107f,  0.006f},
            {1.260f, 0.170f, 0.116f,  0.008f},
            {1.330f, 0.180f, 0.120f,  0.010f},
            {1.400f, 0.186f, 0.118f,  0.008f},
            {1.455f, 0.184f, 0.109f,  0.002f},
            {1.498f, 0.128f, 0.084f, -0.004f},
    };

    /** Pelvis, in trouser cloth; the thighs emerge from its underside. */
    private static final float[][] PELVIS = {
            {0.820f, 0.126f, 0.098f, -0.008f},
            {0.865f, 0.148f, 0.112f, -0.018f},
            {0.915f, 0.162f, 0.118f, -0.022f},
            {0.965f, 0.164f, 0.114f, -0.016f},
            {1.015f, 0.155f, 0.105f, -0.008f},
    };

    public MeshData buildBody() {
        MeshData mesh = new MeshData(true, 7000);
        b.target(mesh).identity().noTint().occlusion(1f);

        buildPelvisAndLegs();
        buildTorso();
        buildArms();
        buildHands();
        buildNeckAndHead();

        b.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    /**
     * The apron is its own mesh so only staff wear one.
     *
     * <p>It follows the body's contour as a wrapped sheet rather than a flat slab
     * hung off the chest, and has straps and a waist tie.
     */
    public MeshData buildApron() {
        MeshData mesh = new MeshData(true, 1800);
        b.target(mesh).identity().noTint().occlusion(1f).uv(UV_APRON);
        b.mat(Materials.APRON);
        b.skin(new SkinBinders.HeightChain(
                new int[]{Skeleton.HIPS, Skeleton.SPINE, Skeleton.CHEST},
                new float[]{0.960f, 1.110f, 1.290f}));

        // Rows of (height, half-width, half-depth, forward offset, angular half-span).
        // The radii are the torso and pelvis sections plus a standoff clear of the
        // shirt, not hand-picked numbers: pick them by eye and the shirt underneath
        // pushes through wherever the two curves happen to cross. Below the hips the
        // apron hangs free, so it stops following the body and drops straight.
        final float[][] rows = {
                {1.345f, 0.212f, 0.149f, 0.009f, 0.58f},
                {1.280f, 0.203f, 0.147f, 0.009f, 0.62f},
                {1.190f, 0.186f, 0.137f, 0.006f, 0.76f},
                {1.100f, 0.177f, 0.129f, 0.001f, 0.90f},
                {1.020f, 0.188f, 0.139f, -0.007f, 0.98f},
                {0.960f, 0.192f, 0.143f, -0.013f, 1.02f},
                {0.880f, 0.182f, 0.144f, -0.019f, 1.04f},
                {0.790f, 0.182f, 0.144f, -0.014f, 1.02f},
                {0.730f, 0.181f, 0.143f, -0.010f, 1.00f},
        };
        apronSheet(rows, 0f, 1f);
        apronSheet(rows, -0.009f, -1f);

        // Straps over the shoulders, and a tie round the waist.
        b.skin(new SkinBinders.Rigid(Skeleton.CHEST));
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(side * 0.072f, 1.412f, 0.040f);
            b.rotateY(side * 0.16f);
            b.rotateX(0.36f);
            b.roundedBox(0.038f, 0.200f, 0.012f, 0.005f, 1);
            b.pop();
        }
        b.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        b.push();
        b.translate(0f, 1.008f, -0.006f);
        b.scale(1f, 1f, 0.75f);
        b.cylinder(0.199f, 0.199f, 0.036f, 18, false, false);
        b.pop();

        b.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    /** One face of the apron: a partial ellipse sweep, wrapped round the front. */
    private void apronSheet(float[][] rows, float offset, float facing) {
        final int span = 10;
        int[][] grid = new int[rows.length][span + 1];
        for (int i = 0; i < rows.length; i++) {
            float y = rows[i][0];
            float halfWidth = rows[i][1] + offset;
            float halfDepth = rows[i][2] + offset;
            float centerZ = rows[i][3];
            float halfAngle = rows[i][4];
            for (int j = 0; j <= span; j++) {
                float t = -1f + 2f * j / span;
                float theta = t * halfAngle;
                float sin = (float) Math.sin(theta), cos = (float) Math.cos(theta);
                float px = sin * halfWidth;
                float pz = cos * halfDepth + centerZ;
                float nx = sin / halfWidth * facing;
                float nz = cos / halfDepth * facing;
                float length = (float) Math.sqrt(nx * nx + nz * nz);
                grid[i][j] = b.vertex(px, y, pz, nx / length, 0f, nz / length,
                        theta * halfWidth, y);
            }
        }
        for (int i = 0; i < rows.length - 1; i++) {
            for (int j = 0; j < span; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
    }

    /** Hair is a separate mesh so a character can pick a style. */
    public MeshData buildHair(int style) {
        MeshData mesh = new MeshData(true, 1400);
        b.target(mesh).identity();
        b.skin(new SkinBinders.Rigid(Skeleton.HEAD));
        b.mat(Materials.HAIR).occlusion(0.82f).noTint().uv(UV_HAIR);

        float cx = 0f;
        float cy = Skeleton.bindY(Skeleton.HEAD) + HEAD_RISE;
        float cz = 0f;

        int segments = 26, rings = 16;
        float lift = style == 2 ? 0.009f : 0.017f;
        int[][] grid = new int[rings + 1][segments + 1];
        boolean[][] used = new boolean[rings + 1][segments + 1];

        for (int i = 0; i <= rings; i++) {
            double phi = Math.PI * 0.68 * i / rings;
            float ny = (float) Math.cos(phi);
            float ring = (float) Math.sin(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = Math.PI * 2 * j / segments;
                float nx = (float) Math.cos(theta) * ring;
                float nz = (float) Math.sin(theta) * ring;

                // Hairline: high over the forehead, sweeping down past the ears to
                // the nape. Smoothed rather than stepped, so it is not a bad wig.
                float front = Math.max(0f, nz);
                float side = Math.abs(nx);
                // High over the forehead, dropping past the temples and down the
                // nape. A constant height here would cut a straight fringe - a
                // bowl cut - right across the face.
                float limit = 0.30f + front * front * 0.22f - side * side * 0.20f
                        - Math.max(0f, -nz) * 0.42f;
                limit += (float) Math.sin(theta * 3.0) * 0.022f;
                if (style == 1) limit -= 0.14f;
                if (style == 2) limit += 0.10f;
                if (ny < limit) {
                    used[i][j] = false;
                    continue;
                }
                // Thicker over the crown, tapering to nothing at the hairline, so the
                // edge feathers instead of ending in a cliff.
                float edge = SkinBinders.smoothstep(limit, limit + 0.30f, ny);
                float radius = headRadius(nx, ny, nz) + lift * (0.25f + 0.75f * edge);
                if (style == 0) radius += Math.max(0f, nz) * 0.008f * edge;
                grid[i][j] = b.vertex(
                        cx + nx * radius * HEAD_WIDTH,
                        cy + ny * radius * HEAD_HEIGHT,
                        cz + nz * radius * HEAD_DEPTH,
                        nx, ny, nz,
                        (float) (theta * HEAD_WIDTH), (float) (phi * HEAD_HEIGHT));
                used[i][j] = true;
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                if (used[i][j] && used[i][j + 1] && used[i + 1][j + 1] && used[i + 1][j]) {
                    b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
                }
            }
        }

        if (style == 1) {
            // Shoulder-length fall behind the ears.
            for (int side = -1; side <= 1; side += 2) {
                b.push();
                b.translate(cx + side * 0.072f, cy - 0.115f, cz - 0.040f);
                b.rotateZ(side * 0.09f);
                b.scale(1f, 1f, 0.72f);
                b.ellipsoid(0.042f, 0.135f, 0.062f, 10, 7);
                b.pop();
            }
            b.push();
            b.translate(cx, cy - 0.085f, cz - 0.086f);
            b.scale(1.35f, 1f, 0.55f);
            b.ellipsoid(0.058f, 0.105f, 0.060f, 12, 8);
            b.pop();
        }
        b.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    // ------------------------------------------------------------------ shaping

    /**
     * Radius multiplier that turns a sphere into a head: a flat back, a brow ridge,
     * cheekbones, eye sockets and a jaw that tapers to a chin.
     */
    private static float headRadius(float nx, float ny, float nz) {
        float r = 1f;
        float front = Math.max(0f, nz);
        float down = Math.max(0f, -ny);
        float side = Math.abs(nx);

        // Jaw narrows below the cheekbones but stays square enough to read as a jaw
        // rather than the small end of an egg.
        float jaw = SkinBinders.smoothstep(0.18f, 0.92f, down);
        r -= jaw * 0.12f * (1f - front * 0.45f);
        r -= jaw * side * side * 0.07f;
        // Jawline corner below the ear, and a chin at the front.
        float jawCorner = SkinBinders.smoothstep(0.15f, 0.55f, down)
                * (1f - SkinBinders.smoothstep(0.55f, 0.85f, down))
                * SkinBinders.smoothstep(0.30f, 0.75f, side);
        r += jawCorner * 0.045f;
        r += jaw * front * front * 0.10f;
        // Brow ridge over the eyes.
        float brow = SkinBinders.smoothstep(0.04f, 0.26f, ny)
                * (1f - SkinBinders.smoothstep(0.26f, 0.58f, ny));
        r += brow * front * 0.050f;
        // Eye sockets just under the brow, set back a little.
        float socket = SkinBinders.smoothstep(-0.02f, 0.12f, ny)
                * (1f - SkinBinders.smoothstep(0.12f, 0.32f, ny))
                * SkinBinders.smoothstep(0.20f, 0.55f, side)
                * (1f - SkinBinders.smoothstep(0.62f, 0.92f, side));
        r -= socket * front * 0.045f;
        // Cheekbones.
        float cheek = SkinBinders.smoothstep(0.05f, 0.42f, down) * side * front;
        r += cheek * 0.060f;
        // The back of the skull is flatter than a sphere.
        r -= Math.max(0f, -nz) * 0.060f;
        // Temples pull in slightly above the ears.
        r -= SkinBinders.smoothstep(0.25f, 0.60f, ny) * side * 0.035f;
        return r;
    }

    // -------------------------------------------------------------- lower body

    private void buildPelvisAndLegs() {
        b.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        b.mat(Materials.DENIM).occlusion(0.94f).noTint().uv(UV_DENIM);
        loft(PELVIS, 0f, TORSO_SEGMENTS, true, true);

        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int thigh = left ? Skeleton.THIGH_L : Skeleton.THIGH_R;
            int shin = left ? Skeleton.SHIN_L : Skeleton.SHIN_R;
            int foot = left ? Skeleton.FOOT_L : Skeleton.FOOT_R;
            float lx = Skeleton.bindX(thigh);
            float hipY = Skeleton.bindY(thigh);
            float kneeY = Skeleton.bindY(shin);
            float ankleY = Skeleton.bindY(foot);
            float mirror = left ? 1f : -1f;

            // Thigh: heaviest at the top, tucking in at the knee. It starts up inside
            // the pelvis so there is no seam where the leg meets the hip.
            b.skin(new SkinBinders.Segment(Skeleton.HIPS, thigh,
                    lx, hipY + 0.08f, 0f, lx, kneeY, 0f, 0.10f, 0.46f));
            b.mat(Materials.DENIM).occlusion(0.93f).uv(UV_DENIM);
            float[] thighPath = {
                    lx - mirror * 0.006f, hipY + 0.075f, -0.006f,
                    lx, hipY - 0.070f, 0.002f,
                    lx + mirror * 0.004f, hipY - 0.230f, 0.004f,
                    lx + mirror * 0.004f, kneeY + 0.022f, 0.002f,
            };
            b.tube(thighPath, new float[]{0.098f, 0.093f, 0.077f, 0.068f},
                    LIMB_SEGMENTS, false);

            // Shin, with the calf bulge sitting behind the bone.
            b.skin(new SkinBinders.Segment(thigh, shin,
                    lx, kneeY + 0.06f, 0f, lx, ankleY, 0f, 0.06f, 0.38f));
            float[] shinPath = {
                    lx + mirror * 0.004f, kneeY + 0.032f, 0.002f,
                    lx + mirror * 0.003f, kneeY - 0.090f, -0.012f,
                    lx + mirror * 0.002f, kneeY - 0.240f, -0.004f,
                    lx + mirror * 0.001f, ankleY + 0.035f, 0.004f,
            };
            b.tube(shinPath, new float[]{0.066f, 0.067f, 0.050f, 0.038f},
                    LIMB_SEGMENTS, false);

            // Trouser hem breaking over the shoe.
            b.push();
            b.translate(lx + mirror * 0.001f, ankleY + 0.046f, 0.002f);
            b.cylinder(0.052f, 0.049f, 0.046f, LIMB_SEGMENTS, false, false);
            b.pop();

            buildShoe(foot, lx, ankleY, mirror);
        }
        b.skin(null);
    }

    /** Sole, upper, toe box, heel counter and laces, rigid to the foot bone. */
    private void buildShoe(int footBone, float lx, float ankleY, float mirror) {
        b.skin(new SkinBinders.Rigid(footBone));
        b.uv(UV_LEATHER);

        b.push();
        b.translate(lx, ankleY - 0.042f, 0.032f);

        b.mat(Materials.RUBBER_MAT).occlusion(0.80f).tint(0.78f, 0.78f, 0.80f);
        b.push();
        b.translate(0f, -0.012f, 0.012f);
        b.roundedBox(0.090f, 0.028f, 0.256f, 0.012f, 1);
        b.pop();

        b.mat(Materials.LEATHER).occlusion(0.88f).tint(0.32f, 0.28f, 0.26f);
        b.push();
        b.translate(0f, 0.026f, 0.026f);
        b.roundedBox(0.083f, 0.052f, 0.208f, 0.022f, 2);
        b.pop();
        b.push();
        b.translate(0f, 0.016f, 0.100f);
        b.scale(1f, 0.76f, 1f);
        b.ellipsoid(0.042f, 0.042f, 0.050f, 10, 6);
        b.pop();
        b.push();
        b.translate(0f, 0.046f, -0.060f);
        b.roundedBox(0.078f, 0.078f, 0.058f, 0.022f, 1);
        b.pop();

        b.mat(Materials.LEATHER).tint(0.23f, 0.21f, 0.20f);
        b.push();
        b.translate(0f, 0.060f, 0.030f);
        b.rotateX(-0.20f);
        b.roundedBox(0.050f, 0.012f, 0.090f, 0.005f, 1);
        b.pop();
        b.mat(Materials.PAPER).tint(0.88f, 0.86f, 0.82f);
        for (int i = 0; i < 3; i++) {
            b.push();
            b.translate(0f, 0.066f - i * 0.002f, 0.004f + i * 0.030f);
            b.rotateZ(0.12f);
            b.roundedBox(0.054f, 0.006f, 0.009f, 0.002f, 1);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    // -------------------------------------------------------------- upper body

    private void buildTorso() {
        b.skin(new SkinBinders.HeightChain(
                new int[]{Skeleton.HIPS, Skeleton.SPINE, Skeleton.CHEST},
                new float[]{0.960f, 1.110f, 1.290f}));

        // The body underneath, in skin. Only the neckline shows it, but without it
        // the collar opening looks into an empty shell.
        b.mat(Materials.SKIN).occlusion(0.86f).noTint().uv(UV_SKIN);
        loft(TORSO, 0f, TORSO_SEGMENTS, true, true);

        // Shirt: the same sections pushed out, hemmed at the hip over the waistband.
        b.mat(Materials.SHIRT).occlusion(0.96f).uv(UV_FABRIC);
        loft(TORSO, 0.013f, TORSO_SEGMENTS, true, true, 0.955f);

        // Hem lip, so the shirt ends in an edge rather than a cut.
        b.push();
        b.translate(0f, 0.962f, -0.012f);
        b.scale(1f, 1f, 0.78f);
        b.cylinder(0.172f, 0.178f, 0.032f, TORSO_SEGMENTS, false, false);
        b.pop();

        // Open collar around the neck.
        b.push();
        b.translate(0f, 1.482f, -0.006f);
        b.scale(1f, 1f, 0.88f);
        b.cylinder(0.076f, 0.082f, 0.030f, 16, false, false);
        b.pop();

        // Waistband and belt.
        b.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        b.mat(Materials.LEATHER).occlusion(0.82f).tint(0.26f, 0.21f, 0.17f).uv(UV_LEATHER);
        b.push();
        b.translate(0f, 1.000f, -0.010f);
        b.scale(1f, 1f, 0.64f);
        b.cylinder(0.150f, 0.148f, 0.042f, 16, false, false);
        b.pop();
        b.skin(null);
    }

    private void buildArms() {
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int upper = left ? Skeleton.UPPERARM_L : Skeleton.UPPERARM_R;
            int fore = left ? Skeleton.FOREARM_L : Skeleton.FOREARM_R;
            int hand = left ? Skeleton.HAND_L : Skeleton.HAND_R;
            float sx = Skeleton.bindX(upper);
            float shoulderY = Skeleton.bindY(upper);
            float elbowY = Skeleton.bindY(fore);
            float wristY = Skeleton.bindY(hand);
            float mirror = left ? 1f : -1f;

            // Shoulder: the arm grows out of the torso as a swept tube that starts
            // inside it, rather than a ball parked on top. A separate deltoid
            // sphere always ends up proud of the shoulder line, which is what
            // reads as a puffed sleeve - and it has to be there at all only
            // because an open tube end would otherwise show a hole.
            b.skin(new SkinBinders.Segment(Skeleton.CHEST, upper,
                    sx * 0.35f, shoulderY + 0.05f, 0f, sx * 1.02f, shoulderY - 0.07f, 0f,
                    0.22f, 0.96f));
            b.mat(Materials.SHIRT).occlusion(0.93f).noTint().uv(UV_FABRIC);
            // The first ring sits well inside the torso so its end cap is buried; poking
            // out even a centimetre leaves a visible notch at the shoulder seam.
            float[] shoulderPath = {
                    sx * 0.30f, shoulderY + 0.020f, -0.004f,
                    sx * 0.62f, shoulderY - 0.002f, 0f,
                    sx * 0.88f, shoulderY - 0.036f, 0.002f,
                    sx * 0.99f, shoulderY - 0.062f, 0.002f,
            };
            b.tube(shoulderPath, new float[]{0.086f, 0.078f, 0.068f, 0.063f},
                    LIMB_SEGMENTS, true);

            // Upper arm, sleeved to just above the elbow, continuing from the shoulder.
            b.skin(new SkinBinders.Segment(upper, fore,
                    sx, shoulderY, 0f, sx, elbowY, 0f, 0.64f, 0.98f));
            float[] upperPath = {
                    sx * 0.99f, shoulderY - 0.062f, 0.002f,
                    sx + mirror * 0.004f, shoulderY - 0.130f, 0.003f,
                    sx + mirror * 0.011f, shoulderY - 0.190f, 0.004f,
                    sx + mirror * 0.013f, elbowY + 0.014f, 0.004f,
            };
            b.tube(upperPath, new float[]{0.063f, 0.058f, 0.051f, 0.047f},
                    LIMB_SEGMENTS, false);

            // Sleeve cuff.
            b.push();
            b.translate(sx + mirror * 0.013f, elbowY + 0.006f, 0.004f);
            b.cylinder(0.052f, 0.049f, 0.028f, LIMB_SEGMENTS, false, false);
            b.pop();

            // Forearm, bare: full at the elbow, narrowing to the wrist.
            b.skin(new SkinBinders.Segment(fore, hand,
                    sx, elbowY, 0f, sx, wristY, 0f, 0.74f, 1f));
            float[] forePath = {
                    sx + mirror * 0.013f, elbowY + 0.024f, 0.004f,
                    sx + mirror * 0.015f, elbowY - 0.060f, 0.007f,
                    sx + mirror * 0.016f, elbowY - 0.145f, 0.009f,
                    sx + mirror * 0.017f, wristY + 0.010f, 0.010f,
            };
            b.mat(Materials.SKIN).occlusion(0.96f).uv(UV_SKIN);
            b.tube(forePath, new float[]{0.048f, 0.044f, 0.036f, 0.030f},
                    LIMB_SEGMENTS, false);
        }
        b.skin(null);
    }

    /** Palm plus five digits, each tapered and slightly curled. */
    private void buildHands() {
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int hand = left ? Skeleton.HAND_L : Skeleton.HAND_R;
            float hx = Skeleton.bindX(hand);
            float hy = Skeleton.bindY(hand);
            float hz = Skeleton.bindZ(hand);
            float mirror = left ? 1f : -1f;

            b.skin(new SkinBinders.Rigid(hand));
            b.mat(Materials.SKIN).occlusion(0.95f).noTint().uv(UV_SKIN);

            b.push();
            b.translate(hx, hy - 0.040f, hz);
            b.roundedBox(0.058f, 0.088f, 0.032f, 0.014f, 2);
            b.pop();

            final float[] fingerLength = {0.056f, 0.062f, 0.058f, 0.047f};
            final float[] fingerRadius = {0.0103f, 0.0110f, 0.0103f, 0.0092f};
            for (int f = 0; f < 4; f++) {
                float offsetX = (-0.021f + f * 0.0140f) * mirror;
                float baseY = hy - 0.084f;
                float length = fingerLength[f];
                float curl = 0.24f + f * 0.02f;
                b.push();
                b.translate(hx + offsetX, baseY, hz + 0.001f);
                b.rotateX(curl);
                float[] path = {
                        0f, 0f, 0f,
                        0f, -length * 0.42f, length * 0.05f,
                        0f, -length * 0.78f, length * 0.16f,
                        0f, -length, length * 0.27f,
                };
                float r = fingerRadius[f];
                b.tube(path, new float[]{r, r * 0.95f, r * 0.85f, r * 0.72f}, 6, true);
                b.pop();
            }

            b.push();
            b.translate(hx + 0.029f * mirror, hy - 0.030f, hz + 0.010f);
            b.rotateZ(mirror * 0.78f);
            b.rotateX(0.42f);
            float[] thumbPath = {
                    0f, 0f, 0f,
                    0f, -0.021f, 0.007f,
                    0f, -0.040f, 0.016f,
            };
            b.tube(thumbPath, new float[]{0.0128f, 0.0116f, 0.0096f}, 6, true);
            b.pop();
        }
        b.skin(null);
    }

    // ------------------------------------------------------------- head & face

    private void buildNeckAndHead() {
        float neckY = Skeleton.bindY(Skeleton.NECK);
        float headY = Skeleton.bindY(Skeleton.HEAD);
        float centreY = headY + HEAD_RISE;

        b.skin(new SkinBinders.Segment(Skeleton.CHEST, Skeleton.NECK,
                0f, neckY - 0.13f, 0f, 0f, neckY + 0.07f, 0f, 0.12f, 0.86f));
        b.mat(Materials.SKIN).occlusion(0.80f).noTint().uv(UV_SKIN);
        b.push();
        b.translate(0f, neckY - 0.105f, -0.004f);
        b.rotateX(-0.06f);
        b.cylinder(0.066f, 0.058f, 0.150f, 14, false, false);
        b.pop();

        b.skin(new SkinBinders.Segment(Skeleton.NECK, Skeleton.HEAD,
                0f, neckY, 0f, 0f, headY, 0f, 0.05f, 0.60f));
        b.occlusion(1f);
        buildHead(0f, centreY, 0f);

        b.skin(new SkinBinders.Rigid(Skeleton.HEAD));
        buildFace(0f, centreY, 0f);
        b.skin(null);
    }

    private void buildHead(float cx, float cy, float cz) {
        int[][] grid = new int[HEAD_RINGS + 1][HEAD_SEGMENTS + 1];
        for (int i = 0; i <= HEAD_RINGS; i++) {
            double phi = Math.PI * i / HEAD_RINGS;
            float ny = (float) Math.cos(phi);
            float ring = (float) Math.sin(phi);
            for (int j = 0; j <= HEAD_SEGMENTS; j++) {
                double theta = Math.PI * 2 * j / HEAD_SEGMENTS;
                float nx = (float) Math.cos(theta) * ring;
                float nz = (float) Math.sin(theta) * ring;
                float r = headRadius(nx, ny, nz);
                grid[i][j] = b.vertex(
                        cx + nx * r * HEAD_WIDTH,
                        cy + ny * r * HEAD_HEIGHT,
                        cz + nz * r * HEAD_DEPTH,
                        nx, ny, nz,
                        (float) (theta * HEAD_WIDTH), (float) (phi * HEAD_HEIGHT));
            }
        }
        for (int i = 0; i < HEAD_RINGS; i++) {
            for (int j = 0; j < HEAD_SEGMENTS; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }

        // Ears: small, flat against the skull, set level with the eyes and swept back.
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(cx + side * HEAD_WIDTH * 0.93f, cy + 0.006f, cz - 0.020f);
            b.rotateY(side * 0.30f);
            b.rotateZ(side * -0.12f);
            b.scale(0.34f, 1f, 1f);
            b.ellipsoid(0.029f, 0.038f, 0.023f, 7, 5);
            b.pop();
        }
    }

    /**
     * Places a feature on the head's actual surface.
     *
     * <p>The head is a deformed ellipsoid, so its surface is nowhere near a fixed
     * depth: guessing one puts the eyes and nose <em>inside</em> the skull, where
     * they are simply invisible. This evaluates the same shaping function the head
     * mesh uses and returns the point and its outward normal.
     *
     * @return position xyz followed by normal xyz
     */
    private static float[] onFace(float cx, float cy, float cz,
                                  float dx, float dy, float dz, float outward) {
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length; dy /= length; dz /= length;
        float r = headRadius(dx, dy, dz);

        float nx = dx / HEAD_WIDTH, ny = dy / HEAD_HEIGHT, nz = dz / HEAD_DEPTH;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;

        return new float[]{
                cx + dx * r * HEAD_WIDTH + nx * outward,
                cy + dy * r * HEAD_HEIGHT + ny * outward,
                cz + dz * r * HEAD_DEPTH + nz * outward,
                nx, ny, nz,
        };
    }

    /** Eyes in their sockets, brows, a nose and a mouth. */
    private void buildFace(float cx, float cy, float cz) {
        for (int side = -1; side <= 1; side += 2) {
            // Eyeball set into the socket so the cornea sits just proud of the lids.
            float[] eye = onFace(cx, cy, cz, side * 0.46f, 0.13f, 0.88f, -0.0145f);
            b.mat(Materials.CERAMIC).occlusion(0.76f).tint(0.95f, 0.95f, 0.93f).uv(4f);
            b.push();
            b.translate(eye[0], eye[1], eye[2]);
            b.sphere(0.0190f, 10, 7);
            b.pop();

            // Iris and pupil sit where the eye is looking, which is forward. Putting
            // them on the socket's outward normal instead makes the character
            // wall-eyed, with each iris drifting to the outside of its own ball.
            final float gazeX = side * 0.10f, gazeY = 0.04f, gazeZ = 1f;
            float gazeLength = (float) Math.sqrt(gazeX * gazeX + gazeY * gazeY + gazeZ * gazeZ);
            float gx = gazeX / gazeLength, gy = gazeY / gazeLength, gz = gazeZ / gazeLength;

            b.mat(Materials.PLASTIC).occlusion(0.88f).tint(0.30f, 0.45f, 0.54f).uv(6f);
            b.push();
            b.translate(eye[0] + gx * 0.0163f, eye[1] + gy * 0.0163f, eye[2] + gz * 0.0163f);
            b.scale(1f, 1f, 0.42f);
            b.sphere(0.0098f, 10, 6);
            b.pop();
            b.mat(Materials.PLASTIC).tint(0.05f, 0.05f, 0.06f);
            b.push();
            b.translate(eye[0] + gx * 0.0186f, eye[1] + gy * 0.0186f, eye[2] + gz * 0.0186f);
            b.scale(1f, 1f, 0.34f);
            b.sphere(0.0048f, 6, 4);
            b.pop();

            // Lids, cutting the top and bottom of the ball.
            b.mat(Materials.SKIN).occlusion(0.90f).noTint().uv(UV_SKIN);
            float[] upper = onFace(cx, cy, cz, side * 0.44f, 0.27f, 0.86f, -0.004f);
            b.push();
            b.translate(upper[0], upper[1], upper[2]);
            b.rotateY(side * -0.30f);
            b.rotateX(0.34f);
            b.scale(1.40f, 0.30f, 0.92f);
            b.ellipsoid(0.0205f, 0.0205f, 0.0205f, 10, 5);
            b.pop();
            float[] lower = onFace(cx, cy, cz, side * 0.44f, -0.02f, 0.88f, -0.006f);
            b.push();
            b.translate(lower[0], lower[1], lower[2]);
            b.rotateY(side * -0.30f);
            b.rotateX(-0.28f);
            b.scale(1.28f, 0.24f, 0.88f);
            b.ellipsoid(0.0195f, 0.0195f, 0.0195f, 10, 5);
            b.pop();

            // Brow: a ridge of hair sitting on the surface above the eye.
            float[] brow = onFace(cx, cy, cz, side * 0.44f, 0.36f, 0.83f, 0.003f);
            b.mat(Materials.HAIR).occlusion(0.78f).noTint().uv(UV_HAIR);
            b.push();
            b.translate(brow[0], brow[1], brow[2]);
            b.rotateY(side * -0.32f);
            b.rotateZ(side * -0.18f);
            b.rotateX(-0.30f);
            b.scale(2.15f, 0.34f, 0.45f);
            b.ellipsoid(0.0180f, 0.0180f, 0.0180f, 8, 4);
            b.pop();
        }

        // Nose: bridge from between the brows, a rounded tip and nostril wings, each
        // anchored to the surface it grows out of.
        b.mat(Materials.SKIN).occlusion(0.94f).noTint().uv(UV_SKIN);
        float[] bridge = onFace(cx, cy, cz, 0f, 0.16f, 0.99f, 0.000f);
        float[] tip = onFace(cx, cy, cz, 0f, -0.16f, 0.99f, 0.012f);
        b.push();
        b.translate((bridge[0] + tip[0]) * 0.5f, (bridge[1] + tip[1]) * 0.5f,
                (bridge[2] + tip[2]) * 0.5f);
        b.rotateX(-0.20f);
        b.scale(0.38f, 1.70f, 0.62f);
        b.ellipsoid(0.0195f, 0.0195f, 0.0195f, 8, 5);
        b.pop();
        b.push();
        b.translate(tip[0], tip[1], tip[2]);
        b.scale(0.92f, 0.78f, 0.92f);
        b.ellipsoid(0.0128f, 0.0128f, 0.0128f, 8, 5);
        b.pop();
        for (int side = -1; side <= 1; side += 2) {
            float[] wing = onFace(cx, cy, cz, side * 0.14f, -0.20f, 0.97f, 0.001f);
            b.push();
            b.translate(wing[0], wing[1], wing[2]);
            b.scale(0.74f, 0.70f, 0.80f);
            b.ellipsoid(0.0094f, 0.0094f, 0.0094f, 6, 4);
            b.pop();
        }

        // Mouth: two lips with a darker seam between them, sized to read at a
        // distance without turning into a smear.
        float[] mouth = onFace(cx, cy, cz, 0f, -0.42f, 0.90f, -0.002f);
        b.push();
        b.translate(mouth[0], mouth[1], mouth[2]);
        b.rotateX(-0.18f);
        b.mat(Materials.SKIN).occlusion(0.90f).tint(0.93f, 0.75f, 0.70f).uv(UV_SKIN);
        b.push();
        b.translate(0f, 0.0055f, 0.0008f);
        b.scale(2.05f, 0.34f, 0.42f);
        b.ellipsoid(0.0128f, 0.0128f, 0.0128f, 10, 5);
        b.pop();
        b.push();
        b.translate(0f, -0.0052f, 0.0012f);
        b.scale(1.90f, 0.40f, 0.46f);
        b.ellipsoid(0.0128f, 0.0128f, 0.0128f, 10, 5);
        b.pop();
        b.mat(Materials.SKIN).tint(0.55f, 0.36f, 0.33f);
        b.push();
        b.translate(0f, 0.0002f, 0.0022f);
        b.scale(2.00f, 0.075f, 0.20f);
        b.ellipsoid(0.0128f, 0.0128f, 0.0128f, 10, 4);
        b.pop();
        b.pop();
        b.noTint();
    }

    // ---------------------------------------------------------------- lofting

    private void loft(float[][] sections, float inflate, int segments,
                      boolean capBottom, boolean capTop) {
        loft(sections, inflate, segments, capBottom, capTop, -1f);
    }

    /**
     * Lofts a closed shell through the given cross-sections.
     *
     * @param floorY sections below this height are lifted to it, which is how the
     *               shirt gets a hem at the hip instead of running to the crotch
     */
    private void loft(float[][] sections, float inflate, int segments,
                      boolean capBottom, boolean capTop, float floorY) {
        int rows = sections.length;
        int[][] grid = new int[rows][segments + 1];
        float[][] used = new float[rows][];

        for (int i = 0; i < rows; i++) {
            float y = Math.max(sections[i][0], floorY);
            float halfWidth = sections[i][1] + inflate;
            float halfDepth = sections[i][2] + inflate;
            float centerZ = sections[i][3];
            used[i] = new float[]{y, halfWidth, halfDepth, centerZ};

            for (int j = 0; j <= segments; j++) {
                double theta = Math.PI * 2 * j / segments;
                float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
                float px = c * halfWidth;
                float pz = s * halfDepth + centerZ;
                float nx = c / halfWidth, nz = s / halfDepth;
                float ny = 0f;
                if (i > 0 && i < rows - 1) {
                    float dr = sections[i + 1][1] - sections[i - 1][1];
                    float dy = sections[i + 1][0] - sections[i - 1][0];
                    ny = dy > 1e-5f ? -dr / dy * 0.55f : 0f;
                }
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1e-6f) length = 1f;
                grid[i][j] = b.vertex(px, y, pz, nx / length, ny / length, nz / length,
                        (float) (theta * (halfWidth + halfDepth) * 0.5), y);
            }
        }
        for (int i = 0; i < rows - 1; i++) {
            // Skip rows collapsed onto the floor height by the hem.
            if (Math.abs(used[i][0] - used[i + 1][0]) < 1e-5f) continue;
            for (int j = 0; j < segments; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
        if (capBottom) cap(firstVisible(used), -1f, segments);
        if (capTop) cap(used[rows - 1], 1f, segments);
    }

    private static float[] firstVisible(float[][] used) {
        for (int i = 0; i < used.length - 1; i++) {
            if (Math.abs(used[i][0] - used[i + 1][0]) > 1e-5f) return used[i];
        }
        return used[0];
    }

    /** Closes a loft end with its own fan, carrying the cap's normal. */
    private void cap(float[] section, float facing, int segments) {
        float y = section[0], halfWidth = section[1], halfDepth = section[2];
        float centerZ = section[3];
        int centre = b.vertex(0f, y, centerZ, 0f, facing, 0f, 0f, 0f);
        int prev = -1;
        for (int j = 0; j <= segments; j++) {
            double theta = Math.PI * 2 * j / segments;
            float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
            int v = b.vertex(c * halfWidth, y, s * halfDepth + centerZ, 0f, facing, 0f,
                    c * halfWidth, s * halfDepth);
            if (prev >= 0) b.triangleOriented(centre, prev, v);
            prev = v;
        }
    }
}
