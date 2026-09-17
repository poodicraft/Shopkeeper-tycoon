import com.poodicraft.shopkeeper.character.Animator;
import com.poodicraft.shopkeeper.character.CharacterMesh;
import com.poodicraft.shopkeeper.character.Skeleton;
import com.poodicraft.shopkeeper.gl.MeshData;
import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * Checks the rig and the animation the way the GPU will use them.
 *
 * <p>Skinning failures are silent: a wrong matrix does not throw, it produces a
 * character with a leg growing out of its shoulder. So this applies the real
 * skinning matrices to the real mesh and asserts the body stays a body - the right
 * height, joined up, feet on the floor - through every animation state.
 */
public final class CharacterTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        MeshData body = new CharacterMesh().buildBody();

        testSilhouette(body);
        testBindPose(body);
        testHierarchy();
        testPosesStayHuman(body);
        testWalkCycleKeepsFeetDown();

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " character checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " character checks FAILED.");
            System.exit(1);
        }
    }

    /**
     * Checks the things that decide whether the figure reads as a person.
     *
     * <p>These are all failures that no amount of assertion about winding, weights or
     * bounds would ever catch: a body can be watertight, correctly skinned and
     * exactly 1.78 m tall and still be a slab with its face pointing at the ceiling.
     * Each of these was a real bug, found by rendering the mesh and looking at it.
     */
    private static void testSilhouette(MeshData body) {
        section("Silhouette");

        // The shoulders have to be wider than the waist. When the ribcage is widened
        // to reach shoulder width instead of the deltoids doing it, the outline stops
        // changing between the armpit and the hip, and the eye reads that as furniture.
        // Measured across the back of the body only. Nothing else reaches past
        // z = -0.075: measure the widest point at any height instead and the answer
        // at the waist is the width of the forearms, not of the waist.
        float chest = torsoWidth(body, 1.30f, 1.36f);
        float waist = torsoWidth(body, 1.05f, 1.12f);
        check(String.format("the torso tapers from chest to waist (%.2f m vs %.2f m)",
                chest, waist), chest > waist * 1.15f);
        float shoulders = widthBetween(body, 1.28f, 1.40f);
        check(String.format("the shoulders are an adult's, not a coat hanger's (%.2f m)",
                shoulders), shoulders > 0.42f && shoulders < 0.52f);

        // The chin must be the lowest point of the head. Parameterise a head as an
        // ellipsoid and the lowest point lands under the ear instead, which tips the
        // whole face back however level the head bone is.
        float lowestY = Float.MAX_VALUE, lowestZ = 0f;
        for (int v = 0; v < body.vertexCount(); v++) {
            float y = body.get(v, 1);
            if (y < 1.50f || Math.abs(body.get(v, 0)) > 0.05f) continue;
            if (y < lowestY) { lowestY = y; lowestZ = body.get(v, 2); }
        }
        check(String.format("the chin is the lowest part of the head, and forward (z = %.3f m)",
                lowestZ), lowestZ > 0.015f);

        // Fingertips reach mid-thigh. Short arms are what make an adult read as a doll.
        float fingertips = Float.MAX_VALUE;
        for (int v = 0; v < body.vertexCount(); v++) {
            // Outboard of the widest trouser and above the shoes, only hands remain.
            if (Math.abs(body.get(v, 0)) < 0.175f || body.get(v, 1) < 0.30f) continue;
            fingertips = Math.min(fingertips, body.get(v, 1));
        }
        check(String.format("the fingertips reach mid-thigh (%.2f m)", fingertips),
                fingertips > 0.60f && fingertips < 0.72f);

        // The head is shaded by its own surface, not by the sphere underneath it. Get
        // this wrong and the nose is present in the geometry and invisible on screen.
        float steepest = 0f;
        for (int v = 0; v < body.vertexCount(); v++) {
            float x = body.get(v, 0), y = body.get(v, 1), z = body.get(v, 2);
            if (y < 1.57f || y > 1.66f || z < 0.07f || Math.abs(x) > 0.03f) continue;
            if (body.get(v, 5) < 0.2f) continue;   // front-facing surface only
            // Direction out from the skull's centre, which is what a sphere's normal
            // would be, against the normal the mesh actually carries.
            float rx = x, ry = y - 1.657f, rz = z - 0.004f;
            float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl < 1e-4f) continue;
            float dot = (rx * body.get(v, 3) + ry * body.get(v, 4) + rz * body.get(v, 5)) / rl;
            steepest = Math.max(steepest,
                    (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, dot)))));
        }
        check(String.format("the nose is shaded as a nose, not as a sphere (%.0f deg off radial)",
                steepest), steepest > 20f);
    }

    /** Widest point across the back of the torso in a height band, arms excluded. */
    private static float torsoWidth(MeshData body, float low, float high) {
        float half = 0f;
        for (int v = 0; v < body.vertexCount(); v++) {
            float y = body.get(v, 1);
            if (y < low || y > high || body.get(v, 2) > -0.075f) continue;
            half = Math.max(half, Math.abs(body.get(v, 0)));
        }
        return half * 2f;
    }

    /** Widest point of the body across a height band, ignoring nothing. */
    private static float widthBetween(MeshData body, float low, float high) {
        float half = 0f;
        for (int v = 0; v < body.vertexCount(); v++) {
            float y = body.get(v, 1);
            if (y >= low && y <= high) half = Math.max(half, Math.abs(body.get(v, 0)));
        }
        return half * 2f;
    }

    /** With no rotations, skinning must be the identity apart from the root transform. */
    private static void testBindPose(MeshData body) {
        section("Bind pose");
        Skeleton skeleton = new Skeleton();
        skeleton.resetPose();
        skeleton.computePose(0f, 0f, 0f, 0f, 1f);

        float worst = 0f;
        Vec3 point = new Vec3();
        Vec3 out = new Vec3();
        for (int v = 0; v < body.vertexCount(); v += 7) {
            point.set(body.get(v, 0), body.get(v, 1), body.get(v, 2));
            skin(body, v, skeleton, point, out);
            worst = Math.max(worst, out.distanceTo(point));
        }
        check(String.format("an unposed skeleton leaves the mesh where it was (%.5f m)", worst),
                worst < 0.0005f);

        // Moving and turning the root should move the whole body rigidly.
        skeleton.computePose(3f, 0f, -2f, (float) Math.PI * 0.5f, 1f);
        point.set(0f, 1f, 0f);
        skinSingle(Skeleton.HIPS, skeleton, point, out);
        check(String.format("the root transform places the body (%.2f, %.2f, %.2f)",
                        out.x, out.y, out.z),
                Math.abs(out.x - 3f) < 0.001f && Math.abs(out.z + 2f) < 0.001f);
    }

    /** A bone must carry its descendants and leave everything else alone. */
    private static void testHierarchy() {
        section("Hierarchy");
        Skeleton skeleton = new Skeleton();
        Vec3 before = new Vec3();
        Vec3 after = new Vec3();

        skeleton.resetPose();
        skeleton.computePose(0f, 0f, 0f, 0f, 1f);
        skeleton.boneWorldPosition(Skeleton.HAND_L, before);
        Vec3 footBefore = new Vec3();
        skeleton.boneWorldPosition(Skeleton.FOOT_R, footBefore);

        skeleton.local[Skeleton.UPPERARM_L].setEuler(-1.2f, 0f, 0f);
        skeleton.computePose(0f, 0f, 0f, 0f, 1f);
        skeleton.boneWorldPosition(Skeleton.HAND_L, after);
        Vec3 footAfter = new Vec3();
        skeleton.boneWorldPosition(Skeleton.FOOT_R, footAfter);

        check(String.format("raising the shoulder carries the hand (%.2f m)",
                        before.distanceTo(after)),
                before.distanceTo(after) > 0.30f);
        check("raising the shoulder leaves the far foot alone",
                footBefore.distanceTo(footAfter) < 0.001f);

        // Joints stay joined: a child's head must sit at its parent's bone end.
        skeleton.resetPose();
        skeleton.local[Skeleton.THIGH_R].setEuler(0.8f, 0f, 0f);
        skeleton.local[Skeleton.SHIN_R].setEuler(-0.6f, 0f, 0f);
        skeleton.computePose(0f, 0f, 0f, 0f, 1f);

        Vec3 knee = new Vec3();
        Vec3 ankle = new Vec3();
        skeleton.boneWorldPosition(Skeleton.SHIN_R, knee);
        skeleton.boneWorldPosition(Skeleton.FOOT_R, ankle);
        float shinLength = Skeleton.boneLength(Skeleton.SHIN_R, Skeleton.FOOT_R);
        check(String.format("the shin keeps its length when bent (%.3f vs %.3f)",
                        knee.distanceTo(ankle), shinLength),
                Math.abs(knee.distanceTo(ankle) - shinLength) < 0.002f);
    }

    /** Every animation state must still produce a person-shaped person. */
    private static void testPosesStayHuman(MeshData body) {
        section("Poses");
        String[] names = {"idle", "walking", "running", "carrying", "stocking low",
                "stocking high", "serving", "walking while carrying"};
        for (int i = 0; i < names.length; i++) {
            Animator animator = new Animator();
            animator.personalPhase = 1.3f;
            switch (i) {
                case 1: animator.locomotion = 1f; animator.speed = 2.3f; break;
                case 2: animator.locomotion = 1f; animator.runBlend = 1f; animator.speed = 4f; break;
                case 3: animator.carry = 1f; break;
                case 4: animator.reach = 1f; animator.reachHeight = 0.35f; animator.crouch = 1f; break;
                case 5: animator.reach = 1f; animator.reachHeight = 1.6f; break;
                case 6: animator.scan = 1f; break;
                case 7: animator.locomotion = 1f; animator.carry = 1f; animator.speed = 2f; break;
                default: break;
            }
            animator.lookYaw = 0.5f;
            animator.lookPitch = -0.2f;

            Skeleton skeleton = new Skeleton();
            boolean ok = true;
            // Sample right through the cycle, not just one frame of it.
            for (int step = 0; step < 24; step++) {
                animator.update(1f / 24f);
                animator.pose(skeleton);
                skeleton.computePose(0f, 0f, 0f, 0.4f, 1f);
                if (!inspectPose(body, skeleton, names[i], step)) { ok = false; break; }
            }
            check("pose stays human: " + names[i], ok);
        }
    }

    private static final Vec3 tmpIn = new Vec3();
    private static final Vec3 tmpOut = new Vec3();

    private static boolean inspectPose(MeshData body, Skeleton skeleton, String name, int step) {
        float minX = 9e9f, maxX = -9e9f, minY = 9e9f, maxY = -9e9f, minZ = 9e9f, maxZ = -9e9f;
        for (int v = 0; v < body.vertexCount(); v += 5) {
            tmpIn.set(body.get(v, 0), body.get(v, 1), body.get(v, 2));
            skin(body, v, skeleton, tmpIn, tmpOut);
            if (Float.isNaN(tmpOut.x) || Float.isNaN(tmpOut.y) || Float.isNaN(tmpOut.z)) {
                System.out.println("        NaN vertex in " + name + " at step " + step);
                return false;
            }
            minX = Math.min(minX, tmpOut.x); maxX = Math.max(maxX, tmpOut.x);
            minY = Math.min(minY, tmpOut.y); maxY = Math.max(maxY, tmpOut.y);
            minZ = Math.min(minZ, tmpOut.z); maxZ = Math.max(maxZ, tmpOut.z);
        }
        float height = maxY - minY;
        float width = maxX - minX;
        float depth = maxZ - minZ;
        // A crouched, reaching adult is still between a metre and two tall, and no
        // wider than arm span. Anything outside that is a rig that has come apart.
        if (height < 0.95f || height > 2.10f || width > 1.90f || depth > 1.90f) {
            System.out.printf("        %s step %d: %.2f tall, %.2f wide, %.2f deep%n",
                    name, step, height, width, depth);
            return false;
        }
        return true;
    }

    /** A walk cycle with feet drifting off the floor reads as skating or sinking. */
    private static void testWalkCycleKeepsFeetDown() {
        section("Walk cycle");
        Animator animator = new Animator();
        animator.locomotion = 1f;
        animator.speed = 2.3f;
        Skeleton skeleton = new Skeleton();

        float lowest = 9e9f, highest = -9e9f;
        Vec3 foot = new Vec3();
        for (int step = 0; step < 60; step++) {
            animator.update(1f / 60f);
            animator.pose(skeleton);
            skeleton.computePose(0f, 0f, 0f, 0f, 1f);
            for (int side = 0; side < 2; side++) {
                skeleton.boneWorldPosition(side == 0 ? Skeleton.FOOT_L : Skeleton.FOOT_R, foot);
                lowest = Math.min(lowest, foot.y);
                highest = Math.max(highest, foot.y);
            }
        }
        check(String.format("ankles never dig below the floor (%.3f m)", lowest),
                lowest > -0.02f);
        // A walking ankle clears the floor by a little; much more than this and the
        // character is marching rather than walking.
        check(String.format("ankles lift for the swing phase (%.3f m)", highest),
                highest > 0.15f && highest < 0.42f);
    }

    // ------------------------------------------------------------------ helpers

    /** Applies the two-bone blend the vertex shader does. */
    private static void skin(MeshData body, int vertex, Skeleton skeleton, Vec3 in, Vec3 out) {
        int b0 = (int) body.get(vertex, MeshData.OFFSET_BONE_INDEX);
        int b1 = (int) body.get(vertex, MeshData.OFFSET_BONE_INDEX + 1);
        float w0 = body.get(vertex, MeshData.OFFSET_BONE_WEIGHT);
        float w1 = body.get(vertex, MeshData.OFFSET_BONE_WEIGHT + 1);

        Vec3 a = new Vec3();
        Vec3 b = new Vec3();
        skeleton.skin[b0].transformPoint(in, a);
        skeleton.skin[b1].transformPoint(in, b);
        out.set(a.x * w0 + b.x * w1, a.y * w0 + b.y * w1, a.z * w0 + b.z * w1);
    }

    private static void skinSingle(int bone, Skeleton skeleton, Vec3 in, Vec3 out) {
        skeleton.skin[bone].transformPoint(in, out);
    }

    private static void section(String name) {
        System.out.println();
        System.out.println("== " + name);
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL  " + description);
        } else {
            System.out.println("  ok    " + description);
        }
    }
}
