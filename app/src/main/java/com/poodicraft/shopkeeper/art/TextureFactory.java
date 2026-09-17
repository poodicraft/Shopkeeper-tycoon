package com.poodicraft.shopkeeper.art;

/**
 * Generates every surface in the game as pixels: an albedo layer and a matching
 * tangent-space normal map, for each entry in {@link Materials}.
 *
 * <p>The game ships no image files. Each material describes a colour and a height
 * at a point; the normal map is then derived from the height field by central
 * differences, which is what gives grout lines, plank gaps, fabric weave and
 * leather grain their relief under a moving light.
 *
 * <p>Pure arithmetic and no Android types, so this runs on a worker thread during
 * loading and can be checked off-device.
 */
public final class TextureFactory {

    public static final int SIZE = 256;

    private final Noise noise = new Noise(0x5EED1234L);

    /** ARGB pixels per material layer. */
    public final int[][] albedo = new int[Materials.COUNT][];
    /** Tangent-space normal in RGB with the height in alpha. */
    public final int[][] normal = new int[Materials.COUNT][];

    private final float[] surfaceOut = new float[4];

    /** Generates every layer, spreading the work over the available cores. */
    public void generateAll() {
        final int cores = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        if (cores == 1) {
            for (int i = 0; i < Materials.COUNT; i++) generateLayer(i);
            return;
        }
        Thread[] workers = new Thread[cores];
        for (int w = 0; w < cores; w++) {
            final int start = w;
            final int step = cores;
            workers[w] = new Thread(new Runnable() {
                @Override public void run() {
                    TextureFactory local = TextureFactory.this;
                    float[] out = new float[4];
                    for (int layer = start; layer < Materials.COUNT; layer += step) {
                        local.renderLayer(layer, out);
                    }
                }
            }, "texgen-" + w);
            workers[w].start();
        }
        for (int w = 0; w < cores; w++) {
            try {
                workers[w].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void generateLayer(int layer) { renderLayer(layer, surfaceOut); }

    private void renderLayer(int layer, float[] out) {
        int[] colour = new int[SIZE * SIZE];
        float[] height = new float[SIZE * SIZE];

        for (int y = 0; y < SIZE; y++) {
            float v = (float) y / SIZE;
            for (int x = 0; x < SIZE; x++) {
                float u = (float) x / SIZE;
                surface(layer, u, v, out);
                int r = clamp255(out[0] * 255f);
                int g = clamp255(out[1] * 255f);
                int b = clamp255(out[2] * 255f);
                int index = y * SIZE + x;
                colour[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
                height[index] = out[3];
            }
        }

        albedo[layer] = colour;
        normal[layer] = heightToNormal(height);
    }

    /**
     * Converts a height field into a tangent-space normal map.
     *
     * <p>Sampling wraps, so the normal map tiles exactly like the albedo it came from.
     */
    private int[] heightToNormal(float[] height) {
        int[] out = new int[SIZE * SIZE];
        final float strength = 6.0f;
        for (int y = 0; y < SIZE; y++) {
            int up = ((y - 1) + SIZE) % SIZE;
            int down = (y + 1) % SIZE;
            for (int x = 0; x < SIZE; x++) {
                int left = ((x - 1) + SIZE) % SIZE;
                int right = (x + 1) % SIZE;
                float dx = (height[y * SIZE + right] - height[y * SIZE + left]) * strength;
                float dy = (height[down * SIZE + x] - height[up * SIZE + x]) * strength;
                float nx = -dx, ny = -dy, nz = 1f;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;
                int r = clamp255((nx * 0.5f + 0.5f) * 255f);
                int g = clamp255((ny * 0.5f + 0.5f) * 255f);
                int b = clamp255((nz * 0.5f + 0.5f) * 255f);
                int a = clamp255(height[y * SIZE + x] * 255f);
                out[y * SIZE + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    // --------------------------------------------------------------- surfaces

    /** Writes r, g, b (0..1) and height (0..1) for one point on one material. */
    public void surface(int layer, float u, float v, float[] out) {
        switch (layer) {
            case Materials.FLOOR_TILE:    floorTile(u, v, out); break;
            case Materials.WALL_PAINT:    wallPaint(u, v, out); break;
            case Materials.WOOD_LIGHT:    wood(u, v, out, 0.72f, 0.54f, 0.35f); break;
            case Materials.WOOD_DARK:     wood(u, v, out, 0.40f, 0.27f, 0.17f); break;
            case Materials.METAL_BRUSHED: brushedMetal(u, v, out, 0.74f); break;
            case Materials.METAL_DARK:    brushedMetal(u, v, out, 0.36f); break;
            case Materials.SKIN:          skin(u, v, out); break;
            case Materials.HAIR:          hair(u, v, out); break;
            case Materials.SHIRT:         weave(u, v, out, 26f, 0.86f, 0.55f); break;
            case Materials.DENIM:         denim(u, v, out); break;
            case Materials.APRON:         weave(u, v, out, 16f, 0.80f, 0.85f); break;
            case Materials.LEATHER:       leather(u, v, out); break;
            case Materials.CARDBOARD:     cardboard(u, v, out); break;
            case Materials.PLASTIC:       plastic(u, v, out); break;
            case Materials.GLASS:         glass(u, v, out); break;
            case Materials.CEILING:       ceilingPanel(u, v, out); break;
            case Materials.RUBBER_MAT:    rubberMat(u, v, out); break;
            case Materials.PAPER:         paper(u, v, out); break;
            case Materials.PAINTED:       painted(u, v, out); break;
            case Materials.CONCRETE:      concrete(u, v, out); break;
            case Materials.MARBLE:        marble(u, v, out); break;
            case Materials.CHROME:        chrome(u, v, out); break;
            case Materials.FOLIAGE:       foliage(u, v, out); break;
            case Materials.MOSAIC:        mosaic(u, v, out); break;
            case Materials.BRICK:         brick(u, v, out); break;
            case Materials.CARPET:        carpet(u, v, out); break;
            case Materials.CERAMIC:       ceramic(u, v, out); break;
            case Materials.EMISSIVE:      emissive(u, v, out); break;
            case Materials.FACE:          face(u, v, out); break;
            case Materials.EYE:           eye(u, v, out); break;
            default:                      labelBase(u, v, out); break;
        }
    }

    // ------------------------------------------------------------------- face

    /**
     * The face, painted as an equirectangular map of the whole head.
     *
     * <p>{@code u} runs right round the skull with the nose at 0.5 and the seam at
     * the back; {@code v} runs from the crown at 0 to under the chin at 1. Everything
     * off the face is plain skin, so the head can be one material with no seam
     * anywhere on it.
     *
     * <p>The colours here are <em>ratios</em>, not absolutes: the layer is tinted by
     * the character's skin, so 1.0 means "skin" and 0.3 means "a dark line on skin".
     * That is why the eyes are not here — a white painted into this map comes out the
     * colour of the face around it, and there is no skin tone this works for.
     */
    private void face(float u, float v, float[] out) {
        float du = u - 0.5f;
        if (du > 0.5f) du -= 1f;
        if (du < -0.5f) du += 1f;
        float side = Math.abs(du);

        // Skin underneath everything. The noise is sampled through the inverse of
        // the face mapping, so pores stay the same size per centimetre of skin: read
        // straight off u and v they stretch 3:1 across the compressed band and the
        // side of the head comes out visibly streaked.
        float un = side <= 0.40f ? side / 2.3529f : 0.17f + (side - 0.40f) / 0.30303f;
        float vn = (v - 0.5f) / 1.60f + 0.615f;
        float pores = noise.cellular(un * 220f, vn * 150f, 220);
        float mottle = noise.fbm(un * 30f, vn * 19f, 30, 4, 0.5f);
        float tone = 0.95f + mottle * 0.06f - (1f - smoothstep(0f, 0.35f, pores)) * 0.05f;
        float r = tone, g = tone * 0.985f, b = tone * 0.970f;
        float height = 0.5f + pores * 0.04f;

        // Warmth across the cheeks.
        float cheek = bump(side, 0.250f, 0.130f) * bump(v, 0.420f, 0.128f);
        r += cheek * 0.030f; g -= cheek * 0.014f; b -= cheek * 0.020f;

        // Landmarks are real head measurements carried through CharacterMesh.faceU
        // and faceV: pupils 31.5 mm out from the centreline and level with the middle
        // of the head, brow ends at 7.4 and 52.5 mm, nostrils at 9.5, mouth corners
        // at 25, jaw and hairline where an adult skull actually puts them.
        final float eyeU = 0.1537f, eyeV = 0.3058f;

        for (int s = -1; s <= 1; s += 2) {
            float ex = (du - s * eyeU) * s;   // outward-positive across the eye
            float ey = v - eyeV;

            // Socket: a soft hollow the eye sits in.
            float socket = bump(ex, 0f, 0.118f) * bump(ey, -0.0064f, 0.064f);
            float shade = socket * 0.13f;
            r -= shade; g -= shade * 0.95f; b -= shade * 0.86f;
            height -= socket * 0.10f;

            // Upper lash line, hugging the top of the eye and heavier at the outer end.
            float lashY = -0.0420f - ex * 0.0374f + ex * ex * 0.751f;
            float lash = bump(ey - lashY, 0f, 0.0150f + Math.max(0f, ex) * 0.0045f)
                    * window(ex, -0.0880f, 0.1010f, 0.0250f);
            float lashInk = lash * 0.82f;
            r *= 1f - lashInk; g *= 1f - lashInk * 0.98f; b *= 1f - lashInk * 0.94f;
            height -= lash * 0.06f;

            // Lower lid, and the crease above the eye.
            float lidY = 0.0400f - ex * 0.0136f + ex * ex * 0.462f;
            float lid = bump(ey - lidY, 0f, 0.0095f) * window(ex, -0.0800f, 0.0860f, 0.0300f);
            r -= lid * 0.16f; g -= lid * 0.16f; b -= lid * 0.14f;
            float crease = bump(ey - (lashY - 0.0336f), 0f, 0.012f)
                    * window(ex, -0.0659f, 0.0706f, 0.0329f);
            r -= crease * 0.09f; g -= crease * 0.09f; b -= crease * 0.08f;
            height += crease * 0.05f;

            // Brow: an arc rising from the inner end and falling away outboard. Its
            // inner feather has to be narrower than the gap between the two brows, or
            // they blur into a single bar straight across the face.
            float browY = 0.1180f - 0.0170f * smoothstep(-0.040f, 0.090f, ex)
                    + 0.0330f * smoothstep(0.100f, 0.190f, ex);
            float brow = bump(v - browY, 0f, 0.0230f - Math.max(0f, ex) * 0.0260f)
                    * window(ex, -0.0988f, 0.1600f, 0.0200f);
            float browInk = brow * 0.88f;
            r *= 1f - browInk; g *= 1f - browInk * 0.99f; b *= 1f - browInk * 0.97f;
            height += brow * 0.10f;
        }

        // Ear. It has to sit inside the map's linear region, below u = 0.40: past
        // that the mapping squeezes the back of the head into a tenth of the texture,
        // and anything painted there gets smeared right across the side of the skull.
        // Everything out in that band is plain skin, which stretches invisibly.
        float earX = (side - 0.3850f) * 3.0f, earY = v - 0.399f;
        float earOval = 1f - smoothstep(0.085f, 0.135f,
                (float) Math.sqrt(earX * earX + earY * earY));
        r -= earOval * 0.070f; g -= earOval * 0.070f; b -= earOval * 0.062f;
        float earRim = earOval * smoothstep(-0.020f, 0.040f, side - 0.3850f);
        r += earRim * 0.055f; g += earRim * 0.052f; b += earRim * 0.046f;
        height += earRim * 0.16f - earOval * 0.06f;

        // Nose. The crease sits at the edge of the nose's own form, 18.5 mm out, and
        // does not start until the eye line; any higher or any closer in and it draws
        // a line down the bridge, which reads as a blade rather than a nose.
        float noseDown = smoothstep(0.316f, 0.540f, v);
        float flankAt = 0.0882f + noseDown * 0.0141f;
        float flank = bump(side, flankAt, 0.0176f + noseDown * 0.0118f)
                * window(v, 0.316f, 0.564f, 0.072f);
        r -= flank * 0.055f; g -= flank * 0.059f; b -= flank * 0.059f;
        height -= flank * 0.05f;

        float nostril = bump(side, 0.0452f, 0.0207f) * bump(v, 0.5528f, 0.0176f);
        float ink = nostril * 0.82f;
        r *= 1f - ink; g *= 1f - ink * 0.96f; b *= 1f - ink * 0.92f;
        height -= nostril * 0.30f;

        float underNose = bump(v, 0.5848f, 0.024f) * window(du, -0.0706f, 0.0706f, 0.0329f);
        r -= underNose * 0.070f; g -= underNose * 0.068f; b -= underNose * 0.060f;

        float philtrum = bump(du, 0f, 0.0165f) * window(v, 0.5848f, 0.6808f, 0.016f);
        height -= philtrum * 0.08f;

        // Lips. The upper is darker and dips at the centre into a cupid's bow; the
        // lower is fuller and catches light along the middle.
        float bow = 0.692f + bump(du, 0f, 0.0306f) * 0.0056f;
        float lipHalf = 0.1205f;
        float across = window(du, -lipHalf, lipHalf, 0.0376f);
        float upper = window(v, bow - 0.0312f, bow, 0.008f) * across;
        float lower = window(v, bow, bow + 0.0424f, 0.0096f) * across;
        float lip = Math.max(upper, lower);
        r = mix(r, r * 1.00f, lip);
        g = mix(g, g * 0.62f, lip);
        b = mix(b, b * 0.60f, lip);
        r += lower * 0.030f; g += lower * 0.010f; b += lower * 0.008f;
        height += upper * 0.10f + lower * 0.16f;

        float seam = bump(v - bow, 0f, 0.0067f) * across;
        float seamInk = seam * 0.62f;
        r *= 1f - seamInk; g *= 1f - seamInk * 0.92f; b *= 1f - seamInk * 0.90f;
        height -= seam * 0.22f;

        float under = bump(v - (bow + 0.048f), 0f, 0.0176f)
                * window(du, -0.0847f, 0.0847f, 0.0424f);
        r -= under * 0.055f; g -= under * 0.052f; b -= under * 0.046f;
        float corner = bump(side, lipHalf, 0.0259f) * bump(v - bow, 0.0016f, 0.0176f);
        r -= corner * 0.090f; g -= corner * 0.085f; b -= corner * 0.075f;

        // The jawline runs as a curve from the chin up and back to under the ear. A
        // straight band across the bottom of the face, which is the obvious thing to
        // paint, is a shadow under the chin and not a jaw at all.
        float jawV = 0.8952f - 0.968f * du * du;
        float below = bump(v - jawV, 0.0448f, 0.0544f) * window(side, 0f, 0.335f, 0.100f);
        float above = bump(v - jawV, -0.0416f, 0.0448f) * window(side, 0f, 0.318f, 0.100f);
        r -= below * 0.105f; g -= below * 0.102f; b -= below * 0.092f;
        r += above * 0.022f; g += above * 0.020f; b += above * 0.017f;
        height += above * 0.07f - below * 0.09f;

        // A hollow under the cheekbone, which is what gives a face a cheek at all.
        float hollow = bump(side, 0.266f, 0.089f) * bump(v, 0.572f, 0.104f);
        r -= hollow * 0.052f; g -= hollow * 0.052f; b -= hollow * 0.047f;
        height -= hollow * 0.05f;

        out[0] = clamp01(r);
        out[1] = clamp01(g);
        out[2] = clamp01(b);
        out[3] = clamp01(height);
    }

    /**
     * The eye, filling its own layer: the almond of geometry on the face is mapped
     * straight onto this, so the patch's outline is the eye's outline.
     *
     * <p>Untinted, which is the whole reason it is not part of {@link #face}.
     */
    private void eye(float u, float v, float[] out) {
        // Sclera, shaded towards the top where the lid sits over it, and warmed
        // towards the corners where it meets skin.
        float shade = 1f - smoothstep(0.30f, 0.0f, v) * 0.34f;
        float corner = smoothstep(0.34f, 0.0f, Math.min(u, 1f - u));
        float r = (0.965f * shade) - corner * 0.10f;
        float g = (0.945f * shade) - corner * 0.14f;
        float b = (0.930f * shade) - corner * 0.15f;
        float height = 0.55f;

        // Iris, a touch below centre so the eye is not staring.
        float ix = (u - 0.5f) * 1.85f, iy = v - 0.54f;
        float radial = (float) Math.sqrt(ix * ix + iy * iy);
        float iris = 1f - smoothstep(0.300f, 0.318f, radial);
        if (iris > 0f) {
            // Fibres running out from the pupil, and a darker limbal ring at the rim.
            float angle = (float) Math.atan2(iy, ix);
            float fibre = noise.value(angle * 9f, radial * 7f, 64);
            float tone = 0.52f + fibre * 0.22f - smoothstep(0.16f, 0.30f, radial) * 0.26f;
            r = mix(r, tone * 0.62f, iris);
            g = mix(g, tone * 0.78f, iris);
            b = mix(b, tone * 0.92f, iris);
            height = mix(height, 0.72f, iris);
        }
        float pupil = 1f - smoothstep(0.118f, 0.132f, radial);
        r = mix(r, 0.045f, pupil);
        g = mix(g, 0.045f, pupil);
        b = mix(b, 0.055f, pupil);

        // Catchlight. One small bright spot is most of what stops an eye looking dead.
        float cx = (u - 0.415f) * 1.85f, cy = v - 0.415f;
        float spark = 1f - smoothstep(0.052f, 0.070f, (float) Math.sqrt(cx * cx + cy * cy));
        r = mix(r, 1f, spark); g = mix(g, 1f, spark); b = mix(b, 1f, spark);

        // Lash shadow along the very top, and the dark rim all the way round.
        float rim = smoothstep(0.46f, 0.54f, Math.abs(v - 0.5f) * 1.6f
                + Math.abs(u - 0.5f) * 1.1f);
        r = mix(r, 0.16f, rim); g = mix(g, 0.16f, rim); b = mix(b, 0.17f, rim);

        out[0] = clamp01(r);
        out[1] = clamp01(g);
        out[2] = clamp01(b);
        out[3] = clamp01(height);
    }

    /** 1 at {@code centre}, falling to 0 at {@code width} either side. */
    private static float bump(float x, float centre, float width) {
        float d = Math.abs(x - centre) / Math.max(1e-6f, width);
        return d >= 1f ? 0f : 1f - smoothstep(0f, 1f, d);
    }

    /** 1 between {@code low} and {@code high}, feathered by {@code soft}. */
    private static float window(float x, float low, float high, float soft) {
        return smoothstep(low - soft, low + soft, x)
                * (1f - smoothstep(high - soft, high + soft, x));
    }

    private static float clamp01(float x) { return x < 0f ? 0f : (x > 1f ? 1f : x); }

    private void floorTile(float u, float v, float[] out) {
        final float tiles = 2f;
        float tu = u * tiles, tv = v * tiles;
        float fu = tu - (float) Math.floor(tu);
        float fv = tv - (float) Math.floor(tv);
        float edge = Math.min(Math.min(fu, 1f - fu), Math.min(fv, 1f - fv));
        float grout = smoothstep(0.012f, 0.032f, edge);

        // Per-tile colour drift so a large floor does not look stamped.
        float tileSeed = noise.value((float) Math.floor(tu) * 3.1f + 0.5f,
                (float) Math.floor(tv) * 3.1f + 0.5f, 64);
        float speck = noise.cellular(u * 48f, v * 48f, 48);
        float mottle = noise.fbm(u * 10f, v * 10f, 10, 4, 0.55f);

        float base = 0.80f + tileSeed * 0.07f + mottle * 0.10f - speck * 0.06f;
        float r = base * 0.99f, g = base * 0.96f, b = base * 0.90f;
        float groutColour = 0.34f + mottle * 0.06f;
        r = mix(groutColour, r, grout);
        g = mix(groutColour * 0.98f, g, grout);
        b = mix(groutColour * 0.95f, b, grout);

        out[0] = r; out[1] = g; out[2] = b;
        // Tiles sit proud of the grout with a slightly domed face.
        out[3] = grout * (0.82f + 0.10f * (1f - Math.abs(fu - 0.5f) * 2f)) + mottle * 0.04f;
    }

    private void wallPaint(float u, float v, float[] out) {
        float grain = noise.fbm(u * 26f, v * 26f, 26, 4, 0.5f);
        float broad = noise.fbm(u * 5f, v * 5f, 5, 3, 0.55f);
        float tone = 0.90f + grain * 0.05f + broad * 0.04f;
        out[0] = tone; out[1] = tone * 0.985f; out[2] = tone * 0.95f;
        out[3] = 0.45f + grain * 0.10f;
    }

    private void wood(float u, float v, float[] out, float r, float g, float b) {
        final float planks = 4f;
        float pv = v * planks;
        float plank = (float) Math.floor(pv);
        float fv = pv - plank;
        // Stagger each plank along its length so the ends do not line up.
        float shift = noise.value(plank * 7.3f, 1.7f, 32);
        float su = u + shift;

        float rings = noise.ridged(su * 3.2f, (v + shift) * 26f, 8, 4);
        float fine = noise.fbm(su * 40f, v * 90f, 40, 3, 0.5f);
        float knot = noise.cellular(su * 4f + plank * 13f, v * 4f, 8);
        float knotMask = 1f - smoothstep(0.04f, 0.16f, knot);

        float tone = 0.78f + rings * 0.30f + fine * 0.12f;
        tone -= knotMask * 0.30f;

        float gapTop = smoothstep(0f, 0.035f, fv);
        float gapBottom = smoothstep(0f, 0.035f, 1f - fv);
        float gap = Math.min(gapTop, gapBottom);

        out[0] = r * tone * mix(0.45f, 1f, gap);
        out[1] = g * tone * mix(0.45f, 1f, gap);
        out[2] = b * tone * mix(0.45f, 1f, gap);
        out[3] = gap * (0.62f + rings * 0.22f + fine * 0.10f - knotMask * 0.20f);
    }

    private void brushedMetal(float u, float v, float[] out, float level) {
        // Stretching the noise along u gives the anisotropic brushed look.
        float streak = noise.fbm(u * 3f, v * 220f, 8, 3, 0.55f);
        float scratch = noise.fbm(u * 8f, v * 460f, 16, 2, 0.5f);
        float tone = level + (streak - 0.5f) * 0.16f + (scratch - 0.5f) * 0.07f;
        out[0] = tone; out[1] = tone * 1.005f; out[2] = tone * 1.03f;
        out[3] = 0.5f + (streak - 0.5f) * 0.35f;
    }

    private void skin(float u, float v, float[] out) {
        float pores = noise.cellular(u * 120f, v * 120f, 120);
        float mottle = noise.fbm(u * 12f, v * 12f, 12, 4, 0.5f);
        float tone = 0.94f + mottle * 0.10f - (1f - smoothstep(0f, 0.35f, pores)) * 0.05f;
        out[0] = tone;
        out[1] = tone * 0.965f;
        out[2] = tone * 0.935f;
        out[3] = 0.5f + pores * 0.08f + mottle * 0.05f;
    }

    private void hair(float u, float v, float[] out) {
        // Strands run along v, so stretch the noise hard in that direction.
        float strand = noise.ridged(u * 130f, v * 5f, 32, 3);
        float clump = noise.fbm(u * 20f, v * 3f, 20, 3, 0.55f);
        float tone = 0.55f + strand * 0.45f + clump * 0.18f;
        out[0] = tone; out[1] = tone * 0.97f; out[2] = tone * 0.94f;
        out[3] = strand * 0.75f + clump * 0.2f;
    }

    private void weave(float u, float v, float[] out, float threads, float level, float contrast) {
        float tu = u * threads, tv = v * threads;
        float su = (float) Math.sin(tu * Math.PI * 2);
        float sv = (float) Math.sin(tv * Math.PI * 2);
        // Over-under: warp shows where the weft dips and vice versa.
        int cell = ((int) Math.floor(tu) + (int) Math.floor(tv)) & 1;
        float ridge = cell == 0 ? su : sv;
        float fuzz = noise.fbm(u * 150f, v * 150f, 64, 2, 0.5f);
        float tone = level + ridge * 0.06f * contrast + (fuzz - 0.5f) * 0.09f;
        out[0] = tone; out[1] = tone; out[2] = tone;
        out[3] = 0.5f + ridge * 0.38f * contrast + (fuzz - 0.5f) * 0.12f;
    }

    private void denim(float u, float v, float[] out) {
        // Twill runs on the diagonal.
        float diagonal = (float) Math.sin((u * 60f + v * 60f) * Math.PI);
        float threads = (float) Math.sin(v * 120f * Math.PI);
        float fuzz = noise.fbm(u * 120f, v * 120f, 60, 3, 0.5f);
        float tone = 0.74f + diagonal * 0.10f + threads * 0.04f + (fuzz - 0.5f) * 0.10f;
        out[0] = tone * 0.94f; out[1] = tone * 0.97f; out[2] = tone;
        out[3] = 0.5f + diagonal * 0.34f + (fuzz - 0.5f) * 0.16f;
    }

    private void leather(float u, float v, float[] out) {
        float cells = noise.cellular(u * 26f, v * 26f, 26);
        float fine = noise.fbm(u * 90f, v * 90f, 45, 3, 0.5f);
        float creases = smoothstep(0.02f, 0.12f, cells);
        float tone = 0.72f + creases * 0.22f + (fine - 0.5f) * 0.10f;
        out[0] = tone; out[1] = tone * 0.96f; out[2] = tone * 0.92f;
        out[3] = creases * 0.72f + fine * 0.14f;
    }

    private void cardboard(float u, float v, float[] out) {
        float fibres = noise.fbm(u * 200f, v * 60f, 64, 3, 0.5f);
        float flutes = (float) Math.sin(u * 42f * Math.PI) * 0.5f + 0.5f;
        float tone = 0.74f + (fibres - 0.5f) * 0.16f + flutes * 0.05f;
        out[0] = tone * 0.86f; out[1] = tone * 0.71f; out[2] = tone * 0.50f;
        out[3] = 0.45f + flutes * 0.22f + (fibres - 0.5f) * 0.25f;
    }

    private void plastic(float u, float v, float[] out) {
        float peel = noise.fbm(u * 55f, v * 55f, 28, 3, 0.5f);
        float tone = 0.92f + (peel - 0.5f) * 0.05f;
        out[0] = tone; out[1] = tone; out[2] = tone;
        out[3] = 0.5f + (peel - 0.5f) * 0.25f;
    }

    private void glass(float u, float v, float[] out) {
        float ripple = noise.fbm(u * 9f, v * 9f, 9, 2, 0.5f);
        out[0] = 0.86f; out[1] = 0.93f; out[2] = 0.96f;
        out[3] = 0.5f + (ripple - 0.5f) * 0.10f;
    }

    private void ceilingPanel(float u, float v, float[] out) {
        float holes = noise.cellular(u * 46f, v * 46f, 46);
        float dip = 1f - smoothstep(0.05f, 0.22f, holes);
        float panelU = u * 2f, panelV = v * 2f;
        float seamU = Math.min(frac(panelU), 1f - frac(panelU));
        float seamV = Math.min(frac(panelV), 1f - frac(panelV));
        float seam = smoothstep(0.006f, 0.022f, Math.min(seamU, seamV));
        float tone = (0.94f - dip * 0.14f) * mix(0.72f, 1f, seam);
        out[0] = tone; out[1] = tone; out[2] = tone * 0.985f;
        out[3] = seam * (0.62f - dip * 0.42f);
    }

    private void rubberMat(float u, float v, float[] out) {
        float ribs = (float) Math.sin(v * 70f * Math.PI) * 0.5f + 0.5f;
        float grit = noise.fbm(u * 130f, v * 130f, 64, 2, 0.5f);
        float tone = 0.20f + ribs * 0.07f + (grit - 0.5f) * 0.05f;
        out[0] = tone; out[1] = tone * 1.02f; out[2] = tone;
        out[3] = ribs * 0.6f + grit * 0.2f;
    }

    private void paper(float u, float v, float[] out) {
        float fibres = noise.fbm(u * 200f, v * 200f, 100, 2, 0.5f);
        float tone = 0.95f + (fibres - 0.5f) * 0.05f;
        out[0] = tone; out[1] = tone * 0.995f; out[2] = tone * 0.97f;
        out[3] = 0.5f + (fibres - 0.5f) * 0.15f;
    }

    private void painted(float u, float v, float[] out) {
        float peel = noise.fbm(u * 38f, v * 38f, 19, 3, 0.5f);
        float tone = 0.93f + (peel - 0.5f) * 0.04f;
        out[0] = tone; out[1] = tone; out[2] = tone;
        out[3] = 0.5f + (peel - 0.5f) * 0.18f;
    }

    private void concrete(float u, float v, float[] out) {
        float aggregate = noise.cellular(u * 40f, v * 40f, 40);
        float broad = noise.fbm(u * 8f, v * 8f, 8, 4, 0.55f);
        float pits = 1f - smoothstep(0.02f, 0.10f, aggregate);
        float tone = 0.60f + broad * 0.16f - pits * 0.14f;
        out[0] = tone; out[1] = tone * 0.995f; out[2] = tone * 0.97f;
        out[3] = 0.55f + broad * 0.2f - pits * 0.45f;
    }

    private void marble(float u, float v, float[] out) {
        float turbulence = noise.fbm(u * 6f, v * 6f, 6, 5, 0.6f);
        float veins = (float) Math.sin((u * 5f + turbulence * 4.5f) * Math.PI * 2);
        float mask = 1f - smoothstep(0.55f, 0.95f, Math.abs(veins));
        float tone = 0.90f - mask * 0.30f + turbulence * 0.05f;
        out[0] = tone; out[1] = tone * 0.99f; out[2] = tone * 0.98f;
        out[3] = 0.5f + mask * 0.07f;
    }

    private void chrome(float u, float v, float[] out) {
        float smudge = noise.fbm(u * 18f, v * 18f, 18, 3, 0.5f);
        float tone = 0.88f + (smudge - 0.5f) * 0.06f;
        out[0] = tone * 0.98f; out[1] = tone; out[2] = tone * 1.02f;
        out[3] = 0.5f + (smudge - 0.5f) * 0.08f;
    }

    private void foliage(float u, float v, float[] out) {
        float midrib = 1f - smoothstep(0.0f, 0.04f, Math.abs(u - 0.5f));
        float veins = Math.abs((float) Math.sin((v * 16f + (u - 0.5f) * 7f) * Math.PI));
        float veinMask = 1f - smoothstep(0.80f, 0.99f, veins);
        float blotch = noise.fbm(u * 14f, v * 14f, 14, 3, 0.55f);
        float tone = 0.62f + blotch * 0.26f;
        out[0] = tone * 0.42f + midrib * 0.10f;
        out[1] = tone * 0.86f + midrib * 0.10f;
        out[2] = tone * 0.36f;
        out[3] = 0.45f + midrib * 0.30f + veinMask * 0.18f + blotch * 0.10f;
    }

    private void mosaic(float u, float v, float[] out) {
        final float tiles = 10f;
        float fu = frac(u * tiles), fv = frac(v * tiles);
        float edge = Math.min(Math.min(fu, 1f - fu), Math.min(fv, 1f - fv));
        float grout = smoothstep(0.04f, 0.11f, edge);
        float seed = noise.value((float) Math.floor(u * tiles) * 5.3f,
                (float) Math.floor(v * tiles) * 5.3f, 32);
        float tone = 0.72f + seed * 0.24f;
        out[0] = mix(0.52f, tone * 0.80f, grout);
        out[1] = mix(0.52f, tone * 0.92f, grout);
        out[2] = mix(0.50f, tone * 0.95f, grout);
        out[3] = grout * 0.85f;
    }

    private void brick(float u, float v, float[] out) {
        final float rows = 8f;
        float rv = v * rows;
        int row = (int) Math.floor(rv);
        float fv = rv - row;
        float offset = (row & 1) == 0 ? 0f : 0.5f;
        float cu = frac(u * 4f + offset);
        float edge = Math.min(Math.min(cu, 1f - cu) * 2f, Math.min(fv, 1f - fv));
        float mortar = smoothstep(0.04f, 0.10f, edge);
        float seed = noise.value(row * 3.7f + (float) Math.floor(u * 4f + offset) * 11f, 2.2f, 32);
        float grit = noise.fbm(u * 80f, v * 80f, 40, 3, 0.5f);
        float tone = 0.58f + seed * 0.22f + (grit - 0.5f) * 0.10f;
        out[0] = mix(0.68f, tone * 0.98f, mortar);
        out[1] = mix(0.66f, tone * 0.58f, mortar);
        out[2] = mix(0.62f, tone * 0.48f, mortar);
        out[3] = mortar * 0.85f + grit * 0.12f;
    }

    private void carpet(float u, float v, float[] out) {
        float tufts = noise.cellular(u * 90f, v * 90f, 90);
        float fuzz = noise.fbm(u * 160f, v * 160f, 80, 2, 0.5f);
        float tone = 0.62f + tufts * 0.28f + (fuzz - 0.5f) * 0.12f;
        out[0] = tone; out[1] = tone * 0.99f; out[2] = tone * 0.96f;
        out[3] = tufts * 0.6f + fuzz * 0.3f;
    }

    private void ceramic(float u, float v, float[] out) {
        float crackle = noise.cellular(u * 30f, v * 30f, 30);
        float line = 1f - smoothstep(0.005f, 0.03f, crackle);
        float tone = 0.95f - line * 0.10f;
        out[0] = tone; out[1] = tone; out[2] = tone * 0.99f;
        out[3] = 0.55f - line * 0.2f;
    }

    private void emissive(float u, float v, float[] out) {
        float ripple = noise.fbm(u * 12f, v * 12f, 12, 2, 0.5f);
        float tone = 0.96f + ripple * 0.04f;
        out[0] = tone; out[1] = tone * 0.95f; out[2] = tone * 0.82f;
        out[3] = 0.5f;
    }

    /**
     * Blank packaging: a coloured field with a printed panel and a barcode block.
     * Product names are painted over this at load time.
     */
    private void labelBase(float u, float v, float[] out) {
        float paperGrain = noise.fbm(u * 120f, v * 120f, 60, 2, 0.5f);
        float tone = 0.93f + (paperGrain - 0.5f) * 0.06f;
        out[0] = tone; out[1] = tone; out[2] = tone * 0.98f;

        // A band across the middle and a barcode near the bottom give the packaging
        // printed structure even before any text lands on it.
        float band = (v > 0.34f && v < 0.60f) ? 1f : 0f;
        if (band > 0f) {
            out[0] = tone * 0.30f; out[1] = tone * 0.42f; out[2] = tone * 0.40f;
        }
        if (v > 0.74f && v < 0.90f && u > 0.18f && u < 0.82f) {
            float bars = noise.value(u * 70f, 3f, 70) > 0.5f ? 0.08f : 0.95f;
            out[0] = bars; out[1] = bars; out[2] = bars;
        }
        out[3] = 0.5f + (paperGrain - 0.5f) * 0.12f + band * 0.05f;
    }

    // ---------------------------------------------------------------- helpers

    private static float frac(float v) { return v - (float) Math.floor(v); }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 - edge0 < 1e-8f) return x < edge0 ? 0f : 1f;
        float t = (x - edge0) / (edge1 - edge0);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
    }

    private static int clamp255(float v) {
        int i = (int) (v + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
