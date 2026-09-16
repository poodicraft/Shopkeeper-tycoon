package com.poodicraft.shopkeeper.world;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;

/**
 * Builds the stock sitting on a shelf.
 *
 * <p>Only the front facings are modelled individually, with a filler block behind
 * them - which is both what a real shelf looks like from the aisle and a large
 * saving over modelling every unit. Each shelf gets its own mesh so it can be
 * culled and rebuilt independently as its stock changes.
 */
public final class GoodsBuilder {

    /** Facings across one shelf board. */
    public static final int FACINGS = 4;
    public static final int VISIBLE_UNITS = FACINGS * WorldBuilder.SHELF_TIERS;

    private final GeometryBuilder b = new GeometryBuilder();

    /**
     * @param product what the shelf sells, or null for an empty shelf
     * @param stock   units on the shelf
     * @param capacity what a full shelf holds
     * @param seed    keeps each shelf's small rotations stable between rebuilds
     */
    public MeshData build(ProductType product, int stock, int capacity, int seed) {
        MeshData mesh = new MeshData(false, 3000);
        b.target(mesh).identity().noTint().occlusion(1f).uv(1f);
        if (product == null || stock <= 0) return mesh;

        int shown = Math.max(1, Math.min(VISIBLE_UNITS,
                Math.round(stock * (float) VISIBLE_UNITS / Math.max(1, capacity))));
        float spacing = (ShopLayout.SHELF_WIDTH - 0.46f) / FACINGS;
        float startX = -(ShopLayout.SHELF_WIDTH - 0.46f) * 0.5f + spacing * 0.5f;

        for (int i = 0; i < shown; i++) {
            int tier = i % WorldBuilder.SHELF_TIERS;
            int facing = i / WorldBuilder.SHELF_TIERS;
            if (facing >= FACINGS) break;
            float x = startX + facing * spacing;
            float y = WorldBuilder.tierHeight(tier) + 0.03f;

            b.push();
            b.translate(x, y, ShopLayout.SHELF_DEPTH * 0.5f - 0.24f);
            // A few degrees of variation stops a full shelf looking machine-stamped.
            b.rotateY(((seed * 31 + i * 17) % 13 - 6) * 0.012f);
            unit(product);
            b.pop();

            // Filler behind the facing, so a stocked shelf reads as deep.
            b.push();
            b.translate(x, y, -0.06f);
            b.mat(Materials.CARDBOARD).tint(product.color).uv(1.2f);
            b.boxAt(0f, 0.09f, 0f, spacing * 0.86f, 0.18f, 0.30f);
            b.pop();
            b.noTint();
        }

        mesh.computeTangents();
        return mesh;
    }

    /** One unit of merchandise, standing on the local origin. */
    public void unit(ProductType product) {
        int labelLayer = Materials.LABEL_BASE + (product.ordinal() % Materials.LABEL_COUNT);
        switch (product.shape) {
            case LOAF:      loaf(product, labelLayer); break;
            case CARTON:    carton(product, labelLayer); break;
            case BAG:       bag(product, labelLayer); break;
            case WEDGE:     wedge(product, labelLayer); break;
            case BOX:       box(product, labelLayer); break;
            case BOTTLE:    bottle(product, labelLayer); break;
            case TRAY:      tray(product, labelLayer); break;
            default:        jar(product, labelLayer); break;
        }
        b.noTint();
    }

    private void loaf(ProductType p, int label) {
        b.mat(Materials.PAPER).tint(p.color).uv(1.6f);
        b.push();
        b.translate(0f, 0.095f, 0f);
        b.scale(1f, 0.72f, 0.80f);
        b.ellipsoid(0.115f, 0.115f, 0.115f, 10, 6);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.095f, 0.082f);
        b.roundedBox(0.130f, 0.090f, 0.006f, 0.003f, 1);
        b.pop();
    }

    private void carton(ProductType p, int label) {
        b.mat(Materials.PAPER).tint(p.color).uv(1.4f);
        b.push();
        b.translate(0f, 0.105f, 0f);
        b.roundedBox(0.090f, 0.210f, 0.090f, 0.010f, 1);
        b.pop();
        // Gable top.
        b.push();
        b.translate(0f, 0.225f, 0f);
        b.rotateZ((float) Math.PI * 0.5f);
        b.cylinder(0.046f, 0.046f, 0.090f, 3, true, true);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.115f, 0.047f);
        b.roundedBox(0.078f, 0.150f, 0.005f, 0.002f, 1);
        b.pop();
    }

    private void bag(ProductType p, int label) {
        b.mat(Materials.PLASTIC).tint(p.color).uv(1.5f);
        b.push();
        b.translate(0f, 0.090f, 0f);
        b.roundedBox(0.135f, 0.180f, 0.070f, 0.026f, 2);
        b.pop();
        // Crimped seam along the top.
        b.push();
        b.translate(0f, 0.186f, 0f);
        b.roundedBox(0.110f, 0.022f, 0.016f, 0.006f, 1);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.095f, 0.038f);
        b.roundedBox(0.105f, 0.120f, 0.005f, 0.002f, 1);
        b.pop();
    }

    private void wedge(ProductType p, int label) {
        b.mat(Materials.PAPER).tint(p.color).uv(1.4f);
        b.push();
        b.translate(0f, 0.055f, 0f);
        b.rotateX((float) Math.PI * 0.5f);
        b.cylinder(0.105f, 0.105f, 0.085f, 3, true, true);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.060f, 0.046f);
        b.roundedBox(0.082f, 0.060f, 0.005f, 0.002f, 1);
        b.pop();
    }

    private void box(ProductType p, int label) {
        b.mat(Materials.CARDBOARD).tint(p.color).uv(1.3f);
        b.push();
        b.translate(0f, 0.085f, 0f);
        b.roundedBox(0.130f, 0.170f, 0.055f, 0.008f, 1);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.090f, 0.031f);
        b.roundedBox(0.112f, 0.140f, 0.005f, 0.002f, 1);
        b.pop();
    }

    private void bottle(ProductType p, int label) {
        b.mat(Materials.GLASS).tint(p.color).uv(1f);
        b.lathe(new float[]{
                0.000f, 0.000f, 0.042f, 0.000f, 0.046f, 0.012f,
                0.046f, 0.130f, 0.040f, 0.160f, 0.022f, 0.195f,
                0.019f, 0.240f, 0.021f, 0.256f, 0.019f, 0.262f,
        }, 10, true, true);
        b.mat(Materials.PAINTED).tint(0.72f, 0.60f, 0.24f);
        b.push();
        b.translate(0f, 0.256f, 0f);
        b.cylinder(0.023f, 0.023f, 0.022f, 10, false, true);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.075f, 0.0f);
        b.cylinder(0.0475f, 0.0475f, 0.085f, 10, false, false);
        b.pop();
    }

    private void tray(ProductType p, int label) {
        b.mat(Materials.PLASTIC).tint(0.18f, 0.19f, 0.21f).uv(1.6f);
        b.push();
        b.translate(0f, 0.020f, 0f);
        b.roundedBox(0.170f, 0.040f, 0.115f, 0.010f, 1);
        b.pop();
        b.mat(Materials.PAPER).tint(p.color).uv(1.4f);
        for (int i = 0; i < 3; i++) {
            b.push();
            b.translate(-0.052f + i * 0.052f, 0.055f, 0f);
            b.cylinder(0.024f, 0.024f, 0.030f, 6, false, true);
            b.pop();
        }
        b.mat(Materials.GLASS).tint(0.92f, 0.95f, 0.97f).uv(1f);
        b.push();
        b.translate(0f, 0.078f, 0f);
        b.roundedBox(0.172f, 0.010f, 0.117f, 0.004f, 1);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.084f, 0f);
        b.rotateX(-(float) Math.PI * 0.5f);
        b.roundedBox(0.110f, 0.070f, 0.004f, 0.002f, 1);
        b.pop();
    }

    private void jar(ProductType p, int label) {
        b.mat(Materials.GLASS).tint(p.color).uv(1f);
        b.lathe(new float[]{
                0.000f, 0.000f, 0.055f, 0.000f, 0.060f, 0.012f,
                0.060f, 0.090f, 0.050f, 0.110f, 0.044f, 0.120f,
        }, 10, true, false);
        b.mat(Materials.PAINTED).tint(0.58f, 0.52f, 0.20f).uv(1.6f);
        b.push();
        b.translate(0f, 0.118f, 0f);
        b.cylinder(0.048f, 0.048f, 0.026f, 10, false, true);
        b.pop();
        b.mat(label).noTint().uv(1f);
        b.push();
        b.translate(0f, 0.050f, 0f);
        b.cylinder(0.0615f, 0.0615f, 0.062f, 10, false, false);
        b.pop();
    }
}
