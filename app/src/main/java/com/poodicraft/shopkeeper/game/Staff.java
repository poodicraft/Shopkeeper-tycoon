package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;

import java.util.Random;

/**
 * Hired help.
 *
 * <p>A cashier holds the till so the queue moves while the player is out on the
 * floor; a stocker ferries crates from the stockroom to whichever shelf is emptiest.
 * Both do exactly what the player would otherwise be doing by hand.
 */
public final class Staff extends Actor {

    public enum Role { CASHIER, STOCKER }

    public enum Task { IDLE, TO_STOCKROOM, LOADING, TO_SHELF, STOCKING, RETURNING }

    public final Role role;
    public Task task = Task.IDLE;
    public float taskTimer = 0f;

    public int targetShelf = -1;
    public ProductType carrying = null;
    public int carryCount = 0;

    public float walkSpeed;
    public float homeX, homeZ, homeHeading;

    public Staff(Role role, Random rng) {
        this.role = role;
        appearance = Appearance.staffMember(rng);
        if (role == Role.STOCKER) {
            appearance.shirt = 0x3D6EA5;
            appearance.markDirty();
        }
        radius = 0.28f;
        walkSpeed = role == Role.STOCKER ? 1.75f : 1.35f;
        animator.personalPhase = rng.nextFloat() * 6.28f;
    }

    public void setHome(float x, float z, float heading) {
        homeX = x;
        homeZ = z;
        homeHeading = heading;
    }

    public boolean isCarrying() { return carrying != null && carryCount > 0; }

    public void updateAnimation(float dt, boolean walking, boolean serving, boolean stocking) {
        animator.locomotion = MathUtil.approach(animator.locomotion, walking ? 1f : 0f, dt * 6f);
        animator.carry = MathUtil.approach(animator.carry, isCarrying() ? 1f : 0f, dt * 4f);
        animator.scan = MathUtil.approach(animator.scan, serving ? 1f : 0f, dt * 5f);
        animator.reach = MathUtil.approach(animator.reach, stocking ? 1f : 0f, dt * 5f);
        animator.crouch = MathUtil.approach(animator.crouch,
                stocking && animator.reachHeight < 0.75f ? 0.85f : 0f, dt * 4f);
    }
}
