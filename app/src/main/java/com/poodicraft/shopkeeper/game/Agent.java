package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;

import java.util.ArrayList;
import java.util.Random;

/** Shared movement and animation for anything that walks around the shop. */
public abstract class Agent {
    public final Vec3 pos = new Vec3();
    public float heading;
    public float speed = 1.45f;

    protected final ArrayList<float[]> path = new ArrayList<float[]>();
    private int waypoint = 0;

    /** Advances while walking; drives the leg swing and body bob in the renderer. */
    public float walkPhase = 0f;
    /** 0 when standing, 1 at full stride; smoothed so stops and starts do not pop. */
    public float walkBlend = 0f;
    public boolean arrived = true;

    // Look
    public int skinColor = 0xE8B48C;
    public int shirtColor = 0x4A7FBF;
    public int trouserColor = 0x37414F;
    public int hairColor = 0x2B2119;
    public float heightScale = 1f;
    public int accessory = 0;

    public void randomizeLook(Random rng) {
        final int[] skins = {0xF2CDA7, 0xE8B48C, 0xD19A6B, 0xA9713F, 0x7B4A25, 0x5A3418};
        final int[] shirts = {0x4A7FBF, 0xD9584C, 0x4FAF73, 0xE0A63C, 0x8A63C7, 0x2FA6A6,
                0xE07BA8, 0x5C6BC0, 0x9E5B3A, 0x3E8E7E};
        final int[] trousers = {0x37414F, 0x2A2F3A, 0x4A4034, 0x5A5A66, 0x243447};
        final int[] hairs = {0x2B2119, 0x4A3222, 0x6E4B2A, 0xC49A55, 0x8A8A92, 0x1B1B20, 0xA33B2A};
        skinColor = skins[rng.nextInt(skins.length)];
        shirtColor = shirts[rng.nextInt(shirts.length)];
        trouserColor = trousers[rng.nextInt(trousers.length)];
        hairColor = hairs[rng.nextInt(hairs.length)];
        heightScale = 0.9f + rng.nextFloat() * 0.22f;
        accessory = rng.nextInt(5);
    }

    public void teleport(float x, float z) {
        pos.set(x, 0f, z);
        path.clear();
        waypoint = 0;
        arrived = true;
    }

    public void setPath(ArrayList<float[]> waypoints) {
        path.clear();
        for (int i = 0; i < waypoints.size(); i++) path.add(waypoints.get(i));
        waypoint = 0;
        arrived = path.isEmpty();
    }

    public void stop() {
        path.clear();
        waypoint = 0;
        arrived = true;
    }

    public boolean hasPath() { return waypoint < path.size(); }

    /**
     * Walks along the current path.
     *
     * @return true on the frame the agent reaches the last waypoint.
     */
    public boolean advance(float dt) {
        boolean justArrived = false;
        if (waypoint < path.size()) {
            float[] target = path.get(waypoint);
            float dx = target[0] - pos.x, dz = target[1] - pos.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.06f) {
                waypoint++;
                if (waypoint >= path.size()) {
                    arrived = true;
                    justArrived = true;
                }
            } else {
                float step = Math.min(speed * dt, dist);
                pos.x += dx / dist * step;
                pos.z += dz / dist * step;
                float desired = (float) Math.atan2(dx, dz);
                heading = MathUtil.approachAngle(heading, desired, 9f * dt);
                walkPhase += step * 4.4f;
            }
        }
        boolean walking = waypoint < path.size();
        walkBlend = MathUtil.approach(walkBlend, walking ? 1f : 0f, dt * 7f);
        if (!walking && walkBlend < 0.02f) {
            // Settle the stride so idle characters stand with their feet together.
            walkPhase = MathUtil.approach(walkPhase % ((float) Math.PI * 2), 0f, dt * 6f);
        }
        return justArrived;
    }

    /** Turns to face a world point without moving. */
    public void faceTowards(float x, float z, float dt) {
        float desired = (float) Math.atan2(x - pos.x, z - pos.z);
        heading = MathUtil.approachAngle(heading, desired, 7f * dt);
    }
}
