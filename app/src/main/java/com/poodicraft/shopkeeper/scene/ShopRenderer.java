package com.poodicraft.shopkeeper.scene;

import com.poodicraft.shopkeeper.game.Agent;
import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Staff;
import com.poodicraft.shopkeeper.gl.Camera;
import com.poodicraft.shopkeeper.gl.Mesh;
import com.poodicraft.shopkeeper.gl.Renderer3D;
import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.MathUtil;

/** Turns the simulation state into draw calls, including the day/night lighting. */
public final class ShopRenderer {

    private static final float HIP_Y = 0.78f;
    private static final float SHOULDER_Y = 1.42f;
    private static final float HEAD_Y = 1.70f;
    private static final float ARM_X = 0.285f;
    private static final float LEG_X = 0.115f;

    private final SceneAssets assets;
    private final Renderer3D renderer;
    private final Renderer3D.Lighting lighting = new Renderer3D.Lighting();

    private final Mat4 model = new Mat4();
    private final Mat4 part = new Mat4();
    private final Mat4 sub = new Mat4();
    private final Mat4 tmp = new Mat4();

    /** Shelf the player currently has selected, or -1. */
    public int selectedShelf = -1;
    public float time = 0f;

    /** Ambient clear colour for the current time of day. */
    public float clearR = 0.1f, clearG = 0.12f, clearB = 0.18f;

    public ShopRenderer(SceneAssets assets, Renderer3D renderer) {
        this.assets = assets;
        this.renderer = renderer;
    }

    public void render(Shop shop, Camera camera, float dt) {
        time += dt;
        updateLighting(shop.state.dayFraction());

        renderer.clear(clearR, clearG, clearB);
        renderer.beginFrame(camera, lighting);

        renderer.draw(assets.environment, (Mat4) null, 1f, 1f, 1f, 1f, false);
        drawShelves(shop);
        drawPeople(shop);

        renderer.beginTransparentPass();
        drawShadows(shop);
        drawGhostSlots(shop);
        drawSelection(shop);
        renderer.endTransparentPass();
    }

    // ----------------------------------------------------------------- lighting

    private void updateLighting(float dayFraction) {
        float daylight = MathUtil.clamp(
                (float) Math.sin((dayFraction - 0.16f) / 0.68f * Math.PI), 0f, 1f);
        daylight = MathUtil.smoothstep(daylight);

        // The sun sweeps across the shop as the day advances.
        float angle = (dayFraction - 0.25f) * (float) Math.PI * 2f;
        lighting.sunX = (float) Math.cos(angle) * 0.55f;
        lighting.sunY = 0.45f + daylight * 0.55f;
        lighting.sunZ = (float) Math.sin(angle) * 0.35f + 0.3f;
        lighting.normalizeSun();

        // Interior lamps keep a warm floor of light even at night.
        lighting.sunR = MathUtil.lerp(0.62f, 1.02f, daylight);
        lighting.sunG = MathUtil.lerp(0.48f, 0.97f, daylight);
        lighting.sunB = MathUtil.lerp(0.34f, 0.88f, daylight);

        lighting.skyR = MathUtil.lerp(0.16f, 0.42f, daylight);
        lighting.skyG = MathUtil.lerp(0.17f, 0.46f, daylight);
        lighting.skyB = MathUtil.lerp(0.26f, 0.54f, daylight);

        lighting.groundR = MathUtil.lerp(0.13f, 0.26f, daylight);
        lighting.groundG = MathUtil.lerp(0.11f, 0.24f, daylight);
        lighting.groundB = MathUtil.lerp(0.12f, 0.21f, daylight);

        clearR = MathUtil.lerp(0.055f, 0.53f, daylight);
        clearG = MathUtil.lerp(0.065f, 0.70f, daylight);
        clearB = MathUtil.lerp(0.105f, 0.86f, daylight);

        lighting.fogR = clearR;
        lighting.fogG = clearG;
        lighting.fogB = clearB;
        lighting.fogDensity = 0.009f;
    }

    // ------------------------------------------------------------------ shelves

    private void drawShelves(Shop shop) {
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf s = shop.shelves.get(i);
            if (!s.owned) continue;
            float glow = 1f + s.highlight * 0.22f;
            model.setTranslate(s.x, 0f, s.z);
            if (s.rotation != 0f) {
                tmp.setRotateY(s.rotation);
                model.multiply(tmp);
            }
            renderer.draw(assets.shelfUnit, model, glow, glow, glow, 1f, false);
            drawShelfGoods(shop, s);
        }
    }

    private void drawShelfGoods(Shop shop, Shelf s) {
        if (s.product == null || s.stock <= 0) return;
        Mesh good = assets.goods[s.product.shape.ordinal()];
        float r = Palette.red(s.product.color);
        float g = Palette.green(s.product.color);
        float b = Palette.blue(s.product.color);

        int capacity = s.capacity(shop.state);
        int shown = Math.min(SceneAssets.VISIBLE_SLOTS,
                Math.max(1, Math.round(s.stock * (float) SceneAssets.VISIBLE_SLOTS / capacity)));
        if (s.stock > 0 && shown <= 0) shown = 1;

        boolean bound = false;
        for (int i = 0; i < shown; i++) {
            int tier = i % SceneAssets.TIER_COUNT;
            int slot = i / SceneAssets.TIER_COUNT;
            if (slot >= SceneAssets.SLOTS_PER_TIER) break;

            model.setTranslate(s.x, 0f, s.z);
            if (s.rotation != 0f) {
                tmp.setRotateY(s.rotation);
                model.multiply(tmp);
            }
            tmp.setTranslate(SceneAssets.slotX(slot), SceneAssets.tierY(tier), 0.04f);
            model.multiply(tmp);
            // Nudge each unit so a full shelf does not look machine-stamped.
            tmp.setRotateY(((i * 37) % 17) * 0.035f - 0.28f);
            model.multiply(tmp);

            if (!bound) {
                renderer.draw(good, model, r, g, b, 1f, false);
                bound = true;
            } else {
                renderer.drawAgain(model.m, r, g, b, 1f, false);
            }
        }
    }

    private void drawGhostSlots(Shop shop) {
        float pulse = 0.45f + 0.25f * (float) Math.sin(time * 2.2);
        boolean bound = false;
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf s = shop.shelves.get(i);
            if (s.owned) continue;
            boolean affordable = shop.state.money >= s.purchaseCost;
            float r = affordable ? Palette.red(Palette.GHOST) : 0.55f;
            float g = affordable ? Palette.green(Palette.GHOST) : 0.55f;
            float b = affordable ? Palette.blue(Palette.GHOST) : 0.58f;
            model.setTranslate(s.x, 0f, s.z);
            if (!bound) {
                renderer.draw(assets.shelfGhost, model, r, g, b, pulse, true);
                bound = true;
            } else {
                renderer.drawAgain(model.m, r, g, b, pulse, true);
            }
        }
    }

    private void drawSelection(Shop shop) {
        Shelf s = shop.shelf(selectedShelf);
        if (s == null) return;
        float pulse = 0.55f + 0.2f * (float) Math.sin(time * 5.0);
        model.setTranslate(s.x, 0.02f, s.z);
        tmp.setScale(1.55f, 1f, 0.85f);
        model.multiply(tmp);
        renderer.draw(assets.selectionRing, model, 1f, 0.85f, 0.35f, pulse, true);
    }

    // ------------------------------------------------------------------- people

    private void drawPeople(Shop shop) {
        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            float alpha = Math.min(c.fadeIn, c.fadeOut);
            if (alpha <= 0.02f) continue;
            drawCharacter(c, alpha, c.carryBlend, false);
            if (c.carryBlend > 0.05f) drawCarriedBasket(c, alpha);
        }
        for (int i = 0; i < shop.staff.size(); i++) {
            Staff s = shop.staff.get(i);
            drawCharacter(s, 1f, s.carryBlend, true);
            if (s.carryBlend > 0.05f) drawCarriedCrate(s);
        }
    }

    private void drawCharacter(Agent a, float alpha, float carry, boolean isStaff) {
        float swing = (float) Math.sin(a.walkPhase);
        float legSwing = swing * 0.62f * a.walkBlend;
        float armSwing = -swing * 0.5f * a.walkBlend;
        float bob = (Math.abs(swing) - 0.5f) * 0.045f * a.walkBlend;
        float lean = 0.08f * a.walkBlend;

        model.setTranslate(a.pos.x, bob, a.pos.z);
        tmp.setRotateY(a.heading);
        model.multiply(tmp);
        tmp.setScale(a.heightScale, a.heightScale, a.heightScale);
        model.multiply(tmp);
        tmp.setRotateX(lean);
        model.multiply(tmp);

        float skinR = Palette.red(a.skinColor), skinG = Palette.green(a.skinColor), skinB = Palette.blue(a.skinColor);
        float shirtR = Palette.red(a.shirtColor), shirtG = Palette.green(a.shirtColor), shirtB = Palette.blue(a.shirtColor);
        float legR = Palette.red(a.trouserColor), legG = Palette.green(a.trouserColor), legB = Palette.blue(a.trouserColor);
        float hairR = Palette.red(a.hairColor), hairG = Palette.green(a.hairColor), hairB = Palette.blue(a.hairColor);

        // Legs, each swinging about the hip.
        for (int side = 0; side < 2; side++) {
            float dir = side == 0 ? 1f : -1f;
            part.set(model);
            translate(part, LEG_X * dir, HIP_Y, 0f);
            rotateX(part, legSwing * dir);
            renderer.draw(assets.leg, part, legR, legG, legB, alpha, false);
            sub.set(part);
            translate(sub, 0f, -0.72f, 0f);
            renderer.draw(assets.shoe, sub, legR * 0.55f, legG * 0.55f, legB * 0.55f, alpha, false);
        }

        // Torso.
        part.set(model);
        translate(part, 0f, HIP_Y - 0.06f, 0f);
        renderer.draw(assets.torso, part, shirtR, shirtG, shirtB, alpha, false);
        if (isStaff) {
            renderer.draw(assets.apron, part, 0.94f, 0.94f, 0.9f, alpha, false);
        }

        // Arms: they swing while walking and swing forward when carrying something.
        float carryPose = carry * -1.15f;
        for (int side = 0; side < 2; side++) {
            float dir = side == 0 ? 1f : -1f;
            part.set(model);
            translate(part, ARM_X * dir, SHOULDER_Y, 0f);
            rotateZ(part, dir * 0.08f);
            rotateX(part, armSwing * dir * (1f - carry) + carryPose);
            renderer.draw(assets.arm, part, shirtR, shirtG, shirtB, alpha, false);
        }

        // Head, hair and optional headwear.
        part.set(model);
        translate(part, 0f, HEAD_Y, 0f);
        rotateY(part, (float) Math.sin(time * 0.7 + a.pos.x) * 0.06f);
        renderer.draw(assets.head, part, skinR, skinG, skinB, alpha, false);
        renderer.draw(assets.hair, part, hairR, hairG, hairB, alpha, false);
        if (a.accessory == 1 || isStaff) {
            sub.set(part);
            translate(sub, 0f, 0.06f, 0f);
            float capR = isStaff ? 0.95f : shirtR;
            float capG = isStaff ? 0.95f : shirtG;
            float capB = isStaff ? 0.95f : shirtB;
            renderer.draw(assets.cap, sub, capR, capG, capB, alpha, false);
        }
    }

    private void drawCarriedBasket(Customer c, float alpha) {
        model.setTranslate(c.pos.x, 0f, c.pos.z);
        tmp.setRotateY(c.heading);
        model.multiply(tmp);
        tmp.setScale(c.heightScale, c.heightScale, c.heightScale);
        model.multiply(tmp);
        translate(model, ARM_X, SHOULDER_Y - 0.52f, 0.42f * c.carryBlend);
        renderer.draw(assets.basket, model, 0.86f, 0.4f, 0.3f, alpha, false);

        // Show the top item poking out of the basket.
        if (!c.basket.isEmpty()) {
            ProductType p = c.basket.get(c.basket.size() - 1).product;
            translate(model, 0f, 0.05f, 0f);
            renderer.draw(assets.goods[p.shape.ordinal()], model,
                    Palette.red(p.color), Palette.green(p.color), Palette.blue(p.color), alpha, false);
        }
    }

    private void drawCarriedCrate(Staff s) {
        model.setTranslate(s.pos.x, 0f, s.pos.z);
        tmp.setRotateY(s.heading);
        model.multiply(tmp);
        translate(model, 0f, 0.95f, 0.36f * s.carryBlend);
        renderer.draw(assets.crate, model, 0.72f, 0.52f, 0.32f, 1f, false);
        if (s.carrying != null) {
            translate(model, 0f, 0.3f, 0f);
            renderer.draw(assets.goods[s.carrying.shape.ordinal()], model,
                    Palette.red(s.carrying.color), Palette.green(s.carrying.color),
                    Palette.blue(s.carrying.color), 1f, false);
        }
    }

    private void drawShadows(Shop shop) {
        boolean bound = false;
        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            float alpha = Math.min(c.fadeIn, c.fadeOut) * 0.32f;
            if (alpha <= 0.01f) continue;
            bound = drawShadow(c.pos.x, c.pos.z, c.heightScale, alpha, bound);
        }
        for (int i = 0; i < shop.staff.size(); i++) {
            Staff s = shop.staff.get(i);
            bound = drawShadow(s.pos.x, s.pos.z, s.heightScale, 0.32f, bound);
        }
    }

    private boolean drawShadow(float x, float z, float scale, float alpha, boolean bound) {
        model.setTranslate(x, 0.012f, z);
        tmp.setScale(scale, 1f, scale);
        model.multiply(tmp);
        if (!bound) {
            renderer.draw(assets.shadowDisc, model,
                    Palette.red(Palette.SHADOW), Palette.green(Palette.SHADOW),
                    Palette.blue(Palette.SHADOW), alpha, true);
            return true;
        }
        renderer.drawAgain(model.m,
                Palette.red(Palette.SHADOW), Palette.green(Palette.SHADOW),
                Palette.blue(Palette.SHADOW), alpha, true);
        return true;
    }

    // ------------------------------------------------------------ matrix helpers

    private void translate(Mat4 m, float x, float y, float z) {
        tmp.setTranslate(x, y, z);
        m.multiply(tmp);
    }

    private void rotateX(Mat4 m, float a) {
        tmp.setRotateX(a);
        m.multiply(tmp);
    }

    private void rotateY(Mat4 m, float a) {
        tmp.setRotateY(a);
        m.multiply(tmp);
    }

    private void rotateZ(Mat4 m, float a) {
        tmp.setRotateZ(a);
        m.multiply(tmp);
    }
}
