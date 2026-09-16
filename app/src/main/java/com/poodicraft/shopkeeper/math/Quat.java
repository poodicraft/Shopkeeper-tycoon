package com.poodicraft.shopkeeper.math;

/**
 * Unit quaternion for bone rotation.
 *
 * <p>Animation blends poses constantly, and quaternions interpolate without the
 * gimbal lock and shear that Euler angles or matrix lerps introduce at a joint.
 */
public final class Quat {
    public float x, y, z, w = 1f;

    public Quat() { }

    public Quat(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w;
    }

    public Quat identity() { x = 0f; y = 0f; z = 0f; w = 1f; return this; }

    public Quat set(Quat o) { x = o.x; y = o.y; z = o.z; w = o.w; return this; }

    public Quat set(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w; return this;
    }

    public Quat setAxisAngle(float ax, float ay, float az, float radians) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-8f) return identity();
        float half = radians * 0.5f;
        float s = (float) Math.sin(half) / len;
        x = ax * s; y = ay * s; z = az * s;
        w = (float) Math.cos(half);
        return this;
    }

    /** Rotation about X, then Y, then Z, applied in that order to a local vector. */
    public Quat setEuler(float pitch, float yaw, float roll) {
        float cx = (float) Math.cos(pitch * 0.5f), sx = (float) Math.sin(pitch * 0.5f);
        float cy = (float) Math.cos(yaw * 0.5f), sy = (float) Math.sin(yaw * 0.5f);
        float cz = (float) Math.cos(roll * 0.5f), sz = (float) Math.sin(roll * 0.5f);
        w = cx * cy * cz + sx * sy * sz;
        x = sx * cy * cz - cx * sy * sz;
        y = cx * sy * cz + sx * cy * sz;
        z = cx * cy * sz - sx * sy * cz;
        return normalize();
    }

    /** this = a * b, meaning b's rotation applied first, then a's. */
    public Quat multiply(Quat a, Quat b) {
        float nx = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y;
        float ny = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x;
        float nz = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w;
        float nw = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z;
        return set(nx, ny, nz, nw);
    }

    public Quat normalize() {
        float l = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (l > 1e-8f) { x /= l; y /= l; z /= l; w /= l; }
        else { identity(); }
        return this;
    }

    /** Spherical interpolation from a to b, taking the short way round. */
    public Quat slerp(Quat a, Quat b, float t) {
        float dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        float bx = b.x, by = b.y, bz = b.z, bw = b.w;
        if (dot < 0f) {
            dot = -dot;
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }
        if (dot > 0.9995f) {
            // Nearly parallel: lerp and renormalize, which avoids dividing by sin(0).
            x = a.x + (bx - a.x) * t;
            y = a.y + (by - a.y) * t;
            z = a.z + (bz - a.z) * t;
            w = a.w + (bw - a.w) * t;
            return normalize();
        }
        float theta = (float) Math.acos(dot);
        float sinTheta = (float) Math.sin(theta);
        float sa = (float) Math.sin((1f - t) * theta) / sinTheta;
        float sb = (float) Math.sin(t * theta) / sinTheta;
        x = a.x * sa + bx * sb;
        y = a.y * sa + by * sb;
        z = a.z * sa + bz * sb;
        w = a.w * sa + bw * sb;
        return normalize();
    }

    /** Writes the equivalent rotation into the upper 3x3 of a column-major matrix. */
    public void toMatrix(float[] m) {
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;
        m[0] = 1f - 2f * (yy + zz); m[4] = 2f * (xy - wz);       m[8]  = 2f * (xz + wy);
        m[1] = 2f * (xy + wz);      m[5] = 1f - 2f * (xx + zz);  m[9]  = 2f * (yz - wx);
        m[2] = 2f * (xz - wy);      m[6] = 2f * (yz + wx);       m[10] = 1f - 2f * (xx + yy);
        m[3] = 0f; m[7] = 0f; m[11] = 0f;
        m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f;
    }
}
