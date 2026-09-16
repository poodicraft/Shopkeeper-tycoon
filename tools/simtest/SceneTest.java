import com.poodicraft.shopkeeper.character.CharacterMesh;
import com.poodicraft.shopkeeper.character.Skeleton;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.gl.MeshData;
import com.poodicraft.shopkeeper.world.GoodsBuilder;
import com.poodicraft.shopkeeper.world.WorldBuilder;

/**
 * Builds the whole 3D scene off-device.
 *
 * <p>Checks that nothing throws, that every mesh is well formed and wound the right
 * way, that the skinned character's bone bindings are valid, and that the triangle
 * budget stays inside what a phone GPU will hold up under.
 */
public final class SceneTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        long start = System.nanoTime();
        WorldBuilder world = new WorldBuilder();
        MeshData room = world.buildRoom();
        MeshData shelf = world.buildShelfUnit();
        MeshData crate = world.buildCarryCrate();

        CharacterMesh characters = new CharacterMesh();
        MeshData body = characters.buildBody(true);
        MeshData[] hair = new MeshData[3];
        for (int i = 0; i < hair.length; i++) hair[i] = characters.buildHair(i);

        GoodsBuilder goods = new GoodsBuilder();
        int goodsTriangles = 0;
        for (int i = 0; i < ProductType.ALL.length; i++) {
            MeshData mesh = goods.build(ProductType.ALL[i], 30, 30, i);
            inspect("goods: " + ProductType.ALL[i].displayName, mesh);
            goodsTriangles = Math.max(goodsTriangles, mesh.triangleCount());
        }
        long micros = (System.nanoTime() - start) / 1000;

        inspect("room", room);
        inspect("shelf unit", shelf);
        inspect("crate", crate);
        inspect("character body", body);
        for (int i = 0; i < hair.length; i++) inspect("hair style " + i, hair[i]);

        checkSkin(body);
        for (int i = 0; i < hair.length; i++) checkSkin(hair[i]);

        System.out.println();
        System.out.println("  built the whole scene in " + (micros / 1000) + " ms");
        System.out.println("  room          " + room.triangleCount() + " triangles (one draw call)");
        System.out.println("  shelf unit    " + shelf.triangleCount() + " triangles");
        System.out.println("  full shelf    " + goodsTriangles + " triangles of stock");
        System.out.println("  character     " + body.triangleCount() + " triangles + hair");

        // A busy shop: the room, twelve stocked shelves and a dozen people, drawn
        // once for the shadow map and once for the scene.
        int worst = room.triangleCount()
                + 12 * (shelf.triangleCount() + goodsTriangles)
                + 12 * (body.triangleCount() + hair[0].triangleCount());
        System.out.println("  worst case    " + worst + " triangles per pass");
        // Frustum culling removes roughly half of this in play: with a 58 degree
        // field of view, most of the shop is behind the player at any moment.
        check("worst-case scene fits a phone GPU budget", worst < 150000);
        check("the room is a single sensible draw call",
                room.triangleCount() > 5000 && room.triangleCount() < 60000);

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " scene checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " scene checks FAILED.");
            System.exit(1);
        }
    }

    private static void inspect(String name, MeshData mesh) {
        int backwards = 0, degenerate = 0, nan = 0;
        for (int i = 0; i < mesh.indexCount; i += 3) {
            int i0 = mesh.indices[i], i1 = mesh.indices[i + 1], i2 = mesh.indices[i + 2];
            float ax = mesh.get(i0, 0), ay = mesh.get(i0, 1), az = mesh.get(i0, 2);
            float ux = mesh.get(i1, 0) - ax, uy = mesh.get(i1, 1) - ay, uz = mesh.get(i1, 2) - az;
            float vx = mesh.get(i2, 0) - ax, vy = mesh.get(i2, 1) - ay, vz = mesh.get(i2, 2) - az;
            float gx = uy * vz - uz * vy, gy = uz * vx - ux * vz, gz = ux * vy - uy * vx;
            if (gx * gx + gy * gy + gz * gz < 1e-18f) { degenerate++; continue; }
            float nx = mesh.get(i0, 3) + mesh.get(i1, 3) + mesh.get(i2, 3);
            float ny = mesh.get(i0, 4) + mesh.get(i1, 4) + mesh.get(i2, 4);
            float nz = mesh.get(i0, 5) + mesh.get(i1, 5) + mesh.get(i2, 5);
            if (gx * nx + gy * ny + gz * nz <= 0f) backwards++;
        }
        for (int v = 0; v < mesh.vertexCount(); v++) {
            for (int f = 0; f < mesh.floatsPerVertex; f++) {
                if (Float.isNaN(mesh.get(v, f))) { nan++; break; }
            }
        }
        boolean ok = backwards == 0 && degenerate == 0 && nan == 0;
        check(name + " is well formed"
                + (ok ? "" : " (" + backwards + " inside out, " + degenerate
                        + " degenerate, " + nan + " NaN)"), ok);
    }

    /** Bone indices must be real bones and weights must sum to one, or limbs collapse. */
    private static void checkSkin(MeshData mesh) {
        if (!mesh.skinned) return;
        int badIndex = 0, badWeight = 0;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            float b0 = mesh.get(v, MeshData.OFFSET_BONE_INDEX);
            float b1 = mesh.get(v, MeshData.OFFSET_BONE_INDEX + 1);
            if (b0 < 0 || b0 >= Skeleton.BONE_COUNT || b1 < 0 || b1 >= Skeleton.BONE_COUNT) {
                badIndex++;
            }
            float sum = mesh.get(v, MeshData.OFFSET_BONE_WEIGHT)
                    + mesh.get(v, MeshData.OFFSET_BONE_WEIGHT + 1);
            if (Math.abs(sum - 1f) > 0.001f) badWeight++;
        }
        check("skin bindings are valid", badIndex == 0 && badWeight == 0);
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (condition) {
            System.out.println("  ok    " + description);
        } else {
            failures++;
            System.out.println("  FAIL  " + description);
        }
    }
}
