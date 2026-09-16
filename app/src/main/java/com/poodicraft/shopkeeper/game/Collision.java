package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.world.ShopLayout;

import java.util.ArrayList;

/**
 * Axis-aligned blockers for characters on the shop floor.
 *
 * <p>Movement resolves per axis, so walking into a shelf at an angle slides along
 * it rather than stopping dead - which is what makes pushing through a doorway or
 * around an aisle end feel right.
 */
public final class Collision {

    private static final class Box {
        final float minX, minZ, maxX, maxZ;

        Box(float minX, float minZ, float maxX, float maxZ) {
            this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
        }
    }

    private final ArrayList<Box> boxes = new ArrayList<Box>();

    public void clear() { boxes.clear(); }

    public void addBox(float minX, float minZ, float maxX, float maxZ) {
        boxes.add(new Box(Math.min(minX, maxX), Math.min(minZ, maxZ),
                Math.max(minX, maxX), Math.max(minZ, maxZ)));
    }

    public void addCentred(float centerX, float centerZ, float width, float depth) {
        addBox(centerX - width * 0.5f, centerZ - depth * 0.5f,
                centerX + width * 0.5f, centerZ + depth * 0.5f);
    }

    /** True when a circle of {@code radius} at (x, z) overlaps any blocker or wall. */
    public boolean blocked(float x, float z, float radius) {
        if (!withinWalls(x, z, radius)) return true;
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            float nearestX = clamp(x, box.minX, box.maxX);
            float nearestZ = clamp(z, box.minZ, box.maxZ);
            float dx = x - nearestX, dz = z - nearestZ;
            if (dx * dx + dz * dz < radius * radius) return true;
        }
        return false;
    }

    /** Walls, with the entrance left open so characters can come and go. */
    private boolean withinWalls(float x, float z, float radius) {
        if (x < -ShopLayout.HALF_WIDTH + radius) return false;
        if (x > ShopLayout.HALF_WIDTH - radius) return false;
        if (z < -ShopLayout.HALF_DEPTH + radius) return false;
        if (z > ShopLayout.HALF_DEPTH - radius) {
            // Beyond the front wall only the doorway is passable.
            boolean inDoorway = x > ShopLayout.DOOR_MIN_X + radius * 0.5f
                    && x < ShopLayout.DOOR_MAX_X - radius * 0.5f;
            return inDoorway && z < ShopLayout.HALF_DEPTH + 2.2f;
        }
        return true;
    }

    /**
     * Moves a circle by (dx, dz), sliding along whatever it hits.
     *
     * @param out receives the resolved x and z
     */
    public void move(float x, float z, float dx, float dz, float radius, float[] out) {
        float newX = x, newZ = z;
        if (dx != 0f && !blocked(x + dx, z, radius)) newX = x + dx;
        if (dz != 0f && !blocked(newX, z + dz, radius)) newZ = z + dz;
        // If the combined step is still blocked, try the other axis order before
        // giving up; a diagonal into a corner otherwise sticks.
        if (newX == x && newZ == z && (dx != 0f || dz != 0f)) {
            if (dz != 0f && !blocked(x, z + dz, radius)) newZ = z + dz;
            if (dx != 0f && !blocked(x + dx, newZ, radius)) newX = x + dx;
        }
        out[0] = newX;
        out[1] = newZ;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
