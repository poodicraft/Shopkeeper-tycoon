package com.poodicraft.shopkeeper.gl;

import com.poodicraft.shopkeeper.math.Mat4;
import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;

/**
 * Orbit camera looking at a point on the shop floor.
 *
 * <p>Also owns the projection maths the rest of the game needs: screen-to-floor
 * picking for taps, and world-to-screen projection for the 2D overlay labels.
 */
public final class Camera {
    public final Vec3 target = new Vec3(0f, 0.8f, 0f);
    public float yaw = (float) (Math.PI * 0.25);
    public float pitch = (float) Math.toRadians(38);
    public float distance = 14f;

    public float fovY = 45f;
    public float near = 0.2f;
    public float far = 160f;

    private static final float MIN_PITCH = (float) Math.toRadians(8);
    private static final float MAX_PITCH = (float) Math.toRadians(78);
    public static final float MIN_DISTANCE = 4.5f;
    public static final float MAX_DISTANCE = 30f;

    public final Mat4 view = new Mat4();
    public final Mat4 projection = new Mat4();
    public final Mat4 viewProjection = new Mat4();
    private final Mat4 inverseViewProjection = new Mat4();
    private boolean inverseValid = false;

    public final Vec3 eye = new Vec3();
    private final Vec3 up = new Vec3(0f, 1f, 0f);

    private int viewportWidth = 1, viewportHeight = 1;

    /** Smoothed values so flicks and pinches ease out instead of stopping dead. */
    private float targetYaw = yaw, targetPitch = pitch, targetDistance = distance;
    private final Vec3 desiredTarget = new Vec3(target);

    public void setViewport(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
    }

    public int viewportWidth() { return viewportWidth; }

    public int viewportHeight() { return viewportHeight; }

    public void orbit(float deltaYaw, float deltaPitch) {
        targetYaw += deltaYaw;
        targetPitch = MathUtil.clamp(targetPitch + deltaPitch, MIN_PITCH, MAX_PITCH);
    }

    public void zoom(float factor) {
        targetDistance = MathUtil.clamp(targetDistance * factor, MIN_DISTANCE, MAX_DISTANCE);
    }

    /** Slides the look-at point across the floor plane, respecting the current heading. */
    public void pan(float screenDx, float screenDy, float limitX, float limitZ) {
        float scale = distance * 0.0022f;
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        // Screen-right maps to the camera's right vector projected on the floor.
        float rightX = cos, rightZ = -sin;
        float forwardX = sin, forwardZ = cos;
        desiredTarget.x -= (rightX * screenDx + forwardX * screenDy) * scale;
        desiredTarget.z -= (rightZ * screenDx + forwardZ * screenDy) * scale;
        desiredTarget.x = MathUtil.clamp(desiredTarget.x, -limitX, limitX);
        desiredTarget.z = MathUtil.clamp(desiredTarget.z, -limitZ, limitZ);
    }

    public void lookAtPoint(float x, float z) {
        desiredTarget.x = x;
        desiredTarget.z = z;
    }

    public void snapToTarget() {
        yaw = targetYaw;
        pitch = targetPitch;
        distance = targetDistance;
        target.set(desiredTarget);
    }

    public void update(float dt) {
        float k = 1f - (float) Math.pow(0.0015, dt);
        yaw += MathUtil.wrapAngle(targetYaw - yaw) * k;
        pitch += (targetPitch - pitch) * k;
        distance += (targetDistance - distance) * k;
        target.x += (desiredTarget.x - target.x) * k;
        target.y += (desiredTarget.y - target.y) * k;
        target.z += (desiredTarget.z - target.z) * k;
        recompute();
    }

    public void recompute() {
        float cp = (float) Math.cos(pitch);
        eye.set(
                target.x + (float) Math.sin(yaw) * cp * distance,
                target.y + (float) Math.sin(pitch) * distance,
                target.z + (float) Math.cos(yaw) * cp * distance);
        view.setLookAt(eye, target, up);
        projection.setPerspective(fovY, (float) viewportWidth / viewportHeight, near, far);
        Mat4.multiply(projection, view, viewProjection);
        inverseValid = false;
    }

    private boolean ensureInverse() {
        if (!inverseValid) {
            inverseValid = viewProjection.invert(inverseViewProjection);
        }
        return inverseValid;
    }

    /**
     * Casts a ray from a touch point onto the horizontal plane at {@code planeY}.
     *
     * @return false when the ray runs parallel to (or away from) the plane.
     */
    public boolean screenToFloor(float screenX, float screenY, float planeY, Vec3 out) {
        if (!ensureInverse()) return false;
        float ndcX = (2f * screenX / viewportWidth) - 1f;
        float ndcY = 1f - (2f * screenY / viewportHeight);

        unproject(ndcX, ndcY, -1f, rayNear);
        unproject(ndcX, ndcY, 1f, rayFar);
        float dy = rayFar.y - rayNear.y;
        if (Math.abs(dy) < 1e-5f) return false;
        float t = (planeY - rayNear.y) / dy;
        if (t < 0f) return false;
        out.set(
                rayNear.x + (rayFar.x - rayNear.x) * t,
                planeY,
                rayNear.z + (rayFar.z - rayNear.z) * t);
        return true;
    }

    private final Vec3 rayNear = new Vec3();
    private final Vec3 rayFar = new Vec3();

    private void unproject(float ndcX, float ndcY, float ndcZ, Vec3 out) {
        float[] m = inverseViewProjection.m;
        float x = m[0] * ndcX + m[4] * ndcY + m[8] * ndcZ + m[12];
        float y = m[1] * ndcX + m[5] * ndcY + m[9] * ndcZ + m[13];
        float z = m[2] * ndcX + m[6] * ndcY + m[10] * ndcZ + m[14];
        float w = m[3] * ndcX + m[7] * ndcY + m[11] * ndcZ + m[15];
        if (Math.abs(w) < 1e-9f) w = 1e-9f;
        out.set(x / w, y / w, z / w);
    }

    /**
     * Projects a world point to overlay pixel coordinates.
     *
     * @param out receives x and y in pixels; z carries the NDC depth so callers can
     *            discard points behind the camera.
     * @return false when the point sits behind the near plane.
     */
    public boolean worldToScreen(float wx, float wy, float wz, Vec3 out) {
        float[] m = viewProjection.m;
        float x = m[0] * wx + m[4] * wy + m[8] * wz + m[12];
        float y = m[1] * wx + m[5] * wy + m[9] * wz + m[13];
        float z = m[2] * wx + m[6] * wy + m[10] * wz + m[14];
        float w = m[3] * wx + m[7] * wy + m[11] * wz + m[15];
        if (w <= 1e-5f) return false;
        float invW = 1f / w;
        out.set(
                (x * invW * 0.5f + 0.5f) * viewportWidth,
                (0.5f - y * invW * 0.5f) * viewportHeight,
                z * invW);
        return true;
    }
}
