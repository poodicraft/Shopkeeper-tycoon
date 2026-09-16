package com.poodicraft.shopkeeper.gl;

import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * Procedural geometry with a transform stack.
 *
 * <p>Pure Java: no OpenGL, so the whole world can be generated on a loading thread
 * and verified in off-device tests.
 *
 * <p>Every quad is emitted through {@link #quadOriented}, which picks the winding
 * that agrees with the vertices' own normals. Back-face culling turns a mis-wound
 * face into silently missing geometry, and parametric patches (sphere octants,
 * swept tubes, lathes) are exactly where sign errors hide, so the builder decides
 * rather than each call site remembering.
 *
 * <p>UVs are measured in world units scaled by {@link #uvScale}, which keeps texel
 * density consistent across props of different sizes.
 */
public final class GeometryBuilder {

    private MeshData mesh;

    private final Mat4 transform = new Mat4();
    private final Mat4[] stack = new Mat4[24];
    private int depth = 0;
    private final Mat4 scratch = new Mat4();

    private final Vec3 tmpA = new Vec3();
    private final Vec3 tmpB = new Vec3();

    public float material = 0f;
    public float ao = 1f;
    /** Multiplies the sampled albedo; lets one baked mesh hold many painted colours. */
    public float tintR = 1f, tintG = 1f, tintB = 1f;
    public float uvScale = 1f;
    /** Shifts UVs so repeated props do not show identical texture patches. */
    public float uvOffsetU = 0f, uvOffsetV = 0f;

    private SkinBinder skinBinder;

    /** Assigns bone weights to each emitted vertex, from its post-transform position. */
    public interface SkinBinder {
        void bind(MeshData mesh, int vertexIndex, float x, float y, float z);
    }

    public GeometryBuilder() {
        for (int i = 0; i < stack.length; i++) stack[i] = new Mat4();
    }

    public GeometryBuilder target(MeshData target) {
        this.mesh = target;
        return this;
    }

    public MeshData mesh() { return mesh; }

    public GeometryBuilder skin(SkinBinder binder) {
        this.skinBinder = binder;
        return this;
    }

    // ---------------------------------------------------------------- transform

    public GeometryBuilder push() {
        if (depth >= stack.length) {
            throw new IllegalStateException("Transform stack overflow: a generator is "
                    + "pushing without popping");
        }
        stack[depth++].set(transform);
        return this;
    }

    public GeometryBuilder pop() {
        if (depth <= 0) {
            // Unbalanced push/pop otherwise surfaces as a bare array index error a
            // long way from the generator that caused it.
            throw new IllegalStateException("Transform stack underflow: pop() without a "
                    + "matching push()");
        }
        transform.set(stack[--depth]);
        return this;
    }

    /** Depth of the transform stack; zero between top-level prop calls. */
    public int stackDepth() { return depth; }

    public GeometryBuilder identity() { transform.identity(); return this; }

    public GeometryBuilder translate(float x, float y, float z) {
        scratch.setTranslate(x, y, z);
        transform.multiply(scratch);
        return this;
    }

    public GeometryBuilder rotateX(float r) {
        scratch.setRotateX(r); transform.multiply(scratch); return this;
    }

    public GeometryBuilder rotateY(float r) {
        scratch.setRotateY(r); transform.multiply(scratch); return this;
    }

    public GeometryBuilder rotateZ(float r) {
        scratch.setRotateZ(r); transform.multiply(scratch); return this;
    }

    public GeometryBuilder scale(float x, float y, float z) {
        scratch.setScale(x, y, z); transform.multiply(scratch); return this;
    }

    public GeometryBuilder mat(float materialIndex) { material = materialIndex; return this; }

    public GeometryBuilder occlusion(float value) { ao = value; return this; }

    public GeometryBuilder tint(float r, float g, float b) {
        tintR = r; tintG = g; tintB = b;
        return this;
    }

    /** Sets the tint from a packed 0xRRGGBB colour. */
    public GeometryBuilder tint(int rgb) {
        return tint(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    public GeometryBuilder noTint() { return tint(1f, 1f, 1f); }

    public GeometryBuilder uv(float scale) { uvScale = scale; return this; }

    // ----------------------------------------------------------------- vertices

    /** Emits one vertex through the current transform; returns its index. */
    public int vertex(float px, float py, float pz, float nx, float ny, float nz,
                      float u, float v) {
        tmpA.set(px, py, pz);
        transform.transformPoint(tmpA, tmpB);
        float wx = tmpB.x, wy = tmpB.y, wz = tmpB.z;

        tmpA.set(nx, ny, nz);
        transform.transformDirection(tmpA, tmpB);
        float len = tmpB.length();
        if (len > 1e-6f) { tmpB.x /= len; tmpB.y /= len; tmpB.z /= len; }
        else { tmpB.set(0f, 1f, 0f); }

        int index = mesh.addVertex(wx, wy, wz, tmpB.x, tmpB.y, tmpB.z,
                u * uvScale + uvOffsetU, v * uvScale + uvOffsetV, material, ao,
                tintR, tintG, tintB);
        if (skinBinder != null) skinBinder.bind(mesh, index, wx, wy, wz);
        return index;
    }

    /**
     * Emits a quad with whichever winding faces the same way its normals do.
     *
     * <p>Vertices must be given in ring order (a-b-c-d around the quad); the
     * direction of travel does not matter.
     */
    public void quadOriented(int a, int b, int c, int d) {
        // Each half is oriented on its own. Deciding both from the first triangle
        // fails wherever that triangle is degenerate, which is exactly what happens
        // at sphere poles and where a bevel collapses - and there the second
        // triangle is real geometry that would come out inside out.
        triangleOriented(a, b, c);
        triangleOriented(a, c, d);
    }

    /** Emits a triangle wound to match its normals; degenerate ones are dropped. */
    public void triangleOriented(int a, int b, int c) {
        float o = orientation(a, b, c);
        if (Float.isNaN(o) || o == 0f) return;
        if (o > 0f) mesh.addTriangle(a, b, c);
        else mesh.addTriangle(a, c, b);
    }

    /** Positive when the winding a-b-c agrees with the stored vertex normals. */
    private float orientation(int a, int b, int c) {
        int fa = a * mesh.floatsPerVertex, fb = b * mesh.floatsPerVertex, fc = c * mesh.floatsPerVertex;
        float[] v = mesh.vertices;
        float e1x = v[fb] - v[fa], e1y = v[fb + 1] - v[fa + 1], e1z = v[fb + 2] - v[fa + 2];
        float e2x = v[fc] - v[fa], e2y = v[fc + 1] - v[fa + 1], e2z = v[fc + 2] - v[fa + 2];
        float gx = e1y * e2z - e1z * e2y;
        float gy = e1z * e2x - e1x * e2z;
        float gz = e1x * e2y - e1y * e2x;
        float area = gx * gx + gy * gy + gz * gz;
        // Below this the triangle covers no pixels and its normal is float noise.
        if (area < 1e-16f) return 0f;
        // Average the three vertex normals so a single odd normal cannot flip a face.
        float nx = v[fa + 3] + v[fb + 3] + v[fc + 3];
        float ny = v[fa + 4] + v[fb + 4] + v[fc + 4];
        float nz = v[fa + 5] + v[fb + 5] + v[fc + 5];
        return gx * nx + gy * ny + gz * nz;
    }

    // -------------------------------------------------------------------- boxes

    /** Sharp-edged box centred on the local origin. */
    public GeometryBuilder box(float w, float h, float d) {
        return boxAt(0f, 0f, 0f, w, h, d);
    }

    public GeometryBuilder boxAt(float cx, float cy, float cz, float w, float h, float d) {
        float hx = w * 0.5f, hy = h * 0.5f, hz = d * 0.5f;
        face(cx, cy, cz, 1f, 0f, 0f, hx, hy, hz);
        face(cx, cy, cz, -1f, 0f, 0f, hx, hy, hz);
        face(cx, cy, cz, 0f, 1f, 0f, hx, hy, hz);
        face(cx, cy, cz, 0f, -1f, 0f, hx, hy, hz);
        face(cx, cy, cz, 0f, 0f, 1f, hx, hy, hz);
        face(cx, cy, cz, 0f, 0f, -1f, hx, hy, hz);
        return this;
    }

    public GeometryBuilder boxSpan(float x0, float y0, float z0, float x1, float y1, float z1) {
        return boxAt((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f,
                Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0));
    }

    private void face(float cx, float cy, float cz, float nx, float ny, float nz,
                      float hx, float hy, float hz) {
        // Pick in-plane axes u, v with u x v = n, so a ring order of
        // (-u-v, +u-v, +u+v, -u+v) is counter-clockwise seen from outside.
        float ux, uy, uz, vx, vy, vz, eu, ev, offset;
        if (nx != 0f) {
            ux = 0; uy = 1; uz = 0; vx = 0; vy = 0; vz = 1;
            eu = hy; ev = hz; offset = hx;
        } else if (ny != 0f) {
            ux = 0; uy = 0; uz = 1; vx = 1; vy = 0; vz = 0;
            eu = hz; ev = hx; offset = hy;
        } else {
            ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0;
            eu = hx; ev = hy; offset = hz;
        }
        float sign = nx + ny + nz;
        float ox = cx + nx * offset, oy = cy + ny * offset, oz = cz + nz * offset;
        // Flipping u on the back faces keeps the texture from mirroring.
        float su = sign;

        int a = vertex(ox - ux * eu * su - vx * ev, oy - uy * eu * su - vy * ev, oz - uz * eu * su - vz * ev,
                nx, ny, nz, -eu, -ev);
        int b = vertex(ox + ux * eu * su - vx * ev, oy + uy * eu * su - vy * ev, oz + uz * eu * su - vz * ev,
                nx, ny, nz, eu, -ev);
        int c = vertex(ox + ux * eu * su + vx * ev, oy + uy * eu * su + vy * ev, oz + uz * eu * su + vz * ev,
                nx, ny, nz, eu, ev);
        int d = vertex(ox - ux * eu * su + vx * ev, oy - uy * eu * su + vy * ev, oz - uz * eu * su + vz * ev,
                nx, ny, nz, -eu, ev);
        quadOriented(a, b, c, d);
    }

    /**
     * Box with rounded edges, assembled from six flat faces, twelve swept edges and
     * eight sphere-octant corners.
     *
     * <p>Almost every prop uses this rather than {@link #box}: a rounded edge catches
     * a highlight, which is most of what separates a modelled object from a
     * primitive.
     */
    public GeometryBuilder roundedBox(float w, float h, float d, float bevel, int segments) {
        float hx = w * 0.5f, hy = h * 0.5f, hz = d * 0.5f;
        float r = Math.min(bevel, Math.min(hx, Math.min(hy, hz)) * 0.95f);
        if (r <= 1e-4f) return box(w, h, d);
        int n = Math.max(1, segments);
        float ix = hx - r, iy = hy - r, iz = hz - r;

        // Faces, inset by the bevel on both in-plane axes.
        insetFace(1f, 0f, 0f, hx, iy, iz);
        insetFace(-1f, 0f, 0f, hx, iy, iz);
        insetFace(0f, 1f, 0f, iz, hy, ix);
        insetFace(0f, -1f, 0f, iz, hy, ix);
        insetFace(0f, 0f, 1f, ix, iy, hz);
        insetFace(0f, 0f, -1f, ix, iy, hz);

        // Edges: a quarter arc swept along the third axis.
        for (int axis = 0; axis < 3; axis++) {
            for (int sb = -1; sb <= 1; sb += 2) {
                for (int sc = -1; sc <= 1; sc += 2) {
                    sweptEdge(axis, sb, sc, ix, iy, iz, r, n);
                }
            }
        }

        // Corners.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    cornerPatch(sx * ix, sy * iy, sz * iz, sx, sy, sz, r, n);
                }
            }
        }
        return this;
    }

    private void insetFace(float nx, float ny, float nz, float ex, float ey, float ez) {
        float hx, hy, hz;
        if (nx != 0f) { hx = ex; hy = ey; hz = ez; }
        else if (ny != 0f) { hx = ez; hy = ey; hz = ex; }
        else { hx = ex; hy = ey; hz = ez; }
        face(0f, 0f, 0f, nx, ny, nz, hx, hy, hz);
    }

    /** One rounded edge: a quarter cylinder between two faces. */
    private void sweptEdge(int axis, int sb, int sc,
                           float ix, float iy, float iz, float r, int n) {
        // (A, B, C) is a right-handed cycle, so B x C = A.
        float[] A = new float[3], B = new float[3], C = new float[3];
        A[axis] = 1f;
        B[(axis + 1) % 3] = 1f;
        C[(axis + 2) % 3] = 1f;
        float[] inner = {ix, iy, iz};
        float halfA = inner[axis];
        float offB = inner[(axis + 1) % 3] * sb;
        float offC = inner[(axis + 2) % 3] * sc;

        int[] prev = new int[2];
        for (int j = 0; j <= n; j++) {
            double angle = Math.PI * 0.5 * j / n;
            float cb = (float) Math.cos(angle) * sb;
            float cc = (float) Math.sin(angle) * sc;
            float dx = B[0] * cb + C[0] * cc;
            float dy = B[1] * cb + C[1] * cc;
            float dz = B[2] * cb + C[2] * cc;

            float baseX = B[0] * offB + C[0] * offC;
            float baseY = B[1] * offB + C[1] * offC;
            float baseZ = B[2] * offB + C[2] * offC;

            float arcU = (float) (angle * r);
            int v0 = vertex(baseX + dx * r - A[0] * halfA,
                    baseY + dy * r - A[1] * halfA,
                    baseZ + dz * r - A[2] * halfA, dx, dy, dz, arcU, -halfA);
            int v1 = vertex(baseX + dx * r + A[0] * halfA,
                    baseY + dy * r + A[1] * halfA,
                    baseZ + dz * r + A[2] * halfA, dx, dy, dz, arcU, halfA);
            if (j > 0) quadOriented(prev[0], v0, v1, prev[1]);
            prev[0] = v0;
            prev[1] = v1;
        }
    }

    /** One rounded corner: an octant of a sphere. */
    private void cornerPatch(float cx, float cy, float cz,
                             int sx, int sy, int sz, float r, int n) {
        int[][] grid = new int[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            double phi = Math.PI * 0.5 * i / n;
            float ny = (float) Math.cos(phi) * sy;
            float ring = (float) Math.sin(phi);
            for (int j = 0; j <= n; j++) {
                double theta = Math.PI * 0.5 * j / n;
                float nx = (float) Math.cos(theta) * ring * sx;
                float nz = (float) Math.sin(theta) * ring * sz;
                grid[i][j] = vertex(cx + nx * r, cy + ny * r, cz + nz * r,
                        nx, ny, nz, (float) (theta * r), (float) (phi * r));
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
    }

    // ---------------------------------------------------------------- revolves

    /** Flat horizontal plane, subdivided so vertex lighting has something to work with. */
    public GeometryBuilder plane(float w, float d, int subdivX, int subdivZ, float y) {
        int nx = Math.max(1, subdivX), nz = Math.max(1, subdivZ);
        int[][] grid = new int[nz + 1][nx + 1];
        for (int j = 0; j <= nz; j++) {
            float z = -d * 0.5f + d * j / nz;
            for (int i = 0; i <= nx; i++) {
                float x = -w * 0.5f + w * i / nx;
                grid[j][i] = vertex(x, y, z, 0f, 1f, 0f, x, z);
            }
        }
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                quadOriented(grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]);
            }
        }
        return this;
    }

    /** Cylinder or truncated cone standing on the local origin. */
    public GeometryBuilder cylinder(float bottomRadius, float topRadius, float height,
                                    int segments, boolean capBottom, boolean capTop) {
        int seg = Math.max(3, segments);
        int[] lower = new int[seg + 1];
        int[] upper = new int[seg + 1];
        float slope = (bottomRadius - topRadius);
        float slopeLen = (float) Math.sqrt(slope * slope + height * height);
        float ny = slopeLen > 1e-6f ? slope / slopeLen : 0f;
        float nr = slopeLen > 1e-6f ? height / slopeLen : 1f;

        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float circumference = (float) (a * (bottomRadius + topRadius) * 0.5);
            lower[i] = vertex(c * bottomRadius, 0f, s * bottomRadius,
                    c * nr, ny, s * nr, circumference, 0f);
            upper[i] = vertex(c * topRadius, height, s * topRadius,
                    c * nr, ny, s * nr, circumference, height);
        }
        for (int i = 0; i < seg; i++) {
            quadOriented(lower[i], lower[i + 1], upper[i + 1], upper[i]);
        }
        if (capTop && topRadius > 1e-5f) disc(topRadius, height, 1f, seg);
        if (capBottom && bottomRadius > 1e-5f) disc(bottomRadius, 0f, -1f, seg);
        return this;
    }

    /** Horizontal disc facing up or down, used for caps and contact shadows. */
    public GeometryBuilder disc(float radius, float y, float facing, int segments) {
        int seg = Math.max(3, segments);
        int centre = vertex(0f, y, 0f, 0f, facing, 0f, 0f, 0f);
        int first = -1, prev = -1;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            int v = vertex(c * radius, y, s * radius, 0f, facing, 0f, c * radius, s * radius);
            if (prev >= 0) triangleOriented(centre, prev, v);
            if (first < 0) first = v;
            prev = v;
        }
        return this;
    }

    public GeometryBuilder sphere(float radius, int segments, int rings) {
        return ellipsoid(radius, radius, radius, segments, rings);
    }

    public GeometryBuilder ellipsoid(float rx, float ry, float rz, int segments, int rings) {
        int seg = Math.max(3, segments), rng = Math.max(2, rings);
        int[][] grid = new int[rng + 1][seg + 1];
        for (int i = 0; i <= rng; i++) {
            double phi = Math.PI * i / rng;
            float cy = (float) Math.cos(phi), sy = (float) Math.sin(phi);
            for (int j = 0; j <= seg; j++) {
                double theta = Math.PI * 2 * j / seg;
                float nx = (float) Math.cos(theta) * sy;
                float nz = (float) Math.sin(theta) * sy;
                float px = nx * rx, py = cy * ry, pz = nz * rz;
                // Ellipsoid normals need the inverse-square scaling, not the position.
                float nlx = nx / rx, nly = cy / ry, nlz = nz / rz;
                float len = (float) Math.sqrt(nlx * nlx + nly * nly + nlz * nlz);
                if (len < 1e-6f) len = 1f;
                grid[i][j] = vertex(px, py, pz, nlx / len, nly / len, nlz / len,
                        (float) (theta * (rx + rz) * 0.5), (float) (phi * ry));
            }
        }
        for (int i = 0; i < rng; i++) {
            for (int j = 0; j < seg; j++) {
                quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
        return this;
    }

    /**
     * Surface of revolution from a profile of (radius, height) pairs.
     *
     * <p>Bottles, jars, lamp shades, plant pots and table legs are all one call with
     * a different profile, which is what keeps the prop library varied.
     */
    public GeometryBuilder lathe(float[] profile, int segments, boolean capBottom, boolean capTop) {
        int points = profile.length / 2;
        if (points < 2) return this;
        int seg = Math.max(3, segments);

        // Profile-space normals, from the slope between neighbouring points.
        float[] pnx = new float[points], pny = new float[points];
        for (int p = 0; p < points; p++) {
            int a = Math.max(0, p - 1), b = Math.min(points - 1, p + 1);
            float dr = profile[b * 2] - profile[a * 2];
            float dy = profile[b * 2 + 1] - profile[a * 2 + 1];
            float len = (float) Math.sqrt(dr * dr + dy * dy);
            if (len < 1e-6f) { pnx[p] = 1f; pny[p] = 0f; }
            else { pnx[p] = dy / len; pny[p] = -dr / len; }
        }

        int[][] grid = new int[points][seg + 1];
        for (int p = 0; p < points; p++) {
            float radius = profile[p * 2], y = profile[p * 2 + 1];
            for (int j = 0; j <= seg; j++) {
                double theta = Math.PI * 2 * j / seg;
                float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
                grid[p][j] = vertex(c * radius, y, s * radius,
                        c * pnx[p], pny[p], s * pnx[p],
                        (float) (theta * Math.max(radius, 0.02f)), y);
            }
        }
        for (int p = 0; p < points - 1; p++) {
            for (int j = 0; j < seg; j++) {
                quadOriented(grid[p][j], grid[p][j + 1], grid[p + 1][j + 1], grid[p + 1][j]);
            }
        }
        if (capBottom && profile[0] > 1e-5f) disc(profile[0], profile[1], -1f, seg);
        if (capTop && profile[(points - 1) * 2] > 1e-5f) {
            disc(profile[(points - 1) * 2], profile[(points - 1) * 2 + 1], 1f, seg);
        }
        return this;
    }

    /**
     * Sweeps a circular cross-section along a path, growing the limbs of a character.
     *
     * <p>Frames are carried along the path rather than rebuilt per segment, so the
     * cross-section does not spin as the path bends.
     */
    public GeometryBuilder tube(float[] path, float[] radii, int segments, boolean capEnds) {
        int points = path.length / 3;
        if (points < 2 || radii.length < points) return this;
        int seg = Math.max(3, segments);

        float upX = 0f, upY = 1f, upZ = 0f;
        int[][] grid = new int[points][seg + 1];
        float[] prevNormal = null;

        for (int p = 0; p < points; p++) {
            int a = Math.max(0, p - 1), b = Math.min(points - 1, p + 1);
            float tx = path[b * 3] - path[a * 3];
            float ty = path[b * 3 + 1] - path[a * 3 + 1];
            float tz = path[b * 3 + 2] - path[a * 3 + 2];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-6f) { tx = 0f; ty = 1f; tz = 0f; tl = 1f; }
            tx /= tl; ty /= tl; tz /= tl;

            float nx, ny, nz;
            if (prevNormal == null) {
                // Seed the frame from whichever world axis is least parallel.
                if (Math.abs(ty) > 0.9f) { nx = 1f; ny = 0f; nz = 0f; }
                else { nx = upX; ny = upY; nz = upZ; }
            } else {
                nx = prevNormal[0]; ny = prevNormal[1]; nz = prevNormal[2];
            }
            float dot = nx * tx + ny * ty + nz * tz;
            nx -= tx * dot; ny -= ty * dot; nz -= tz * dot;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-5f) { nx = 1f; ny = 0f; nz = 0f; nl = 1f; }
            nx /= nl; ny /= nl; nz /= nl;
            prevNormal = new float[]{nx, ny, nz};

            float bx = ty * nz - tz * ny;
            float by = tz * nx - tx * nz;
            float bz = tx * ny - ty * nx;

            float cx = path[p * 3], cy = path[p * 3 + 1], cz = path[p * 3 + 2];
            float radius = radii[p];
            for (int j = 0; j <= seg; j++) {
                double theta = Math.PI * 2 * j / seg;
                float ct = (float) Math.cos(theta), st = (float) Math.sin(theta);
                float dirX = nx * ct + bx * st;
                float dirY = ny * ct + by * st;
                float dirZ = nz * ct + bz * st;
                grid[p][j] = vertex(cx + dirX * radius, cy + dirY * radius, cz + dirZ * radius,
                        dirX, dirY, dirZ, (float) (theta * radius), p * 0.25f);
            }
        }
        for (int p = 0; p < points - 1; p++) {
            for (int j = 0; j < seg; j++) {
                quadOriented(grid[p][j], grid[p][j + 1], grid[p + 1][j + 1], grid[p + 1][j]);
            }
        }
        if (capEnds) {
            int last = points - 1;
            capEnd(path, radii, 0, -1, seg);
            capEnd(path, radii, last, 1, seg);
        }
        return this;
    }

    /**
     * Closes one end of a tube with its own fan.
     *
     * <p>The cap gets fresh vertices carrying the cap's axial normal. Reusing the
     * side ring would give the cap the tube's radial normals, which both shades it
     * as though it were still curved and leaves the winding test with nothing
     * meaningful to compare against.
     */
    private void capEnd(float[] path, float[] radii, int point, int direction, int seg) {
        int points = path.length / 3;
        int other = direction < 0 ? Math.min(points - 1, point + 1) : Math.max(0, point - 1);
        float ax = path[point * 3] - path[other * 3];
        float ay = path[point * 3 + 1] - path[other * 3 + 1];
        float az = path[point * 3 + 2] - path[other * 3 + 2];
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6f) { ax = 0f; ay = direction; az = 0f; }
        else { ax /= len; ay /= len; az /= len; }

        float cx = path[point * 3], cy = path[point * 3 + 1], cz = path[point * 3 + 2];
        float radius = radii[point];

        // Basis perpendicular to the axis, matching the side ring's start angle.
        float ux, uy, uz;
        if (Math.abs(ay) > 0.9f) { ux = 1f; uy = 0f; uz = 0f; }
        else { ux = 0f; uy = 1f; uz = 0f; }
        float dot = ux * ax + uy * ay + uz * az;
        ux -= ax * dot; uy -= ay * dot; uz -= az * dot;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-5f) { ux = 1f; uy = 0f; uz = 0f; ul = 1f; }
        ux /= ul; uy /= ul; uz /= ul;
        float bx = ay * uz - az * uy;
        float by = az * ux - ax * uz;
        float bz = ax * uy - ay * ux;

        int centre = vertex(cx, cy, cz, ax, ay, az, 0f, 0f);
        int first = -1, prev = -1;
        for (int j = 0; j <= seg; j++) {
            double theta = Math.PI * 2 * j / seg;
            float ct = (float) Math.cos(theta), st = (float) Math.sin(theta);
            float dx = ux * ct + bx * st;
            float dy = uy * ct + by * st;
            float dz = uz * ct + bz * st;
            int v = vertex(cx + dx * radius, cy + dy * radius, cz + dz * radius,
                    ax, ay, az, ct * radius, st * radius);
            if (prev >= 0) triangleOriented(centre, prev, v);
            if (first < 0) first = v;
            prev = v;
        }
    }

    /** Thin double-sided card, for leaves, labels and paper. */
    public GeometryBuilder card(float w, float h, float thickness) {
        return roundedBox(w, h, Math.max(0.004f, thickness), Math.max(0.002f, thickness * 0.4f), 1);
    }
}
