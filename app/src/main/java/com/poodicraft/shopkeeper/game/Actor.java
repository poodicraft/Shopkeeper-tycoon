package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.character.Animator;
import com.poodicraft.shopkeeper.character.Skeleton;
import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;

import java.util.ArrayList;

/** Anything on the shop floor with a body: the player, shoppers and staff. */
public abstract class Actor {

    public final Vec3 position = new Vec3();
    /** Facing, in radians; 0 looks along +Z, matching the character's bind pose. */
    public float heading = 0f;
    public float radius = 0.28f;

    public final Skeleton skeleton = new Skeleton();
    public final Animator animator = new Animator();
    public Appearance appearance = new Appearance();

    /** Flat buffer of skinning matrices, reused every frame. */
    public final float[] boneMatrices = new float[Skeleton.BONE_COUNT * 16];

    /** Metres per second last frame, used to drive the walk cycle. */
    public float speed = 0f;

    private final Vec3 previous = new Vec3();

    /** Waypoints from the navigation grid, as x,z pairs. */
    protected final ArrayList<float[]> path = new ArrayList<float[]>();
    private int waypoint = 0;
    private final float[] resolved = new float[2];

    public void setPath(ArrayList<float[]> waypoints) {
        path.clear();
        for (int i = 0; i < waypoints.size(); i++) path.add(waypoints.get(i));
        waypoint = 0;
    }

    public void clearPath() {
        path.clear();
        waypoint = 0;
    }

    public boolean hasPath() { return waypoint < path.size(); }

    /**
     * Walks one step along the current path.
     *
     * @return true on the frame the last waypoint is reached
     */
    public boolean followPath(float dt, float walkSpeed, Collision collision) {
        if (waypoint >= path.size()) return false;
        float[] target = path.get(waypoint);
        float dx = target[0] - position.x;
        float dz = target[1] - position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.10f) {
            waypoint++;
            return waypoint >= path.size();
        }
        float step = Math.min(walkSpeed * dt, distance);
        float stepX = dx / distance * step;
        float stepZ = dz / distance * step;
        if (collision != null) {
            collision.move(position.x, position.z, stepX, stepZ, radius, resolved);
            // Stuck against a fitting: drop the waypoint rather than grinding on it.
            if (Math.abs(resolved[0] - position.x) < step * 0.15f
                    && Math.abs(resolved[1] - position.z) < step * 0.15f) {
                waypoint++;
            }
            position.x = resolved[0];
            position.z = resolved[1];
        } else {
            position.x += stepX;
            position.z += stepZ;
        }
        faceDirection(dx, dz, dt, 8f);
        return false;
    }

    public void teleport(float x, float z) {
        position.set(x, 0f, z);
        previous.set(position);
        speed = 0f;
    }

    /** Turns toward a world point at a natural rate. */
    public void faceTowards(float x, float z, float dt, float rate) {
        float desired = (float) Math.atan2(x - position.x, z - position.z);
        heading = MathUtil.approachAngle(heading, desired, rate * dt);
    }

    public void faceDirection(float dirX, float dirZ, float dt, float rate) {
        if (Math.abs(dirX) < 1e-4f && Math.abs(dirZ) < 1e-4f) return;
        float desired = (float) Math.atan2(dirX, dirZ);
        heading = MathUtil.approachAngle(heading, desired, rate * dt);
    }

    /** Recomputes speed from actual displacement, then poses and skins the body. */
    public void updatePose(float dt) {
        if (dt > 1e-5f) {
            float dx = position.x - previous.x;
            float dz = position.z - previous.z;
            float travelled = (float) Math.sqrt(dx * dx + dz * dz) / dt;
            // Smoothed so a single stuttered frame does not jolt the stride.
            speed += (travelled - speed) * Math.min(1f, dt * 12f);
        }
        previous.set(position);

        animator.speed = speed;
        animator.update(dt);
        animator.pose(skeleton);
        skeleton.computePose(position.x, position.y, position.z, heading, appearance.height);
        skeleton.writeSkinMatrices(boneMatrices);
    }

    /** Eye-level point, for camera framing and speech bubbles. */
    public float headHeight() { return 1.70f * appearance.height; }
}
