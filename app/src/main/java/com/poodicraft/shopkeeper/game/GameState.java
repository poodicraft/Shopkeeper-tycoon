package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;

/**
 * All persistent player progress: wallet, clock, upgrades, stockroom and stats.
 *
 * <p>Shelf contents live on {@link Shelf} but are serialised from here so a save is
 * a single blob.
 */
public final class GameState {

    /** Seconds of real time in one in-game day. */
    public static final float DAY_LENGTH = 180f;
    /** The shop trades between these fractions of the day; outside it customers thin out. */
    public static final float OPEN_FRACTION = 0.06f;
    public static final float CLOSE_FRACTION = 0.92f;

    public double money = 400.0;
    public int day = 1;
    public float dayTime = DAY_LENGTH * 0.12f;

    public int level = 1;
    public float xp = 0f;

    /** Rolling 0..1 reputation; drives arrival rate and how much customers will pay. */
    public float satisfaction = 0.75f;

    public final int[] stock = new int[ProductType.ALL.length];
    public final int[] upgradeLevels = new int[Upgrade.ALL.length];

    public double totalRevenue = 0.0;
    public int totalCustomersServed = 0;
    public int totalCustomersLost = 0;

    public double dayRevenue = 0.0;
    public double dayCosts = 0.0;
    public int dayServed = 0;
    public int dayLost = 0;

    public boolean soundEnabled = true;
    public boolean autoRestockEnabled = true;
    /** Wall-clock millis of the last save, used to grant offline staff earnings. */
    public long lastPlayedMillis = 0L;

    public GameState() {
        stock[ProductType.BREAD.ordinal()] = 14;
        stock[ProductType.MILK.ordinal()] = 10;
    }

    // ------------------------------------------------------------------ economy

    public int upgradeLevel(Upgrade u) { return upgradeLevels[u.ordinal()]; }

    public boolean canUpgrade(Upgrade u) {
        int lvl = upgradeLevel(u);
        return lvl < u.maxLevel && money >= u.costAt(lvl);
    }

    public boolean isUpgradeMaxed(Upgrade u) { return upgradeLevel(u) >= u.maxLevel; }

    /** Applies a purchase; returns false when it is unaffordable or already maxed. */
    public boolean buyUpgrade(Upgrade u) {
        int lvl = upgradeLevel(u);
        if (lvl >= u.maxLevel) return false;
        float cost = u.costAt(lvl);
        if (money < cost) return false;
        money -= cost;
        dayCosts += cost;
        upgradeLevels[u.ordinal()] = lvl + 1;
        return true;
    }

    public int shelfCapacity() { return 12 + 6 * upgradeLevel(Upgrade.SHELVING); }

    /**
     * Seconds to scan one item at the till.
     *
     * <p>A basket is scanned item by item, so this sets how long a customer holds
     * the queue and how long the player is pinned behind the counter.
     */
    public float scanTime() {
        return 0.52f * (float) Math.pow(0.82, upgradeLevel(Upgrade.REGISTER));
    }

    public float marketingMultiplier() { return 1f + 0.34f * upgradeLevel(Upgrade.MARKETING); }

    /** How far above base price customers will still buy, before satisfaction is applied. */
    public float priceTolerance() {
        return 1.04f + 0.075f * upgradeLevel(Upgrade.DECOR) + 0.30f * (satisfaction - 0.5f);
    }

    public int cashierCount() { return upgradeLevel(Upgrade.CASHIER); }

    public int stockerCount() { return upgradeLevel(Upgrade.STOCKER); }

    public float dailyWages() {
        float total = 0f;
        for (int i = 0; i < Upgrade.ALL.length; i++) {
            total += Upgrade.ALL[i].dailyWage(upgradeLevels[i]);
        }
        return total;
    }

    public float dailyRent(int ownedShelves) { return 18f + 7f * ownedShelves; }

    // ------------------------------------------------------------------- levels

    public float xpForLevel(int lvl) { return (float) (110 * Math.pow(1.55, lvl - 1)); }

    public float levelProgress() {
        return MathUtil.clamp(xp / xpForLevel(level), 0f, 1f);
    }

    /** Adds XP and returns how many levels were gained. */
    public int addXp(float amount) {
        xp += amount;
        int gained = 0;
        while (xp >= xpForLevel(level)) {
            xp -= xpForLevel(level);
            level++;
            gained++;
        }
        return gained;
    }

    public ProductType nextUnlock() {
        ProductType best = null;
        for (int i = 0; i < ProductType.ALL.length; i++) {
            ProductType p = ProductType.ALL[i];
            if (p.unlockLevel > level && (best == null || p.unlockLevel < best.unlockLevel)) best = p;
        }
        return best;
    }

    // -------------------------------------------------------------------- clock

    /** 0 at midnight, 1 at the next midnight. */
    public float dayFraction() { return dayTime / DAY_LENGTH; }

    public boolean isOpen() {
        float f = dayFraction();
        return f >= OPEN_FRACTION && f <= CLOSE_FRACTION;
    }

    /** Formats the in-game clock as 24-hour time. */
    public String clockText() {
        float hours = dayFraction() * 24f;
        int h = (int) hours;
        int m = (int) ((hours - h) * 60f);
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    /** Bell-shaped footfall: a lunchtime peak and an evening bump. */
    public float trafficCurve() {
        float f = dayFraction();
        if (f < OPEN_FRACTION || f > CLOSE_FRACTION) return 0.05f;
        float lunch = (float) Math.exp(-Math.pow((f - 0.52f) / 0.13f, 2));
        float evening = (float) Math.exp(-Math.pow((f - 0.80f) / 0.09f, 2)) * 0.75f;
        float morning = (float) Math.exp(-Math.pow((f - 0.32f) / 0.10f, 2)) * 0.55f;
        return MathUtil.clamp(0.22f + lunch + evening + morning, 0.05f, 2.1f);
    }

    public void adjustSatisfaction(float delta) {
        satisfaction = MathUtil.clamp(satisfaction + delta, 0.05f, 1f);
    }

    public int totalStock() {
        int total = 0;
        for (int i = 0; i < stock.length; i++) total += stock[i];
        return total;
    }

    /** Units of a product waiting in the stockroom. */
    public int stockOf(ProductType product) { return stock[product.ordinal()]; }
}
