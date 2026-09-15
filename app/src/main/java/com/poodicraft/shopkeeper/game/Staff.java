package com.poodicraft.shopkeeper.game;

import java.util.Random;

/** Hired help: cashiers hold the till, stockers ferry goods from the stockroom. */
public final class Staff extends Agent {

    public enum Role { CASHIER, STOCKER }

    public enum Task { IDLE, TO_STOCKROOM, LOADING, TO_SHELF, STOCKING, RETURNING }

    public final Role role;
    public Task task = Task.IDLE;
    public float taskTimer = 0f;

    public int targetShelf = -1;
    public ProductType carrying = null;
    public int carryCount = 0;
    /** Drives the crate-in-hands pose while a stocker is loaded up. */
    public float carryBlend = 0f;

    /** Where a cashier stands, or where a stocker returns to when idle. */
    public float homeX, homeZ, homeHeading;

    public Staff(Role role, Random rng) {
        this.role = role;
        randomizeLook(rng);
        // Staff wear a consistent uniform so they read as employees at a glance.
        shirtColor = role == Role.CASHIER ? 0x2E7D63 : 0x2F5D8A;
        trouserColor = 0x2B303B;
        accessory = role == Role.CASHIER ? 1 : 2;
        speed = role == Role.STOCKER ? 1.7f : 1.4f;
        heightScale = 0.98f + rng.nextFloat() * 0.08f;
    }

    public void setHome(float x, float z, float heading) {
        homeX = x;
        homeZ = z;
        homeHeading = heading;
    }

    public boolean isCarrying() { return carrying != null && carryCount > 0; }
}
