package com.poodicraft.shopkeeper.world;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.gl.GeometryBuilder;

/**
 * Reusable furniture and clutter.
 *
 * <p>Each helper draws at the builder's current transform, so a prop can be placed
 * anywhere by pushing a transform first. Clutter is what makes a room read as a
 * working shop rather than a box with shelves in it.
 */
public final class Props {
    private Props() { }

    /** Glazed pot, soil, stem and a crown of leaves. */
    public static void pottedPlant(GeometryBuilder b, float scale, int potColor, int leafTint) {
        b.push();
        b.scale(scale, scale, scale);

        b.mat(Materials.CERAMIC).tint(potColor).uv(1.6f);
        b.lathe(new float[]{
                0.00f, 0.00f, 0.17f, 0.00f, 0.19f, 0.03f,
                0.22f, 0.22f, 0.25f, 0.40f, 0.27f, 0.44f, 0.24f, 0.46f,
        }, 16, true, false);

        b.mat(Materials.CONCRETE).tint(0.22f, 0.17f, 0.13f);
        b.push();
        b.translate(0f, 0.43f, 0f);
        b.disc(0.235f, 0f, 1f, 16);
        b.pop();

        b.mat(Materials.FOLIAGE).tint(leafTint).uv(1f);
        b.push();
        b.translate(0f, 0.43f, 0f);
        b.cylinder(0.022f, 0.016f, 0.30f, 6, false, false);
        b.pop();

        // Leaves fan out on a spiral so no two sit in the same plane.
        for (int i = 0; i < 11; i++) {
            float angle = i * 2.399f;
            float lift = 0.52f + (i % 4) * 0.075f;
            float tilt = 0.55f + (i % 3) * 0.20f;
            float length = 0.30f - (i % 3) * 0.045f;
            b.push();
            b.translate(0f, lift, 0f);
            b.rotateY(angle);
            b.rotateX(-tilt);
            b.translate(0f, length * 0.5f, 0f);
            b.scale(1f, 1f, 0.22f);
            b.ellipsoid(0.085f, length * 0.55f, 0.085f, 8, 5);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    /** Recessed ceiling panel with a diffuser that reads as the light source. */
    public static void ceilingLight(GeometryBuilder b) {
        b.push();
        b.mat(Materials.METAL_BRUSHED).tint(0.86f, 0.87f, 0.89f).uv(1.2f);
        b.roundedBox(0.92f, 0.07f, 0.62f, 0.022f, 1);
        b.mat(Materials.EMISSIVE).tint(1f, 0.97f, 0.90f);
        b.push();
        b.translate(0f, -0.040f, 0f);
        b.roundedBox(0.80f, 0.020f, 0.50f, 0.008f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Pendant lamp on a rod, used over the checkout. */
    public static void pendantLamp(GeometryBuilder b, float dropLength, int shadeColor) {
        b.push();
        b.mat(Materials.METAL_DARK).tint(0.30f, 0.31f, 0.33f);
        b.push();
        b.translate(0f, -dropLength * 0.5f, 0f);
        b.cylinder(0.014f, 0.014f, dropLength, 6, false, false);
        b.pop();
        b.push();
        b.translate(0f, -0.03f, 0f);
        b.cylinder(0.07f, 0.055f, 0.045f, 10, true, false);
        b.pop();

        b.mat(Materials.PAINTED).tint(shadeColor).uv(1.4f);
        b.push();
        b.translate(0f, -dropLength, 0f);
        b.lathe(new float[]{
                0.04f, 0.00f, 0.13f, -0.06f, 0.22f, -0.15f, 0.26f, -0.22f, 0.265f, -0.235f,
        }, 18, false, false);
        b.pop();

        b.mat(Materials.EMISSIVE).tint(1f, 0.93f, 0.78f);
        b.push();
        b.translate(0f, -dropLength - 0.17f, 0f);
        b.sphere(0.058f, 10, 7);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Cardboard crate with flaps and a printed panel. */
    public static void crate(GeometryBuilder b, float size, boolean open) {
        b.push();
        b.mat(Materials.CARDBOARD).tint(0.92f, 0.86f, 0.78f).uv(1.5f);
        float h = size * 0.68f;
        b.push();
        b.translate(0f, h * 0.5f, 0f);
        b.roundedBox(size, h, size * 0.86f, size * 0.035f, 1);
        b.pop();
        if (open) {
            // Flaps folded outward, which reads instantly as "open box".
            for (int side = 0; side < 4; side++) {
                b.push();
                b.rotateY(side * (float) Math.PI * 0.5f);
                b.translate(0f, h, size * 0.43f);
                b.rotateX(-1.15f);
                b.translate(0f, 0f, size * 0.20f);
                b.roundedBox(size * 0.92f, 0.012f, size * 0.40f, 0.006f, 1);
                b.pop();
            }
        }
        b.mat(Materials.PAPER).tint(0.94f, 0.92f, 0.88f);
        b.push();
        b.translate(0f, h * 0.55f, size * 0.435f);
        b.roundedBox(size * 0.5f, h * 0.34f, 0.008f, 0.004f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Stacking shopping basket. */
    public static void basket(GeometryBuilder b, int color) {
        b.push();
        b.mat(Materials.PLASTIC).tint(color).uv(2f);
        float w = 0.40f, d = 0.29f, h = 0.20f;
        b.push();
        b.translate(0f, 0.012f, 0f);
        b.roundedBox(w, 0.024f, d, 0.010f, 1);
        b.pop();
        for (int side = 0; side < 4; side++) {
            boolean lengthwise = side % 2 == 0;
            float offset = (side < 2 ? 1f : -1f) * (lengthwise ? d * 0.5f : w * 0.5f);
            b.push();
            if (lengthwise) b.translate(0f, h * 0.5f, offset);
            else b.translate(offset, h * 0.5f, 0f);
            b.rotateX(lengthwise ? 0.10f * Math.signum(offset) : 0f);
            b.rotateZ(lengthwise ? 0f : -0.10f * Math.signum(offset));
            b.roundedBox(lengthwise ? w : 0.018f, h, lengthwise ? 0.018f : d, 0.008f, 1);
            b.pop();
        }
        // Folding handles.
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(0f, h + 0.045f, side * d * 0.20f);
            b.rotateX(side * 0.45f);
            b.roundedBox(w * 0.52f, 0.016f, 0.016f, 0.007f, 1);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    /** Wire shopping trolley with a basket, handle and castors. */
    public static void trolley(GeometryBuilder b) {
        b.push();
        b.mat(Materials.CHROME).tint(0.80f, 0.82f, 0.85f).uv(2f);
        // Basket: a tapered open box suggested by its rim and ribs.
        b.push();
        b.translate(0f, 0.62f, 0f);
        b.rotateX(-0.10f);
        for (int i = 0; i < 7; i++) {
            b.push();
            b.translate(0f, 0f, -0.28f + i * 0.095f);
            b.roundedBox(0.52f, 0.30f, 0.012f, 0.005f, 1);
            b.pop();
        }
        b.push();
        b.translate(0f, 0.15f, 0f);
        b.roundedBox(0.55f, 0.016f, 0.63f, 0.007f, 1);
        b.pop();
        b.push();
        b.translate(0f, -0.15f, 0f);
        b.roundedBox(0.50f, 0.014f, 0.58f, 0.006f, 1);
        b.pop();
        b.pop();

        // Frame and legs.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                b.push();
                b.translate(sx * 0.23f, 0.26f, sz * 0.27f);
                b.rotateZ(sx * 0.07f);
                b.cylinder(0.014f, 0.014f, 0.50f, 6, false, false);
                b.pop();
                b.push();
                b.translate(sx * 0.23f, 0.05f, sz * 0.27f);
                b.mat(Materials.RUBBER_MAT).tint(0.22f, 0.22f, 0.24f);
                b.rotateX((float) Math.PI * 0.5f);
                b.cylinder(0.048f, 0.048f, 0.030f, 10, true, true);
                b.mat(Materials.CHROME).tint(0.80f, 0.82f, 0.85f);
                b.pop();
            }
        }
        // Push handle.
        b.push();
        b.translate(0f, 0.92f, -0.34f);
        b.rotateZ((float) Math.PI * 0.5f);
        b.cylinder(0.018f, 0.018f, 0.54f, 8, true, true);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Round wall clock with a rim, face and hands. */
    public static void wallClock(GeometryBuilder b, float hourAngle, float minuteAngle) {
        b.push();
        b.rotateX((float) Math.PI * 0.5f);
        b.mat(Materials.PAINTED).tint(0.20f, 0.21f, 0.23f).uv(2f);
        b.lathe(new float[]{
                0.00f, 0.00f, 0.150f, 0.00f, 0.162f, 0.012f, 0.165f, 0.040f, 0.150f, 0.048f,
        }, 20, false, false);
        b.mat(Materials.PAPER).tint(0.96f, 0.95f, 0.92f);
        b.push();
        b.translate(0f, 0.044f, 0f);
        b.disc(0.150f, 0f, 1f, 20);
        b.pop();
        b.mat(Materials.PAINTED).tint(0.12f, 0.12f, 0.14f);
        for (int i = 0; i < 12; i++) {
            b.push();
            b.translate(0f, 0.047f, 0f);
            b.rotateY(i * (float) Math.PI / 6f);
            b.translate(0f, 0f, 0.125f);
            b.roundedBox(0.010f, 0.004f, i % 3 == 0 ? 0.034f : 0.020f, 0.002f, 1);
            b.pop();
        }
        b.push();
        b.translate(0f, 0.050f, 0f);
        b.rotateY(hourAngle);
        b.translate(0f, 0f, 0.034f);
        b.roundedBox(0.011f, 0.005f, 0.075f, 0.002f, 1);
        b.pop();
        b.push();
        b.translate(0f, 0.053f, 0f);
        b.rotateY(minuteAngle);
        b.translate(0f, 0f, 0.048f);
        b.roundedBox(0.008f, 0.005f, 0.105f, 0.002f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Framed poster or sign panel on a wall. */
    public static void poster(GeometryBuilder b, float width, float height, int frameColor,
                              int sheetColor) {
        b.push();
        b.mat(Materials.WOOD_DARK).tint(frameColor).uv(1.5f);
        b.roundedBox(width + 0.06f, height + 0.06f, 0.035f, 0.012f, 1);
        b.mat(Materials.PAPER).tint(sheetColor).uv(1f);
        b.push();
        b.translate(0f, 0f, 0.020f);
        b.roundedBox(width, height, 0.006f, 0.003f, 1);
        b.pop();
        // Suggested headline and body blocks.
        b.mat(Materials.PAPER).tint(0.20f, 0.24f, 0.28f);
        b.push();
        b.translate(0f, height * 0.26f, 0.025f);
        b.roundedBox(width * 0.68f, height * 0.16f, 0.004f, 0.002f, 1);
        b.pop();
        for (int i = 0; i < 3; i++) {
            b.push();
            b.translate(-width * 0.06f, -height * (0.05f + i * 0.12f), 0.025f);
            b.roundedBox(width * (0.56f - i * 0.10f), height * 0.045f, 0.004f, 0.002f, 1);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    /** Swing-lid waste bin. */
    public static void bin(GeometryBuilder b, int color) {
        b.push();
        b.mat(Materials.PLASTIC).tint(color).uv(1.4f);
        b.lathe(new float[]{
                0.00f, 0.00f, 0.155f, 0.00f, 0.170f, 0.05f,
                0.195f, 0.48f, 0.205f, 0.54f, 0.195f, 0.56f,
        }, 16, true, false);
        b.push();
        b.translate(0f, 0.575f, 0f);
        b.mat(Materials.PLASTIC).tint(0.22f, 0.23f, 0.25f);
        b.lathe(new float[]{0.00f, 0.00f, 0.20f, -0.01f, 0.205f, -0.035f}, 16, false, false);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Wall-mounted fire extinguisher, the kind of detail a real shop has. */
    public static void fireExtinguisher(GeometryBuilder b) {
        b.push();
        b.mat(Materials.PAINTED).tint(0.72f, 0.12f, 0.10f).uv(1.6f);
        b.lathe(new float[]{
                0.00f, 0.00f, 0.075f, 0.01f, 0.082f, 0.05f,
                0.082f, 0.40f, 0.060f, 0.45f, 0.024f, 0.48f,
        }, 14, true, false);
        b.mat(Materials.METAL_DARK).tint(0.35f, 0.36f, 0.38f);
        b.push();
        b.translate(0f, 0.48f, 0f);
        b.cylinder(0.024f, 0.024f, 0.055f, 10, false, true);
        b.translate(0f, 0.055f, 0.02f);
        b.rotateX(0.25f);
        b.roundedBox(0.030f, 0.020f, 0.10f, 0.008f, 1);
        b.pop();
        b.push();
        b.translate(0f, 0.30f, -0.08f);
        b.roundedBox(0.09f, 0.055f, 0.045f, 0.012f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Ceiling sprinkler head. */
    public static void sprinkler(GeometryBuilder b) {
        b.push();
        b.mat(Materials.CHROME).tint(0.78f, 0.76f, 0.70f);
        b.push();
        b.translate(0f, -0.012f, 0f);
        b.cylinder(0.038f, 0.030f, 0.024f, 10, true, false);
        b.pop();
        b.push();
        b.translate(0f, -0.045f, 0f);
        b.cylinder(0.011f, 0.011f, 0.035f, 6, true, false);
        b.pop();
        b.push();
        b.translate(0f, -0.058f, 0f);
        b.sphere(0.014f, 6, 4);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Dome security camera. */
    public static void securityCamera(GeometryBuilder b) {
        b.push();
        b.mat(Materials.PLASTIC).tint(0.92f, 0.92f, 0.94f);
        b.push();
        b.translate(0f, -0.02f, 0f);
        b.cylinder(0.075f, 0.072f, 0.035f, 14, true, false);
        b.pop();
        b.mat(Materials.GLASS).tint(0.18f, 0.19f, 0.22f);
        b.push();
        b.translate(0f, -0.052f, 0f);
        b.scale(1f, 0.62f, 1f);
        b.sphere(0.068f, 14, 8);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Air-conditioning grille. */
    public static void airVent(GeometryBuilder b, float width, float length) {
        b.push();
        b.mat(Materials.METAL_BRUSHED).tint(0.88f, 0.89f, 0.90f).uv(1.4f);
        b.roundedBox(width, 0.045f, length, 0.014f, 1);
        b.mat(Materials.METAL_DARK).tint(0.52f, 0.53f, 0.55f);
        int slats = (int) (length / 0.075f);
        for (int i = 0; i < slats; i++) {
            b.push();
            b.translate(0f, -0.030f, -length * 0.5f + 0.05f + i * 0.075f);
            b.rotateX(0.55f);
            b.roundedBox(width - 0.08f, 0.008f, 0.050f, 0.003f, 1);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    /** Free-standing A-board with a chalk surface. */
    public static void chalkboardSign(GeometryBuilder b) {
        b.push();
        b.mat(Materials.WOOD_DARK).tint(0.48f, 0.34f, 0.22f).uv(1.2f);
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(0f, 0.46f, side * 0.13f);
            b.rotateX(side * 0.16f);
            b.roundedBox(0.60f, 0.92f, 0.035f, 0.012f, 1);
            b.mat(Materials.PAINTED).tint(0.13f, 0.15f, 0.14f);
            b.push();
            b.translate(0f, 0.02f, side * 0.024f);
            b.roundedBox(0.50f, 0.78f, 0.010f, 0.004f, 1);
            b.pop();
            b.mat(Materials.PAPER).tint(0.90f, 0.90f, 0.86f);
            for (int i = 0; i < 4; i++) {
                b.push();
                b.translate(-0.04f + (i % 2) * 0.05f, 0.24f - i * 0.15f, side * 0.031f);
                b.roundedBox(0.34f - i * 0.045f, 0.035f, 0.004f, 0.002f, 1);
                b.pop();
            }
            b.mat(Materials.WOOD_DARK).tint(0.48f, 0.34f, 0.22f);
            b.pop();
        }
        b.pop();
        b.noTint();
    }
}
