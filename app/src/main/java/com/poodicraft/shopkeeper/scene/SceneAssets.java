package com.poodicraft.shopkeeper.scene;

import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.gl.Mesh;
import com.poodicraft.shopkeeper.gl.MeshBuilder;

/**
 * Builds every mesh the game draws.
 *
 * <p>The whole static environment — floor, walls, windows, counter, till, plants and
 * lamps — is baked into one mesh so it costs a single draw call. Anything that moves
 * or repeats (shelves, goods, people) is a small mesh drawn with a per-object matrix.
 */
public final class SceneAssets {

    /** Everything fixed in place, baked in world coordinates. */
    public Mesh environment;
    /** One shelf unit in local space: origin at the centre of its footprint, on the floor. */
    public Mesh shelfUnit;
    /** Translucent pad shown on shelf slots that are still for sale. */
    public Mesh shelfGhost;
    /** Goods, indexed by {@link ProductType.Shape#ordinal()}; built white so tint sets colour. */
    public Mesh[] goods;

    // Character parts, all white so each one can be tinted per person.
    public Mesh head, hair, cap, torso, arm, leg, shoe, apron;
    public Mesh basket, crate;

    public Mesh shadowDisc;
    public Mesh selectionRing;

    private final MeshBuilder builder = new MeshBuilder(8192);

    public void build() {
        environment = buildEnvironment();
        shelfUnit = buildShelfUnit();
        shelfGhost = buildShelfGhost();
        buildGoods();
        buildCharacterParts();

        builder.reset();
        builder.disc(0.42f, 18, 0xFFFFFF);
        shadowDisc = builder.buildAndReset();

        selectionRing = buildSelectionRing();
    }

    /** Drops every GPU buffer name, for use when the EGL context is recreated. */
    public void invalidate() {
        forEachMesh(true);
    }

    /** Uploads every mesh. Must run on the GL thread. */
    public void upload() {
        forEachMesh(false);
    }

    private void forEachMesh(boolean invalidateOnly) {
        Mesh[] all = {environment, shelfUnit, shelfGhost, head, hair, cap, torso, arm, leg,
                shoe, apron, basket, crate, shadowDisc, selectionRing};
        for (int i = 0; i < all.length; i++) {
            if (all[i] == null) continue;
            if (invalidateOnly) all[i].invalidate(); else all[i].upload();
        }
        if (goods != null) {
            for (int i = 0; i < goods.length; i++) {
                if (goods[i] == null) continue;
                if (invalidateOnly) goods[i].invalidate(); else goods[i].upload();
            }
        }
    }

    // -------------------------------------------------------------- environment

    private Mesh buildEnvironment() {
        MeshBuilder b = builder;
        b.reset();

        buildFloor(b);
        buildWalls(b);
        buildCounter(b);
        buildStockroomDoor(b);
        buildDecor(b);

        return b.buildAndReset();
    }

    private void buildFloor(MeshBuilder b) {
        final float tile = 1f;
        int cols = (int) (Shop.HALF_WIDTH * 2 / tile);
        int rows = (int) (Shop.HALF_DEPTH * 2 / tile);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x0 = -Shop.HALF_WIDTH + c * tile;
                float z0 = -Shop.HALF_DEPTH + r * tile;
                int color = ((r + c) & 1) == 0 ? Palette.FLOOR_LIGHT : Palette.FLOOR_DARK;
                b.floorQuad(x0 + 0.02f, z0 + 0.02f, x0 + tile - 0.02f, z0 + tile - 0.02f, 0f, color);
            }
        }
        // Grout sits just below the tiles so the seams read as lines, not gaps.
        b.floorQuad(-Shop.HALF_WIDTH, -Shop.HALF_DEPTH, Shop.HALF_WIDTH, Shop.HALF_DEPTH,
                -0.015f, Palette.FLOOR_BORDER);

        // Entry rug, angled to point shoppers into the aisles.
        b.push();
        b.translate(Shop.DOOR_CENTER_X, 0.006f, Shop.HALF_DEPTH - 1.1f);
        b.floorQuad(-1.15f, -0.8f, 1.15f, 0.8f, 0f, Palette.RUG);
        b.floorQuad(-1.0f, -0.66f, 1.0f, 0.66f, 0.002f, Palette.RUG_EDGE);
        b.pop();

        // Aisle runner down the middle of the shop.
        b.floorQuad(-0.55f, -Shop.HALF_DEPTH + 0.6f, 0.55f, Shop.HALF_DEPTH - 2.4f,
                0.004f, Palette.FLOOR_LIGHT);
    }

    private void buildWalls(MeshBuilder b) {
        final float w = Shop.HALF_WIDTH;
        final float d = Shop.HALF_DEPTH;
        final float h = Shop.WALL_HEIGHT;
        final float t = 0.22f;

        // Back wall.
        wallSegment(b, -w, -d - t, w, -d, h);
        // Left and right walls.
        wallSegment(b, -w - t, -d, -w, d, h);
        wallSegment(b, w, -d, w + t, d, h);

        // Front wall, split around the doorway.
        wallSegment(b, -w, d, Shop.DOOR_MIN_X, d + t, h);
        wallSegment(b, Shop.DOOR_MAX_X, d, w, d + t, h);
        // Lintel above the door.
        b.boxSpan(Shop.DOOR_MIN_X, 2.35f, d, Shop.DOOR_MAX_X, h, d + t, Palette.WALL);

        // Door frame and glass panes.
        b.boxSpan(Shop.DOOR_MIN_X - 0.06f, 0f, d - 0.02f, Shop.DOOR_MIN_X + 0.08f, 2.35f, d + t + 0.02f, Palette.ACCENT);
        b.boxSpan(Shop.DOOR_MAX_X - 0.08f, 0f, d - 0.02f, Shop.DOOR_MAX_X + 0.06f, 2.35f, d + t + 0.02f, Palette.ACCENT);
        b.boxSpan(Shop.DOOR_MIN_X, 2.24f, d - 0.02f, Shop.DOOR_MAX_X, 2.35f, d + t + 0.02f, Palette.ACCENT);

        // Shop window beside the door.
        buildWindow(b, -3.4f, d, 2.6f, 1.35f, 1.0f);
        buildWindow(b, 0.6f, d, 2.2f, 1.35f, 1.0f);

        // Skirting board around the interior.
        float skirt = 0.16f;
        b.boxSpan(-w, 0f, -d, w, skirt, -d + 0.06f, Palette.SKIRTING);
        b.boxSpan(-w, 0f, -d, -w + 0.06f, skirt, d, Palette.SKIRTING);
        b.boxSpan(w - 0.06f, 0f, -d, w, skirt, d, Palette.SKIRTING);

        // Wainscot panelling on the back wall.
        for (float x = -w + 0.3f; x < w - 0.3f; x += 1.2f) {
            b.boxSpan(x, skirt, -d + 0.02f, x + 1.0f, 1.05f, -d + 0.09f, Palette.WAINSCOT);
        }

        // Signboard over the back wall.
        b.push();
        b.translate(0f, 1.95f, -d + 0.12f);
        b.boxAt(0f, 0.34f, 0f, 4.6f, 0.68f, 0.1f, Palette.ACCENT);
        b.boxAt(0f, 0.34f, 0.06f, 4.3f, 0.5f, 0.04f, Palette.LAMP_SHADE);
        b.pop();
    }

    private void wallSegment(MeshBuilder b, float x0, float z0, float x1, float z1, float h) {
        b.boxSpan(x0, 0f, z0, x1, h, z1, Palette.WALL);
        // A slightly darker cap keeps the top edge from disappearing against the sky.
        b.boxSpan(x0, h - 0.12f, z0, x1, h, z1, Palette.WALL_TOP);
    }

    private void buildWindow(MeshBuilder b, float centerX, float wallZ,
                             float width, float height, float sillY) {
        float hw = width * 0.5f;
        b.boxSpan(centerX - hw, sillY, wallZ + 0.02f, centerX + hw, sillY + height, wallZ + 0.2f, Palette.GLASS);
        // Frame.
        b.boxSpan(centerX - hw - 0.08f, sillY - 0.08f, wallZ - 0.01f, centerX - hw, sillY + height + 0.08f, wallZ + 0.23f, Palette.WOOD_DARK);
        b.boxSpan(centerX + hw, sillY - 0.08f, wallZ - 0.01f, centerX + hw + 0.08f, sillY + height + 0.08f, wallZ + 0.23f, Palette.WOOD_DARK);
        b.boxSpan(centerX - hw - 0.08f, sillY - 0.12f, wallZ - 0.01f, centerX + hw + 0.08f, sillY, wallZ + 0.26f, Palette.WOOD_DARK);
        b.boxSpan(centerX - hw - 0.08f, sillY + height, wallZ - 0.01f, centerX + hw + 0.08f, sillY + height + 0.08f, wallZ + 0.23f, Palette.WOOD_DARK);
        b.boxSpan(centerX - 0.03f, sillY, wallZ + 0.0f, centerX + 0.03f, sillY + height, wallZ + 0.22f, Palette.WOOD_DARK);
    }

    private void buildCounter(MeshBuilder b) {
        float x0 = Shop.COUNTER_MIN_X, x1 = Shop.COUNTER_MAX_X;
        float z0 = Shop.COUNTER_MIN_Z, z1 = Shop.COUNTER_MAX_Z;
        float h = Shop.COUNTER_HEIGHT;

        b.boxSpan(x0, 0f, z0, x1, h - 0.07f, z1, Palette.COUNTER_BODY);
        b.boxSpan(x0 - 0.06f, h - 0.07f, z0 - 0.06f, x1 + 0.06f, h, z1 + 0.06f, Palette.COUNTER_TOP);
        // Kick plate and a panel line so the front face is not a blank slab.
        b.boxSpan(x0 + 0.05f, 0.05f, z1 - 0.02f, x1 - 0.05f, h - 0.18f, z1 + 0.01f, Palette.WOOD_DARK);

        // Till.
        b.push();
        b.translate(Shop.REGISTER_X, h, Shop.REGISTER_Z);
        b.boxAt(0f, 0.14f, 0f, 0.5f, 0.28f, 0.42f, Palette.REGISTER_BODY);
        b.boxAt(0f, 0.30f, -0.09f, 0.42f, 0.06f, 0.24f, Palette.REGISTER_DARK);
        b.push();
        b.translate(0f, 0.33f, -0.04f);
        b.rotateX(-0.42f);
        b.boxAt(0f, 0.16f, 0f, 0.38f, 0.32f, 0.05f, Palette.REGISTER_DARK);
        b.boxAt(0f, 0.16f, 0.035f, 0.32f, 0.24f, 0.02f, Palette.SCREEN);
        b.pop();
        b.boxAt(0f, 0.04f, 0.2f, 0.44f, 0.08f, 0.04f, Palette.REGISTER_DARK);
        b.pop();

        // Card reader on a little stand facing the customer.
        b.push();
        b.translate(Shop.REGISTER_X + 0.75f, h, Shop.REGISTER_Z + 0.18f);
        b.cylinder(0.05f, 0.16f, 10, Palette.METAL_DARK);
        b.translate(0f, 0.16f, 0f);
        b.rotateX(-0.5f);
        b.boxAt(0f, 0.08f, 0f, 0.16f, 0.22f, 0.04f, Palette.REGISTER_DARK);
        b.boxAt(0f, 0.11f, 0.026f, 0.12f, 0.1f, 0.01f, Palette.SCREEN);
        b.pop();

        // Impulse-buy basket on the counter.
        b.push();
        b.translate(x0 + 0.7f, h, (z0 + z1) * 0.5f);
        b.boxAt(0f, 0.09f, 0f, 0.6f, 0.18f, 0.36f, Palette.WOOD_LIGHT);
        b.boxAt(0f, 0.2f, 0f, 0.5f, 0.1f, 0.28f, Palette.ACCENT_WARM);
        b.pop();
    }

    private void buildStockroomDoor(MeshBuilder b) {
        float z = -Shop.HALF_DEPTH;
        b.boxSpan(Shop.STOCKROOM_X - 0.7f, 0f, z + 0.02f,
                Shop.STOCKROOM_X + 0.7f, 2.1f, z + 0.12f, Palette.WOOD_DARK);
        b.boxSpan(Shop.STOCKROOM_X - 0.6f, 0.08f, z + 0.12f,
                Shop.STOCKROOM_X + 0.6f, 2.0f, z + 0.16f, Palette.WOOD);
        b.boxSpan(Shop.STOCKROOM_X - 0.5f, 1.45f, z + 0.16f,
                Shop.STOCKROOM_X + 0.5f, 1.85f, z + 0.18f, Palette.METAL_DARK);
        // Crates stacked beside the hatch.
        b.push();
        b.translate(Shop.STOCKROOM_X + 1.35f, 0f, z + 0.55f);
        b.boxAt(0f, 0.22f, 0f, 0.62f, 0.44f, 0.62f, Palette.WOOD_LIGHT);
        b.boxAt(0.05f, 0.64f, 0.03f, 0.55f, 0.4f, 0.55f, Palette.WOOD);
        b.rotateY(0.3f);
        b.boxAt(0f, 0.96f, 0f, 0.48f, 0.24f, 0.48f, Palette.ACCENT_WARM);
        b.pop();
    }

    private void buildDecor(MeshBuilder b) {
        // Corner plants.
        plant(b, -Shop.HALF_WIDTH + 0.85f, Shop.HALF_DEPTH - 0.9f, 1f);
        plant(b, Shop.HALF_WIDTH - 0.8f, -Shop.HALF_DEPTH + 0.9f, 1.15f);
        plant(b, -Shop.HALF_WIDTH + 0.8f, -Shop.HALF_DEPTH + 1.0f, 0.85f);

        // Pendant lamps down the aisles.
        for (int i = 0; i < 3; i++) {
            float z = -5f + i * 4.2f;
            pendantLamp(b, -3.6f, z);
            pendantLamp(b, 3.6f, z);
        }
        pendantLamp(b, 0f, Shop.HALF_DEPTH - 2.2f);

        // Posters on the side walls.
        poster(b, -Shop.HALF_WIDTH + 0.06f, 1.9f, -2.5f, 1.4f, 1.0f, Palette.ACCENT_WARM, true);
        poster(b, -Shop.HALF_WIDTH + 0.06f, 1.9f, 1.5f, 1.4f, 1.0f, Palette.SIGN_RED, true);
        poster(b, Shop.HALF_WIDTH - 0.06f, 1.9f, -1.0f, 1.4f, 1.0f, Palette.ACCENT, false);

        // Chiller cabinet against the right wall.
        b.push();
        b.translate(Shop.HALF_WIDTH - 0.65f, 0f, 5.2f);
        b.boxAt(0f, 0.95f, 0f, 1.0f, 1.9f, 1.6f, Palette.METAL);
        b.boxAt(-0.42f, 1.0f, 0f, 0.14f, 1.5f, 1.3f, Palette.GLASS);
        b.boxAt(0f, 1.94f, 0f, 1.06f, 0.1f, 1.66f, Palette.METAL_DARK);
        b.pop();
    }

    private void plant(MeshBuilder b, float x, float z, float scale) {
        b.push();
        b.translate(x, 0f, z);
        b.scale(scale, scale, scale);
        b.cone(0.26f, 0.32f, 0.36f, 12, Palette.POT);
        b.translate(0f, 0.36f, 0f);
        b.cylinder(0.05f, 0.5f, 6, Palette.PLANT_DARK);
        b.translate(0f, 0.5f, 0f);
        b.sphere(0.42f, 10, 6, Palette.PLANT);
        b.push();
        b.translate(0.22f, 0.24f, 0.1f);
        b.sphere(0.26f, 8, 5, Palette.PLANT_DARK);
        b.pop();
        b.push();
        b.translate(-0.2f, 0.3f, -0.14f);
        b.sphere(0.23f, 8, 5, Palette.PLANT);
        b.pop();
        b.pop();
    }

    private void pendantLamp(MeshBuilder b, float x, float z) {
        b.push();
        b.translate(x, Shop.WALL_HEIGHT - 0.02f, z);
        b.push();
        b.translate(0f, -0.55f, 0f);
        b.cylinder(0.02f, 0.57f, 6, Palette.METAL_DARK);
        b.pop();
        b.push();
        b.translate(0f, -0.82f, 0f);
        b.cone(0.34f, 0.12f, 0.27f, 14, Palette.LAMP_SHADE);
        b.pop();
        b.push();
        b.translate(0f, -0.86f, 0f);
        b.sphere(0.12f, 8, 5, Palette.LAMP_GLOW);
        b.pop();
        b.pop();
    }

    private void poster(MeshBuilder b, float x, float y, float z, float w, float h,
                        int color, boolean facingRight) {
        b.push();
        b.translate(x, y, z);
        b.rotateY(facingRight ? (float) Math.PI * 0.5f : -(float) Math.PI * 0.5f);
        b.boxAt(0f, 0f, 0f, w + 0.1f, h + 0.1f, 0.03f, Palette.WOOD_DARK);
        b.boxAt(0f, 0f, 0.025f, w, h, 0.02f, color);
        b.boxAt(0f, h * 0.18f, 0.035f, w * 0.62f, h * 0.22f, 0.01f, Palette.LAMP_SHADE);
        b.boxAt(0f, -h * 0.2f, 0.035f, w * 0.44f, h * 0.14f, 0.01f, Palette.LAMP_SHADE);
        b.pop();
    }

    // -------------------------------------------------------------------- shelf

    /** Three-tier gondola unit; origin at the centre of the footprint, on the floor. */
    private Mesh buildShelfUnit() {
        MeshBuilder b = builder;
        b.reset();

        float hw = Shop.shelfWidth() * 0.5f;
        float hd = Shop.shelfDepth() * 0.5f;
        float height = 1.8f;

        // Side panels and back.
        b.boxSpan(-hw, 0f, -hd, -hw + 0.08f, height, hd, Palette.WOOD);
        b.boxSpan(hw - 0.08f, 0f, -hd, hw, height, hd, Palette.WOOD);
        b.boxSpan(-hw, 0f, -hd, hw, height, -hd + 0.07f, Palette.WOOD_DARK);
        // Toe kick.
        b.boxSpan(-hw, 0f, -hd, hw, 0.14f, hd, Palette.WOOD_DARK);
        // Top cap.
        b.boxSpan(-hw - 0.04f, height - 0.08f, -hd - 0.04f, hw + 0.04f, height, hd + 0.04f, Palette.WOOD_LIGHT);

        // Tier boards, tilted slightly forward like a real gondola.
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            float y = tierY(tier);
            b.boxSpan(-hw + 0.08f, y - 0.05f, -hd + 0.07f, hw - 0.08f, y, hd - 0.02f, Palette.WOOD_LIGHT);
            // Price rail along the front edge.
            b.boxSpan(-hw + 0.08f, y, hd - 0.06f, hw - 0.08f, y + 0.07f, hd - 0.02f, Palette.METAL);
        }
        return b.buildAndReset();
    }

    public static final int TIER_COUNT = 3;
    public static final int SLOTS_PER_TIER = 6;
    public static final int VISIBLE_SLOTS = TIER_COUNT * SLOTS_PER_TIER;

    /** Height of the board for a tier, counting from the bottom. */
    public static float tierY(int tier) { return 0.38f + tier * 0.48f; }

    /** Local X of a slot within a tier. */
    public static float slotX(int slot) {
        float usable = Shop.shelfWidth() - 0.38f;
        float step = usable / SLOTS_PER_TIER;
        return -usable * 0.5f + step * (slot + 0.5f);
    }

    private Mesh buildShelfGhost() {
        MeshBuilder b = builder;
        b.reset();
        float hw = Shop.shelfWidth() * 0.5f;
        float hd = Shop.shelfDepth() * 0.5f;
        b.floorQuad(-hw, -hd, hw, hd, 0.03f, 0xFFFFFF);
        // Dashed outline.
        float dash = 0.26f;
        for (float x = -hw; x < hw - 0.01f; x += dash * 2) {
            float x2 = Math.min(x + dash, hw);
            b.boxSpan(x, 0.03f, -hd, x2, 0.09f, -hd + 0.06f, 0xFFFFFF);
            b.boxSpan(x, 0.03f, hd - 0.06f, x2, 0.09f, hd, 0xFFFFFF);
        }
        for (float z = -hd; z < hd - 0.01f; z += dash * 2) {
            float z2 = Math.min(z + dash, hd);
            b.boxSpan(-hw, 0.03f, z, -hw + 0.06f, 0.09f, z2, 0xFFFFFF);
            b.boxSpan(hw - 0.06f, 0.03f, z, hw, 0.09f, z2, 0xFFFFFF);
        }
        // A plus sign floating in the middle of the pad.
        b.boxAt(0f, 0.5f, 0f, 0.62f, 0.14f, 0.14f, 0xFFFFFF);
        b.boxAt(0f, 0.5f, 0f, 0.14f, 0.62f, 0.14f, 0xFFFFFF);
        return b.buildAndReset();
    }

    private Mesh buildSelectionRing() {
        MeshBuilder b = builder;
        b.reset();
        int segments = 28;
        float inner = 0.94f, outer = 1.06f;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            b.quad(c0 * inner, 0f, s0 * inner,
                    c1 * inner, 0f, s1 * inner,
                    c1 * outer, 0f, s1 * outer,
                    c0 * outer, 0f, s0 * outer,
                    0f, 1f, 0f, 0xFFFFFF, 1f);
        }
        return b.buildAndReset();
    }

    // -------------------------------------------------------------------- goods

    private void buildGoods() {
        ProductType.Shape[] shapes = ProductType.Shape.values();
        goods = new Mesh[shapes.length];
        for (int i = 0; i < shapes.length; i++) {
            goods[i] = buildGood(shapes[i]);
        }
    }

    /** One unit of merchandise, built white so the product colour comes from the tint. */
    private Mesh buildGood(ProductType.Shape shape) {
        MeshBuilder b = builder;
        b.reset();
        final int W = 0xFFFFFF;
        switch (shape) {
            case LOAF:
                b.push();
                b.translate(0f, 0.09f, 0f);
                b.scale(1f, 0.62f, 0.72f);
                b.sphere(0.16f, 10, 6, W);
                b.pop();
                b.boxAt(0f, 0.015f, 0f, 0.26f, 0.03f, 0.18f, W);
                break;
            case CARTON:
                b.boxAt(0f, 0.12f, 0f, 0.15f, 0.24f, 0.15f, W);
                b.push();
                b.translate(0f, 0.24f, 0f);
                b.rotateZ(0.0f);
                b.cone(0.106f, 0.02f, 0.07f, 4, W);
                b.pop();
                break;
            case BAG:
                b.boxAt(0f, 0.11f, 0f, 0.19f, 0.22f, 0.11f, W);
                b.boxAt(0f, 0.23f, 0f, 0.14f, 0.04f, 0.07f, W);
                break;
            case WEDGE:
                b.push();
                b.rotateY(0.4f);
                b.wedge(0.26f, 0.15f, 0.22f, W);
                b.pop();
                break;
            case BOX:
                b.boxAt(0f, 0.06f, 0f, 0.24f, 0.12f, 0.16f, W);
                b.boxAt(0f, 0.125f, 0f, 0.2f, 0.02f, 0.13f, W);
                break;
            case BOTTLE:
                b.cylinder(0.06f, 0.2f, 10, W);
                b.push();
                b.translate(0f, 0.2f, 0f);
                b.cone(0.06f, 0.026f, 0.1f, 10, W);
                b.translate(0f, 0.1f, 0f);
                b.cylinder(0.026f, 0.08f, 8, W);
                b.translate(0f, 0.08f, 0f);
                b.cylinder(0.032f, 0.03f, 8, W);
                b.pop();
                break;
            case TRAY:
                b.boxAt(0f, 0.03f, 0f, 0.3f, 0.06f, 0.2f, W);
                b.boxAt(0f, 0.075f, 0f, 0.27f, 0.03f, 0.17f, W);
                for (int i = 0; i < 3; i++) {
                    b.push();
                    b.translate(-0.08f + i * 0.08f, 0.1f, 0f);
                    b.cylinder(0.032f, 0.05f, 8, W);
                    b.pop();
                }
                break;
            case JAR:
            default:
                b.cylinder(0.085f, 0.13f, 12, W);
                b.push();
                b.translate(0f, 0.13f, 0f);
                b.cylinder(0.095f, 0.04f, 12, W);
                b.pop();
                break;
        }
        return b.buildAndReset();
    }

    // --------------------------------------------------------------- characters

    private void buildCharacterParts() {
        MeshBuilder b = builder;
        final int W = 0xFFFFFF;

        b.reset();
        b.push();
        b.scale(1f, 1.08f, 0.94f);
        b.sphere(0.17f, 12, 8, W);
        b.pop();
        // Nose, so a character's facing is readable at a glance.
        b.push();
        b.translate(0f, -0.01f, 0.16f);
        b.scale(0.6f, 0.6f, 1.1f);
        b.sphere(0.05f, 6, 4, W);
        b.pop();
        head = b.buildAndReset();

        b.reset();
        b.push();
        b.translate(0f, 0.035f, -0.01f);
        b.scale(1.06f, 0.86f, 1.06f);
        b.sphere(0.175f, 12, 7, W);
        b.pop();
        hair = b.buildAndReset();

        b.reset();
        b.push();
        b.translate(0f, 0.11f, 0f);
        b.cylinder(0.185f, 0.09f, 14, W);
        b.pop();
        b.push();
        b.translate(0f, 0.1f, 0.17f);
        b.scale(1f, 0.35f, 1f);
        b.cylinder(0.2f, 0.06f, 12, W);
        b.pop();
        cap = b.buildAndReset();

        b.reset();
        // Tapered torso: wider at the shoulders than the waist.
        b.boxAt(0f, 0.34f, 0f, 0.44f, 0.68f, 0.26f, W);
        b.boxAt(0f, 0.66f, 0f, 0.5f, 0.12f, 0.28f, W);
        b.boxAt(0f, 0.72f, 0f, 0.17f, 0.08f, 0.17f, W);
        torso = b.buildAndReset();

        b.reset();
        // Arm hangs down from a pivot at the local origin.
        b.boxAt(0f, -0.28f, 0f, 0.13f, 0.56f, 0.15f, W);
        b.push();
        b.translate(0f, -0.6f, 0.01f);
        b.sphere(0.075f, 8, 5, W);
        b.pop();
        arm = b.buildAndReset();

        b.reset();
        b.boxAt(0f, -0.36f, 0f, 0.17f, 0.72f, 0.18f, W);
        leg = b.buildAndReset();

        b.reset();
        b.boxAt(0f, 0.045f, 0.035f, 0.19f, 0.09f, 0.28f, W);
        shoe = b.buildAndReset();

        b.reset();
        b.boxAt(0f, 0.26f, 0.145f, 0.4f, 0.56f, 0.03f, W);
        b.boxAt(0f, 0.54f, 0.145f, 0.16f, 0.06f, 0.02f, W);
        apron = b.buildAndReset();

        b.reset();
        // Shopping basket: open box with a handle.
        b.boxSpan(-0.17f, 0f, -0.12f, 0.17f, 0.03f, 0.12f, W);
        b.boxSpan(-0.17f, 0f, -0.13f, 0.17f, 0.16f, -0.11f, W);
        b.boxSpan(-0.17f, 0f, 0.11f, 0.17f, 0.16f, 0.13f, W);
        b.boxSpan(-0.18f, 0f, -0.13f, -0.16f, 0.16f, 0.13f, W);
        b.boxSpan(0.16f, 0f, -0.13f, 0.18f, 0.16f, 0.13f, W);
        b.boxSpan(-0.02f, 0.16f, -0.02f, 0.02f, 0.3f, 0.02f, W);
        b.boxSpan(-0.1f, 0.28f, -0.02f, 0.1f, 0.32f, 0.02f, W);
        basket = b.buildAndReset();

        b.reset();
        b.boxAt(0f, 0.16f, 0f, 0.46f, 0.32f, 0.36f, W);
        b.boxAt(0f, 0.33f, 0f, 0.4f, 0.04f, 0.3f, W);
        crate = b.buildAndReset();
    }
}
