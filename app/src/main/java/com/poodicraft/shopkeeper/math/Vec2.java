package com.poodicraft.shopkeeper.math;

/** Mutable 2D vector, used for UVs and screen-space input. */
public final class Vec2 {
    public float x, y;

    public Vec2() { }

    public Vec2(float x, float y) { this.x = x; this.y = y; }

    public Vec2 set(float x, float y) { this.x = x; this.y = y; return this; }

    public float length() { return (float) Math.sqrt(x * x + y * y); }

    public Vec2 normalize() {
        float l = length();
        if (l > 1e-6f) { x /= l; y /= l; }
        return this;
    }
}
