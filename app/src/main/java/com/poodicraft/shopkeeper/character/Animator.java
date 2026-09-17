package com.poodicraft.shopkeeper.character;

import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Quat;

/**
 * Procedural animation for the humanoid rig.
 *
 * <p>There are no animation files. Each pose is built from curves driven by the
 * character's speed and what they are doing, then layered: locomotion underneath,
 * then carrying, crouching, reaching and scanning on top. Because the layers are
 * blend weights rather than discrete clips, a shopkeeper can walk while carrying a
 * crate and turn their head toward a customer at the same time.
 *
 * <p>Sign conventions in the bind pose, where the character faces +Z: a positive X
 * rotation swings a downward-pointing limb backwards, so a forward arm swing is
 * negative and a knee bend is positive.
 */
public final class Animator {

    /** How far through the walk cycle, in radians. */
    public float walkPhase = 0f;
    private float idlePhase = 0f;
    private float breathPhase = 0f;

    /** 0 standing still, 1 full stride. */
    public float locomotion = 0f;
    /** 0 walking, 1 running. */
    public float runBlend = 0f;
    /** Holding a crate in both hands. */
    public float carry = 0f;
    /** Crouched down to reach a low shelf. */
    public float crouch = 0f;
    /** Reaching out with one arm; see {@link #reachHeight} and {@link #reachRight}. */
    public float reach = 0f;
    /** World height the reaching hand aims for, relative to the feet. */
    public float reachHeight = 1.2f;
    public boolean reachRight = true;
    /** Working the till. */
    public float scan = 0f;
    private float scanPhase = 0f;

    /** Where the head is looking, relative to the body's facing. */
    public float lookYaw = 0f;
    public float lookPitch = 0f;

    /** Set by the owner each frame; drives stride length and cadence. */
    public float speed = 0f;

    private final float[] pitch = new float[Skeleton.BONE_COUNT];
    private final float[] yaw = new float[Skeleton.BONE_COUNT];
    private final float[] roll = new float[Skeleton.BONE_COUNT];
    private final Quat scratch = new Quat();

    /** Small per-character offset so a crowd does not breathe in unison. */
    public float personalPhase = 0f;

    public void update(float dt) {
        // Cadence rises with speed; the constant keeps a walk near two steps a second.
        float cadence = 2.1f + speed * 1.55f;
        walkPhase += dt * cadence * Math.max(0.15f, locomotion);
        if (walkPhase > Math.PI * 2) walkPhase -= (float) (Math.PI * 2);
        idlePhase += dt * 0.7f;
        breathPhase += dt * 1.15f;
        scanPhase += dt * 3.4f;
    }

    /** Writes the current pose into the skeleton's local rotations. */
    public void pose(Skeleton skeleton) {
        for (int i = 0; i < Skeleton.BONE_COUNT; i++) {
            pitch[i] = 0f; yaw[i] = 0f; roll[i] = 0f;
        }
        skeleton.hipOffsetY = 0f;

        applyIdle(skeleton);
        if (locomotion > 0.001f) applyLocomotion(skeleton);
        if (carry > 0.001f) applyCarry();
        if (crouch > 0.001f) applyCrouch(skeleton);
        if (reach > 0.001f) applyReach();
        if (scan > 0.001f) applyScan();
        levelTheHead();
        applyLook();

        for (int i = 0; i < Skeleton.BONE_COUNT; i++) {
            scratch.setEuler(pitch[i], yaw[i], roll[i]);
            skeleton.local[i].set(scratch);
        }
    }

    /**
     * Takes most of the spine's lean back out again at the neck.
     *
     * <p>People keep their eyes on the horizon: lean forward to pick something up and
     * the head stays level, it does not rotate with the chest. Let the head simply
     * inherit whatever the spine is doing and the face points at the ceiling every
     * time the body bends, which is the single most obvious thing wrong with a rig
     * that is otherwise fine.
     */
    private void levelTheHead() {
        float lean = pitch[Skeleton.SPINE] + pitch[Skeleton.CHEST];
        pitch[Skeleton.NECK] -= lean * 0.45f;
        pitch[Skeleton.HEAD] -= lean * 0.50f;
    }

    // ------------------------------------------------------------------ layers

    private void applyIdle(Skeleton skeleton) {
        float rest = 1f - MathUtil.clamp(locomotion, 0f, 1f);
        if (rest <= 0.001f) return;
        float breath = (float) Math.sin(breathPhase + personalPhase);
        float sway = (float) Math.sin(idlePhase * 0.8f + personalPhase);
        float drift = (float) Math.sin(idlePhase * 0.43f + personalPhase * 1.7f);

        // Breathing lifts the chest and rocks the shoulders very slightly.
        pitch[Skeleton.CHEST] += -breath * 0.016f * rest;
        pitch[Skeleton.SPINE] += breath * 0.010f * rest;
        roll[Skeleton.SHOULDER_L] += breath * 0.020f * rest;
        roll[Skeleton.SHOULDER_R] += -breath * 0.020f * rest;

        // Contrapposto: the weight rests on one leg, so that hip rides up and the
        // shoulders tilt back the other way to keep the head over the feet. A figure
        // standing square on both feet with everything level is a shop dummy — it is
        // the one pose a person almost never actually holds.
        float stand = 0.34f + sway * 0.66f;
        roll[Skeleton.HIPS] += stand * 0.052f * rest;
        roll[Skeleton.CHEST] += -stand * 0.036f * rest;
        roll[Skeleton.NECK] += stand * 0.020f * rest;
        yaw[Skeleton.CHEST] += drift * 0.034f * rest;
        yaw[Skeleton.NECK] += drift * 0.026f * rest;
        // The loaded leg straightens and the free one softens at the knee.
        pitch[Skeleton.SHIN_L] += Math.max(0f, -stand) * 0.16f * rest;
        pitch[Skeleton.SHIN_R] += Math.max(0f, stand) * 0.16f * rest;
        skeleton.hipOffsetY += -Math.abs(stand) * 0.008f * rest;

        // Arms rest with a little elbow flex, which carries the hands forward of the
        // thighs. Hanging them dead straight is most of what makes an idle read as a
        // plank — but the inward roll has to stay small: the upper arm is only
        // 0.178 m out from the centreline, so rolling it in by the 0.165 rad that
        // looks right on paper drives the elbow straight through the waist.
        roll[Skeleton.UPPERARM_L] += (-0.040f + sway * 0.014f) * rest;
        roll[Skeleton.UPPERARM_R] += (0.040f - sway * 0.014f) * rest;
        pitch[Skeleton.UPPERARM_L] += -0.05f * rest;
        pitch[Skeleton.UPPERARM_R] += -0.05f * rest;
        pitch[Skeleton.FOREARM_L] += -0.22f * rest;
        pitch[Skeleton.FOREARM_R] += -0.22f * rest;
        yaw[Skeleton.FOREARM_L] += -0.10f * rest;
        yaw[Skeleton.FOREARM_R] += 0.10f * rest;
    }

    private void applyLocomotion(Skeleton skeleton) {
        float amount = MathUtil.clamp(locomotion, 0f, 1f);
        float run = MathUtil.clamp(runBlend, 0f, 1f);
        float p = walkPhase;

        // Human walking uses roughly 40 degrees of hip range and peaks near 60
        // degrees of knee flexion during swing. Running opens both up. Overshooting
        // these turns the walk into a march, with the ankle lifting half a metre.
        float stride = (0.40f + run * 0.34f) * amount;
        float armAmount = (0.42f + run * 0.32f) * amount;
        float kneeAmount = (0.58f + run * 0.62f) * amount;

        float legL = (float) Math.sin(p);
        float legR = (float) Math.sin(p + Math.PI);

        pitch[Skeleton.THIGH_L] += legL * stride;
        pitch[Skeleton.THIGH_R] += legR * stride;

        // The knee flexes just after the leg passes behind, then straightens to land.
        pitch[Skeleton.SHIN_L] += kneeFlex(p) * kneeAmount;
        pitch[Skeleton.SHIN_R] += kneeFlex((float) (p + Math.PI)) * kneeAmount;

        // Ankle keeps the sole roughly level, with a push-off at toe-off.
        pitch[Skeleton.FOOT_L] += (-legL * 0.30f + toeOff(p) * 0.34f) * amount;
        pitch[Skeleton.FOOT_R] += (-legR * 0.30f + toeOff((float) (p + Math.PI)) * 0.34f) * amount;

        // Arms swing opposite the leg on the same side.
        float carryFree = 1f - MathUtil.clamp(carry, 0f, 1f);
        pitch[Skeleton.UPPERARM_L] += -legL * armAmount * carryFree;
        pitch[Skeleton.UPPERARM_R] += -legR * armAmount * carryFree;
        pitch[Skeleton.FOREARM_L] += (-0.30f - Math.max(0f, -legL) * 0.40f) * amount * carryFree;
        pitch[Skeleton.FOREARM_R] += (-0.30f - Math.max(0f, -legR) * 0.40f) * amount * carryFree;
        roll[Skeleton.UPPERARM_L] += -0.10f * amount * carryFree;
        roll[Skeleton.UPPERARM_R] += 0.10f * amount * carryFree;

        // Pelvis and chest counter-rotate; the chest lags, which reads as natural.
        yaw[Skeleton.HIPS] += legL * 0.085f * amount;
        yaw[Skeleton.CHEST] += -legL * 0.135f * amount;
        roll[Skeleton.HIPS] += -legL * 0.055f * amount;

        // Lean into the run. The spine points up, so a positive pitch tips it
        // forward — the opposite sign to a limb that hangs down, and getting it
        // backwards puts a walker on their heels with their chin in the air.
        pitch[Skeleton.SPINE] += (0.045f + run * 0.13f) * amount;

        // Vertical bob runs at twice the stride frequency.
        float bob = (float) Math.cos(p * 2f);
        skeleton.hipOffsetY += (bob * 0.5f - 0.5f) * (0.028f + run * 0.022f) * amount;
    }

    /** Knee flexion curve: near zero at heel strike, peaking during the swing. */
    private static float kneeFlex(float p) {
        float s = (float) Math.sin(p - 0.35f);
        float swing = Math.max(0f, s);
        return 0.12f + swing * swing * 1.05f;
    }

    /** A short push-off spike as the foot leaves the ground. */
    private static float toeOff(float p) {
        float s = (float) Math.sin(p - 0.15f);
        return Math.max(0f, s) * Math.max(0f, s);
    }

    private void applyCarry() {
        float w = MathUtil.clamp(carry, 0f, 1f);
        // Both arms come up and in to hold a crate against the chest.
        pitch[Skeleton.UPPERARM_L] = MathUtil.lerp(pitch[Skeleton.UPPERARM_L], -1.02f, w);
        pitch[Skeleton.UPPERARM_R] = MathUtil.lerp(pitch[Skeleton.UPPERARM_R], -1.02f, w);
        roll[Skeleton.UPPERARM_L] = MathUtil.lerp(roll[Skeleton.UPPERARM_L], -0.34f, w);
        roll[Skeleton.UPPERARM_R] = MathUtil.lerp(roll[Skeleton.UPPERARM_R], 0.34f, w);
        pitch[Skeleton.FOREARM_L] = MathUtil.lerp(pitch[Skeleton.FOREARM_L], -1.28f, w);
        pitch[Skeleton.FOREARM_R] = MathUtil.lerp(pitch[Skeleton.FOREARM_R], -1.28f, w);
        yaw[Skeleton.FOREARM_L] += -0.30f * w;
        yaw[Skeleton.FOREARM_R] += 0.30f * w;
        // Leaning back counterbalances the load.
        pitch[Skeleton.SPINE] += -0.10f * w;
        pitch[Skeleton.CHEST] += -0.05f * w;
    }

    private void applyCrouch(Skeleton skeleton) {
        float w = MathUtil.clamp(crouch, 0f, 1f);
        pitch[Skeleton.THIGH_L] += -0.95f * w;
        pitch[Skeleton.THIGH_R] += -0.95f * w;
        pitch[Skeleton.SHIN_L] += 1.65f * w;
        pitch[Skeleton.SHIN_R] += 1.65f * w;
        pitch[Skeleton.FOOT_L] += -0.62f * w;
        pitch[Skeleton.FOOT_R] += -0.62f * w;
        pitch[Skeleton.SPINE] += 0.26f * w;
        pitch[Skeleton.CHEST] += 0.12f * w;
        skeleton.hipOffsetY += -0.36f * w;
    }

    private void applyReach() {
        float w = MathUtil.clamp(reach, 0f, 1f);
        int upper = reachRight ? Skeleton.UPPERARM_R : Skeleton.UPPERARM_L;
        int fore = reachRight ? Skeleton.FOREARM_R : Skeleton.FOREARM_L;
        float side = reachRight ? 1f : -1f;

        // Aim the arm from the shoulder at the target height. The shoulder sits near
        // 1.44 m, so a shelf above that needs the arm swung past horizontal.
        float shoulderHeight = Skeleton.bindY(upper);
        float delta = reachHeight - shoulderHeight;
        float armAngle = MathUtil.clamp(-1.35f - delta * 1.25f, -2.6f, -0.15f);

        pitch[upper] = MathUtil.lerp(pitch[upper], armAngle, w);
        roll[upper] = MathUtil.lerp(roll[upper], -side * 0.22f, w);
        // Straighter arm for a high shelf, more bend for a low one.
        float elbow = MathUtil.clamp(-0.85f + delta * 0.9f, -1.5f, -0.15f);
        pitch[fore] = MathUtil.lerp(pitch[fore], elbow, w);
        yaw[Skeleton.CHEST] += -side * 0.22f * w;
        pitch[Skeleton.CHEST] += 0.10f * w;
    }

    private void applyScan() {
        float w = MathUtil.clamp(scan, 0f, 1f);
        float cycle = (float) Math.sin(scanPhase);
        float grab = Math.max(0f, cycle);
        float sweep = Math.max(0f, -cycle);

        // Right hand picks an item off the belt and draws it across the scanner.
        pitch[Skeleton.UPPERARM_R] = MathUtil.lerp(pitch[Skeleton.UPPERARM_R],
                -0.55f - grab * 0.35f, w);
        pitch[Skeleton.FOREARM_R] = MathUtil.lerp(pitch[Skeleton.FOREARM_R],
                -1.15f - sweep * 0.45f, w);
        yaw[Skeleton.UPPERARM_R] += (-0.30f + sweep * 0.55f) * w;
        roll[Skeleton.UPPERARM_R] += 0.18f * w;

        // Left hand rests near the till and taps between items.
        pitch[Skeleton.UPPERARM_L] = MathUtil.lerp(pitch[Skeleton.UPPERARM_L], -0.62f, w);
        pitch[Skeleton.FOREARM_L] = MathUtil.lerp(pitch[Skeleton.FOREARM_L],
                -1.30f + grab * 0.18f, w);
        yaw[Skeleton.UPPERARM_L] += 0.26f * w;

        pitch[Skeleton.SPINE] += -0.07f * w;
        yaw[Skeleton.CHEST] += -0.14f * w * (0.5f + sweep * 0.5f);
    }

    private void applyLook() {
        // Split the turn between neck and head so the whole spine does not swivel.
        float clampedYaw = MathUtil.clamp(lookYaw, -1.15f, 1.15f);
        float clampedPitch = MathUtil.clamp(lookPitch, -0.6f, 0.55f);
        yaw[Skeleton.NECK] += clampedYaw * 0.38f;
        yaw[Skeleton.HEAD] += clampedYaw * 0.62f;
        pitch[Skeleton.NECK] += clampedPitch * 0.35f;
        pitch[Skeleton.HEAD] += clampedPitch * 0.65f;
    }
}
