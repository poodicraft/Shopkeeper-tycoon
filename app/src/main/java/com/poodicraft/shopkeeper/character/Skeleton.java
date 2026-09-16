package com.poodicraft.shopkeeper.character;

import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.Quat;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * A 19-bone humanoid rig.
 *
 * <p>The bind pose is a relaxed stand with the arms at the sides, measured in metres
 * so the character sits correctly against shop fittings: the origin is between the
 * feet and the head tops out near 1.78 m.
 *
 * <p>Poses are stored as local rotations per bone. {@link #computePose} walks the
 * hierarchy once per character per frame and produces the skinning matrices the
 * vertex shader consumes.
 */
public final class Skeleton {

    public static final int HIPS = 0;
    public static final int SPINE = 1;
    public static final int CHEST = 2;
    public static final int NECK = 3;
    public static final int HEAD = 4;
    public static final int SHOULDER_L = 5;
    public static final int UPPERARM_L = 6;
    public static final int FOREARM_L = 7;
    public static final int HAND_L = 8;
    public static final int SHOULDER_R = 9;
    public static final int UPPERARM_R = 10;
    public static final int FOREARM_R = 11;
    public static final int HAND_R = 12;
    public static final int THIGH_L = 13;
    public static final int SHIN_L = 14;
    public static final int FOOT_L = 15;
    public static final int THIGH_R = 16;
    public static final int SHIN_R = 17;
    public static final int FOOT_R = 18;

    public static final int BONE_COUNT = 19;

    public static final int[] PARENT = {
            -1,        // HIPS
            HIPS,      // SPINE
            SPINE,     // CHEST
            CHEST,     // NECK
            NECK,      // HEAD
            CHEST,     // SHOULDER_L
            SHOULDER_L, UPPERARM_L, FOREARM_L,
            CHEST,     // SHOULDER_R
            SHOULDER_R, UPPERARM_R, FOREARM_R,
            HIPS,      // THIGH_L
            THIGH_L, SHIN_L,
            HIPS,      // THIGH_R
            THIGH_R, SHIN_R,
    };

    /** Bind-pose head position of each bone, in model space. */
    public static final float[][] BIND = {
            {0.000f, 0.980f, 0.000f},   // HIPS
            {0.000f, 1.120f, 0.005f},   // SPINE
            {0.000f, 1.290f, 0.000f},   // CHEST
            {0.000f, 1.500f, -0.010f},  // NECK
            {0.000f, 1.585f, 0.000f},   // HEAD
            {0.055f, 1.455f, 0.000f},   // SHOULDER_L
            {0.183f, 1.440f, 0.000f},   // UPPERARM_L
            {0.196f, 1.168f, 0.004f},   // FOREARM_L
            {0.205f, 0.925f, 0.008f},   // HAND_L
            {-0.055f, 1.455f, 0.000f},  // SHOULDER_R
            {-0.183f, 1.440f, 0.000f},  // UPPERARM_R
            {-0.196f, 1.168f, 0.004f},  // FOREARM_R
            {-0.205f, 0.925f, 0.008f},  // HAND_R
            {0.095f, 0.955f, 0.000f},   // THIGH_L
            {0.100f, 0.530f, 0.004f},   // SHIN_L
            {0.102f, 0.090f, 0.000f},   // FOOT_L
            {-0.095f, 0.955f, 0.000f},  // THIGH_R
            {-0.100f, 0.530f, 0.004f},  // SHIN_R
            {-0.102f, 0.090f, 0.000f},  // FOOT_R
    };

    /** Animated local rotation of each bone, relative to its bind orientation. */
    public final Quat[] local = new Quat[BONE_COUNT];
    /** Model-to-world matrix of each bone after posing. */
    public final Mat4[] world = new Mat4[BONE_COUNT];
    /** world * inverseBind, uploaded to the vertex shader. */
    public final Mat4[] skin = new Mat4[BONE_COUNT];

    private final Mat4 rootTransform = new Mat4();
    private final Mat4 boneLocal = new Mat4();
    private final Mat4 inverseBind = new Mat4();

    /** Extra world-space offset applied to the hips, for crouching and leaning. */
    public float hipOffsetY = 0f;

    public Skeleton() {
        for (int i = 0; i < BONE_COUNT; i++) {
            local[i] = new Quat();
            world[i] = new Mat4();
            skin[i] = new Mat4();
        }
    }

    public void resetPose() {
        for (int i = 0; i < BONE_COUNT; i++) local[i].identity();
        hipOffsetY = 0f;
    }

    public static float bindX(int bone) { return BIND[bone][0]; }

    public static float bindY(int bone) { return BIND[bone][1]; }

    public static float bindZ(int bone) { return BIND[bone][2]; }

    /** Straight-line length from a bone to its child, used when building the mesh. */
    public static float boneLength(int bone, int child) {
        float dx = BIND[child][0] - BIND[bone][0];
        float dy = BIND[child][1] - BIND[bone][1];
        float dz = BIND[child][2] - BIND[bone][2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Walks the hierarchy and fills {@link #skin}.
     *
     * <p>Bones are ordered parents-before-children, so one pass suffices.
     */
    public void computePose(float worldX, float worldY, float worldZ, float yaw, float scale) {
        rootTransform.setRotateY(yaw);
        float[] r = rootTransform.m;
        for (int c = 0; c < 3; c++) {
            r[c * 4] *= scale;
            r[c * 4 + 1] *= scale;
            r[c * 4 + 2] *= scale;
        }
        r[12] = worldX; r[13] = worldY; r[14] = worldZ;

        for (int b = 0; b < BONE_COUNT; b++) {
            int parent = PARENT[b];
            float ox, oy, oz;
            if (parent < 0) {
                ox = BIND[b][0];
                oy = BIND[b][1] + hipOffsetY;
                oz = BIND[b][2];
            } else {
                ox = BIND[b][0] - BIND[parent][0];
                oy = BIND[b][1] - BIND[parent][1];
                oz = BIND[b][2] - BIND[parent][2];
            }
            boneLocal.setTRS(ox, oy, oz, local[b], 1f);
            if (parent < 0) {
                Mat4.multiply(rootTransform, boneLocal, world[b]);
            } else {
                Mat4.multiply(world[parent], boneLocal, world[b]);
            }
            inverseBind.setTranslate(-BIND[b][0], -BIND[b][1], -BIND[b][2]);
            Mat4.multiply(world[b], inverseBind, skin[b]);
        }
    }

    /** Copies the skinning matrices into a flat array for glUniformMatrix4fv. */
    public void writeSkinMatrices(float[] destination) {
        for (int b = 0; b < BONE_COUNT; b++) {
            System.arraycopy(skin[b].m, 0, destination, b * 16, 16);
        }
    }

    private final Vec3 originScratch = new Vec3();

    /** World position of a bone's head after posing; used to attach carried props. */
    public Vec3 boneWorldPosition(int bone, Vec3 out) {
        originScratch.set(BIND[bone][0], BIND[bone][1], BIND[bone][2]);
        return skin[bone].transformPoint(originScratch, out);
    }
}
