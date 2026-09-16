package com.poodicraft.shopkeeper.gl;

import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * Third-person follow camera on a spring arm behind the player.
 *
 * <p>The arm shortens when the room would otherwise come between the camera and the
 * character, so backing into a corner pushes the view in rather than clipping
 * through a wall.
 */
public final class Camera {

    /** Point the camera orbits, normally the player's upper chest. */
    public final Vec3 target = new Vec3(0f, 1.35f, 0f);

    public float yaw = 0f;
    public float pitch = (float) Math.toRadians(14);
    public float distance = 3.4f;

    public float fovY = 58f;
    public float near = 0.08f;
    public float far = 90f;

    public static final float MIN_DISTANCE = 1.5f;
    public static final float MAX_DISTANCE = 7.0f;
    private static final float MIN_PITCH = (float) Math.toRadians(-22);
    private static final float MAX_PITCH = (float) Math.toRadians(62);

    public final Mat4 view = new Mat4();
    public final Mat4 projection = new Mat4();
    public final Mat4 viewProjection = new Mat4();

    public final Vec3 eye = new Vec3();
    /** Unit vector the camera looks along, on the floor plane; drives movement input. */
    public final Vec3 flatForward = new Vec3(0f, 0f, 1f);
    public final Vec3 flatRight = new Vec3(1f, 0f, 0f);

    private final Vec3 up = new Vec3(0f, 1f, 0f);
    private final Vec3 desiredTarget = new Vec3(0f, 1.35f, 0f);

    private float targetYaw = 0f;
    private float targetPitch = (float) Math.toRadians(14);
    private float targetDistance = 3.4f;
    private float currentArm = 3.4f;

    private int viewportWidth = 1, viewportHeight = 1;

    /** Half-extents of the room the camera must stay inside. */
    public float roomHalfWidth = 20f, roomHalfDepth = 20f, roomHeight = 10f;

    /** Shoulder offset so the character does not sit dead centre. */
    public float shoulderOffset = 0.30f;

    public void setViewport(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
    }

    public int viewportWidth() { return viewportWidth; }

    public int viewportHeight() { return viewportHeight; }

    public float aspect() { return (float) viewportWidth / viewportHeight; }

    public void orbit(float deltaYaw, float deltaPitch) {
        targetYaw = MathUtil.wrapAngle(targetYaw + deltaYaw);
        targetPitch = MathUtil.clamp(targetPitch + deltaPitch, MIN_PITCH, MAX_PITCH);
    }

    public void zoom(float factor) {
        targetDistance = MathUtil.clamp(targetDistance * factor, MIN_DISTANCE, MAX_DISTANCE);
    }

    public void follow(float x, float y, float z) {
        desiredTarget.set(x, y, z);
    }

    public void snap() {
        yaw = targetYaw;
        pitch = targetPitch;
        distance = targetDistance;
        currentArm = targetDistance;
        target.set(desiredTarget);
        recompute();
    }

    public void update(float dt) {
        // Exponential smoothing that is stable at any frame rate.
        float follow = 1f - (float) Math.pow(0.0009, dt);
        float look = 1f - (float) Math.pow(0.00002, dt);

        yaw = MathUtil.wrapAngle(yaw + MathUtil.wrapAngle(targetYaw - yaw) * look);
        pitch += (targetPitch - pitch) * look;
        distance += (targetDistance - distance) * follow;
        target.x += (desiredTarget.x - target.x) * follow;
        target.y += (desiredTarget.y - target.y) * follow;
        target.z += (desiredTarget.z - target.z) * follow;

        float allowed = armLength(distance);
        // Snap in immediately when something intrudes; ease back out gently.
        if (allowed < currentArm) currentArm = allowed;
        else currentArm += (allowed - currentArm) * (1f - (float) Math.pow(0.05, dt));

        recompute();
    }

    /**
     * Shortest arm that keeps the camera inside the room.
     *
     * <p>Walks the ray from the target out to the desired distance and stops at the
     * first boundary crossing, which is enough for a single rectangular room and
     * costs nothing per frame.
     */
    private float armLength(float desired) {
        float cp = (float) Math.cos(pitch);
        float dirX = -(float) Math.sin(yaw) * cp;
        float dirY = (float) Math.sin(pitch);
        float dirZ = -(float) Math.cos(yaw) * cp;

        float limit = desired;
        final float margin = 0.30f;
        limit = Math.min(limit, axisLimit(target.x, dirX, roomHalfWidth - margin));
        limit = Math.min(limit, axisLimit(target.z, dirZ, roomHalfDepth - margin));
        if (dirY > 1e-4f) limit = Math.min(limit, (roomHeight - margin - target.y) / dirY);
        if (dirY < -1e-4f) limit = Math.min(limit, (0.45f - target.y) / dirY);
        return MathUtil.clamp(limit, 0.55f, MAX_DISTANCE);
    }

    private static float axisLimit(float origin, float direction, float bound) {
        if (direction > 1e-4f) return (bound - origin) / direction;
        if (direction < -1e-4f) return (-bound - origin) / direction;
        return Float.MAX_VALUE;
    }

    public void recompute() {
        float cp = (float) Math.cos(pitch);
        float forwardX = (float) Math.sin(yaw) * cp;
        float forwardY = -(float) Math.sin(pitch);
        float forwardZ = (float) Math.cos(yaw) * cp;

        flatForward.set((float) Math.sin(yaw), 0f, (float) Math.cos(yaw)).normalize();
        // Screen-right is forward x up, which is what the view matrix's first row holds.
        flatRight.set(-(float) Math.cos(yaw), 0f, (float) Math.sin(yaw));

        float offsetX = flatRight.x * shoulderOffset;
        float offsetZ = flatRight.z * shoulderOffset;

        eye.set(
                target.x - forwardX * currentArm + offsetX,
                target.y - forwardY * currentArm,
                target.z - forwardZ * currentArm + offsetZ);

        Vec3 lookAt = new Vec3(target.x + offsetX, target.y, target.z + offsetZ);
        view.setLookAt(eye, lookAt, up);
        projection.setPerspective(fovY, aspect(), near, far);
        Mat4.multiply(projection, view, viewProjection);
        extractFrustum();
    }

    /** Six frustum planes as (nx, ny, nz, d), rebuilt each time the camera moves. */
    private final float[] frustum = new float[24];

    /**
     * Extracts the view frustum from the combined matrix.
     *
     * <p>Roughly half the shop is behind the player at any moment, so testing each
     * fitting before submitting it removes a large slice of the draw and shadow work
     * for a few dozen multiplies.
     */
    private void extractFrustum() {
        float[] m = viewProjection.m;
        // Rows of the combined matrix, combined into the six clip planes.
        setPlane(0, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]);   // left
        setPlane(1, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]);   // right
        setPlane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]);   // bottom
        setPlane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]);   // top
        setPlane(4, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]);  // near
        setPlane(5, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]);  // far
    }

    private void setPlane(int index, float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length < 1e-8f) length = 1f;
        int i = index * 4;
        frustum[i] = a / length;
        frustum[i + 1] = b / length;
        frustum[i + 2] = c / length;
        frustum[i + 3] = d / length;
    }

    /** True when a bounding sphere is at least partly inside the view. */
    public boolean sphereVisible(float x, float y, float z, float radius) {
        for (int i = 0; i < 6; i++) {
            int p = i * 4;
            float distance = frustum[p] * x + frustum[p + 1] * y + frustum[p + 2] * z + frustum[p + 3];
            if (distance < -radius) return false;
        }
        return true;
    }

    /**
     * Projects a world point to pixels for the 2D overlay.
     *
     * @return false when the point is behind the camera
     */
    public boolean worldToScreen(float wx, float wy, float wz, Vec3 out) {
        float[] m = viewProjection.m;
        float x = m[0] * wx + m[4] * wy + m[8] * wz + m[12];
        float y = m[1] * wx + m[5] * wy + m[9] * wz + m[13];
        float z = m[2] * wx + m[6] * wy + m[10] * wz + m[14];
        float w = m[3] * wx + m[7] * wy + m[11] * wz + m[15];
        if (w <= 1e-5f) return false;
        float invW = 1f / w;
        out.set((x * invW * 0.5f + 0.5f) * viewportWidth,
                (0.5f - y * invW * 0.5f) * viewportHeight,
                z * invW);
        return true;
    }
}
