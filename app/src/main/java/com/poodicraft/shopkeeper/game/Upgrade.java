package com.poodicraft.shopkeeper.game;

/** Everything the player can spend money on, other than stock and shelves. */
public enum Upgrade {
    REGISTER  ("Register",  "Faster checkout, shorter queues",      5,  250f, 2.15f),
    MARKETING ("Marketing", "Brings more customers through the door",6, 180f, 2.30f),
    DECOR     ("Decor",     "Happier shoppers accept higher prices", 5,  320f, 2.25f),
    SHELVING  ("Shelving",  "More units fit on every shelf",         5,  400f, 2.40f),
    CASHIER   ("Cashier",   "Serves the queue while you are away",   2,  700f, 3.60f),
    STOCKER   ("Stocker",   "Refills shelves from the stockroom",    2,  550f, 3.60f);

    public final String displayName;
    public final String description;
    public final int maxLevel;
    public final float baseCost;
    public final float growth;

    Upgrade(String displayName, String description, int maxLevel, float baseCost, float growth) {
        this.displayName = displayName;
        this.description = description;
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.growth = growth;
    }

    public static final Upgrade[] ALL = values();

    /** Cost of moving from {@code currentLevel} to the next one. */
    public float costAt(int currentLevel) {
        return (float) (baseCost * Math.pow(growth, currentLevel));
    }

    /** Daily wage this upgrade adds, which only the staff entries charge. */
    public float dailyWage(int level) {
        if (this == CASHIER) return 55f * level;
        if (this == STOCKER) return 40f * level;
        return 0f;
    }
}
