package com.poodicraft.shopkeeper.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.poodicraft.shopkeeper.math.MathUtil;

/**
 * Persists the whole game to a single preferences string.
 *
 * <p>The format is a flat semicolon-separated key/value list, which survives adding
 * new fields: anything missing on load simply keeps its default.
 */
public final class SaveManager {
    private static final String PREFS = "shopkeeper_save";
    private static final String KEY_BLOB = "blob";
    private static final int VERSION = 1;

    /** Offline progress is credited for at most this long. */
    private static final long MAX_OFFLINE_MILLIS = 8L * 60L * 60L * 1000L;

    private SaveManager() { }

    public static void save(Context context, Shop shop) {
        GameState s = shop.state;
        s.lastPlayedMillis = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder(512);
        put(sb, "v", VERSION);
        put(sb, "money", s.money);
        put(sb, "day", s.day);
        put(sb, "dayTime", s.dayTime);
        put(sb, "level", s.level);
        put(sb, "xp", s.xp);
        put(sb, "sat", s.satisfaction);
        put(sb, "rev", s.totalRevenue);
        put(sb, "served", s.totalCustomersServed);
        put(sb, "lost", s.totalCustomersLost);
        put(sb, "dayRev", s.dayRevenue);
        put(sb, "dayCost", s.dayCosts);
        put(sb, "dayServed", s.dayServed);
        put(sb, "dayLost", s.dayLost);
        put(sb, "sound", s.soundEnabled ? 1 : 0);
        put(sb, "autoStock", s.autoRestockEnabled ? 1 : 0);
        put(sb, "time", s.lastPlayedMillis);
        put(sb, "carry", shop.player.carrying == null ? "-" : shop.player.carrying.name());
        put(sb, "carryN", shop.player.carryCount);

        for (int i = 0; i < s.stock.length; i++) put(sb, "st" + i, s.stock[i]);
        for (int i = 0; i < s.upgradeLevels.length; i++) put(sb, "up" + i, s.upgradeLevels[i]);

        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            put(sb, "sh" + i + "o", shelf.owned ? 1 : 0);
            put(sb, "sh" + i + "p", shelf.product == null ? "-" : shelf.product.name());
            put(sb, "sh" + i + "n", shelf.stock);
            put(sb, "sh" + i + "c", shelf.price);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BLOB, sb.toString()).apply();
    }

    public static boolean hasSave(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String blob = prefs.getString(KEY_BLOB, null);
        return blob != null && blob.length() > 0;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_BLOB).apply();
    }

    /**
     * Loads into an existing shop.
     *
     * @return the offline earnings credited, or 0 when there was no save.
     */
    public static double load(Context context, Shop shop) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String blob = prefs.getString(KEY_BLOB, null);
        if (blob == null || blob.length() == 0) return 0.0;

        Map map = Map.parse(blob);
        GameState s = shop.state;
        s.money = map.getDouble("money", s.money);
        s.day = map.getInt("day", s.day);
        s.dayTime = map.getFloat("dayTime", s.dayTime);
        s.level = Math.max(1, map.getInt("level", s.level));
        s.xp = map.getFloat("xp", s.xp);
        s.satisfaction = MathUtil.clamp(map.getFloat("sat", s.satisfaction), 0.05f, 1f);
        s.totalRevenue = map.getDouble("rev", s.totalRevenue);
        s.totalCustomersServed = map.getInt("served", s.totalCustomersServed);
        s.totalCustomersLost = map.getInt("lost", s.totalCustomersLost);
        s.dayRevenue = map.getDouble("dayRev", 0);
        s.dayCosts = map.getDouble("dayCost", 0);
        s.dayServed = map.getInt("dayServed", 0);
        s.dayLost = map.getInt("dayLost", 0);
        s.soundEnabled = map.getInt("sound", 1) != 0;
        s.autoRestockEnabled = map.getInt("autoStock", 1) != 0;
        s.lastPlayedMillis = map.getLong("time", 0L);

        for (int i = 0; i < s.stock.length; i++) s.stock[i] = map.getInt("st" + i, 0);
        for (int i = 0; i < s.upgradeLevels.length; i++) {
            s.upgradeLevels[i] = MathUtil.clamp(map.getInt("up" + i, 0), 0, Upgrade.ALL[i].maxLevel);
        }

        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            shelf.owned = map.getInt("sh" + i + "o", shelf.owned ? 1 : 0) != 0;
            String productName = map.getString("sh" + i + "p", "-");
            ProductType product = "-".equals(productName) ? null : ProductType.byName(productName);
            shelf.product = product;
            shelf.stock = MathUtil.clamp(map.getInt("sh" + i + "n", 0), 0, shelf.capacity(s));
            shelf.price = map.getFloat("sh" + i + "c", product == null ? 0f : product.basePrice);
            if (product == null) shelf.stock = 0;
            shelf.goodsDirty = true;
        }

        String carried = map.getString("carry", "-");
        ProductType carriedProduct = "-".equals(carried) ? null : ProductType.byName(carried);
        if (carriedProduct != null) {
            shop.player.carrying = carriedProduct;
            shop.player.carryCount = Math.max(0, map.getInt("carryN", 0));
            if (shop.player.carryCount == 0) shop.player.carrying = null;
        }

        shop.rebuildObstacles();
        shop.syncStaff();
        return grantOfflineEarnings(shop);
    }

    /**
     * Credits sales a hired cashier would have made while the app was closed.
     *
     * <p>Capped by what was actually on the shelves, so the stock still has to be
     * bought and the shelves are emptied by the sales.
     */
    private static double grantOfflineEarnings(Shop shop) {
        GameState s = shop.state;
        if (s.cashierCount() <= 0 || s.lastPlayedMillis <= 0L) return 0.0;
        long elapsed = System.currentTimeMillis() - s.lastPlayedMillis;
        if (elapsed < 60_000L) return 0.0;
        elapsed = Math.min(elapsed, MAX_OFFLINE_MILLIS);
        float seconds = elapsed / 1000f;

        float ratePerSecond = s.cashierCount() * (0.9f + 0.55f * s.level)
                * s.satisfaction * s.marketingMultiplier() / 60f;
        double wanted = ratePerSecond * seconds;

        double shelfValue = 0.0;
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (shelf.owned && shelf.product != null) shelfValue += shelf.stock * shelf.price;
        }
        // Idle trade only ever clears part of the shelves.
        double earned = Math.min(wanted, shelfValue * 0.6);
        if (earned < 1.0) return 0.0;

        double fraction = earned / Math.max(0.01, shelfValue);
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned || shelf.product == null) continue;
            int sold = (int) Math.floor(shelf.stock * fraction);
            shelf.stock = Math.max(0, shelf.stock - sold);
            shelf.goodsDirty = true;
        }
        s.money += earned;
        s.totalRevenue += earned;
        return earned;
    }

    // -------------------------------------------------------------- serialising

    private static void put(StringBuilder sb, String key, double value) {
        sb.append(key).append('=').append(value).append(';');
    }

    private static void put(StringBuilder sb, String key, long value) {
        sb.append(key).append('=').append(value).append(';');
    }

    private static void put(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value).append(';');
    }

    /** Minimal key/value reader; unknown or malformed entries fall back to defaults. */
    private static final class Map {
        private final java.util.HashMap<String, String> values = new java.util.HashMap<String, String>();

        static Map parse(String blob) {
            Map map = new Map();
            int start = 0;
            while (start < blob.length()) {
                int end = blob.indexOf(';', start);
                if (end < 0) end = blob.length();
                int eq = blob.indexOf('=', start);
                if (eq > start && eq < end) {
                    map.values.put(blob.substring(start, eq), blob.substring(eq + 1, end));
                }
                start = end + 1;
            }
            return map;
        }

        String getString(String key, String fallback) {
            String v = values.get(key);
            return v == null ? fallback : v;
        }

        int getInt(String key, int fallback) {
            try {
                String v = values.get(key);
                return v == null ? fallback : (int) Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        long getLong(String key, long fallback) {
            try {
                String v = values.get(key);
                return v == null ? fallback : (long) Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        float getFloat(String key, float fallback) {
            try {
                String v = values.get(key);
                return v == null ? fallback : Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        double getDouble(String key, double fallback) {
            try {
                String v = values.get(key);
                return v == null ? fallback : Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }
}
