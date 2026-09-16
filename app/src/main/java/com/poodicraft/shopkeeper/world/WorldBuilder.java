package com.poodicraft.shopkeeper.world;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;

/**
 * Builds the shop interior.
 *
 * <p>Everything fixed in place - floor, walls, ceiling, the checkout, the chiller,
 * the stockroom and all the clutter - is baked into a single mesh, so the entire
 * room costs one draw call in each of the shadow and scene passes. Fittings that
 * come and go (shelves, stock, carried crates) are built separately.
 */
public final class WorldBuilder {

    private final GeometryBuilder b = new GeometryBuilder();

    // ------------------------------------------------------------ environment

    public MeshData buildRoom() {
        MeshData mesh = new MeshData(false, 40000);
        b.target(mesh).identity().noTint().occlusion(1f).uv(1f);

        buildFloor();
        buildWalls();
        buildCeiling();
        buildCheckout();
        buildChiller();
        buildStockroom();
        buildBackOffice();
        buildEntrance();
        buildWallFittings();
        buildClutter();

        mesh.computeTangents();
        return mesh;
    }

    private void buildFloor() {
        final float w = ShopLayout.HALF_WIDTH * 2;
        final float d = ShopLayout.HALF_DEPTH * 2;

        b.mat(Materials.FLOOR_TILE).uv(0.62f).occlusion(1f);
        b.plane(w, d, 16, 20, 0f);

        // Darker border course around the edge of the room.
        b.mat(Materials.MOSAIC).tint(0.72f, 0.74f, 0.76f).uv(1.2f);
        float inset = 0.55f;
        b.push();
        b.translate(0f, 0.004f, 0f);
        for (int side = 0; side < 4; side++) {
            b.push();
            b.rotateY(side * (float) Math.PI * 0.5f);
            float length = (side % 2 == 0) ? w : d;
            float offset = (side % 2 == 0) ? ShopLayout.HALF_DEPTH : ShopLayout.HALF_WIDTH;
            b.translate(0f, 0f, offset - inset * 0.5f);
            b.plane(length, inset, 12, 1, 0f);
            b.pop();
        }
        b.pop();
        b.noTint();

        // Rubber mat inside the entrance.
        b.mat(Materials.RUBBER_MAT).tint(0.30f, 0.32f, 0.34f).uv(1.8f);
        b.push();
        b.translate(ShopLayout.DOOR_CENTER_X, 0.010f, ShopLayout.HALF_DEPTH - 0.95f);
        b.roundedBox(2.10f, 0.018f, 1.40f, 0.020f, 1);
        b.pop();
        b.noTint();

        // Anti-slip mat where the shopkeeper stands at the till.
        b.mat(Materials.RUBBER_MAT).tint(0.26f, 0.28f, 0.30f).uv(1.8f);
        b.push();
        b.translate(ShopLayout.SERVE_X, 0.010f, ShopLayout.SERVE_Z - 0.10f);
        b.roundedBox(2.60f, 0.016f, 0.90f, 0.018f, 1);
        b.pop();
        b.noTint();
    }

    private void buildWalls() {
        final float hw = ShopLayout.HALF_WIDTH;
        final float hd = ShopLayout.HALF_DEPTH;
        final float h = ShopLayout.CEILING_HEIGHT;
        final float t = ShopLayout.WALL_THICKNESS;
        final float dadoHeight = 1.10f;

        // Back and side walls, split into a tiled dado and painted upper.
        wallPanel(-hw, -hd - t, hw, -hd, h, dadoHeight);
        wallPanel(-hw - t, -hd, -hw, hd, h, dadoHeight);
        wallPanel(hw, -hd, hw + t, hd, h, dadoHeight);

        // Front wall either side of the entrance, with a shop window in each.
        wallPanel(-hw, hd, ShopLayout.DOOR_MIN_X, hd + t, h, dadoHeight);
        wallPanel(ShopLayout.DOOR_MAX_X, hd, hw, hd + t, h, dadoHeight);
        b.mat(Materials.WALL_PAINT).uv(0.5f);
        b.boxSpan(ShopLayout.DOOR_MIN_X, ShopLayout.DOOR_HEIGHT, hd,
                ShopLayout.DOOR_MAX_X, h, hd + t);

        shopWindow(-4.30f, hd, 3.60f, 1.45f, 0.95f);
        shopWindow(0.95f, hd, 3.00f, 1.45f, 0.95f);

        // Skirting all round the inside.
        b.mat(Materials.PAINTED).tint(0.90f, 0.90f, 0.88f).uv(1.6f);
        skirting(-hw, -hd, hw, -hd, 0f);
        skirting(-hw, -hd, -hw, hd, 1f);
        skirting(hw, -hd, hw, hd, 1f);
        b.noTint();
    }

    /** One wall: tiled below the dado rail, painted above, with the rail between. */
    private void wallPanel(float x0, float z0, float x1, float z1, float height, float dado) {
        b.mat(Materials.MOSAIC).tint(0.84f, 0.86f, 0.88f).uv(1.1f);
        b.boxSpan(x0, 0f, z0, x1, dado, z1);
        b.noTint();
        b.mat(Materials.WALL_PAINT).uv(0.5f);
        b.boxSpan(x0, dado, z0, x1, height, z1);
        b.mat(Materials.WOOD_LIGHT).tint(0.78f, 0.72f, 0.64f).uv(2f);
        boolean alongX = Math.abs(x1 - x0) > Math.abs(z1 - z0);
        float inset = 0.02f;
        if (alongX) {
            float z = (z0 + z1) * 0.5f;
            float face = z0 < 0 ? z1 + inset : z0 - inset;
            b.boxSpan(x0, dado, face - 0.035f, x1, dado + 0.07f, face + 0.035f);
        } else {
            float face = x0 < 0 ? x1 + inset : x0 - inset;
            b.boxSpan(face - 0.035f, dado, z0, face + 0.035f, dado + 0.07f, z1);
        }
        b.noTint();
    }

    private void skirting(float x0, float z0, float x1, float z1, float axis) {
        float inset = 0.03f;
        if (axis == 0f) {
            b.boxSpan(x0, 0f, z0 + inset, x1, 0.13f, z0 + inset + 0.04f);
        } else {
            float sign = x0 < 0 ? 1f : -1f;
            b.boxSpan(x0 + inset * sign, 0f, z0, x0 + (inset + 0.04f) * sign, 0.13f, z1);
        }
    }

    /** Glazed shop window with a frame, mullions, sill and a display plinth. */
    private void shopWindow(float centerX, float wallZ, float width, float height, float sill) {
        float hw = width * 0.5f;
        b.mat(Materials.GLASS).tint(0.80f, 0.90f, 0.96f).uv(0.4f);
        b.boxSpan(centerX - hw, sill, wallZ + 0.03f, centerX + hw, sill + height, wallZ + 0.22f);
        b.noTint();

        b.mat(Materials.METAL_BRUSHED).tint(0.72f, 0.74f, 0.76f).uv(1.5f);
        float f = 0.055f;
        b.boxSpan(centerX - hw - f, sill - f, wallZ - 0.01f, centerX - hw, sill + height + f, wallZ + 0.25f);
        b.boxSpan(centerX + hw, sill - f, wallZ - 0.01f, centerX + hw + f, sill + height + f, wallZ + 0.25f);
        b.boxSpan(centerX - hw - f, sill + height, wallZ - 0.01f, centerX + hw + f, sill + height + f, wallZ + 0.25f);
        for (int i = 1; i < 3; i++) {
            float x = centerX - hw + width * i / 3f;
            b.boxSpan(x - 0.022f, sill, wallZ + 0.01f, x + 0.022f, sill + height, wallZ + 0.24f);
        }
        b.noTint();

        // Sill and the display plinth in front of it.
        b.mat(Materials.MARBLE).tint(0.92f, 0.91f, 0.88f).uv(0.9f);
        b.boxSpan(centerX - hw - 0.10f, sill - 0.09f, wallZ - 0.12f,
                centerX + hw + 0.10f, sill, wallZ + 0.27f);
        b.noTint();
        b.mat(Materials.WOOD_LIGHT).tint(0.80f, 0.68f, 0.52f).uv(1.1f);
        b.push();
        b.translate(centerX, 0.30f, wallZ - 0.34f);
        b.roundedBox(width * 0.80f, 0.60f, 0.42f, 0.018f, 1);
        b.pop();
        b.noTint();
    }

    private void buildCeiling() {
        final float w = ShopLayout.HALF_WIDTH * 2;
        final float d = ShopLayout.HALF_DEPTH * 2;
        final float h = ShopLayout.CEILING_HEIGHT;

        // Suspended panels. The plane faces up, so flip it to look down into the room.
        b.mat(Materials.CEILING).uv(0.55f).occlusion(0.92f);
        b.push();
        b.translate(0f, h, 0f);
        b.rotateX((float) Math.PI);
        b.plane(w, d, 10, 12, 0f);
        b.pop();
        b.occlusion(1f);

        // T-bar grid.
        b.mat(Materials.METAL_BRUSHED).tint(0.82f, 0.83f, 0.85f).uv(2f);
        for (float x = -ShopLayout.HALF_WIDTH + 1.5f; x < ShopLayout.HALF_WIDTH; x += 1.5f) {
            b.boxSpan(x - 0.015f, h - 0.035f, -ShopLayout.HALF_DEPTH, x + 0.015f, h - 0.005f, ShopLayout.HALF_DEPTH);
        }
        for (float z = -ShopLayout.HALF_DEPTH + 1.5f; z < ShopLayout.HALF_DEPTH; z += 1.5f) {
            b.boxSpan(-ShopLayout.HALF_WIDTH, h - 0.035f, z - 0.015f, ShopLayout.HALF_WIDTH, h - 0.005f, z + 0.015f);
        }
        b.noTint();

        for (int i = 0; i < ShopLayout.LAMPS.length; i++) {
            b.push();
            b.translate(ShopLayout.LAMPS[i][0], h - 0.045f, ShopLayout.LAMPS[i][1]);
            Props.ceilingLight(b);
            b.pop();
        }

        // Pendants over the checkout, which is the warmest part of the room.
        for (int i = 0; i < 3; i++) {
            b.push();
            b.translate(-5.40f + i * 1.75f, h - 0.04f, 5.85f);
            Props.pendantLamp(b, 0.85f, 0x2E6F63);
            b.pop();
        }

        // Services: sprinklers, vents, a camera watching the door.
        float[][] sprinklers = {{-5f, -6f}, {0f, -3f}, {5f, -1f}, {-3f, 3f}, {4f, 6.5f}};
        for (int i = 0; i < sprinklers.length; i++) {
            b.push();
            b.translate(sprinklers[i][0], h - 0.01f, sprinklers[i][1]);
            Props.sprinkler(b);
            b.pop();
        }
        b.push();
        b.translate(-2.2f, h - 0.03f, -4.0f);
        Props.airVent(b, 0.55f, 1.15f);
        b.pop();
        b.push();
        b.translate(2.6f, h - 0.03f, 2.2f);
        Props.airVent(b, 0.55f, 1.15f);
        b.pop();
        b.push();
        b.translate(ShopLayout.HALF_WIDTH - 1.2f, h - 0.05f, ShopLayout.HALF_DEPTH - 1.4f);
        Props.securityCamera(b);
        b.pop();
        b.push();
        b.translate(-4.0f, h - 0.05f, -6.5f);
        Props.securityCamera(b);
        b.pop();
    }

    private void buildCheckout() {
        final float x0 = ShopLayout.COUNTER_MIN_X, x1 = ShopLayout.COUNTER_MAX_X;
        final float z0 = ShopLayout.COUNTER_MIN_Z, z1 = ShopLayout.COUNTER_MAX_Z;
        final float h = ShopLayout.COUNTER_HEIGHT;

        // Carcass with a recessed kick plate.
        b.mat(Materials.PAINTED).tint(0.24f, 0.44f, 0.40f).uv(0.9f);
        b.push();
        b.translate((x0 + x1) * 0.5f, (h - 0.08f) * 0.5f + 0.09f, (z0 + z1) * 0.5f);
        b.roundedBox(x1 - x0, h - 0.17f, z1 - z0, 0.022f, 1);
        b.pop();
        b.noTint();
        b.mat(Materials.METAL_DARK).tint(0.28f, 0.29f, 0.31f).uv(1.6f);
        b.boxSpan(x0 + 0.06f, 0f, z0 + 0.06f, x1 - 0.06f, 0.09f, z1 - 0.06f);
        b.noTint();

        // Worktop with a lip that overhangs the carcass.
        b.mat(Materials.MARBLE).tint(0.93f, 0.92f, 0.90f).uv(0.7f);
        b.push();
        b.translate((x0 + x1) * 0.5f, h - 0.03f, (z0 + z1) * 0.5f);
        b.roundedBox(x1 - x0 + 0.10f, 0.06f, z1 - z0 + 0.10f, 0.018f, 2);
        b.pop();
        b.noTint();

        // Timber front panel facing the queue.
        b.mat(Materials.WOOD_LIGHT).tint(0.74f, 0.58f, 0.40f).uv(1.0f);
        for (int i = 0; i < 6; i++) {
            b.push();
            b.translate(x0 + 0.45f + i * 0.80f, h * 0.52f, z1 + 0.015f);
            b.roundedBox(0.70f, h - 0.30f, 0.030f, 0.010f, 1);
            b.pop();
        }
        b.noTint();

        buildTill();

        // Bagging shelf under the worktop on the staff side.
        b.mat(Materials.METAL_BRUSHED).tint(0.80f, 0.81f, 0.83f).uv(1.4f);
        b.push();
        b.translate(x0 + 1.05f, 0.42f, z0 - 0.02f);
        b.roundedBox(1.30f, 0.030f, 0.50f, 0.010f, 1);
        b.pop();
        b.noTint();
        b.mat(Materials.PAPER).tint(0.86f, 0.80f, 0.68f).uv(1.2f);
        for (int i = 0; i < 3; i++) {
            b.push();
            b.translate(x0 + 0.80f + i * 0.22f, 0.47f, z0 - 0.02f);
            b.rotateY(0.1f * i);
            b.roundedBox(0.22f, 0.055f, 0.34f, 0.006f, 1);
            b.pop();
        }
        b.noTint();

        // Impulse-buy rack on the queue side.
        b.mat(Materials.CHROME).tint(0.78f, 0.80f, 0.83f).uv(2f);
        b.push();
        b.translate(x1 + 0.28f, 0.50f, z1 - 0.20f);
        for (int tier = 0; tier < 3; tier++) {
            b.push();
            b.translate(0f, tier * 0.34f - 0.20f, 0f);
            b.roundedBox(0.44f, 0.020f, 0.34f, 0.008f, 1);
            b.pop();
        }
        b.push();
        b.translate(0f, 0.16f, -0.18f);
        b.roundedBox(0.44f, 1.00f, 0.020f, 0.008f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    /** Point-of-sale terminal, scanner, card reader and receipt printer. */
    private void buildTill() {
        b.push();
        b.translate(ShopLayout.TILL_X, ShopLayout.COUNTER_HEIGHT, ShopLayout.TILL_Z);

        // Base and cash drawer.
        b.mat(Materials.PLASTIC).tint(0.90f, 0.90f, 0.92f).uv(1.4f);
        b.push();
        b.translate(0f, 0.075f, 0f);
        b.roundedBox(0.46f, 0.15f, 0.40f, 0.016f, 2);
        b.pop();
        b.mat(Materials.METAL_DARK).tint(0.32f, 0.33f, 0.35f);
        b.push();
        b.translate(0f, 0.055f, 0.205f);
        b.roundedBox(0.40f, 0.055f, 0.020f, 0.006f, 1);
        b.pop();

        // Screen on a stand, tilted toward the operator.
        b.mat(Materials.METAL_DARK).tint(0.26f, 0.27f, 0.29f);
        b.push();
        b.translate(0f, 0.19f, -0.06f);
        b.cylinder(0.035f, 0.030f, 0.13f, 10, true, false);
        b.pop();
        b.push();
        b.translate(0f, 0.34f, -0.05f);
        b.rotateX(-0.36f);
        b.roundedBox(0.34f, 0.26f, 0.028f, 0.010f, 2);
        b.mat(Materials.EMISSIVE).tint(0.36f, 0.78f, 0.66f);
        b.push();
        b.translate(0f, 0f, 0.017f);
        b.roundedBox(0.30f, 0.22f, 0.006f, 0.003f, 1);
        b.pop();
        b.pop();

        // Keypad.
        b.mat(Materials.PLASTIC).tint(0.86f, 0.86f, 0.88f);
        b.push();
        b.translate(0f, 0.16f, 0.14f);
        b.rotateX(-0.16f);
        b.roundedBox(0.30f, 0.020f, 0.16f, 0.006f, 1);
        b.mat(Materials.PLASTIC).tint(0.46f, 0.48f, 0.52f);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                b.push();
                b.translate(-0.11f + col * 0.055f, 0.016f, -0.045f + row * 0.045f);
                b.roundedBox(0.038f, 0.010f, 0.030f, 0.004f, 1);
                b.pop();
            }
        }
        b.pop();

        // Scanner glass set into the worktop, and a card reader on a stalk.
        b.mat(Materials.METAL_DARK).tint(0.22f, 0.23f, 0.25f);
        b.push();
        b.translate(0.52f, 0.012f, 0.10f);
        b.roundedBox(0.34f, 0.024f, 0.30f, 0.008f, 1);
        b.mat(Materials.EMISSIVE).tint(0.85f, 0.20f, 0.16f);
        b.push();
        b.translate(0f, 0.014f, 0f);
        b.roundedBox(0.26f, 0.006f, 0.22f, 0.003f, 1);
        b.pop();
        b.pop();

        b.mat(Materials.METAL_DARK).tint(0.30f, 0.31f, 0.33f);
        b.push();
        b.translate(0.95f, 0.06f, 0.24f);
        b.cylinder(0.030f, 0.026f, 0.12f, 8, true, false);
        b.translate(0f, 0.12f, 0.02f);
        b.rotateX(-0.55f);
        b.roundedBox(0.11f, 0.17f, 0.024f, 0.008f, 1);
        b.mat(Materials.EMISSIVE).tint(0.30f, 0.62f, 0.85f);
        b.push();
        b.translate(0f, 0.035f, 0.014f);
        b.roundedBox(0.085f, 0.055f, 0.005f, 0.002f, 1);
        b.pop();
        b.pop();
        b.pop();
        b.noTint();
    }

    private void buildChiller() {
        final float x = ShopLayout.CHILLER_X;
        final float z = ShopLayout.CHILLER_Z;
        final float w = ShopLayout.CHILLER_WIDTH;
        final float l = ShopLayout.CHILLER_LENGTH;
        final float h = ShopLayout.CHILLER_HEIGHT;

        b.push();
        b.translate(x, 0f, z);

        b.mat(Materials.METAL_BRUSHED).tint(0.80f, 0.82f, 0.84f).uv(0.9f);
        b.push();
        b.translate(0f, h * 0.5f, 0f);
        b.roundedBox(w, h, l, 0.030f, 2);
        b.pop();

        // Glass doors on the aisle side, with handles and shelves behind.
        b.mat(Materials.GLASS).tint(0.84f, 0.92f, 0.96f).uv(0.5f);
        for (int i = 0; i < 2; i++) {
            b.push();
            b.translate(-w * 0.5f - 0.012f, 1.10f, -l * 0.25f + i * l * 0.5f);
            b.roundedBox(0.030f, 1.55f, l * 0.46f, 0.010f, 1);
            b.pop();
        }
        b.noTint();
        b.mat(Materials.CHROME).tint(0.82f, 0.84f, 0.86f).uv(2f);
        for (int i = 0; i < 2; i++) {
            b.push();
            b.translate(-w * 0.5f - 0.055f, 1.10f, -l * 0.25f + i * l * 0.5f + l * 0.19f);
            b.roundedBox(0.026f, 1.05f, 0.026f, 0.010f, 1);
            b.pop();
        }
        // Wire shelves, visible through the glass.
        for (int tier = 0; tier < 4; tier++) {
            b.push();
            b.translate(0.05f, 0.45f + tier * 0.40f, 0f);
            b.roundedBox(w * 0.78f, 0.016f, l * 0.94f, 0.006f, 1);
            b.pop();
        }
        b.noTint();

        // Signage header and a cold blue glow inside.
        b.mat(Materials.PAINTED).tint(0.16f, 0.36f, 0.56f).uv(1.2f);
        b.push();
        b.translate(-0.02f, h + 0.13f, 0f);
        b.roundedBox(w + 0.06f, 0.26f, l * 0.92f, 0.014f, 1);
        b.pop();
        b.mat(Materials.EMISSIVE).tint(0.60f, 0.82f, 1.00f);
        b.push();
        b.translate(-w * 0.28f, h - 0.14f, 0f);
        b.roundedBox(0.10f, 0.030f, l * 0.86f, 0.010f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    private void buildStockroom() {
        final float z = ShopLayout.STOCKROOM_Z;
        final float x = ShopLayout.STOCKROOM_X;

        // Door in the back wall, with a push plate and a vision panel.
        b.mat(Materials.PAINTED).tint(0.72f, 0.73f, 0.74f).uv(1.0f);
        b.push();
        b.translate(x, 1.05f, z + 0.14f);
        b.roundedBox(1.30f, 2.10f, 0.08f, 0.016f, 1);
        b.pop();
        b.mat(Materials.METAL_DARK).tint(0.34f, 0.35f, 0.37f).uv(1.6f);
        b.push();
        b.translate(x, 1.05f, z + 0.12f);
        b.roundedBox(1.46f, 2.24f, 0.05f, 0.014f, 1);
        b.pop();
        b.mat(Materials.GLASS).tint(0.70f, 0.78f, 0.84f);
        b.push();
        b.translate(x, 1.62f, z + 0.19f);
        b.roundedBox(0.52f, 0.46f, 0.020f, 0.008f, 1);
        b.pop();
        b.mat(Materials.CHROME).tint(0.80f, 0.82f, 0.84f);
        b.push();
        b.translate(x + 0.42f, 1.00f, z + 0.20f);
        b.roundedBox(0.10f, 0.42f, 0.028f, 0.012f, 1);
        b.pop();
        b.noTint();

        // "Staff only" sign above it.
        b.push();
        b.translate(x, 2.42f, z + 0.20f);
        Props.poster(b, 0.60f, 0.26f, 0x3A3F46, 0xD8DEE4);
        b.pop();

        // Pallet with stacked crates beside the door: this is where stock comes from.
        b.mat(Materials.WOOD_DARK).tint(0.62f, 0.50f, 0.36f).uv(1.2f);
        b.push();
        b.translate(x + 1.55f, 0.06f, z + 0.72f);
        for (int i = 0; i < 5; i++) {
            b.push();
            b.translate(-0.48f + i * 0.24f, 0f, 0f);
            b.roundedBox(0.16f, 0.055f, 1.10f, 0.008f, 1);
            b.pop();
        }
        b.push();
        b.translate(0f, -0.05f, 0f);
        b.roundedBox(1.24f, 0.055f, 1.10f, 0.008f, 1);
        b.pop();
        b.pop();
        b.noTint();

        float[][] stack = {
                {x + 1.25f, 0.09f, z + 0.52f, 0.20f},
                {x + 1.85f, 0.09f, z + 0.58f, -0.35f},
                {x + 1.52f, 0.55f, z + 0.78f, 0.55f},
                {x + 1.30f, 0.09f, z + 1.05f, -0.15f},
        };
        for (int i = 0; i < stack.length; i++) {
            b.push();
            b.translate(stack[i][0], stack[i][1], stack[i][2]);
            b.rotateY(stack[i][3]);
            Props.crate(b, 0.62f, i == 2);
            b.pop();
        }

        // Hand truck leaning on the wall.
        b.mat(Materials.PAINTED).tint(0.72f, 0.24f, 0.18f).uv(1.4f);
        b.push();
        b.translate(x - 1.30f, 0.60f, z + 0.42f);
        b.rotateX(-0.22f);
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(side * 0.16f, 0f, 0f);
            b.roundedBox(0.045f, 1.15f, 0.045f, 0.016f, 1);
            b.pop();
        }
        b.push();
        b.translate(0f, -0.55f, 0.10f);
        b.roundedBox(0.40f, 0.030f, 0.24f, 0.010f, 1);
        b.pop();
        b.push();
        b.translate(0f, 0.52f, 0f);
        b.rotateZ((float) Math.PI * 0.5f);
        b.cylinder(0.022f, 0.022f, 0.38f, 8, true, true);
        b.pop();
        b.mat(Materials.RUBBER_MAT).tint(0.20f, 0.20f, 0.22f);
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(side * 0.21f, -0.50f, -0.02f);
            b.rotateZ((float) Math.PI * 0.5f);
            b.cylinder(0.10f, 0.10f, 0.045f, 12, true, true);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    private void buildBackOffice() {
        final float x = ShopLayout.TERMINAL_X;
        final float z = ShopLayout.TERMINAL_Z;

        // Desk.
        b.mat(Materials.WOOD_LIGHT).tint(0.72f, 0.60f, 0.46f).uv(0.9f);
        b.push();
        b.translate(x, 0.74f, z + 0.10f);
        b.roundedBox(1.70f, 0.055f, 0.70f, 0.014f, 2);
        b.pop();
        b.mat(Materials.METAL_DARK).tint(0.30f, 0.31f, 0.33f).uv(1.5f);
        for (int sx = -1; sx <= 1; sx += 2) {
            b.push();
            b.translate(x + sx * 0.74f, 0.36f, z + 0.10f);
            b.roundedBox(0.05f, 0.72f, 0.60f, 0.012f, 1);
            b.pop();
        }
        b.noTint();

        // Monitor, keyboard and a stack of paperwork: the ordering terminal.
        b.mat(Materials.PLASTIC).tint(0.20f, 0.21f, 0.23f).uv(1.4f);
        b.push();
        b.translate(x, 0.78f, z - 0.05f);
        b.roundedBox(0.28f, 0.022f, 0.20f, 0.008f, 1);
        b.translate(0f, 0.10f, 0f);
        b.cylinder(0.026f, 0.026f, 0.18f, 8, false, false);
        b.translate(0f, 0.28f, 0.015f);
        b.rotateX(-0.20f);
        b.roundedBox(0.62f, 0.38f, 0.026f, 0.010f, 2);
        b.mat(Materials.EMISSIVE).tint(0.42f, 0.72f, 0.92f);
        b.push();
        b.translate(0f, 0f, 0.016f);
        b.roundedBox(0.57f, 0.33f, 0.005f, 0.002f, 1);
        b.pop();
        b.pop();

        b.mat(Materials.PLASTIC).tint(0.86f, 0.86f, 0.88f);
        b.push();
        b.translate(x, 0.775f, z + 0.28f);
        b.roundedBox(0.44f, 0.018f, 0.16f, 0.005f, 1);
        b.pop();
        b.mat(Materials.PAPER).tint(0.95f, 0.94f, 0.90f);
        b.push();
        b.translate(x + 0.58f, 0.785f, z + 0.16f);
        b.rotateY(0.18f);
        b.roundedBox(0.24f, 0.030f, 0.32f, 0.004f, 1);
        b.pop();
        b.noTint();

        // Office chair.
        b.mat(Materials.SHIRT).tint(0.24f, 0.26f, 0.30f).uv(2.2f);
        b.push();
        b.translate(x, 0.47f, z + 0.85f);
        b.roundedBox(0.46f, 0.070f, 0.44f, 0.020f, 2);
        b.translate(0f, 0.30f, -0.19f);
        b.rotateX(0.18f);
        b.roundedBox(0.42f, 0.52f, 0.070f, 0.022f, 2);
        b.pop();
        b.mat(Materials.METAL_DARK).tint(0.28f, 0.29f, 0.31f);
        b.push();
        b.translate(x, 0.22f, z + 0.85f);
        b.cylinder(0.035f, 0.035f, 0.24f, 8, false, false);
        b.translate(0f, -0.20f, 0f);
        for (int i = 0; i < 5; i++) {
            b.push();
            b.rotateY(i * 1.2566f);
            b.translate(0f, 0f, 0.14f);
            b.roundedBox(0.05f, 0.035f, 0.28f, 0.014f, 1);
            b.pop();
        }
        b.pop();
        b.noTint();
    }

    private void buildEntrance() {
        final float hd = ShopLayout.HALF_DEPTH;
        final float t = ShopLayout.WALL_THICKNESS;

        // Automatic sliding doors: two leaves parted in the middle.
        b.mat(Materials.METAL_BRUSHED).tint(0.70f, 0.72f, 0.74f).uv(1.6f);
        b.boxSpan(ShopLayout.DOOR_MIN_X - 0.07f, 0f, hd - 0.02f,
                ShopLayout.DOOR_MIN_X, ShopLayout.DOOR_HEIGHT + 0.10f, hd + t + 0.02f);
        b.boxSpan(ShopLayout.DOOR_MAX_X, 0f, hd - 0.02f,
                ShopLayout.DOOR_MAX_X + 0.07f, ShopLayout.DOOR_HEIGHT + 0.10f, hd + t + 0.02f);
        b.boxSpan(ShopLayout.DOOR_MIN_X - 0.07f, ShopLayout.DOOR_HEIGHT, hd - 0.02f,
                ShopLayout.DOOR_MAX_X + 0.07f, ShopLayout.DOOR_HEIGHT + 0.10f, hd + t + 0.02f);
        b.noTint();

        b.mat(Materials.GLASS).tint(0.84f, 0.92f, 0.96f).uv(0.5f);
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? ShopLayout.DOOR_MIN_X + 0.30f : ShopLayout.DOOR_MAX_X - 0.30f;
            b.push();
            b.translate(x, ShopLayout.DOOR_HEIGHT * 0.5f, hd + t * 0.5f);
            b.roundedBox(0.58f, ShopLayout.DOOR_HEIGHT - 0.06f, 0.05f, 0.012f, 1);
            b.pop();
        }
        b.noTint();

        // Illuminated fascia sign above the door.
        b.mat(Materials.PAINTED).tint(0.16f, 0.42f, 0.36f).uv(0.9f);
        b.push();
        b.translate(ShopLayout.DOOR_CENTER_X, ShopLayout.DOOR_HEIGHT + 0.46f, hd + 0.16f);
        b.roundedBox(2.70f, 0.60f, 0.16f, 0.020f, 2);
        b.pop();
        b.mat(Materials.EMISSIVE).tint(1.00f, 0.86f, 0.52f);
        b.push();
        b.translate(ShopLayout.DOOR_CENTER_X, ShopLayout.DOOR_HEIGHT + 0.46f, hd + 0.25f);
        b.roundedBox(2.30f, 0.36f, 0.04f, 0.012f, 1);
        b.pop();
        b.noTint();

        // Baskets stacked just inside, and a trolley parked beside them.
        for (int i = 0; i < 5; i++) {
            b.push();
            b.translate(ShopLayout.DOOR_MIN_X - 0.75f, 0.03f + i * 0.085f, hd - 1.30f);
            b.rotateY(0.04f * i);
            Props.basket(b, i % 2 == 0 ? 0x2E7D63 : 0x2A6B92);
            b.pop();
        }
        b.push();
        b.translate(ShopLayout.HALF_WIDTH - 1.05f, 0f, hd - 1.60f);
        b.rotateY(-0.55f);
        Props.trolley(b);
        b.pop();

        b.push();
        b.translate(ShopLayout.DOOR_MIN_X - 1.85f, 0f, hd - 1.05f);
        b.rotateY(0.65f);
        Props.chalkboardSign(b);
        b.pop();
    }

    private void buildWallFittings() {
        final float hw = ShopLayout.HALF_WIDTH;
        final float hd = ShopLayout.HALF_DEPTH;

        // Wall shelving down the left-hand side.
        b.mat(Materials.WOOD_LIGHT).tint(0.76f, 0.63f, 0.46f).uv(1.0f);
        for (int bay = 0; bay < 3; bay++) {
            float z = -6.0f + bay * 2.6f;
            b.push();
            b.translate(-hw + 0.24f, 0f, z);
            for (int tier = 0; tier < 4; tier++) {
                b.push();
                b.translate(0f, 0.55f + tier * 0.42f, 0f);
                b.roundedBox(0.42f, 0.035f, 2.30f, 0.010f, 1);
                b.pop();
            }
            b.mat(Materials.METAL_DARK).tint(0.34f, 0.35f, 0.37f).uv(1.6f);
            for (int side = -1; side <= 1; side += 2) {
                b.push();
                b.translate(0.02f, 1.10f, side * 1.10f);
                b.roundedBox(0.05f, 2.10f, 0.05f, 0.012f, 1);
                b.pop();
            }
            b.mat(Materials.WOOD_LIGHT).tint(0.76f, 0.63f, 0.46f).uv(1.0f);
            b.pop();
        }
        b.noTint();

        // Clock, posters, extinguisher and a first-aid box.
        b.push();
        b.translate(0.6f, 2.55f, -hd + 0.14f);
        Props.wallClock(b, 1.05f, -2.10f);
        b.pop();

        b.push();
        b.translate(-2.4f, 1.95f, -hd + 0.15f);
        Props.poster(b, 1.10f, 0.80f, 0x6B4A2E, 0xE8B54A);
        b.pop();
        b.push();
        b.translate(6.2f, 1.95f, -hd + 0.15f);
        Props.poster(b, 0.95f, 1.25f, 0x3A4650, 0xC7DCE6);
        b.pop();
        b.push();
        b.translate(hw - 0.16f, 1.90f, -3.8f);
        b.rotateY(-(float) Math.PI * 0.5f);
        Props.poster(b, 1.20f, 0.85f, 0x2E4A44, 0xDCE8DA);
        b.pop();

        b.push();
        b.translate(hw - 0.22f, 0.95f, -6.6f);
        b.rotateY(-(float) Math.PI * 0.5f);
        Props.fireExtinguisher(b);
        b.pop();

        b.mat(Materials.PAINTED).tint(0.88f, 0.90f, 0.88f).uv(1.4f);
        b.push();
        b.translate(hw - 0.20f, 1.65f, -6.6f);
        b.rotateY(-(float) Math.PI * 0.5f);
        b.roundedBox(0.34f, 0.28f, 0.14f, 0.014f, 1);
        b.mat(Materials.PAINTED).tint(0.18f, 0.60f, 0.34f);
        b.push();
        b.translate(0f, 0f, 0.075f);
        b.roundedBox(0.14f, 0.05f, 0.010f, 0.004f, 1);
        b.roundedBox(0.05f, 0.14f, 0.010f, 0.004f, 1);
        b.pop();
        b.pop();
        b.noTint();

        // Aisle number signs hanging over each row.
        for (int row = 0; row < ShopLayout.ROW_Z.length; row++) {
            float z = ShopLayout.ROW_Z[row] + 1.55f;
            b.mat(Materials.METAL_DARK).tint(0.32f, 0.33f, 0.35f).uv(1.6f);
            b.push();
            b.translate(0f, ShopLayout.CEILING_HEIGHT - 0.28f, z);
            for (int side = -1; side <= 1; side += 2) {
                b.push();
                b.translate(side * 0.42f, 0.14f, 0f);
                b.cylinder(0.010f, 0.010f, 0.28f, 6, false, false);
                b.pop();
            }
            b.mat(Materials.PAINTED).tint(0.20f, 0.46f, 0.40f).uv(1.0f);
            b.roundedBox(1.05f, 0.30f, 0.045f, 0.012f, 1);
            b.mat(Materials.PAPER).tint(0.95f, 0.94f, 0.90f);
            b.push();
            b.translate(-0.30f, 0f, 0.030f);
            b.roundedBox(0.16f, 0.18f, 0.006f, 0.003f, 1);
            b.pop();
            for (int i = 0; i < 2; i++) {
                b.push();
                b.translate(0.14f, 0.05f - i * 0.09f, 0.030f);
                b.roundedBox(0.52f - i * 0.14f, 0.045f, 0.006f, 0.003f, 1);
                b.pop();
            }
            b.pop();
            b.noTint();
        }
    }

    private void buildClutter() {
        Props.pottedPlant(b, 1.15f, 0xB4653A, 0x4F9A5A);
        b.push();
        b.translate(-ShopLayout.HALF_WIDTH + 0.95f, 0f, ShopLayout.HALF_DEPTH - 1.10f);
        Props.pottedPlant(b, 1.05f, 0x9A5C3A, 0x57A366);
        b.pop();
        b.push();
        b.translate(ShopLayout.HALF_WIDTH - 0.95f, 0f, -ShopLayout.HALF_DEPTH + 1.25f);
        Props.pottedPlant(b, 1.30f, 0x7E6A56, 0x469152);
        b.pop();
        b.push();
        b.translate(-1.10f, 0f, ShopLayout.HALF_DEPTH - 0.95f);
        Props.pottedPlant(b, 0.90f, 0xB4653A, 0x62AA6E);
        b.pop();

        b.push();
        b.translate(ShopLayout.COUNTER_MIN_X - 0.55f, 0f, ShopLayout.COUNTER_MAX_Z + 0.35f);
        Props.bin(b, 0x38444E);
        b.pop();
        b.push();
        b.translate(ShopLayout.TERMINAL_X - 1.35f, 0f, ShopLayout.TERMINAL_Z + 0.45f);
        Props.bin(b, 0x38444E);
        b.pop();

        // A crate left out on the shop floor, as though mid-restock.
        b.push();
        b.translate(-1.95f, 0f, -2.05f);
        b.rotateY(0.42f);
        Props.crate(b, 0.55f, true);
        b.pop();
    }

    // ---------------------------------------------------------------- fittings

    /**
     * One gondola shelving unit in local space, origin at the centre of its
     * footprint on the floor.
     */
    public MeshData buildShelfUnit() {
        MeshData mesh = new MeshData(false, 6000);
        b.target(mesh).identity().noTint().occlusion(1f).uv(1f);

        final float hw = ShopLayout.SHELF_WIDTH * 0.5f;
        final float hd = ShopLayout.SHELF_DEPTH * 0.5f;
        final float h = ShopLayout.SHELF_HEIGHT;

        // Uprights with slotted brackets.
        b.mat(Materials.METAL_BRUSHED).tint(0.74f, 0.75f, 0.77f).uv(1.2f);
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(side * (hw - 0.035f), h * 0.5f, 0f);
            b.roundedBox(0.07f, h, hd * 2f - 0.06f, 0.016f, 1);
            b.pop();
        }
        // Centre upright, which is what real gondolas have.
        b.push();
        b.translate(0f, h * 0.5f, 0f);
        b.roundedBox(0.05f, h, hd * 2f - 0.10f, 0.014f, 1);
        b.pop();

        // Back panel, pressed with a shallow rib pattern.
        b.mat(Materials.PAINTED).tint(0.86f, 0.87f, 0.88f).uv(1.0f);
        b.push();
        b.translate(0f, h * 0.5f, -hd + 0.035f);
        b.roundedBox(hw * 2f - 0.10f, h - 0.10f, 0.045f, 0.012f, 1);
        b.pop();
        b.mat(Materials.METAL_BRUSHED).tint(0.78f, 0.79f, 0.81f).uv(1.6f);
        for (int i = 0; i < 5; i++) {
            b.push();
            b.translate(-hw + 0.32f + i * 0.59f, h * 0.5f, -hd + 0.062f);
            b.boxAt(0f, 0f, 0f, 0.035f, h - 0.22f, 0.014f);
            b.pop();
        }
        b.noTint();

        // Kick plate.
        b.mat(Materials.METAL_DARK).tint(0.36f, 0.37f, 0.39f).uv(1.5f);
        b.push();
        b.translate(0f, 0.055f, 0f);
        b.roundedBox(hw * 2f, 0.11f, hd * 2f - 0.04f, 0.014f, 1);
        b.pop();
        b.noTint();

        // Shelf boards, each tipped slightly forward with a price rail on the front.
        b.mat(Materials.METAL_BRUSHED).tint(0.88f, 0.89f, 0.90f).uv(0.9f);
        for (int tier = 0; tier < SHELF_TIERS; tier++) {
            float y = tierHeight(tier);
            b.push();
            b.translate(0f, y, 0.015f);
            b.rotateX(-0.045f);
            b.roundedBox(hw * 2f - 0.10f, 0.028f, hd * 2f - 0.14f, 0.008f, 1);
            b.pop();
            // Lip to stop stock sliding off.
            b.push();
            b.translate(0f, y + 0.022f, hd - 0.085f);
            b.roundedBox(hw * 2f - 0.10f, 0.038f, 0.020f, 0.006f, 1);
            b.pop();
            // Price rail.
            b.mat(Materials.PLASTIC).tint(0.94f, 0.94f, 0.92f).uv(1.4f);
            b.push();
            b.translate(0f, y + 0.008f, hd - 0.062f);
            b.rotateX(0.45f);
            b.roundedBox(hw * 2f - 0.14f, 0.052f, 0.012f, 0.004f, 1);
            b.pop();
            // Little printed tags along the rail.
            b.mat(Materials.PAPER).tint(0.97f, 0.96f, 0.92f).uv(1f);
            for (int i = 0; i < 3; i++) {
                b.push();
                b.translate(-hw + 0.52f + i * 0.98f, y + 0.010f, hd - 0.055f);
                b.rotateX(0.45f);
                b.boxAt(0f, 0f, 0f, 0.34f, 0.036f, 0.005f);
                b.pop();
            }
            b.mat(Materials.METAL_BRUSHED).tint(0.88f, 0.89f, 0.90f).uv(0.9f);
        }
        b.noTint();

        // Top canopy with a header card.
        b.mat(Materials.PAINTED).tint(0.22f, 0.44f, 0.40f).uv(1.0f);
        b.push();
        b.translate(0f, h + 0.045f, 0f);
        b.roundedBox(hw * 2f + 0.06f, 0.09f, hd * 2f + 0.06f, 0.016f, 1);
        b.pop();
        b.mat(Materials.PAPER).tint(0.96f, 0.95f, 0.91f);
        b.push();
        b.translate(0f, h + 0.045f, hd + 0.045f);
        b.roundedBox(hw * 1.5f, 0.055f, 0.008f, 0.003f, 1);
        b.pop();
        b.noTint();

        mesh.computeTangents();
        return mesh;
    }

    public static final int SHELF_TIERS = 4;

    /** Height of shelf board {@code tier}, counting from the bottom. */
    public static float tierHeight(int tier) { return 0.30f + tier * 0.42f; }

    /** A carryable crate, origin at its base. */
    public MeshData buildCarryCrate() {
        MeshData mesh = new MeshData(false, 1200);
        b.target(mesh).identity().noTint().occlusion(1f).uv(1f);
        Props.crate(b, 0.56f, true);
        mesh.computeTangents();
        return mesh;
    }
}
