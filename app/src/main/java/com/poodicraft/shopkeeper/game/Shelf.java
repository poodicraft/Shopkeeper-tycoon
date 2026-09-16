package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.world.ShopLayout;

/** One gondola on the shop floor: what it sells, how much of it, at what price. */
public final class Shelf {
    public final int index;
    public final float x, z;
    /** Floor position a character stands at to work this shelf. */
    public final float approachX, approachZ;
    public final int purchaseCost;

    public boolean owned;
    public ProductType product;
    public int stock;
    public float price;

    /** Set when a stocker is on their way, so two never target the same shelf. */
    public boolean restockClaimed;
    /** The goods mesh needs rebuilding. */
    public boolean goodsDirty = true;

    public Shelf(int index) {
        this.index = index;
        this.x = ShopLayout.shelfX(index);
        this.z = ShopLayout.shelfZ(index);
        // Shoppers and staff work a gondola from its front, which faces +Z.
        this.approachX = x;
        this.approachZ = z + ShopLayout.SHELF_APPROACH;
        this.purchaseCost = ShopLayout.shelfCost(index);
    }

    public int capacity(GameState state) { return state.shelfCapacity(); }

    public boolean isStocked() { return product != null && stock > 0; }

    public float fillRatio(GameState state) {
        int cap = capacity(state);
        return cap <= 0 ? 0f : Math.min(1f, (float) stock / cap);
    }

    /** Height of the topmost occupied board, so a character reaches the right shelf. */
    public float reachHeight(GameState state) {
        float ratio = fillRatio(state);
        int tier = Math.min(com.poodicraft.shopkeeper.world.WorldBuilder.SHELF_TIERS - 1,
                (int) (ratio * com.poodicraft.shopkeeper.world.WorldBuilder.SHELF_TIERS));
        return com.poodicraft.shopkeeper.world.WorldBuilder.tierHeight(tier) + 0.15f;
    }

    public void assign(ProductType type) {
        if (product == type) return;
        product = type;
        stock = 0;
        price = type == null ? 0f : type.basePrice;
        goodsDirty = true;
    }

    public void changeStock(int delta) {
        stock += delta;
        if (stock < 0) stock = 0;
        goodsDirty = true;
    }
}
