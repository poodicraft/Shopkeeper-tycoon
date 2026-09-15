package com.poodicraft.shopkeeper.game;

/** Currency formatting shared by the HUD, panels and floating labels. */
public final class Money {
    private Money() { }

    /** Compact form for the HUD: $12.40, $8.2k, $3.1M. */
    public static String format(double amount) {
        boolean negative = amount < 0;
        double v = Math.abs(amount);
        String body;
        if (v >= 1_000_000_000d) body = trim(v / 1_000_000_000d) + "B";
        else if (v >= 1_000_000d) body = trim(v / 1_000_000d) + "M";
        else if (v >= 10_000d) body = trim(v / 1000d) + "k";
        else if (v >= 100d) body = String.valueOf((long) v);
        else body = trim2(v);
        return (negative ? "-$" : "$") + body;
    }

    /** Always-exact form for shop panels where the cents matter. */
    public static String exact(double amount) {
        return (amount < 0 ? "-$" : "$") + trim2(Math.abs(amount));
    }

    private static String trim(double v) {
        double rounded = Math.round(v * 10d) / 10d;
        if (rounded == Math.floor(rounded)) return String.valueOf((long) rounded);
        return String.valueOf(rounded);
    }

    private static String trim2(double v) {
        long cents = Math.round(v * 100d);
        long whole = cents / 100;
        long frac = cents % 100;
        if (frac == 0) return String.valueOf(whole);
        return whole + "." + (frac < 10 ? "0" : "") + frac;
    }
}
