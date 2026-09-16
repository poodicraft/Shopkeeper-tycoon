import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;

/**
 * Off-device geometry check.
 *
 * <p>Back-face culling turns a mis-wound triangle into silently missing geometry,
 * so for every triangle of every primitive this compares the normal implied by the
 * vertex order against the normal stored on the vertices. It also checks that
 * normals and tangents come out unit length and perpendicular, because a bad
 * tangent basis makes normal mapping light a surface from the wrong direction.
 */
public final class GeometryTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        check("box", build(new Shape() {
            public void emit(GeometryBuilder b) { b.box(1f, 2f, 3f); }
        }));
        check("boxSpan", build(new Shape() {
            public void emit(GeometryBuilder b) { b.boxSpan(-1f, 0f, -2f, 3f, 2f, 1f); }
        }));
        check("plane", build(new Shape() {
            public void emit(GeometryBuilder b) { b.plane(4f, 6f, 5, 7, 0f); }
        }));
        check("roundedBox (chamfer)", build(new Shape() {
            public void emit(GeometryBuilder b) { b.roundedBox(1f, 0.6f, 1.4f, 0.08f, 1); }
        }));
        check("roundedBox (fillet)", build(new Shape() {
            public void emit(GeometryBuilder b) { b.roundedBox(1f, 0.6f, 1.4f, 0.14f, 3); }
        }));
        check("cylinder", build(new Shape() {
            public void emit(GeometryBuilder b) { b.cylinder(0.5f, 0.5f, 1.5f, 14, true, true); }
        }));
        check("cone", build(new Shape() {
            public void emit(GeometryBuilder b) { b.cylinder(0.6f, 0.2f, 1f, 12, true, true); }
        }));
        check("sphere", build(new Shape() {
            public void emit(GeometryBuilder b) { b.sphere(0.8f, 16, 10); }
        }));
        check("ellipsoid", build(new Shape() {
            public void emit(GeometryBuilder b) { b.ellipsoid(0.4f, 0.9f, 0.6f, 14, 9); }
        }));
        check("disc", build(new Shape() {
            public void emit(GeometryBuilder b) { b.disc(0.7f, 0.2f, 1f, 16); }
        }));
        check("lathe (bottle)", build(new Shape() {
            public void emit(GeometryBuilder b) {
                b.lathe(new float[]{0f, 0f, 0.05f, 0f, 0.05f, 0.2f, 0.02f, 0.3f, 0.02f, 0.34f},
                        14, true, true);
            }
        }));
        check("tube (limb)", build(new Shape() {
            public void emit(GeometryBuilder b) {
                b.tube(new float[]{0f, 1f, 0f, 0.05f, 0.7f, 0.02f, 0.06f, 0.4f, 0.05f, 0.06f, 0.1f, 0.05f},
                        new float[]{0.09f, 0.08f, 0.06f, 0.05f}, 10, true);
            }
        }));
        check("transformed composition", build(new Shape() {
            public void emit(GeometryBuilder b) {
                b.push();
                b.translate(3f, 1f, -2f);
                b.rotateY(0.7f);
                b.rotateX(0.3f);
                b.rotateZ(-0.4f);
                b.scale(1.4f, 0.8f, 1.1f);
                b.cylinder(0.4f, 0.4f, 1f, 10, true, true);
                b.sphere(0.3f, 10, 6);
                b.roundedBox(0.6f, 0.4f, 0.5f, 0.06f, 2);
                b.pop();
            }
        }));

        checkStackBalance();

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " geometry checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " geometry checks FAILED.");
            System.exit(1);
        }
    }

    private interface Shape {
        void emit(GeometryBuilder b);
    }

    private static MeshData build(Shape shape) {
        MeshData mesh = new MeshData(false, 2048);
        GeometryBuilder b = new GeometryBuilder();
        b.target(mesh).identity();
        shape.emit(b);
        mesh.computeTangents();
        return mesh;
    }

    private static void check(String name, MeshData mesh) {
        checks++;
        int backwards = 0, degenerate = 0, badNormals = 0, badTangents = 0;

        for (int i = 0; i < mesh.indexCount; i += 3) {
            int i0 = mesh.indices[i], i1 = mesh.indices[i + 1], i2 = mesh.indices[i + 2];
            float ax = mesh.get(i0, 0), ay = mesh.get(i0, 1), az = mesh.get(i0, 2);
            float ux = mesh.get(i1, 0) - ax, uy = mesh.get(i1, 1) - ay, uz = mesh.get(i1, 2) - az;
            float vx = mesh.get(i2, 0) - ax, vy = mesh.get(i2, 1) - ay, vz = mesh.get(i2, 2) - az;
            float gx = uy * vz - uz * vy;
            float gy = uz * vx - ux * vz;
            float gz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (length < 1e-9f) { degenerate++; continue; }

            float nx = mesh.get(i0, 3) + mesh.get(i1, 3) + mesh.get(i2, 3);
            float ny = mesh.get(i0, 4) + mesh.get(i1, 4) + mesh.get(i2, 4);
            float nz = mesh.get(i0, 5) + mesh.get(i1, 5) + mesh.get(i2, 5);
            if (gx * nx + gy * ny + gz * nz <= 0f) backwards++;
        }

        for (int v = 0; v < mesh.vertexCount(); v++) {
            float nx = mesh.get(v, 3), ny = mesh.get(v, 4), nz = mesh.get(v, 5);
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (Math.abs(nl - 1f) > 0.02f || Float.isNaN(nl)) badNormals++;

            float tx = mesh.get(v, 6), ty = mesh.get(v, 7), tz = mesh.get(v, 8);
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (Math.abs(tl - 1f) > 0.02f || Float.isNaN(tl)) { badTangents++; continue; }
            // Gram-Schmidt should leave the tangent perpendicular to the normal.
            if (Math.abs(nx * tx + ny * ty + nz * tz) > 0.02f) badTangents++;
        }

        boolean ok = backwards == 0 && degenerate == 0 && badNormals == 0
                && badTangents == 0 && mesh.triangleCount() > 0;
        if (ok) {
            System.out.println("  ok    " + name + " (" + mesh.triangleCount() + " triangles)");
        } else {
            failures++;
            System.out.println("  FAIL  " + name + " (" + mesh.triangleCount() + " triangles, "
                    + backwards + " inside out, " + degenerate + " degenerate, "
                    + badNormals + " bad normals, " + badTangents + " bad tangents)");
        }
    }

    /** A generator that pushes without popping corrupts every prop placed after it. */
    private static void checkStackBalance() {
        checks++;
        GeometryBuilder b = new GeometryBuilder();
        b.target(new MeshData(false, 64));
        boolean threw = false;
        try {
            b.pop();
        } catch (IllegalStateException expected) {
            threw = true;
        }
        if (threw && b.stackDepth() == 0) {
            System.out.println("  ok    unbalanced pop is reported, not silently corrupting");
        } else {
            failures++;
            System.out.println("  FAIL  unbalanced pop was not reported");
        }
    }
}
