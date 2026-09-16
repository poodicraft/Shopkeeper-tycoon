package com.poodicraft.shopkeeper.character;

import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;

/**
 * Bone-weight assignment for the generated character mesh.
 *
 * <p>Weights are bound explicitly per body part rather than by nearest-bone search:
 * in the bind pose the hands rest beside the thighs, so a distance-based
 * auto-skin would weld fingers to legs.
 */
public final class SkinBinders {
    private SkinBinders() { }

    /** Every vertex belongs entirely to one bone. Used for rigid parts like shoes. */
    public static final class Rigid implements GeometryBuilder.SkinBinder {
        private final int bone;

        public Rigid(int bone) { this.bone = bone; }

        @Override public void bind(MeshData mesh, int index, float x, float y, float z) {
            mesh.setSkin(index, bone, bone, 1f, 0f);
        }
    }

    /**
     * Blends two bones along a line: {@code boneA} owns the start, {@code boneB} the
     * end, with a soft transition so the joint creases instead of collapsing.
     */
    public static final class Segment implements GeometryBuilder.SkinBinder {
        private final int boneA, boneB;
        private final float ax, ay, az;
        private final float dx, dy, dz;
        private final float invLengthSq;
        private final float blendStart, blendEnd;

        public Segment(int boneA, int boneB,
                       float ax, float ay, float az,
                       float bx, float by, float bz,
                       float blendStart, float blendEnd) {
            this.boneA = boneA;
            this.boneB = boneB;
            this.ax = ax; this.ay = ay; this.az = az;
            this.dx = bx - ax; this.dy = by - ay; this.dz = bz - az;
            float lengthSq = dx * dx + dy * dy + dz * dz;
            this.invLengthSq = lengthSq > 1e-8f ? 1f / lengthSq : 0f;
            this.blendStart = blendStart;
            this.blendEnd = blendEnd;
        }

        @Override public void bind(MeshData mesh, int index, float x, float y, float z) {
            float t = ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) * invLengthSq;
            float w = smoothstep(blendStart, blendEnd, t);
            mesh.setSkin(index, boneA, boneB, 1f - w, w);
        }
    }

    /**
     * Blends a chain of bones by height, for the spine.
     *
     * <p>Each vertex picks the two control points that bracket it, so a torso bends
     * smoothly from hips through spine to chest.
     */
    public static final class HeightChain implements GeometryBuilder.SkinBinder {
        private final int[] bones;
        private final float[] heights;

        public HeightChain(int[] bones, float[] heights) {
            this.bones = bones;
            this.heights = heights;
        }

        @Override public void bind(MeshData mesh, int index, float x, float y, float z) {
            if (y <= heights[0]) {
                mesh.setSkin(index, bones[0], bones[0], 1f, 0f);
                return;
            }
            int last = bones.length - 1;
            if (y >= heights[last]) {
                mesh.setSkin(index, bones[last], bones[last], 1f, 0f);
                return;
            }
            for (int i = 0; i < last; i++) {
                if (y >= heights[i] && y <= heights[i + 1]) {
                    float span = heights[i + 1] - heights[i];
                    float t = span > 1e-6f ? (y - heights[i]) / span : 0f;
                    t = t * t * (3f - 2f * t);
                    mesh.setSkin(index, bones[i], bones[i + 1], 1f - t, t);
                    return;
                }
            }
            mesh.setSkin(index, bones[last], bones[last], 1f, 0f);
        }
    }

    static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 - edge0 < 1e-8f) return x < edge0 ? 0f : 1f;
        float t = (x - edge0) / (edge1 - edge0);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
    }
}
