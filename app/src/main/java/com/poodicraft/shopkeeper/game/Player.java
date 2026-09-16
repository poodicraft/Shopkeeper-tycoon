package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.world.ShopLayout;

import java.util.Random;

/**
 * The shopkeeper you control.
 *
 * <p>Everything the shop needs doing, this character physically does: carrying
 * crates from the stockroom, kneeling to fill shelves, standing at the till to
 * scan a customer's basket.
 */
public final class Player extends Actor {

    public enum Task { FREE, STOCKING, SERVING, BUSY }

    public static final float WALK_SPEED = 2.35f;
    public static final float RUN_SPEED = 4.05f;
    /** Carrying a crate slows you down, which is the cost of a big load. */
    public static final float CARRY_PENALTY = 0.74f;

    public Task task = Task.FREE;

    /** What is in the crate on the player's shoulder, or null when empty-handed. */
    public ProductType carrying = null;
    public int carryCount = 0;

    /** Progress through the current stocking or serving action. */
    public float actionProgress = 0f;
    public float actionDuration = 1f;
    public int actionShelf = -1;

    /** Smoothed input magnitude, so the walk cycle eases in and out. */
    private float moveBlend = 0f;
    private float runBlend = 0f;
    private final float[] resolved = new float[2];

    public Player() {
        appearance = Appearance.staffMember(new Random(7));
        // The player wears the apron and reads as the owner rather than a shopper.
        appearance.shirt = 0x2F6E8E;
        appearance.apron = 0xF2EEE4;
        appearance.wearsApron = true;
        appearance.height = 1.0f;
        appearance.markDirty();
        radius = 0.30f;
        animator.personalPhase = 0.6f;
    }

    public boolean isCarrying() { return carrying != null && carryCount > 0; }

    public int carryCapacity(GameState state) { return 10 + 4 * state.upgradeLevel(Upgrade.STOCKER); }

    /**
     * Walks the player.
     *
     * @param inputX     stick left/right, already in world space
     * @param inputZ     stick forward/back, already in world space
     * @param running    true when the run button is held
     */
    public void move(float inputX, float inputZ, boolean running, float dt, Collision collision) {
        float magnitude = (float) Math.sqrt(inputX * inputX + inputZ * inputZ);
        if (magnitude > 1f) { inputX /= magnitude; inputZ /= magnitude; magnitude = 1f; }

        boolean canMove = task == Task.FREE;
        if (!canMove) magnitude = 0f;

        float targetRun = running && magnitude > 0.65f ? 1f : 0f;
        runBlend = MathUtil.approach(runBlend, targetRun, dt * 3.5f);
        moveBlend = MathUtil.approach(moveBlend, magnitude, dt * 7f);

        float speedLimit = MathUtil.lerp(WALK_SPEED, RUN_SPEED, runBlend);
        if (isCarrying()) speedLimit *= CARRY_PENALTY;

        if (magnitude > 0.02f) {
            float step = speedLimit * magnitude * dt;
            collision.move(position.x, position.z, inputX * step, inputZ * step, radius, resolved);
            position.x = resolved[0];
            position.z = resolved[1];
            faceDirection(inputX, inputZ, dt, 11f);
        }

        animator.locomotion = moveBlend;
        animator.runBlend = runBlend;
        animator.carry = MathUtil.approach(animator.carry, isCarrying() ? 1f : 0f, dt * 4f);
    }

    /** Nudges the player onto an exact working spot, for serving and stocking. */
    public void easeToward(float x, float z, float dt) {
        position.x += (x - position.x) * Math.min(1f, dt * 6f);
        position.z += (z - position.z) * Math.min(1f, dt * 6f);
    }

    public void beginStocking(int shelfIndex, float shelfHeight, float duration) {
        task = Task.STOCKING;
        actionShelf = shelfIndex;
        actionProgress = 0f;
        actionDuration = Math.max(0.2f, duration);
        animator.reachHeight = shelfHeight;
        animator.reachRight = true;
    }

    public void beginServing(float duration) {
        task = Task.SERVING;
        actionProgress = 0f;
        actionDuration = Math.max(0.2f, duration);
    }

    public void finishTask() {
        task = Task.FREE;
        actionShelf = -1;
        actionProgress = 0f;
    }

    /** Drives the reach, crouch and scan layers from whatever the player is doing. */
    public void updateTaskAnimation(float dt) {
        boolean stocking = task == Task.STOCKING;
        boolean serving = task == Task.SERVING;

        animator.reach = MathUtil.approach(animator.reach, stocking ? 1f : 0f, dt * 5f);
        animator.scan = MathUtil.approach(animator.scan, serving ? 1f : 0f, dt * 5f);
        // Low shelves need a crouch; high ones only need the arm.
        float wantCrouch = stocking && animator.reachHeight < 0.75f ? 1f : 0f;
        animator.crouch = MathUtil.approach(animator.crouch, wantCrouch, dt * 4f);

        if (stocking || serving) {
            animator.locomotion = MathUtil.approach(animator.locomotion, 0f, dt * 8f);
        }
    }

    /** True when the player is standing in the serving spot behind the counter. */
    public boolean atTill() {
        float dx = position.x - ShopLayout.SERVE_X;
        float dz = position.z - ShopLayout.SERVE_Z;
        return dx * dx + dz * dz < 1.05f * 1.05f;
    }
}
