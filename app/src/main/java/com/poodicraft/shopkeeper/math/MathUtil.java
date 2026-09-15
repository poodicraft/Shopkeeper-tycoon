package com.poodicraft.shopkeeper.math;

/** Shared numeric helpers used across simulation and rendering. */
public final class MathUtil {
    private MathUtil() { }

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Smooth 0..1 ramp, used for easing camera and UI motion. */
    public static float smoothstep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Wraps an angle into (-PI, PI], so heading interpolation takes the short way round. */
    public static float wrapAngle(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a <= -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    /** Moves {@code from} toward {@code to} by at most {@code maxStep} radians. */
    public static float approachAngle(float from, float to, float maxStep) {
        float delta = wrapAngle(to - from);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return wrapAngle(from + delta);
    }

    public static float approach(float from, float to, float maxStep) {
        if (from < to) return Math.min(from + maxStep, to);
        return Math.max(from - maxStep, to);
    }
}
