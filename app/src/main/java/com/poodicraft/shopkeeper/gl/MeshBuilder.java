package com.poodicraft.shopkeeper.gl;

import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * Procedural geometry accumulator.
 *
 * <p>Every shape is emitted through the current transform, so callers can build a
 * whole prop in convenient local coordinates. All output is a flat triangle soup
 * ready for {@link Mesh}.
 */
public final class MeshBuilder {
    private float[] data;
    private int count;

    private final Mat4 transform = new Mat4();
    private final Mat4[] stack = new Mat4[16];
    private int stackDepth = 0;

    private final Mat4 scratch = new Mat4();
    private final Vec3 tmpA = new Vec3();
    private final Vec3 tmpB = new Vec3();
    private final Vec3 tmpC = new Vec3();

    /** Multiplies every emitted colour, letting a whole prop be tinted at build time. */
    private float tintR = 1f, tintG = 1f, tintB = 1f;

    public MeshBuilder() { this(4096); }

    public MeshBuilder(int initialVertices) {
        data = new float[Math.max(initialVertices, 64) * Mesh.FLOATS_PER_VERTEX];
        for (int i = 0; i < stack.length; i++) stack[i] = new Mat4();
    }

    public int vertexCount() { return count / Mesh.FLOATS_PER_VERTEX; }

    public MeshBuilder reset() {
        count = 0;
        stackDepth = 0;
        transform.identity();
        tintR = tintG = tintB = 1f;
        return this;
    }

    // ---------------------------------------------------------------- transform

    public MeshBuilder push() {
        stack[stackDepth++].set(transform);
        return this;
    }

    public MeshBuilder pop() {
        transform.set(stack[--stackDepth]);
        return this;
    }

    public MeshBuilder identity() { transform.identity(); return this; }

    public MeshBuilder translate(float x, float y, float z) {
        scratch.setTranslate(x, y, z);
        transform.multiply(scratch);
        return this;
    }

    public MeshBuilder rotateY(float radians) {
        scratch.setRotateY(radians);
        transform.multiply(scratch);
        return this;
    }

    public MeshBuilder rotateX(float radians) {
        scratch.setRotateX(radians);
        transform.multiply(scratch);
        return this;
    }

    public MeshBuilder rotateZ(float radians) {
        scratch.setRotateZ(radians);
        transform.multiply(scratch);
        return this;
    }

    public MeshBuilder scale(float x, float y, float z) {
        scratch.setScale(x, y, z);
        transform.multiply(scratch);
        return this;
    }

    public MeshBuilder tint(float r, float g, float b) {
        tintR = r; tintG = g; tintB = b;
        return this;
    }

    public MeshBuilder clearTint() { return tint(1f, 1f, 1f); }

    // ------------------------------------------------------------------- shapes

    /** Axis-aligned box centred on the local origin. */
    public MeshBuilder box(float sx, float sy, float sz, int color) {
        return boxAt(0f, 0f, 0f, sx, sy, sz, color);
    }

    /** Box centred at (cx, cy, cz) in local space with full extents (sx, sy, sz). */
    public MeshBuilder boxAt(float cx, float cy, float cz, float sx, float sy, float sz, int color) {
        float hx = sx * 0.5f, hy = sy * 0.5f, hz = sz * 0.5f;
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        // Faces are shaded slightly differently so flat-lit geometry still reads as solid.
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0f, 1f, 0f, color, 1.00f);   // top
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f, color, 0.55f);  // bottom
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f, color, 0.88f);   // front
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f, color, 0.70f);  // back
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f, color, 0.80f);   // right
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f, color, 0.78f);  // left
        return this;
    }

    /** Box spanning a min/max corner pair, which is how most props are measured. */
    public MeshBuilder boxSpan(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        return boxAt((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f,
                Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), color);
    }

    /** Flat horizontal quad at height y, used for floors and rugs. */
    public MeshBuilder floorQuad(float x0, float z0, float x1, float z1, float y, int color) {
        quad(x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, 0f, 1f, 0f, color, 1f);
        return this;
    }

    /** Y-axis cylinder standing on the local origin. */
    public MeshBuilder cylinder(float radius, float height, int segments, int color) {
        return cone(radius, radius, height, segments, color);
    }

    /** Truncated cone standing on the local origin; equal radii give a cylinder. */
    public MeshBuilder cone(float bottomRadius, float topRadius, float height, int segments, int color) {
        double step = Math.PI * 2 / segments;
        for (int i = 0; i < segments; i++) {
            double a0 = i * step, a1 = (i + 1) * step;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

            float bx0 = c0 * bottomRadius, bz0 = s0 * bottomRadius;
            float bx1 = c1 * bottomRadius, bz1 = s1 * bottomRadius;
            float tx0 = c0 * topRadius, tz0 = s0 * topRadius;
            float tx1 = c1 * topRadius, tz1 = s1 * topRadius;

            // Side: normal leans outward, averaged across the slope. Wound from the
            // far edge back so the outward face is the front face under GL_CCW.
            float nmx = (c0 + c1) * 0.5f, nmz = (s0 + s1) * 0.5f;
            quad(bx1, 0f, bz1, bx0, 0f, bz0, tx0, height, tz0, tx1, height, tz1, nmx, 0.25f, nmz, color, 0.86f);

            if (topRadius > 1e-4f) {
                tri(0f, height, 0f, tx1, height, tz1, tx0, height, tz0, 0f, 1f, 0f, color, 1.0f);
            }
            if (bottomRadius > 1e-4f) {
                tri(0f, 0f, 0f, bx0, 0f, bz0, bx1, 0f, bz1, 0f, -1f, 0f, color, 0.5f);
            }
        }
        return this;
    }

    /** Low-poly UV sphere centred on the local origin. */
    public MeshBuilder sphere(float radius, int segments, int rings, int color) {
        for (int r = 0; r < rings; r++) {
            double p0 = Math.PI * r / rings;
            double p1 = Math.PI * (r + 1) / rings;
            float y0 = (float) Math.cos(p0) * radius, r0 = (float) Math.sin(p0) * radius;
            float y1 = (float) Math.cos(p1) * radius, r1 = (float) Math.sin(p1) * radius;
            for (int s = 0; s < segments; s++) {
                double a0 = Math.PI * 2 * s / segments;
                double a1 = Math.PI * 2 * (s + 1) / segments;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                float ax = c0 * r0, az = s0 * r0;
                float bx = c1 * r0, bz = s1 * r0;
                float cx = c1 * r1, cz = s1 * r1;
                float dx = c0 * r1, dz = s0 * r1;

                float nx = (ax + bx + cx + dx) * 0.25f;
                float ny = (y0 + y1) * 0.5f;
                float nz = (az + bz + cz + dz) * 0.25f;
                quad(ax, y0, az, bx, y0, bz, cx, y1, cz, dx, y1, dz, nx, ny, nz, color, 0.92f);
            }
        }
        return this;
    }

    /** Horizontal disc facing up; the basis for contact shadows. */
    public MeshBuilder disc(float radius, int segments, int color) {
        double step = Math.PI * 2 / segments;
        for (int i = 0; i < segments; i++) {
            double a0 = i * step, a1 = (i + 1) * step;
            tri(0f, 0f, 0f,
                    (float) Math.cos(a1) * radius, 0f, (float) Math.sin(a1) * radius,
                    (float) Math.cos(a0) * radius, 0f, (float) Math.sin(a0) * radius,
                    0f, 1f, 0f, color, 1f);
        }
        return this;
    }

    /** Right-angled wedge: a box whose +Z face slopes down to the -Z bottom edge. */
    public MeshBuilder wedge(float sx, float sy, float sz, int color) {
        float hx = sx * 0.5f, hz = sz * 0.5f;
        tri(-hx, 0f, -hz, hx, 0f, -hz, hx, 0f, hz, 0f, -1f, 0f, color, 0.5f);
        tri(-hx, 0f, -hz, hx, 0f, hz, -hx, 0f, hz, 0f, -1f, 0f, color, 0.5f);
        quad(-hx, 0f, hz, hx, 0f, hz, hx, sy, hz, -hx, sy, hz, 0f, 0f, 1f, color, 0.88f);
        // The ramp face rises toward +Z, so its outward normal tilts up and toward -Z.
        quad(-hx, 0f, -hz, -hx, sy, hz, hx, sy, hz, hx, 0f, -hz, 0f, hz, -sz * 0.5f, color, 1.0f);
        tri(hx, 0f, -hz, hx, sy, hz, hx, 0f, hz, 1f, 0f, 0f, color, 0.8f);
        tri(-hx, 0f, -hz, -hx, 0f, hz, -hx, sy, hz, -1f, 0f, 0f, color, 0.78f);
        return this;
    }

    /** Thin upright panel on the XY plane, handy for signs and posters. */
    public MeshBuilder panel(float sx, float sy, int color) {
        return boxAt(0f, sy * 0.5f, 0f, sx, sy, 0.04f, color);
    }

    // ---------------------------------------------------------------- primitives

    public MeshBuilder quad(float ax, float ay, float az,
                            float bx, float by, float bz,
                            float cx, float cy, float cz,
                            float dx, float dy, float dz,
                            float nx, float ny, float nz, int color, float shade) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, nx, ny, nz, color, shade);
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, nx, ny, nz, color, shade);
        return this;
    }

    public MeshBuilder tri(float ax, float ay, float az,
                           float bx, float by, float bz,
                           float cx, float cy, float cz,
                           float nx, float ny, float nz, int color, float shade) {
        float r = ((color >> 16) & 0xFF) / 255f * shade * tintR;
        float g = ((color >> 8) & 0xFF) / 255f * shade * tintG;
        float b = (color & 0xFF) / 255f * shade * tintB;

        tmpA.set(nx, ny, nz).normalize();
        // Normals are direction vectors, so translation must not apply.
        float tnx = transform.m[0] * tmpA.x + transform.m[4] * tmpA.y + transform.m[8] * tmpA.z;
        float tny = transform.m[1] * tmpA.x + transform.m[5] * tmpA.y + transform.m[9] * tmpA.z;
        float tnz = transform.m[2] * tmpA.x + transform.m[6] * tmpA.y + transform.m[10] * tmpA.z;
        float nl = (float) Math.sqrt(tnx * tnx + tny * tny + tnz * tnz);
        if (nl > 1e-6f) { tnx /= nl; tny /= nl; tnz /= nl; }

        tmpB.set(ax, ay, az);
        transform.transformPoint(tmpB, tmpC);
        vertex(tmpC.x, tmpC.y, tmpC.z, tnx, tny, tnz, r, g, b);

        tmpB.set(bx, by, bz);
        transform.transformPoint(tmpB, tmpC);
        vertex(tmpC.x, tmpC.y, tmpC.z, tnx, tny, tnz, r, g, b);

        tmpB.set(cx, cy, cz);
        transform.transformPoint(tmpB, tmpC);
        vertex(tmpC.x, tmpC.y, tmpC.z, tnx, tny, tnz, r, g, b);
        return this;
    }

    private void vertex(float x, float y, float z, float nx, float ny, float nz,
                        float r, float g, float b) {
        ensure(Mesh.FLOATS_PER_VERTEX);
        data[count++] = x; data[count++] = y; data[count++] = z;
        data[count++] = nx; data[count++] = ny; data[count++] = nz;
        data[count++] = r; data[count++] = g; data[count++] = b;
    }

    private void ensure(int extra) {
        if (count + extra <= data.length) return;
        int newSize = Math.max(data.length * 2, count + extra);
        float[] grown = new float[newSize];
        System.arraycopy(data, 0, grown, 0, count);
        data = grown;
    }

    /** Raw interleaved vertex data. Exposed so geometry can be checked off-device. */
    public float[] vertexData() { return data; }

    /** Number of valid floats in {@link #vertexData()}. */
    public int vertexFloatCount() { return count; }

    /** Snapshots the accumulated geometry into an uploadable mesh. */
    public Mesh build() { return new Mesh(data, count); }

    /** Builds and clears in one step, ready for the next prop. */
    public Mesh buildAndReset() {
        Mesh mesh = build();
        reset();
        return mesh;
    }
}
