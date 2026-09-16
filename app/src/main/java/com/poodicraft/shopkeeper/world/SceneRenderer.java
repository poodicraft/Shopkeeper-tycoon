package com.poodicraft.shopkeeper.world;

import com.poodicraft.shopkeeper.character.CharacterMesh;
import com.poodicraft.shopkeeper.character.Skeleton;
import com.poodicraft.shopkeeper.game.Actor;
import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.GameState;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Staff;
import com.poodicraft.shopkeeper.gl.Camera;
import com.poodicraft.shopkeeper.gl.GpuMesh;
import com.poodicraft.shopkeeper.gl.MeshData;
import com.poodicraft.shopkeeper.gl.RenderPipeline;
import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * Turns the simulation into draw submissions.
 *
 * <p>Owns every GPU mesh, keeps each shelf's stock mesh in step with what is
 * actually on it, and culls fittings against the view frustum - at any moment
 * roughly half the shop is behind the player.
 */
public final class SceneRenderer {

    private static final int HAIR_STYLES = 3;

    private GpuMesh room;
    private GpuMesh shelfUnit;
    private GpuMesh crate;
    private GpuMesh body;
    private final GpuMesh[] hair = new GpuMesh[HAIR_STYLES];
    private final GpuMesh[] shelfGoods = new GpuMesh[ShopLayout.SHELF_SLOTS];

    private final GoodsBuilder goodsBuilder = new GoodsBuilder();
    private final Mat4 scratch = new Mat4();
    private final Mat4 rotation = new Mat4();
    private final Vec3 handLeft = new Vec3();
    private final Vec3 handRight = new Vec3();

    /** Cached so the crate mesh is only rebuilt when a shelf actually changes. */
    private final int[] lastStock = new int[ShopLayout.SHELF_SLOTS];
    private final int[] lastProduct = new int[ShopLayout.SHELF_SLOTS];

    public final RenderPipeline.Environment environment = new RenderPipeline.Environment();

    private boolean built = false;

    /** Builds every mesh. Safe to run on a worker thread: no GL calls. */
    public void buildMeshes() {
        WorldBuilder world = new WorldBuilder();
        CharacterMesh characters = new CharacterMesh();

        room = new GpuMesh(world.buildRoom());
        shelfUnit = new GpuMesh(world.buildShelfUnit());
        crate = new GpuMesh(world.buildCarryCrate());
        body = new GpuMesh(characters.buildBody(true));
        for (int i = 0; i < HAIR_STYLES; i++) {
            hair[i] = new GpuMesh(characters.buildHair(i));
        }
        for (int i = 0; i < shelfGoods.length; i++) {
            shelfGoods[i] = new GpuMesh(new MeshData(false, 16), true);
            lastStock[i] = -1;
            lastProduct[i] = -1;
        }
        built = true;
    }

    public boolean isBuilt() { return built; }

    /** Drops GPU names after a context loss; the geometry itself survives. */
    public void invalidate() {
        if (!built) return;
        room.invalidate();
        shelfUnit.invalidate();
        crate.invalidate();
        body.invalidate();
        for (int i = 0; i < hair.length; i++) hair[i].invalidate();
        for (int i = 0; i < shelfGoods.length; i++) shelfGoods[i].invalidate();
    }

    // ------------------------------------------------------------- lighting

    /**
     * Sets the lighting for the time of day.
     *
     * <p>The shop is lit by its own ceiling fittings all day; what changes is the
     * daylight coming through the front windows, the colour of the ambient and how
     * strong the sun shadows are.
     */
    public void updateEnvironment(GameState state) {
        float fraction = state.dayFraction();
        float daylight = MathUtil.clamp(
                (float) Math.sin((fraction - 0.17f) / 0.66f * Math.PI), 0f, 1f);
        daylight = MathUtil.smoothstep(daylight);
        float dusk = MathUtil.clamp(1f - Math.abs(fraction - 0.78f) / 0.09f, 0f, 1f);

        // The sun comes in through the front wall, so it swings across +Z.
        float angle = (fraction - 0.25f) * (float) Math.PI * 2f;
        environment.sunX = (float) Math.cos(angle) * 0.62f;
        environment.sunY = 0.42f + daylight * 0.55f;
        environment.sunZ = 0.45f + (float) Math.sin(angle) * 0.28f;

        environment.sunR = MathUtil.lerp(0.30f, 1.08f, daylight) + dusk * 0.28f;
        environment.sunG = MathUtil.lerp(0.26f, 1.00f, daylight) + dusk * 0.10f;
        environment.sunB = MathUtil.lerp(0.30f, 0.92f, daylight);

        environment.skyR = MathUtil.lerp(0.10f, 0.40f, daylight);
        environment.skyG = MathUtil.lerp(0.11f, 0.45f, daylight);
        environment.skyB = MathUtil.lerp(0.16f, 0.54f, daylight);
        environment.groundR = MathUtil.lerp(0.09f, 0.21f, daylight);
        environment.groundG = MathUtil.lerp(0.08f, 0.20f, daylight);
        environment.groundB = MathUtil.lerp(0.09f, 0.18f, daylight);

        environment.clearR = MathUtil.lerp(0.04f, 0.58f, daylight);
        environment.clearG = MathUtil.lerp(0.05f, 0.71f, daylight);
        environment.clearB = MathUtil.lerp(0.09f, 0.86f, daylight);
        environment.fogR = environment.clearR;
        environment.fogG = environment.clearG;
        environment.fogB = environment.clearB;
        environment.fogDensity = 0.0055f;

        environment.shadowStrength = 0.30f + daylight * 0.60f;
        environment.exposure = MathUtil.lerp(1.20f, 1.0f, daylight);
        environment.bloomThreshold = MathUtil.lerp(0.58f, 0.78f, daylight);
        environment.bloomIntensity = MathUtil.lerp(0.62f, 0.34f, daylight);

        // Ceiling fittings, warmer and stronger once the daylight drops.
        environment.clearPointLights();
        float lampIntensity = MathUtil.lerp(0.95f, 0.42f, daylight);
        for (int i = 0; i < ShopLayout.LAMPS.length && i < 8; i++) {
            environment.addPointLight(ShopLayout.LAMPS[i][0],
                    ShopLayout.CEILING_HEIGHT - 0.20f, ShopLayout.LAMPS[i][1],
                    5.6f, 1.0f, 0.94f, 0.84f, lampIntensity);
        }
    }

    // ------------------------------------------------------------- submission

    public void submit(RenderPipeline renderer, Shop shop, Camera camera) {
        if (!built) return;

        renderer.submitStatic(room, null, null, true);
        submitShelves(renderer, shop, camera);
        submitCharacters(renderer, shop, camera);
    }

    private void submitShelves(RenderPipeline renderer, Shop shop, Camera camera) {
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned) continue;
            float radius = ShopLayout.SHELF_WIDTH * 0.75f;
            if (!camera.sphereVisible(shelf.x, ShopLayout.SHELF_HEIGHT * 0.5f, shelf.z, radius)) {
                continue;
            }
            scratch.setTranslate(shelf.x, 0f, shelf.z);
            renderer.submitStatic(shelfUnit, scratch.m, null, true);

            refreshGoods(shop, shelf);
            if (shelfGoods[i].indexCount() > 0 || shelf.stock > 0) {
                renderer.submitStatic(shelfGoods[i], scratch.m, null, true);
            }
        }
    }

    /** Rebuilds a shelf's stock mesh only when its contents have actually changed. */
    private void refreshGoods(Shop shop, Shelf shelf) {
        int i = shelf.index;
        int productOrdinal = shelf.product == null ? -1 : shelf.product.ordinal();
        if (!shelf.goodsDirty && lastStock[i] == shelf.stock && lastProduct[i] == productOrdinal) {
            return;
        }
        MeshData data = goodsBuilder.build(shelf.product, shelf.stock,
                shelf.capacity(shop.state), i);
        shelfGoods[i].update(data);
        lastStock[i] = shelf.stock;
        lastProduct[i] = productOrdinal;
        shelf.goodsDirty = false;
    }

    private void submitCharacters(RenderPipeline renderer, Shop shop, Camera camera) {
        submitActor(renderer, camera, shop.player, true);
        if (shop.player.isCarrying()) submitCarried(renderer, shop.player);

        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            if (Math.min(c.fadeIn, c.fadeOut) <= 0.05f) continue;
            submitActor(renderer, camera, c, false);
        }
        for (int i = 0; i < shop.staff.size(); i++) {
            Staff s = shop.staff.get(i);
            submitActor(renderer, camera, s, false);
            if (s.isCarrying()) submitCarried(renderer, s);
        }
    }

    private void submitActor(RenderPipeline renderer, Camera camera, Actor actor, boolean alwaysDraw) {
        // A person is about 1.8 m tall, so a 1.1 m sphere at chest height bounds them.
        if (!alwaysDraw && !camera.sphereVisible(actor.position.x, 0.95f, actor.position.z, 1.15f)) {
            return;
        }
        float[] tints = actor.appearance.materialTints();
        renderer.submitSkinned(body, actor.boneMatrices, tints, true);
        int style = MathUtil.clamp(actor.appearance.hairStyle, 0, HAIR_STYLES - 1);
        renderer.submitSkinned(hair[style], actor.boneMatrices, tints, true);
    }

    /** Places a carried crate between the character's hands. */
    private void submitCarried(RenderPipeline renderer, Actor actor) {
        actor.skeleton.boneWorldPosition(Skeleton.HAND_L, handLeft);
        actor.skeleton.boneWorldPosition(Skeleton.HAND_R, handRight);
        float cx = (handLeft.x + handRight.x) * 0.5f;
        float cy = (handLeft.y + handRight.y) * 0.5f - 0.04f;
        float cz = (handLeft.z + handRight.z) * 0.5f;

        rotation.setRotateY(actor.heading);
        scratch.set(rotation);
        scratch.m[12] = cx;
        scratch.m[13] = cy;
        scratch.m[14] = cz;
        renderer.submitStatic(crate, scratch.m, null, true);
    }

    public void dispose() {
        if (!built) return;
        room.dispose();
        shelfUnit.dispose();
        crate.dispose();
        body.dispose();
        for (int i = 0; i < hair.length; i++) hair[i].dispose();
        for (int i = 0; i < shelfGoods.length; i++) shelfGoods[i].dispose();
        built = false;
    }
}
