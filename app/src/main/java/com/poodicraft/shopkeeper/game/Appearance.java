package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.art.Materials;

import java.util.Random;

/**
 * How one character looks.
 *
 * <p>Everyone shares a single mesh, so variety comes from a per-draw tint per
 * material: skin, hair, shirt, trousers and shoes are separate materials on the
 * body, which means one colour table turns the same geometry into a different
 * person.
 */
public final class Appearance {

    private static final int[] SKIN_TONES = {
            0xF6D9BE, 0xEEC5A2, 0xDFA878, 0xC88B58, 0xA36B3E, 0x7C4A26, 0x5A3218,
    };
    private static final int[] HAIR_COLOURS = {
            0x2A1F18, 0x3E2A1B, 0x5C3A1E, 0x8A5A2B, 0xC49A55, 0x9A9AA2, 0xE4E1DA, 0x7A2F22,
    };
    private static final int[] SHIRT_COLOURS = {
            0x3E6FB0, 0xC4544A, 0x4B9E6A, 0xD6A13C, 0x7B5EAE, 0x2E9C9C,
            0xD07BA4, 0x4C5BAE, 0x9A5B39, 0x37876F, 0xB8B2A4, 0x51565E,
    };
    private static final int[] TROUSER_COLOURS = {
            0x37414F, 0x2A2F3A, 0x4A4034, 0x555A63, 0x243447, 0x6B6257, 0x2E3B33,
    };
    private static final int[] SHOE_COLOURS = {
            0x3A3230, 0x22201F, 0x5A4234, 0x2C3540, 0x6E6660,
    };

    public int skin = SKIN_TONES[1];
    public int hair = HAIR_COLOURS[0];
    public int shirt = SHIRT_COLOURS[0];
    public int trousers = TROUSER_COLOURS[0];
    public int shoes = SHOE_COLOURS[0];
    public int apron = 0x9C7A52;
    /** 0 short, 1 long, 2 cropped. */
    public int hairStyle = 0;
    /** Staff wear an apron; shoppers do not. */
    public boolean wearsApron = false;
    /** Multiplies the whole skeleton; roughly 1.55 m to 1.92 m tall. */
    public float height = 1.0f;

    /** Material tint table handed to the renderer, three floats per material. */
    private final float[] tints = new float[Materials.COUNT * 3];
    private boolean tintsDirty = true;

    public static Appearance randomShopper(Random rng) {
        Appearance a = new Appearance();
        a.skin = SKIN_TONES[rng.nextInt(SKIN_TONES.length)];
        a.hair = HAIR_COLOURS[rng.nextInt(HAIR_COLOURS.length)];
        a.shirt = SHIRT_COLOURS[rng.nextInt(SHIRT_COLOURS.length)];
        a.trousers = TROUSER_COLOURS[rng.nextInt(TROUSER_COLOURS.length)];
        a.shoes = SHOE_COLOURS[rng.nextInt(SHOE_COLOURS.length)];
        a.hairStyle = rng.nextInt(3);
        a.height = 0.90f + rng.nextFloat() * 0.19f;
        a.tintsDirty = true;
        return a;
    }

    public static Appearance staffMember(Random rng) {
        Appearance a = randomShopper(rng);
        // A uniform makes staff readable at a glance across a busy shop.
        a.shirt = 0x2E7D63;
        a.trousers = 0x2B303B;
        a.apron = 0xA8845A;
        a.wearsApron = true;
        a.height = 0.96f + rng.nextFloat() * 0.10f;
        a.tintsDirty = true;
        return a;
    }

    public void markDirty() { tintsDirty = true; }

    /** Builds (and caches) the tint table for this character. */
    public float[] materialTints() {
        if (!tintsDirty) return tints;
        for (int i = 0; i < tints.length; i++) tints[i] = 1f;
        set(Materials.SKIN, skin);
        set(Materials.HAIR, hair);
        set(Materials.SHIRT, shirt);
        set(Materials.DENIM, trousers);
        set(Materials.LEATHER, shoes);
        set(Materials.APRON, apron);
        // The face map is skin, so it takes the skin tint. Its base is painted a
        // little under white and the tint is scaled to match, which leaves the
        // brows, lips and lash lines room to be darker than skin rather than
        // darker than the texture.
        set(Materials.FACE, skin);
        // The eyes deliberately keep the default white: tinting them by skin is
        // exactly the thing having a separate layer is there to avoid.
        tintsDirty = false;
        return tints;
    }

    private void set(int material, int rgb) {
        tints[material * 3] = ((rgb >> 16) & 0xFF) / 255f;
        tints[material * 3 + 1] = ((rgb >> 8) & 0xFF) / 255f;
        tints[material * 3 + 2] = (rgb & 0xFF) / 255f;
    }
}
