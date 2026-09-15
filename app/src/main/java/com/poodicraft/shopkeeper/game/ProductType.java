package com.poodicraft.shopkeeper.game;

/**
 * The goods catalogue. Each tier unlocks with shop level and carries the numbers
 * the economy runs on plus the look its 3D stand-in uses on the shelf.
 */
public enum ProductType {
    BREAD     ("Bread",     1,   2f,    5f, 0xD9A05B, 1.35f, Shape.LOAF),
    MILK      ("Milk",      1,   3f,    8f, 0xEDF1F5, 1.20f, Shape.CARTON),
    COFFEE    ("Coffee",    2,   7f,   17f, 0x6B4229, 1.10f, Shape.BAG),
    CHEESE    ("Cheese",    3,  12f,   28f, 0xF2C14E, 0.95f, Shape.WEDGE),
    CHOCOLATE ("Chocolate", 4,  20f,   46f, 0x5A3825, 0.90f, Shape.BOX),
    WINE      ("Wine",      5,  34f,   78f, 0x7A1F3D, 0.75f, Shape.BOTTLE),
    SUSHI     ("Sushi",     6,  58f,  130f, 0xE8624A, 0.65f, Shape.TRAY),
    CAVIAR    ("Caviar",    7, 105f,  240f, 0x2F3A4A, 0.50f, Shape.JAR);

    /** Silhouette used when the item is drawn on a shelf or in a customer's hands. */
    public enum Shape { LOAF, CARTON, BAG, WEDGE, BOX, BOTTLE, TRAY, JAR }

    public final String displayName;
    public final int unlockLevel;
    public final float wholesaleCost;
    public final float basePrice;
    public final int color;
    /** Relative popularity; higher means more customers come looking for it. */
    public final float demandWeight;
    public final Shape shape;

    ProductType(String displayName, int unlockLevel, float wholesaleCost, float basePrice,
                int color, float demandWeight, Shape shape) {
        this.displayName = displayName;
        this.unlockLevel = unlockLevel;
        this.wholesaleCost = wholesaleCost;
        this.basePrice = basePrice;
        this.color = color;
        this.demandWeight = demandWeight;
        this.shape = shape;
    }

    public static final ProductType[] ALL = values();

    public boolean isUnlocked(int shopLevel) { return shopLevel >= unlockLevel; }

    /** Bulk price for a restock order, with a small discount on larger quantities. */
    public float orderCost(int quantity) {
        float discount = quantity >= 50 ? 0.90f : (quantity >= 20 ? 0.95f : 1f);
        return wholesaleCost * quantity * discount;
    }

    public static ProductType byName(String name) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].name().equals(name)) return ALL[i];
        }
        return null;
    }
}
