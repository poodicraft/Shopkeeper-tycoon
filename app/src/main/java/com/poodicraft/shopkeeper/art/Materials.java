package com.poodicraft.shopkeeper.art;

/**
 * The material table.
 *
 * <p>Each entry is one layer of the albedo and normal texture arrays plus the
 * shading parameters the fragment shader reads. Geometry carries the index as a
 * vertex attribute, so the entire shop draws as a single call regardless of how
 * many surfaces it mixes.
 */
public final class Materials {
    private Materials() { }

    public static final int FLOOR_TILE    = 0;
    public static final int WALL_PAINT    = 1;
    public static final int WOOD_LIGHT    = 2;
    public static final int WOOD_DARK     = 3;
    public static final int METAL_BRUSHED = 4;
    public static final int METAL_DARK    = 5;
    public static final int SKIN          = 6;
    public static final int HAIR          = 7;
    public static final int SHIRT         = 8;
    public static final int DENIM         = 9;
    public static final int APRON         = 10;
    public static final int LEATHER       = 11;
    public static final int CARDBOARD     = 12;
    public static final int PLASTIC       = 13;
    public static final int GLASS         = 14;
    public static final int CEILING       = 15;
    public static final int RUBBER_MAT    = 16;
    public static final int PAPER         = 17;
    public static final int PAINTED       = 18;
    public static final int CONCRETE      = 19;
    public static final int MARBLE        = 20;
    public static final int CHROME        = 21;
    public static final int FOLIAGE       = 22;
    public static final int MOSAIC        = 23;
    public static final int BRICK         = 24;
    public static final int CARPET        = 25;
    public static final int CERAMIC       = 26;
    public static final int EMISSIVE      = 27;
    /** Printed packaging: one layer per product tier, painted with real text. */
    public static final int LABEL_BASE    = 28;
    public static final int LABEL_COUNT   = 4;
    /**
     * The face, as a painted equirectangular map of the whole head.
     *
     * <p>Brows, lash lines, lips, nostrils and the ear are drawn here rather than
     * built as geometry. Every one of them modelled as a bump on the skull's radius
     * came out as a smooth mound with smooth normals across it, and a dozen of those
     * overlapping is a wax face. Paint has edges; a radius field does not.
     */
    public static final int FACE          = 32;
    /**
     * The eye: sclera, iris, pupil and catchlight, on its own layer because it is the
     * one part of a face that must <em>not</em> take the skin tint. A white painted
     * into the face map comes out the colour of the skin around it.
     */
    public static final int EYE           = 33;

    public static final int COUNT = 34;

    /**
     * Per-material shading: specular strength, shininess, normal-map strength and
     * emissive amount, packed for direct upload as a vec4 array.
     */
    public static float[] shadingParams() {
        float[] p = new float[COUNT * 4];
        set(p, FLOOR_TILE,    0.30f,  42f, 0.85f, 0f);
        set(p, WALL_PAINT,    0.06f,  12f, 0.45f, 0f);
        set(p, WOOD_LIGHT,    0.18f,  26f, 0.80f, 0f);
        set(p, WOOD_DARK,     0.16f,  24f, 0.80f, 0f);
        set(p, METAL_BRUSHED, 0.62f,  70f, 0.55f, 0f);
        set(p, METAL_DARK,    0.45f,  54f, 0.55f, 0f);
        set(p, SKIN,          0.12f,  22f, 0.35f, 0f);
        set(p, HAIR,          0.28f,  36f, 0.45f, 0f);
        // Cloth normal strengths are deliberately low. A weave's height field has a
        // lot of contrast in it, and pushed hard the shirt stops reading as shirting
        // and starts reading as coarse knitwear moulded onto the body.
        set(p, SHIRT,         0.07f,  14f, 0.24f, 0f);
        set(p, DENIM,         0.06f,  12f, 0.28f, 0f);
        set(p, APRON,         0.08f,  16f, 0.26f, 0f);
        set(p, LEATHER,       0.24f,  30f, 0.85f, 0f);
        set(p, CARDBOARD,     0.05f,  10f, 0.70f, 0f);
        set(p, PLASTIC,       0.42f,  58f, 0.30f, 0f);
        set(p, GLASS,         0.75f, 110f, 0.15f, 0f);
        set(p, CEILING,       0.08f,  14f, 0.60f, 0f);
        set(p, RUBBER_MAT,    0.05f,  10f, 0.85f, 0f);
        set(p, PAPER,         0.08f,  16f, 0.35f, 0f);
        set(p, PAINTED,       0.34f,  46f, 0.30f, 0f);
        set(p, CONCRETE,      0.10f,  18f, 0.80f, 0f);
        set(p, MARBLE,        0.50f,  80f, 0.35f, 0f);
        set(p, CHROME,        0.85f, 128f, 0.20f, 0f);
        set(p, FOLIAGE,       0.20f,  28f, 0.70f, 0f);
        set(p, MOSAIC,        0.40f,  60f, 0.80f, 0f);
        set(p, BRICK,         0.08f,  14f, 0.95f, 0f);
        set(p, CARPET,        0.04f,   8f, 0.90f, 0f);
        set(p, CERAMIC,       0.55f,  90f, 0.25f, 0f);
        // The face leans hard on its normal map: the relief of a lip or a nostril is
        // in the height field, so painted features still catch the light.
        set(p, FACE,          0.13f,  24f, 1.00f, 0f);
        set(p, EYE,           0.60f, 110f, 0.30f, 0f);
        set(p, EMISSIVE,      0.20f,  30f, 0.20f, 1f);
        for (int i = 0; i < LABEL_COUNT; i++) {
            set(p, LABEL_BASE + i, 0.26f, 40f, 0.30f, 0f);
        }
        return p;
    }

    private static void set(float[] p, int index, float specular, float shininess,
                            float normalStrength, float emissive) {
        p[index * 4] = specular;
        p[index * 4 + 1] = shininess;
        p[index * 4 + 2] = normalStrength;
        p[index * 4 + 3] = emissive;
    }
}
