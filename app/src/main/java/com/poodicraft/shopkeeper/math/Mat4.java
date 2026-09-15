package com.poodicraft.shopkeeper.math;

/**
 * Column-major 4x4 matrix matching the layout OpenGL ES expects, so the backing
 * array can be handed straight to glUniformMatrix4fv.
 */
public final class Mat4 {
    public final float[] m = new float[16];

    private static final float[] TMP_A = new float[16];

    public Mat4() { identity(); }

    public Mat4 identity() {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = m[5] = m[10] = m[15] = 1f;
        return this;
    }

    public Mat4 set(Mat4 o) { System.arraycopy(o.m, 0, m, 0, 16); return this; }

    public Mat4 set(float[] src) { System.arraycopy(src, 0, m, 0, 16); return this; }

    public Mat4 setPerspective(float fovYDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) * 0.5));
        identity();
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
        m[15] = 0f;
        return this;
    }

    public Mat4 setLookAt(Vec3 eye, Vec3 center, Vec3 up) {
        float fx = center.x - eye.x, fy = center.y - eye.y, fz = center.z - eye.z;
        float rl = 1f / (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx *= rl; fy *= rl; fz *= rl;

        float sx = fy * up.z - fz * up.y;
        float sy = fz * up.x - fx * up.z;
        float sz = fx * up.y - fy * up.x;
        float sl = 1f / (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx *= sl; sy *= sl; sz *= sl;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        m[0] = sx;  m[4] = sy;  m[8]  = sz;  m[12] = -(sx * eye.x + sy * eye.y + sz * eye.z);
        m[1] = ux;  m[5] = uy;  m[9]  = uz;  m[13] = -(ux * eye.x + uy * eye.y + uz * eye.z);
        m[2] = -fx; m[6] = -fy; m[10] = -fz; m[14] = (fx * eye.x + fy * eye.y + fz * eye.z);
        m[3] = 0f;  m[7] = 0f;  m[11] = 0f;  m[15] = 1f;
        return this;
    }

    public Mat4 setTranslate(float x, float y, float z) {
        identity();
        m[12] = x; m[13] = y; m[14] = z;
        return this;
    }

    public Mat4 setScale(float x, float y, float z) {
        identity();
        m[0] = x; m[5] = y; m[10] = z;
        return this;
    }

    public Mat4 setRotateY(float radians) {
        identity();
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        m[0] = c; m[2] = -s; m[8] = s; m[10] = c;
        return this;
    }

    public Mat4 setRotateX(float radians) {
        identity();
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        m[5] = c; m[6] = s; m[9] = -s; m[10] = c;
        return this;
    }

    public Mat4 setRotateZ(float radians) {
        identity();
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        m[0] = c; m[1] = s; m[4] = -s; m[5] = c;
        return this;
    }

    /** this = this * o */
    public Mat4 multiply(Mat4 o) { return multiply(this, o, this); }

    /** out = a * b. {@code out} may alias {@code a} or {@code b}. */
    public static Mat4 multiply(Mat4 a, Mat4 b, Mat4 out) {
        float[] am = a.m, bm = b.m;
        for (int c = 0; c < 4; c++) {
            int c4 = c * 4;
            float b0 = bm[c4], b1 = bm[c4 + 1], b2 = bm[c4 + 2], b3 = bm[c4 + 3];
            TMP_A[c4]     = am[0] * b0 + am[4] * b1 + am[8]  * b2 + am[12] * b3;
            TMP_A[c4 + 1] = am[1] * b0 + am[5] * b1 + am[9]  * b2 + am[13] * b3;
            TMP_A[c4 + 2] = am[2] * b0 + am[6] * b1 + am[10] * b2 + am[14] * b3;
            TMP_A[c4 + 3] = am[3] * b0 + am[7] * b1 + am[11] * b2 + am[15] * b3;
        }
        System.arraycopy(TMP_A, 0, out.m, 0, 16);
        return out;
    }

    /** Applies this matrix to a point (w = 1) and writes the result into {@code out}. */
    public Vec3 transformPoint(Vec3 p, Vec3 out) {
        float x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12];
        float y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13];
        float z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14];
        return out.set(x, y, z);
    }

    /** General inverse; returns false and leaves {@code out} untouched when singular. */
    public boolean invert(Mat4 out) {
        float[] s = m;
        float[] d = new float[16];
        d[0]  =  s[5]*s[10]*s[15] - s[5]*s[11]*s[14] - s[9]*s[6]*s[15] + s[9]*s[7]*s[14] + s[13]*s[6]*s[11] - s[13]*s[7]*s[10];
        d[4]  = -s[4]*s[10]*s[15] + s[4]*s[11]*s[14] + s[8]*s[6]*s[15] - s[8]*s[7]*s[14] - s[12]*s[6]*s[11] + s[12]*s[7]*s[10];
        d[8]  =  s[4]*s[9]*s[15]  - s[4]*s[11]*s[13] - s[8]*s[5]*s[15] + s[8]*s[7]*s[13] + s[12]*s[5]*s[11] - s[12]*s[7]*s[9];
        d[12] = -s[4]*s[9]*s[14]  + s[4]*s[10]*s[13] + s[8]*s[5]*s[14] - s[8]*s[6]*s[13] - s[12]*s[5]*s[10] + s[12]*s[6]*s[9];
        d[1]  = -s[1]*s[10]*s[15] + s[1]*s[11]*s[14] + s[9]*s[2]*s[15] - s[9]*s[3]*s[14] - s[13]*s[2]*s[11] + s[13]*s[3]*s[10];
        d[5]  =  s[0]*s[10]*s[15] - s[0]*s[11]*s[14] - s[8]*s[2]*s[15] + s[8]*s[3]*s[14] + s[12]*s[2]*s[11] - s[12]*s[3]*s[10];
        d[9]  = -s[0]*s[9]*s[15]  + s[0]*s[11]*s[13] + s[8]*s[1]*s[15] - s[8]*s[3]*s[13] - s[12]*s[1]*s[11] + s[12]*s[3]*s[9];
        d[13] =  s[0]*s[9]*s[14]  - s[0]*s[10]*s[13] - s[8]*s[1]*s[14] + s[8]*s[2]*s[13] + s[12]*s[1]*s[10] - s[12]*s[2]*s[9];
        d[2]  =  s[1]*s[6]*s[15]  - s[1]*s[7]*s[14]  - s[5]*s[2]*s[15] + s[5]*s[3]*s[14] + s[13]*s[2]*s[7]  - s[13]*s[3]*s[6];
        d[6]  = -s[0]*s[6]*s[15]  + s[0]*s[7]*s[14]  + s[4]*s[2]*s[15] - s[4]*s[3]*s[14] - s[12]*s[2]*s[7]  + s[12]*s[3]*s[6];
        d[10] =  s[0]*s[5]*s[15]  - s[0]*s[7]*s[13]  - s[4]*s[1]*s[15] + s[4]*s[3]*s[13] + s[12]*s[1]*s[7]  - s[12]*s[3]*s[5];
        d[14] = -s[0]*s[5]*s[14]  + s[0]*s[6]*s[13]  + s[4]*s[1]*s[14] - s[4]*s[2]*s[13] - s[12]*s[1]*s[6]  + s[12]*s[2]*s[5];
        d[3]  = -s[1]*s[6]*s[11]  + s[1]*s[7]*s[10]  + s[5]*s[2]*s[11] - s[5]*s[3]*s[10] - s[9]*s[2]*s[7]   + s[9]*s[3]*s[6];
        d[7]  =  s[0]*s[6]*s[11]  - s[0]*s[7]*s[10]  - s[4]*s[2]*s[11] + s[4]*s[3]*s[10] + s[8]*s[2]*s[7]   - s[8]*s[3]*s[6];
        d[11] = -s[0]*s[5]*s[11]  + s[0]*s[7]*s[9]   + s[4]*s[1]*s[11] - s[4]*s[3]*s[9]  - s[8]*s[1]*s[7]   + s[8]*s[3]*s[5];
        d[15] =  s[0]*s[5]*s[10]  - s[0]*s[6]*s[9]   - s[4]*s[1]*s[10] + s[4]*s[2]*s[9]  + s[8]*s[1]*s[6]   - s[8]*s[2]*s[5];

        float det = s[0] * d[0] + s[1] * d[4] + s[2] * d[8] + s[3] * d[12];
        if (Math.abs(det) < 1e-9f) return false;
        float inv = 1f / det;
        for (int i = 0; i < 16; i++) out.m[i] = d[i] * inv;
        return true;
    }
}
