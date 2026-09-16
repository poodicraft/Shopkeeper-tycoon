package com.poodicraft.shopkeeper.character;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;

/**
 * Builds the skinned humanoid.
 *
 * <p>One continuous mesh over {@link Skeleton}: torso lofted from hips to
 * shoulders, limbs swept as tubes that bulge at the muscle and narrow at the
 * joints, a sculpted head with brow, cheekbones, nose, ears and lips, and hands
 * with five separate digits. Clothing is a slightly inflated shell over the body,
 * which is why sleeves and trouser legs move with the limb underneath.
 *
 * <p>Region colours come from the material index at draw time, so every customer
 * shares this one mesh and differs only by bone matrices, height and tints.
 */
public final class CharacterMesh {

    /** Radial resolution of the limbs. Raising this is the main quality dial. */
    private static final int LIMB_SEGMENTS = 9;
    private static final int TORSO_SEGMENTS = 14;
    private static final int HEAD_SEGMENTS = 18;
    private static final int HEAD_RINGS = 13;

    private final GeometryBuilder builder = new GeometryBuilder();

    /** Builds the body, clothing and hands into one skinned mesh. */
    public MeshData buildBody(boolean wearsApron) {
        MeshData mesh = new MeshData(true, 6000);
        builder.target(mesh).identity();
        builder.uvScale = 1f;
        builder.occlusion(1f);

        buildLegs(mesh);
        buildTorso(mesh, wearsApron);
        buildArms(mesh);
        buildHands(mesh);
        buildNeckAndHead(mesh);

        builder.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    /** Hair is a separate mesh so a character can pick a style. */
    public MeshData buildHair(int style) {
        MeshData mesh = new MeshData(true, 1200);
        builder.target(mesh).identity();
        builder.skin(new SkinBinders.Rigid(Skeleton.HEAD));
        builder.mat(Materials.HAIR).occlusion(0.86f).noTint();

        float hx = Skeleton.bindX(Skeleton.HEAD);
        float hy = Skeleton.bindY(Skeleton.HEAD);
        float hz = Skeleton.bindZ(Skeleton.HEAD);

        // A shell that follows the skull and stops at a hairline.
        int segments = 20, rings = 14;
        float lift = style == 2 ? 0.020f : 0.011f;
        int[][] grid = new int[rings + 1][segments + 1];
        boolean[][] used = new boolean[rings + 1][segments + 1];
        for (int i = 0; i <= rings; i++) {
            double phi = Math.PI * 0.62 * i / rings;
            float ny = (float) Math.cos(phi);
            float ring = (float) Math.sin(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = Math.PI * 2 * j / segments;
                float nx = (float) Math.cos(theta) * ring;
                float nz = (float) Math.sin(theta) * ring;

                // Hairline: high over the forehead, low at the nape.
                float front = Math.max(0f, nz);
                float limit = 0.30f + front * 0.34f - Math.max(0f, -nz) * 0.20f;
                if (style == 1) limit += 0.10f;      // longer, covers more of the nape
                if (style == 2) limit -= 0.06f;      // cropped
                if (ny < limit && i > 0) {
                    used[i][j] = false;
                    continue;
                }
                float radius = headRadius(nx, ny, nz) + lift;
                // A little sculpted volume so it is not a shrink-wrapped cap.
                if (style == 0) radius += Math.max(0f, nz) * 0.012f;
                if (style == 1) radius += 0.010f * (1f - ny);
                grid[i][j] = builder.vertex(
                        hx + nx * radius * HEAD_WIDTH,
                        hy + ny * radius * HEAD_HEIGHT,
                        hz + nz * radius * HEAD_DEPTH,
                        nx, ny, nz,
                        (float) (theta * 0.09), (float) (phi * 0.22));
                used[i][j] = true;
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                if (used[i][j] && used[i][j + 1] && used[i + 1][j + 1] && used[i + 1][j]) {
                    builder.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
                }
            }
        }

        if (style == 1) {
            // Shoulder-length fall behind the ears.
            for (int side = -1; side <= 1; side += 2) {
                builder.push();
                builder.translate(hx + side * 0.075f, hy - 0.10f, hz - 0.035f);
                builder.rotateZ(side * 0.10f);
                builder.roundedBox(0.055f, 0.26f, 0.09f, 0.026f, 2);
                builder.pop();
            }
        }
        builder.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    // ---------------------------------------------------------------- geometry

    private static final float HEAD_WIDTH = 0.092f;
    private static final float HEAD_HEIGHT = 0.112f;
    private static final float HEAD_DEPTH = 0.098f;

    /**
     * Radius multiplier that turns a sphere into a head: flat at the back, a brow
     * ridge over the eyes, cheekbones, and a jaw that tapers to a chin.
     */
    private static float headRadius(float nx, float ny, float nz) {
        float r = 1f;
        float front = Math.max(0f, nz);
        float down = Math.max(0f, -ny);

        // Jaw narrows and shortens below the cheekbones.
        float jaw = SkinBinders.smoothstep(0.05f, 0.75f, down);
        r -= jaw * 0.14f * (1f - front * 0.45f);
        // Chin juts slightly forward and down at the front.
        r += jaw * front * front * 0.10f;
        // Brow ridge.
        float brow = SkinBinders.smoothstep(0.02f, 0.30f, ny) * (1f - SkinBinders.smoothstep(0.30f, 0.62f, ny));
        r += brow * front * 0.045f;
        // Cheekbones.
        float cheek = SkinBinders.smoothstep(0.05f, 0.45f, down) * Math.abs(nx) * front;
        r += cheek * 0.055f;
        // The back of the skull is flatter than a sphere.
        r -= Math.max(0f, -nz) * 0.055f;
        // Slight crown.
        r += SkinBinders.smoothstep(0.6f, 1f, ny) * 0.02f;
        return r;
    }

    private void buildNeckAndHead(MeshData mesh) {
        float neckX = Skeleton.bindX(Skeleton.NECK);
        float neckY = Skeleton.bindY(Skeleton.NECK);
        float neckZ = Skeleton.bindZ(Skeleton.NECK);
        float headY = Skeleton.bindY(Skeleton.HEAD);

        // Neck: tapered, leaning slightly forward like a real cervical spine.
        builder.skin(new SkinBinders.Segment(Skeleton.CHEST, Skeleton.NECK,
                0f, neckY - 0.12f, 0f, 0f, neckY + 0.06f, 0f, 0.1f, 0.85f));
        builder.mat(Materials.SKIN).occlusion(0.78f).noTint();
        builder.push();
        builder.translate(neckX, neckY - 0.10f, neckZ + 0.004f);
        builder.cylinder(0.062f, 0.052f, 0.15f, 14, false, false);
        builder.pop();

        // Head.
        builder.skin(new SkinBinders.Segment(Skeleton.NECK, Skeleton.HEAD,
                0f, neckY, 0f, 0f, headY, 0f, 0.05f, 0.55f));
        builder.mat(Materials.SKIN).occlusion(1f);
        buildHead(mesh, 0f, headY, 0f);

        // Eyes, brows and lips are rigid to the head bone.
        builder.skin(new SkinBinders.Rigid(Skeleton.HEAD));
        buildFace(0f, headY, 0f);
        builder.skin(null);
    }

    private void buildHead(MeshData mesh, float cx, float cy, float cz) {
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
                grid[i][j] = builder.vertex(
                        cx + nx * r * HEAD_WIDTH,
                        cy + ny * r * HEAD_HEIGHT,
                        cz + nz * r * HEAD_DEPTH,
                        nx, ny, nz,
                        (float) (theta * 0.075), (float) (phi * 0.19));
            }
        }
        for (int i = 0; i < HEAD_RINGS; i++) {
            for (int j = 0; j < HEAD_SEGMENTS; j++) {
                builder.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }

        // Ears.
        for (int side = -1; side <= 1; side += 2) {
            builder.push();
            builder.translate(cx + side * 0.088f, cy + 0.004f, cz - 0.012f);
            builder.rotateY(side * 0.25f);
            builder.scale(0.45f, 1f, 1f);
            builder.ellipsoid(0.030f, 0.040f, 0.022f, 8, 5);
            builder.pop();
        }

        // Nose: bridge plus a rounded tip and nostrils.
        builder.push();
        builder.translate(cx, cy - 0.012f, cz + HEAD_DEPTH * 0.82f);
        builder.push();
        builder.rotateX(-0.22f);
        builder.scale(0.5f, 1.35f, 0.7f);
        builder.ellipsoid(0.021f, 0.030f, 0.026f, 8, 5);
        builder.pop();
        builder.push();
        builder.translate(0f, -0.028f, 0.010f);
        builder.ellipsoid(0.017f, 0.013f, 0.016f, 8, 5);
        builder.pop();
        builder.pop();
    }

    private void buildFace(float cx, float cy, float cz) {
        final float eyeY = cy + 0.013f;
        final float eyeZ = cz + HEAD_DEPTH * 0.70f;
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 0.032f;

            // Eyeball.
            builder.mat(Materials.CERAMIC).occlusion(0.72f).tint(0.96f, 0.96f, 0.94f);
            builder.push();
            builder.translate(ex, eyeY, eyeZ - 0.012f);
            builder.sphere(0.0125f, 10, 7);
            builder.pop();

            // Iris and pupil, set into the front of the eyeball.
            builder.mat(Materials.PLASTIC).occlusion(0.8f).tint(0.32f, 0.46f, 0.55f);
            builder.push();
            builder.translate(ex, eyeY, eyeZ - 0.0015f);
            builder.scale(1f, 1f, 0.45f);
            builder.sphere(0.0062f, 8, 5);
            builder.pop();
            builder.mat(Materials.PLASTIC).tint(0.06f, 0.06f, 0.07f);
            builder.push();
            builder.translate(ex, eyeY, eyeZ + 0.0018f);
            builder.scale(1f, 1f, 0.35f);
            builder.sphere(0.0031f, 6, 4);
            builder.pop();

            // Upper lid, a thin skin shell that keeps the eye from reading as a bead.
            builder.mat(Materials.SKIN).occlusion(0.9f).noTint();
            builder.push();
            builder.translate(ex, eyeY + 0.0085f, eyeZ - 0.010f);
            builder.rotateX(0.38f);
            builder.scale(1.25f, 0.42f, 1f);
            builder.ellipsoid(0.0135f, 0.0125f, 0.0125f, 10, 5);
            builder.pop();

            // Eyebrow.
            builder.mat(Materials.HAIR).occlusion(0.8f).noTint();
            builder.push();
            builder.translate(ex, cy + 0.038f, eyeZ - 0.002f);
            builder.rotateZ(side * -0.14f);
            builder.rotateX(-0.25f);
            builder.scale(1.9f, 0.34f, 0.5f);
            builder.ellipsoid(0.017f, 0.017f, 0.017f, 8, 4);
            builder.pop();
        }

        // Lips.
        builder.mat(Materials.SKIN).occlusion(0.86f).tint(0.94f, 0.72f, 0.68f);
        builder.push();
        builder.translate(cx, cy - 0.052f, cz + HEAD_DEPTH * 0.70f);
        builder.push();
        builder.translate(0f, 0.006f, 0f);
        builder.scale(2.5f, 0.55f, 0.55f);
        builder.ellipsoid(0.014f, 0.014f, 0.014f, 10, 5);
        builder.pop();
        builder.push();
        builder.translate(0f, -0.010f, -0.001f);
        builder.scale(2.2f, 0.65f, 0.5f);
        builder.ellipsoid(0.014f, 0.014f, 0.014f, 10, 5);
        builder.pop();
        builder.pop();
        builder.noTint();
    }

    private void buildTorso(MeshData mesh, boolean wearsApron) {
        // Cross-sections from hips to shoulders. Each row is
        // (height, halfWidth, halfDepth), giving a waist and a broader chest.
        final float[][] sections = {
                {0.880f, 0.130f, 0.088f},
                {0.940f, 0.146f, 0.098f},
                {1.000f, 0.142f, 0.096f},
                {1.060f, 0.133f, 0.092f},
                {1.120f, 0.131f, 0.092f},
                {1.190f, 0.142f, 0.098f},
                {1.260f, 0.160f, 0.106f},
                {1.330f, 0.172f, 0.108f},
                {1.400f, 0.176f, 0.106f},
                {1.455f, 0.170f, 0.098f},
                {1.495f, 0.128f, 0.082f},
        };

        builder.skin(new SkinBinders.HeightChain(
                new int[]{Skeleton.HIPS, Skeleton.SPINE, Skeleton.CHEST},
                new float[]{0.98f, 1.12f, 1.29f}));

        // Body underneath, in skin: only the neckline and forearms will show it, but
        // it stops the clothing from looking hollow at the openings.
        builder.mat(Materials.SKIN).occlusion(0.85f).noTint();
        loftTorso(sections, 0f, TORSO_SEGMENTS);

        // Shirt: the same sections pushed out slightly, so it drapes over the body.
        builder.mat(Materials.SHIRT).occlusion(0.95f).noTint();
        loftTorso(sections, 0.011f, TORSO_SEGMENTS);

        // Collar.
        builder.push();
        builder.translate(0f, 1.487f, 0f);
        builder.scale(1f, 1f, 0.82f);
        builder.cylinder(0.083f, 0.094f, 0.035f, 16, false, false);
        builder.pop();

        // Waistband and belt.
        builder.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        builder.mat(Materials.LEATHER).occlusion(0.8f).tint(0.28f, 0.22f, 0.18f);
        builder.push();
        builder.translate(0f, 0.925f, 0f);
        builder.scale(1f, 1f, 0.70f);
        builder.cylinder(0.146f, 0.146f, 0.040f, 18, false, false);
        builder.pop();
        builder.mat(Materials.CHROME).occlusion(0.9f).noTint();
        builder.push();
        builder.translate(0f, 0.945f, 0.098f);
        builder.roundedBox(0.045f, 0.032f, 0.012f, 0.005f, 2);
        builder.pop();

        if (wearsApron) {
            builder.skin(new SkinBinders.HeightChain(
                    new int[]{Skeleton.HIPS, Skeleton.SPINE, Skeleton.CHEST},
                    new float[]{0.98f, 1.12f, 1.29f}));
            builder.mat(Materials.APRON).occlusion(1f).noTint();
            // Bib and skirt, standing just off the shirt.
            builder.push();
            builder.translate(0f, 1.32f, 0.113f);
            builder.roundedBox(0.190f, 0.230f, 0.014f, 0.006f, 1);
            builder.pop();
            builder.push();
            builder.translate(0f, 1.055f, 0.118f);
            builder.roundedBox(0.290f, 0.300f, 0.014f, 0.006f, 1);
            builder.pop();
            // Neck strap.
            for (int side = -1; side <= 1; side += 2) {
                builder.push();
                builder.translate(side * 0.052f, 1.455f, 0.055f);
                builder.rotateX(0.55f);
                builder.roundedBox(0.022f, 0.115f, 0.010f, 0.004f, 1);
                builder.pop();
            }
        }
        builder.skin(null);
    }

    /** Lofts the torso cross-sections into a closed shell. */
    private void loftTorso(float[][] sections, float inflate, int segments) {
        int rows = sections.length;
        int[][] grid = new int[rows][segments + 1];
        for (int i = 0; i < rows; i++) {
            float y = sections[i][0];
            float halfWidth = sections[i][1] + inflate;
            float halfDepth = sections[i][2] + inflate;
            for (int j = 0; j <= segments; j++) {
                double theta = Math.PI * 2 * j / segments;
                float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
                float px = c * halfWidth;
                float pz = s * halfDepth;
                // Elliptical normal.
                float nx = c / halfWidth, nz = s / halfDepth;
                float ny = 0f;
                if (i > 0 && i < rows - 1) {
                    float dr = (sections[i + 1][1] - sections[i - 1][1]);
                    float dy = (sections[i + 1][0] - sections[i - 1][0]);
                    ny = dy > 1e-5f ? -dr / dy * 0.6f : 0f;
                }
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6f) len = 1f;
                grid[i][j] = builder.vertex(px, y, pz, nx / len, ny / len, nz / len,
                        (float) (theta * 0.14), y);
            }
        }
        for (int i = 0; i < rows - 1; i++) {
            for (int j = 0; j < segments; j++) {
                builder.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
        // Close the top and bottom so the shell is watertight.
        capLoft(new float[]{sections[0][0], sections[0][1] + inflate, sections[0][2] + inflate},
                -1f, segments);
        capLoft(new float[]{sections[rows - 1][0], sections[rows - 1][1] + inflate,
                sections[rows - 1][2] + inflate}, 1f, segments);
    }

    /**
     * Closes the top or bottom of the torso shell with its own fan, so the cap
     * carries the cap normal rather than the shell's outward-facing one.
     */
    private void capLoft(float[] section, float facing, int segments) {
        float y = section[0], halfWidth = section[1], halfDepth = section[2];
        int centre = builder.vertex(0f, y, 0f, 0f, facing, 0f, 0f, 0f);
        int prev = -1;
        for (int j = 0; j <= segments; j++) {
            double theta = Math.PI * 2 * j / segments;
            float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
            int v = builder.vertex(c * halfWidth, y, s * halfDepth, 0f, facing, 0f,
                    c * halfWidth, s * halfDepth);
            if (prev >= 0) builder.triangleOriented(centre, prev, v);
            prev = v;
        }
    }

    private void buildArms(MeshData mesh) {
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int shoulder = left ? Skeleton.SHOULDER_L : Skeleton.SHOULDER_R;
            int upper = left ? Skeleton.UPPERARM_L : Skeleton.UPPERARM_R;
            int fore = left ? Skeleton.FOREARM_L : Skeleton.FOREARM_R;
            int hand = left ? Skeleton.HAND_L : Skeleton.HAND_R;
            float sx = Skeleton.bindX(upper);
            float shoulderY = Skeleton.bindY(upper);
            float elbowY = Skeleton.bindY(fore);
            float wristY = Skeleton.bindY(hand);

            // Deltoid: bound mostly to the chest so a raised arm does not tear the
            // shoulder open.
            builder.skin(new SkinBinders.Segment(Skeleton.CHEST, upper,
                    0f, shoulderY + 0.03f, 0f, sx * 1.35f, shoulderY - 0.02f, 0f, 0.15f, 0.9f));
            builder.mat(Materials.SHIRT).occlusion(0.92f).noTint();
            builder.push();
            builder.translate(sx * 0.92f, shoulderY + 0.012f, 0f);
            builder.scale(1f, 0.95f, 1f);
            builder.sphere(0.062f, 10, 7);
            builder.pop();

            // Upper arm, sleeved.
            builder.skin(new SkinBinders.Segment(upper, fore,
                    sx, shoulderY, 0f, sx, elbowY, 0f, 0.62f, 0.98f));
            float[] upperPath = {
                    sx, shoulderY - 0.005f, 0f,
                    sx + (left ? 0.006f : -0.006f), shoulderY - 0.085f, 0.002f,
                    sx + (left ? 0.010f : -0.010f), shoulderY - 0.170f, 0.004f,
                    sx + (left ? 0.012f : -0.012f), elbowY + 0.012f, 0.004f,
            };
            builder.mat(Materials.SHIRT).occlusion(0.94f);
            builder.tube(upperPath, new float[]{0.055f, 0.052f, 0.046f, 0.043f},
                    LIMB_SEGMENTS, false);

            // Sleeve cuff, a short flare where the shirt ends.
            builder.push();
            builder.translate(sx + (left ? 0.012f : -0.012f), elbowY + 0.004f, 0.004f);
            builder.cylinder(0.047f, 0.044f, 0.026f, LIMB_SEGMENTS, false, false);
            builder.pop();

            // Forearm, bare skin: bulges at the elbow, narrows at the wrist.
            builder.skin(new SkinBinders.Segment(fore, hand,
                    sx, elbowY, 0f, sx, wristY, 0f, 0.72f, 1f));
            float[] forePath = {
                    sx + (left ? 0.012f : -0.012f), elbowY + 0.020f, 0.004f,
                    sx + (left ? 0.014f : -0.014f), elbowY - 0.060f, 0.006f,
                    sx + (left ? 0.015f : -0.015f), elbowY - 0.140f, 0.008f,
                    sx + (left ? 0.016f : -0.016f), wristY + 0.006f, 0.008f,
            };
            builder.mat(Materials.SKIN).occlusion(0.96f);
            builder.tube(forePath, new float[]{0.044f, 0.040f, 0.033f, 0.028f},
                    LIMB_SEGMENTS, false);
        }
        builder.skin(null);
    }

    /** Palm plus five digits, each with its own taper and slight curl. */
    private void buildHands(MeshData mesh) {
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int hand = left ? Skeleton.HAND_L : Skeleton.HAND_R;
            float hx = Skeleton.bindX(hand);
            float hy = Skeleton.bindY(hand);
            float hz = Skeleton.bindZ(hand);
            float mirror = left ? 1f : -1f;

            builder.skin(new SkinBinders.Rigid(hand));
            builder.mat(Materials.SKIN).occlusion(0.95f).noTint();

            // Palm.
            builder.push();
            builder.translate(hx, hy - 0.038f, hz);
            builder.roundedBox(0.052f, 0.082f, 0.028f, 0.012f, 2);
            builder.pop();

            // Four fingers, shortest at the outside.
            final float[] fingerLength = {0.052f, 0.058f, 0.054f, 0.044f};
            final float[] fingerRadius = {0.0092f, 0.0098f, 0.0092f, 0.0082f};
            for (int f = 0; f < 4; f++) {
                float offsetX = (-0.019f + f * 0.0127f) * mirror;
                float baseY = hy - 0.079f;
                float length = fingerLength[f];
                float curl = 0.22f + f * 0.02f;
                builder.push();
                builder.translate(hx + offsetX, baseY, hz + 0.001f);
                builder.rotateX(curl);
                float[] path = {
                        0f, 0f, 0f,
                        0f, -length * 0.42f, length * 0.05f,
                        0f, -length * 0.78f, length * 0.16f,
                        0f, -length, length * 0.27f,
                };
                float r = fingerRadius[f];
                builder.tube(path, new float[]{r, r * 0.95f, r * 0.85f, r * 0.72f}, 6, true);
                builder.pop();
            }

            // Thumb, set forward and angled across the palm.
            builder.push();
            builder.translate(hx + 0.026f * mirror, hy - 0.030f, hz + 0.008f);
            builder.rotateZ(mirror * 0.75f);
            builder.rotateX(0.40f);
            float[] thumbPath = {
                    0f, 0f, 0f,
                    0f, -0.019f, 0.006f,
                    0f, -0.036f, 0.014f,
            };
            builder.tube(thumbPath, new float[]{0.0115f, 0.0105f, 0.0088f}, 6, true);
            builder.pop();
        }
        builder.skin(null);
    }

    private void buildLegs(MeshData mesh) {
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

            // Thigh: heaviest at the top, tucking in at the knee.
            builder.skin(new SkinBinders.Segment(Skeleton.HIPS, thigh,
                    lx, hipY + 0.06f, 0f, lx, kneeY, 0f, 0.05f, 0.42f));
            builder.mat(Materials.DENIM).occlusion(0.92f).noTint();
            float[] thighPath = {
                    lx, hipY + 0.035f, 0.002f,
                    lx + mirror * 0.004f, hipY - 0.090f, 0.004f,
                    lx + mirror * 0.006f, hipY - 0.210f, 0.004f,
                    lx + mirror * 0.006f, kneeY + 0.020f, 0.002f,
            };
            builder.tube(thighPath, new float[]{0.088f, 0.081f, 0.070f, 0.063f},
                    LIMB_SEGMENTS, false);

            // Shin with a calf bulge at the back.
            builder.skin(new SkinBinders.Segment(thigh, shin,
                    lx, kneeY + 0.05f, 0f, lx, ankleY, 0f, 0.05f, 0.35f));
            float[] shinPath = {
                    lx + mirror * 0.006f, kneeY + 0.030f, 0.002f,
                    lx + mirror * 0.005f, kneeY - 0.080f, -0.008f,
                    lx + mirror * 0.004f, kneeY - 0.220f, -0.004f,
                    lx + mirror * 0.003f, ankleY + 0.030f, 0.002f,
            };
            builder.tube(shinPath, new float[]{0.062f, 0.063f, 0.048f, 0.036f},
                    LIMB_SEGMENTS, false);

            // Trouser hem.
            builder.push();
            builder.translate(lx + mirror * 0.003f, ankleY + 0.040f, 0f);
            builder.cylinder(0.050f, 0.046f, 0.040f, LIMB_SEGMENTS, false, false);
            builder.pop();

            buildShoe(foot, lx, ankleY, mirror);
        }
        builder.skin(null);
    }

    /** Sole, upper, toe box and a tongue, rigid to the foot bone. */
    private void buildShoe(int footBone, float lx, float ankleY, float mirror) {
        builder.skin(new SkinBinders.Rigid(footBone));
        builder.mat(Materials.LEATHER).occlusion(0.85f).tint(0.30f, 0.26f, 0.24f);

        builder.push();
        builder.translate(lx, ankleY - 0.050f, 0.030f);

        // Sole.
        builder.mat(Materials.RUBBER_MAT).tint(0.80f, 0.80f, 0.82f);
        builder.push();
        builder.translate(0f, -0.014f, 0.012f);
        builder.roundedBox(0.086f, 0.026f, 0.250f, 0.011f, 1);
        builder.pop();

        // Upper.
        builder.mat(Materials.LEATHER).tint(0.30f, 0.26f, 0.24f);
        builder.push();
        builder.translate(0f, 0.022f, 0.026f);
        builder.roundedBox(0.079f, 0.048f, 0.205f, 0.020f, 2);
        builder.pop();

        // Toe box, a touch lower and rounder.
        builder.push();
        builder.translate(0f, 0.014f, 0.098f);
        builder.scale(1f, 0.78f, 1f);
        builder.ellipsoid(0.040f, 0.040f, 0.048f, 10, 6);
        builder.pop();

        // Heel counter.
        builder.push();
        builder.translate(0f, 0.040f, -0.062f);
        builder.roundedBox(0.074f, 0.070f, 0.056f, 0.020f, 1);
        builder.pop();

        // Tongue and laces.
        builder.mat(Materials.LEATHER).tint(0.22f, 0.20f, 0.19f);
        builder.push();
        builder.translate(0f, 0.052f, 0.030f);
        builder.rotateX(-0.20f);
        builder.roundedBox(0.048f, 0.010f, 0.086f, 0.004f, 1);
        builder.pop();
        builder.mat(Materials.PAPER).tint(0.90f, 0.88f, 0.84f);
        for (int i = 0; i < 3; i++) {
            builder.push();
            builder.translate(0f, 0.058f - i * 0.002f, 0.004f + i * 0.030f);
            builder.rotateZ(0.12f);
            builder.roundedBox(0.052f, 0.006f, 0.008f, 0.002f, 1);
            builder.pop();
        }
        builder.pop();
        builder.noTint();
    }
}
