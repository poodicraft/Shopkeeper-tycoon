import com.poodicraft.shopkeeper.gl.Mesh;
import com.poodicraft.shopkeeper.gl.MeshBuilder;

/**
 * Off-device geometry check.
 *
 * <p>The renderer culls back faces, so a primitive whose triangles are wound the
 * wrong way turns inside out or disappears entirely - and nothing in a compile or a
 * simulation run would notice. For every triangle this compares the normal implied
 * by the vertex order against the normal stored on the vertices: they must agree,
 * or the face is pointing the wrong way.
 */
public final class GeometryTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        MeshBuilder b = new MeshBuilder();

        check("box", b.reset().box(1f, 2f, 3f, 0xFF8844));
        check("boxAt off-origin", b.reset().boxAt(2f, 1f, -3f, 1f, 1f, 1f, 0x44FF88));
        check("boxSpan", b.reset().boxSpan(-1f, 0f, -2f, 3f, 2f, 1f, 0x8844FF));
        check("floorQuad", b.reset().floorQuad(-2f, -3f, 4f, 5f, 0.1f, 0xFFFFFF));
        check("cylinder", b.reset().cylinder(0.5f, 1.5f, 12, 0xAABBCC));
        check("cone (tapered)", b.reset().cone(0.6f, 0.2f, 1f, 14, 0xCCBBAA));
        check("cone (spike)", b.reset().cone(0.6f, 0f, 1f, 10, 0xCCBBAA));
        check("sphere", b.reset().sphere(0.8f, 12, 8, 0x33AA77));
        check("disc", b.reset().disc(0.7f, 16, 0x222222));
        check("wedge", b.reset().wedge(1f, 0.6f, 0.8f, 0xDD4422));
        check("panel", b.reset().panel(1.2f, 0.9f, 0x224466));

        // Composition under a transform must not flip anything either.
        b.reset();
        b.push();
        b.translate(3f, 1f, -2f);
        b.rotateY(0.7f);
        b.rotateX(0.3f);
        b.scale(1.4f, 0.8f, 1.1f);
        b.cylinder(0.4f, 1f, 10, 0x998877);
        b.sphere(0.3f, 10, 6, 0x778899);
        b.wedge(0.6f, 0.4f, 0.5f, 0x887799);
        b.pop();
        check("transformed composition", b);

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " geometry checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " geometry checks FAILED.");
            System.exit(1);
        }
    }

    private static void check(String name, MeshBuilder builder) {
        checks++;
        float[] data = builder.vertexData();
        int count = builder.vertexFloatCount();
        int stride = Mesh.FLOATS_PER_VERTEX;
        int triangles = count / (stride * 3);

        int backwards = 0;
        int degenerate = 0;
        int badNormals = 0;

        for (int t = 0; t < triangles; t++) {
            int base = t * stride * 3;
            float ax = data[base],     ay = data[base + 1],     az = data[base + 2];
            float bx = data[base + stride], by = data[base + stride + 1], bz = data[base + stride + 2];
            float cx = data[base + stride * 2], cy = data[base + stride * 2 + 1],
                    cz = data[base + stride * 2 + 2];

            // Normal implied by the winding order, under OpenGL's CCW convention.
            float ux = bx - ax, uy = by - ay, uz = bz - az;
            float vx = cx - ax, vy = cy - ay, vz = cz - az;
            float wx = uy * vz - uz * vy;
            float wy = uz * vx - ux * vz;
            float wz = ux * vy - uy * vx;
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1e-7f) { degenerate++; continue; }
            wx /= wl; wy /= wl; wz /= wl;

            float nx = data[base + 3], ny = data[base + 4], nz = data[base + 5];
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (Math.abs(nl - 1f) > 0.02f) badNormals++;
            if (nl < 1e-6f) continue;
            nx /= nl; ny /= nl; nz /= nl;

            if (wx * nx + wy * ny + wz * nz <= 0.0f) backwards++;
        }

        boolean ok = backwards == 0 && badNormals == 0 && triangles > 0;
        if (ok) {
            System.out.println("  ok    " + name + " (" + triangles + " triangles, "
                    + degenerate + " degenerate)");
        } else {
            failures++;
            System.out.println("  FAIL  " + name + " (" + triangles + " triangles, "
                    + backwards + " wound inside out, " + badNormals + " bad normals)");
        }
    }
}
