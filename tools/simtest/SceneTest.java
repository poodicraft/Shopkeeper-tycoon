import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.gl.Mesh;
import com.poodicraft.shopkeeper.scene.SceneAssets;

/**
 * Builds the full scene off-device.
 *
 * <p>{@link Mesh} only touches OpenGL when it uploads or draws, so the whole asset
 * set can be constructed on a desktop JVM. This checks that nothing throws, that
 * every mesh has real geometry, and that the triangle budget stays sane for a
 * phone GPU.
 */
public final class SceneTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        SceneAssets assets = new SceneAssets();
        long start = System.nanoTime();
        assets.build();
        long micros = (System.nanoTime() - start) / 1000;
        System.out.println("  built in " + micros + " microseconds");

        int total = 0;
        total += require("environment", assets.environment);
        total += require("shelf unit", assets.shelfUnit);
        total += require("shelf ghost", assets.shelfGhost);
        total += require("head", assets.head);
        total += require("hair", assets.hair);
        total += require("cap", assets.cap);
        total += require("torso", assets.torso);
        total += require("arm", assets.arm);
        total += require("leg", assets.leg);
        total += require("shoe", assets.shoe);
        total += require("apron", assets.apron);
        total += require("basket", assets.basket);
        total += require("crate", assets.crate);
        total += require("shadow disc", assets.shadowDisc);
        total += require("selection ring", assets.selectionRing);

        check("one mesh per product silhouette",
                assets.goods != null && assets.goods.length == ProductType.Shape.values().length);
        for (int i = 0; i < assets.goods.length; i++) {
            total += require("goods: " + ProductType.Shape.values()[i], assets.goods[i]);
        }

        int environmentTriangles = assets.environment.vertexCount() / 3;
        System.out.println("  environment is " + environmentTriangles
                + " triangles in a single draw call");
        System.out.println("  all meshes total " + (total / 3) + " triangles");

        check("the static environment fits in one reasonable draw call",
                environmentTriangles > 500 && environmentTriangles < 40000);
        check("a character part is small enough to redraw per person",
                assets.torso.vertexCount() / 3 < 200);

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " scene checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " scene checks FAILED.");
            System.exit(1);
        }
    }

    private static int require(String name, Mesh mesh) {
        boolean ok = mesh != null && mesh.vertexCount() > 0 && mesh.vertexCount() % 3 == 0;
        check(name + " has whole triangles", ok);
        return mesh == null ? 0 : mesh.vertexCount();
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
