package com.poodicraft.shopkeeper.math;

/** Small mutable 3D vector. Kept allocation-light for per-frame use. */
public final class Vec3 {
    public float x, y, z;

    public Vec3() { }

    public Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vec3(Vec3 o) { this.x = o.x; this.y = o.y; this.z = o.z; }

    public Vec3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }

    public Vec3 set(Vec3 o) { this.x = o.x; this.y = o.y; this.z = o.z; return this; }

    public Vec3 add(Vec3 o) { x += o.x; y += o.y; z += o.z; return this; }

    public Vec3 sub(Vec3 o) { x -= o.x; y -= o.y; z -= o.z; return this; }

    public Vec3 scale(float s) { x *= s; y *= s; z *= s; return this; }

    public float length() { return (float) Math.sqrt(x * x + y * y + z * z); }

    public float lengthSq() { return x * x + y * y + z * z; }

    public Vec3 normalize() {
        float l = length();
        if (l > 1e-6f) { x /= l; y /= l; z /= l; }
        return this;
    }

    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public Vec3 cross(Vec3 o) {
        float nx = y * o.z - z * o.y;
        float ny = z * o.x - x * o.z;
        float nz = x * o.y - y * o.x;
        return set(nx, ny, nz);
    }

    /** Linear interpolation toward {@code o} by {@code t}. */
    public Vec3 lerp(Vec3 o, float t) {
        x += (o.x - x) * t;
        y += (o.y - y) * t;
        z += (o.z - z) * t;
        return this;
    }

    public float distanceTo(Vec3 o) {
        float dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
