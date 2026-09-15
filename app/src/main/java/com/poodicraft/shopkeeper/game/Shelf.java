package com.poodicraft.shopkeeper.game;

/** One display unit on the shop floor: what it sells, how much of it, and at what price. */
public final class Shelf {
    public final int index;
    /** Centre of the shelf footprint, in world units. */
    public final float x, z;
    /** Heading in radians; customers browse from the front face. */
    public final float rotation;
    /** Floor position a customer stands at to browse this shelf. */
    public final float approachX, approachZ;
    /** Cost to bring this slot into service. */
    public final int purchaseCost;

    public boolean owned;
    public ProductType product;
    public int stock;
    public float price;

    /** Filled while a stocker is walking a refill over, so two never target the same shelf. */
    public boolean restockClaimed;
    /** Counts down while a browsing customer is taking an item, driving the pick animation. */
    public float highlight;

    public Shelf(int index, float x, float z, float rotation,
                 float approachX, float approachZ, int purchaseCost) {
        this.index = index;
        this.x = x;
        this.z = z;
        this.rotation = rotation;
        this.approachX = approachX;
        this.approachZ = approachZ;
        this.purchaseCost = purchaseCost;
    }

    public int capacity(GameState state) {
        return state.shelfCapacity();
    }

    public boolean isStocked() { return product != null && stock > 0; }

    public boolean hasRoom(GameState state) { return product != null && stock < capacity(state); }

    public float fillRatio(GameState state) {
        int cap = capacity(state);
        return cap <= 0 ? 0f : Math.min(1f, (float) stock / cap);
    }

    public void assign(ProductType type) {
        if (product == type) return;
        product = type;
        stock = 0;
        price = type == null ? 0f : type.basePrice;
    }
}
