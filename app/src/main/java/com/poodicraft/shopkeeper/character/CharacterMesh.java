package com.poodicraft.shopkeeper.character;

import com.poodicraft.shopkeeper.art.Materials;
import com.poodicraft.shopkeeper.gl.GeometryBuilder;
import com.poodicraft.shopkeeper.gl.MeshData;
import com.poodicraft.shopkeeper.math.MathUtil;

/**
 * Builds the skinned humanoid.
 *
 * <p>The figure is built the way a character artist blocks one out, because the
 * things that decide whether it reads as a person are all large: the silhouette
 * first, then the primary forms, then detail — and never the other way round.
 *
 * <ul>
 *   <li><b>Silhouette.</b> A torso that tapers from a 0.33 m chest to a 0.28 m waist,
 *       with the shoulder width coming from the deltoids at ±0.23 m rather than from
 *       a wide ribcage. Widening the ribcage to reach the same shoulder width is what
 *       turns a body into a slab: the outline stops changing between the armpit and
 *       the hip, and the eye reads that as furniture.</li>
 *   <li><b>Joints.</b> Every limb narrows at its joint and swells at its muscle —
 *       knee 0.053 m, calf 0.062 m, elbow 0.041 m, forearm 0.045 m. A limb swept as
 *       a straight cone has no knee and no elbow, so it bends like a hose.</li>
 *   <li><b>The neck.</b> The ribcage stops at 1.44 m and a separate trapezius yoke
 *       carries the shoulder line from there up to the neck. Lofting the torso
 *       straight up to collar height instead leaves a shelf with the head parked on
 *       it, which is the single loudest tell that a model was never looked at.</li>
 * </ul>
 *
 * <p>Detail is deliberately thin — no shoelaces, no fingernails. At the distance
 * this game is played from, a triangle spent on the shoulder line is worth fifty
 * spent inside the shoe, and the budget (about 4,800 triangles including hair,
 * twelve of them on screen) only stretches so far.
 *
 * <p>UV scales here are in <em>texture tiles per metre</em>. Every generator in
 * {@link GeometryBuilder} measures UVs in metres, so a scale of 7 puts seven repeats
 * of a weave across a metre of cloth. Leaving these at 1 is what turns fabric into
 * masonry.
 */
public final class CharacterMesh {

    private static final int LIMB_SEGMENTS = 12;
    private static final int TORSO_SEGMENTS = 16;
    // The head carries more resolution than anything else, because it has to: the
    // nose is only about a fifth of the head wide, so at sixteen segments a single
    // column of vertices lands on it and no amount of shaping can make it a nose.
    private static final int HEAD_SEGMENTS = 26;
    private static final int HEAD_RINGS = 18;

    /** Texture repeats per metre, chosen so each material's detail reads at arm's length. */
    private static final float UV_SKIN = 3.0f;
    private static final float UV_FABRIC = 7.0f;
    private static final float UV_DENIM = 7.0f;
    private static final float UV_APRON = 6.0f;
    private static final float UV_HAIR = 4.0f;
    private static final float UV_LEATHER = 5.0f;

    /** How far the shirt floats off the body it is stretched over. */
    private static final float SHIRT = 0.012f;

    /**
     * Head half-extents: 0.162 m across, 0.236 m tall, 0.198 m deep.
     *
     * <p>Seven and a half of these stack into the 1.78 m figure, which is what an
     * adult actually measures. Eight — the figure-drawing convention — makes the head
     * half a head smaller, and at this distance that reads as a pinhead, not heroic.
     */
    private static final float HEAD_WIDTH = 0.081f;
    private static final float HEAD_HEIGHT = 0.118f;
    private static final float HEAD_DEPTH = 0.099f;
    /** The HEAD bone pivots at the ear canal; the skull's centre is a little above it. */
    private static final float HEAD_RISE = 0.052f;

    private final GeometryBuilder b = new GeometryBuilder();

    /**
     * Torso cross-sections: height, half-width, half-depth, and how far the section
     * is pushed forward or back.
     *
     * <p>The waist at 1.082 m is the narrowest point and the chest at 1.330 m the
     * widest; everything else is read off those two. The last two rows collapse
     * quickly so the top of the loft is a small ring that the shoulder yoke buries.
     *
     * <p>The fourth column is doing as much work as the other two. It carries the
     * spine's S — chest forward, the small of the back hollow, the seat back again.
     * Leave it near zero and the profile is two parallel lines from shoulder to hip,
     * which is how a body with perfectly good width and depth still manages to read
     * as a plank the moment it turns side-on.
     */
    private static final float[][] TORSO = {
            {0.912f, 0.146f, 0.126f, -0.024f},  // seat, carried back by the glutes
            {0.958f, 0.155f, 0.119f, -0.010f},  // iliac crest
            {1.022f, 0.145f, 0.101f,  0.005f},  // above the waistband
            {1.082f, 0.132f, 0.094f,  0.009f},  // waist, hollow at the small of the back
            {1.142f, 0.139f, 0.104f,  0.008f},  // floating ribs
            {1.205f, 0.150f, 0.117f,  0.007f},  // ribcage
            {1.268f, 0.159f, 0.126f,  0.010f},  // chest
            {1.330f, 0.164f, 0.129f,  0.012f},  // pectorals, the widest point
            {1.382f, 0.160f, 0.116f,  0.006f},  // clavicles
            {1.418f, 0.136f, 0.092f, -0.008f},  // top of the ribcage
            {1.440f, 0.098f, 0.062f, -0.018f},  // buried inside the yoke
    };

    /** Pelvis, in trouser cloth; the thighs emerge from its underside. */
    private static final float[][] PELVIS = {
            {0.808f, 0.118f, 0.097f, -0.007f},
            {0.852f, 0.142f, 0.115f, -0.017f},
            {0.900f, 0.156f, 0.124f, -0.022f},  // the seat, at its fullest
            {0.952f, 0.157f, 0.122f, -0.018f},
            {1.008f, 0.150f, 0.104f, -0.005f},
    };

    public MeshData buildBody() {
        MeshData mesh = new MeshData(true, 7000);
        b.target(mesh).identity().noTint().occlusion(1f);

        buildPelvisAndLegs();
        buildTorso();
        buildShoulderYoke();
        buildArms();
        buildHands();
        buildNeckAndHead();

        b.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    // ---------------------------------------------------------------- the apron

    /**
     * The apron is its own mesh so only staff wear one.
     *
     * <p>Its radii are the torso's own cross-sections plus a standoff, never numbers
     * picked by eye: pick them by eye and the shirt underneath surfaces through
     * wherever the two curves happen to cross. What decides the shape is the
     * <em>angular</em> span of each row — a hand's width at the bib, two thirds of
     * the way round at the hem, which is still narrower than the hips. A bib as wide
     * as the chest is wider than a real apron by half, and a hem wider than the hips
     * erases the body's outline entirely: what is left is a cone with a head.
     */
    public MeshData buildApron() {
        MeshData mesh = new MeshData(true, 1800);
        b.target(mesh).identity().noTint().occlusion(1f).uv(UV_APRON);
        b.mat(Materials.APRON);
        b.skin(new SkinBinders.HeightChain(
                new int[]{Skeleton.HIPS, Skeleton.SPINE, Skeleton.CHEST},
                new float[]{0.960f, 1.108f, 1.300f}));

        // Rows of (height, half-width, half-depth, forward offset, angular half-span).
        // Down to the waist the radii track TORSO plus cloth; below it the apron has
        // left the body behind and hangs, so it stops following and drops straight.
        final float[][] rows = {
                {1.382f, 0.176f, 0.134f,  0.006f, 0.26f},   // bib top, 0.090 m across
                {1.330f, 0.186f, 0.151f,  0.012f, 0.34f},   // 0.124
                {1.268f, 0.181f, 0.148f,  0.010f, 0.42f},   // 0.148
                {1.205f, 0.172f, 0.139f,  0.007f, 0.52f},   // 0.171
                {1.142f, 0.161f, 0.126f,  0.008f, 0.64f},   // 0.192
                {1.082f, 0.154f, 0.116f,  0.009f, 0.78f},   // 0.217
                {1.030f, 0.162f, 0.123f,  0.004f, 0.88f},   // 0.249
                {0.958f, 0.172f, 0.136f, -0.008f, 0.92f},   // 0.273
                {0.880f, 0.177f, 0.143f, -0.012f, 0.92f},   // 0.281
                {0.800f, 0.179f, 0.146f, -0.010f, 0.91f},   // 0.283
                {0.744f, 0.180f, 0.147f, -0.008f, 0.90f},   // hem, above the knee
        };
        apronSheet(rows, 0f, 1f);
        apronSheet(rows, -0.008f, -1f);
        apronHem(rows);

        // Straps from the bib corners, over the trapezius, crossing at the back.
        b.skin(new SkinBinders.Rigid(Skeleton.CHEST));
        b.occlusion(0.90f);
        for (int side = -1; side <= 1; side += 2) {
            float[] strap = {
                    side * 0.045f, 1.372f,  0.136f,
                    side * 0.076f, 1.428f,  0.092f,
                    side * 0.097f, 1.476f,  0.026f,
                    side * 0.098f, 1.482f, -0.026f,
                    side * 0.088f, 1.446f, -0.082f,
                    side * 0.072f, 1.372f, -0.120f,
                    side * 0.050f, 1.314f, -0.126f,
            };
            b.tube(strap, fill(strap.length / 3, 0.0115f), 7, true);
        }

        // Waist tie: pushed forward so its front clears the apron and its back sits
        // on the shirt. A ring on the body's own centreline stands a finger's width
        // off the spine and reads as a plank laid across the back.
        b.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        b.occlusion(0.86f);
        final float[][] tie = {
                {1.034f, 0.162f, 0.126f, 0.008f},
                {1.050f, 0.166f, 0.129f, 0.008f},
                {1.066f, 0.162f, 0.126f, 0.008f},
        };
        loft(tie, 0f, 18, false, false);
        for (int side = -1; side <= 1; side += 2) {
            float[] bow = {
                    side * 0.010f, 1.046f, -0.122f,
                    side * 0.046f, 1.052f, -0.146f,
                    side * 0.068f, 1.028f, -0.150f,
            };
            b.tube(bow, new float[]{0.013f, 0.019f, 0.012f}, 6, true);
        }

        b.skin(null);
        b.occlusion(1f);
        mesh.computeTangents();
        return mesh;
    }

    /** One face of the apron: a partial ellipse sweep, wrapped round the front. */
    private void apronSheet(float[][] rows, float offset, float facing) {
        final int span = 10;
        int[][] grid = new int[rows.length][span + 1];
        for (int i = 0; i < rows.length; i++) {
            float y = rows[i][0];
            float halfWidth = rows[i][1] + offset;
            float halfDepth = rows[i][2] + offset;
            float centerZ = rows[i][3];
            float halfAngle = rows[i][4];
            for (int j = 0; j <= span; j++) {
                float t = -1f + 2f * j / span;
                float theta = t * halfAngle;
                float sin = (float) Math.sin(theta), cos = (float) Math.cos(theta);
                float px = sin * halfWidth;
                float pz = cos * halfDepth + centerZ;
                float nx = sin / halfWidth * facing;
                float nz = cos / halfDepth * facing;
                float length = (float) Math.sqrt(nx * nx + nz * nz);
                grid[i][j] = b.vertex(px, y, pz, nx / length, 0f, nz / length,
                        theta * halfWidth, y);
            }
        }
        for (int i = 0; i < rows.length - 1; i++) {
            for (int j = 0; j < span; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
    }

    /** Closes the apron's open edges so the cloth has thickness rather than none. */
    private void apronHem(float[][] rows) {
        final int span = 10;
        // Bottom hem.
        float[] bottom = rows[rows.length - 1];
        for (int j = 0; j < span; j++) {
            int[] a = hemPair(bottom, -1f + 2f * j / span);
            int[] c = hemPair(bottom, -1f + 2f * (j + 1) / span);
            b.quadOriented(a[0], a[1], c[1], c[0]);
        }
        // Side edges, up the length of the sheet.
        for (int edge = 0; edge < 2; edge++) {
            float t = edge == 0 ? -1f : 1f;
            for (int i = 0; i < rows.length - 1; i++) {
                int[] a = hemPair(rows[i], t);
                int[] c = hemPair(rows[i + 1], t);
                b.quadOriented(a[0], a[1], c[1], c[0]);
            }
        }
    }

    /** The outer and inner vertex of one point on the apron's cut edge. */
    private int[] hemPair(float[] row, float t) {
        float y = row[0], centerZ = row[3];
        float theta = t * row[4];
        float sin = (float) Math.sin(theta), cos = (float) Math.cos(theta);
        int[] pair = new int[2];
        for (int layer = 0; layer < 2; layer++) {
            float offset = layer == 0 ? 0f : -0.008f;
            float halfWidth = row[1] + offset, halfDepth = row[2] + offset;
            pair[layer] = b.vertex(sin * halfWidth, y, cos * halfDepth + centerZ,
                    cos * t, 0f, -sin * t, theta * halfWidth, y);
        }
        return pair;
    }

    // ----------------------------------------------------------------- the hair

    /** Hair is a separate mesh so a character can pick a style. */
    public MeshData buildHair(int style) {
        MeshData mesh = new MeshData(true, 1400);
        b.target(mesh).identity();
        b.skin(new SkinBinders.Rigid(Skeleton.HEAD));
        b.mat(Materials.HAIR).occlusion(0.80f).noTint().uv(UV_HAIR);

        float cx = 0f;
        float cy = Skeleton.bindY(Skeleton.HEAD) + HEAD_RISE;
        float cz = Skeleton.bindZ(Skeleton.HEAD);

        int segments = 26, rings = 16;
        float lift = style == 2 ? 0.0105f : 0.0195f;   // metres of hair thickness
        int[][] grid = new int[rings + 1][segments + 1];
        float[] normal = new float[3];
        float[] point = new float[3];

        // Every cell gets a vertex. Cells below the hairline are pulled up onto it
        // instead of being dropped, so the cap ends on a smooth curve. Dropping them
        // snaps the edge to whichever ring happens to straddle it, and on a mesh this
        // coarse that is a visible staircase across the temple.
        for (int i = 0; i <= rings; i++) {
            double phi = Math.PI * 0.80 * i / rings;
            float ringY = (float) Math.cos(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = Math.PI * 2 * j / segments;
                float hx = (float) Math.cos(theta), hz = (float) Math.sin(theta);
                float limit = hairline(hx, hz, style);
                float ny = Math.max(ringY, limit);
                float ring = (float) Math.sqrt(Math.max(0f, 1f - ny * ny));
                float nx = hx * ring, nz = hz * ring;

                // Thick over the crown, feathering to a lip at the hairline, swept
                // up off the forehead so the hair has some volume to it.
                float edge = SkinBinders.smoothstep(limit, limit + 0.42f, ny);
                float thickness = lift * (0.03f + 0.97f * edge)
                        + Math.max(0f, nz) * 0.0012f * edge
                        + Math.max(0f, ny) * 0.0009f * edge;
                // Offset along the head's own normal, off the head's own surface, so
                // the cap cannot drift away from the skull where the skull is not a
                // plain ellipsoid.
                headPoint(nx, ny, nz, point);
                headNormal(nx, ny, nz, normal);
                grid[i][j] = b.vertex(
                        cx + point[0] + normal[0] * thickness,
                        cy + point[1] + normal[1] * thickness,
                        cz + point[2] + normal[2] * thickness,
                        normal[0], normal[1], normal[2],
                        (float) (theta * HEAD_WIDTH), (float) (phi * HEAD_HEIGHT));
            }
        }
        // Rows that both collapsed onto the hairline make degenerate quads, which
        // quadOriented drops on its own.
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }

        if (style == 1) {
            // Shoulder-length fall behind the ears.
            for (int side = -1; side <= 1; side += 2) {
                b.push();
                b.translate(cx + side * 0.064f, cy - 0.100f, cz - 0.036f);
                b.rotateZ(side * 0.09f);
                b.scale(1f, 1f, 0.72f);
                b.ellipsoid(0.038f, 0.122f, 0.056f, 9, 6);
                b.pop();
            }
            b.push();
            b.translate(cx, cy - 0.076f, cz - 0.080f);
            b.scale(1.35f, 1f, 0.55f);
            b.ellipsoid(0.052f, 0.096f, 0.054f, 11, 7);
            b.pop();
        }
        b.skin(null);
        mesh.computeTangents();
        return mesh;
    }

    // ------------------------------------------------------------------ shaping

    /**
     * Radius multiplier that turns a sphere into a head.
     *
     * <p>The nose, the lips and the chin are shaped <em>into this surface</em> rather
     * than stuck onto it afterwards. A nose assembled from three ellipsoids reads as
     * a clown nose however carefully the three are placed, because nothing joins it
     * to the brow above it; grown out of the skull it has a bridge, and the bridge is
     * what makes it a face.
     */
    private static float headRadius(float nx, float ny, float nz) {
        float r = 1f;
        float front = Math.max(0f, nz);
        float down = Math.max(0f, -ny);
        float side = Math.abs(nx);

        // Cranium. The back of the skull is flatter than a sphere and the temples
        // pull in above the ears, but the crown stays domed: pinch it and the head
        // becomes an egg, which is what a silhouette gives away first.
        r -= Math.max(0f, -nz) * 0.030f;
        // Flatten the very top. An ellipsoid taller than it is wide comes to a point
        // at the crown, and with hair on it the silhouette turns into a bullet.
        r -= SkinBinders.smoothstep(0.60f, 1.0f, ny) * 0.050f;
        r -= SkinBinders.smoothstep(0.16f, 0.50f, ny)
                * (1f - SkinBinders.smoothstep(0.60f, 0.95f, ny)) * side * 0.052f;

        // Jaw: narrowing under the cheekbones, square at the angle below the ear,
        // ending in a chin rather than in the small end of an egg.
        // Narrow the jaw across, barely at all down. Because r scales all three
        // axes at once, a jaw taper written as a plain radius cut also shortens the
        // chin, and the lower half of the face ends up too small for the features
        // that have to fit in it.
        float jaw = SkinBinders.smoothstep(0.12f, 0.88f, down);
        r -= jaw * 0.055f * (1f - front * 0.42f);
        r -= jaw * side * side * 0.145f;
        float jawCorner = SkinBinders.smoothstep(0.14f, 0.50f, down)
                * (1f - SkinBinders.smoothstep(0.50f, 0.82f, down))
                * SkinBinders.smoothstep(0.26f, 0.70f, side);
        r += jawCorner * 0.055f;
        float chin = SkinBinders.smoothstep(0.66f, 0.86f, down)
                * (1f - SkinBinders.smoothstep(0.96f, 1.05f, down))
                * (1f - SkinBinders.smoothstep(0.16f, 0.42f, side));
        r += chin * front * 0.085f;

        // Brow ridge, eye sockets set back under it, and cheekbones.
        float brow = SkinBinders.smoothstep(0.10f, 0.30f, ny)
                * (1f - SkinBinders.smoothstep(0.30f, 0.62f, ny));
        r += brow * front * 0.048f;
        float socket = SkinBinders.smoothstep(-0.16f, 0.02f, ny)
                * (1f - SkinBinders.smoothstep(0.02f, 0.22f, ny))
                * SkinBinders.smoothstep(0.16f, 0.50f, side)
                * (1f - SkinBinders.smoothstep(0.58f, 0.92f, side));
        r -= socket * front * 0.055f;
        r += SkinBinders.smoothstep(0.04f, 0.34f, down)
                * (1f - SkinBinders.smoothstep(0.34f, 0.72f, down))
                * side * front * 0.085f;

        // Nose: a bridge from between the brows that swells to a tip and stops.
        float faceFront = SkinBinders.smoothstep(0.50f, 0.86f, front);
        // Runs from the nasion, just under the brow, down to the base of the nose;
        // narrow along the bridge and flaring into wings at the tip.
        float noseSpan = SkinBinders.smoothstep(-0.60f, -0.50f, ny)
                * (1f - SkinBinders.smoothstep(0.02f, 0.20f, ny));
        float tipness = 1f - SkinBinders.smoothstep(-0.46f, -0.05f, ny);
        float noseHalf = 0.13f + tipness * 0.20f;
        float noseWidth = 1f - SkinBinders.smoothstep(noseHalf, noseHalf + 0.13f, side);
        r += noseSpan * faceFront * noseWidth * (0.045f + 0.140f * tipness);

        // Ear: a raised patch on the side of the skull, its top level with the eyes
        // and its lobe with the base of the nose. As separate geometry it is a smooth
        // blob stuck on the head — worse than nothing at the distance this is seen
        // from, and it costs triangles to be worse.
        float earBand = SkinBinders.smoothstep(-0.46f, -0.24f, ny)
                * (1f - SkinBinders.smoothstep(-0.06f, 0.16f, ny))
                * SkinBinders.smoothstep(-0.48f, -0.26f, nz)
                * (1f - SkinBinders.smoothstep(-0.12f, 0.10f, nz));
        r += earBand * SkinBinders.smoothstep(0.62f, 0.86f, side) * 0.038f;

        // Lips, as a swell of the surface. The tinted patches on top of them are
        // only colour; the shape has to be here or they sit on the face like paint.
        float lips = SkinBinders.smoothstep(-0.80f, -0.70f, ny)
                * (1f - SkinBinders.smoothstep(-0.56f, -0.44f, ny))
                * (1f - SkinBinders.smoothstep(0.20f, 0.46f, side));
        r += lips * faceFront * 0.030f;

        return r;
    }

    /**
     * Height on the unit head, above which hair grows, for a given direction round
     * the skull.
     *
     * <p>A constant height cuts a straight fringe across the forehead — a bowl cut.
     * A real hairline dips slightly on the centreline, climbs again at the temples,
     * then falls away past the ears to the nape.
     */
    private static float hairline(float hx, float hz, int style) {
        // hx, hz are the horizontal direction round the skull, so the hairline is a
        // function of bearing alone: make it depend on the ring height too and it
        // becomes circular, since the ring height is what it is deciding.
        float front = Math.max(0f, hz);
        float back = Math.max(0f, -hz);
        float side = Math.abs(hx);
        float limit = 0.14f + 0.36f * front - 0.50f * back;
        limit += 0.14f * front * side;
        limit -= 0.05f * SkinBinders.smoothstep(0.86f, 1f, front);
        // A little scallop, so the edge is a hairline rather than a drawn line. The
        // frequency has to be well above the mesh's, or it becomes one long diagonal
        // sweep instead of an irregularity.
        limit += 0.016f * (float) Math.sin(Math.atan2(hz, hx) * 7.0);
        if (style == 1) limit -= 0.13f;
        if (style == 2) limit += 0.12f;
        return limit;
    }


    /**
     * A point on the shaped head, relative to the skull's centre, for a direction on
     * the unit sphere.
     *
     * <p>The jaw is sheared forward of the cranium and dropped at the front. Without
     * it the lowest point of the head is the pole directly under the ear, because
     * that is where the parameterisation puts it — the chin ends up <em>above</em> the
     * jaw's hinge, the underside of the head slopes the wrong way, and the face reads
     * as tipped back however level the head bone actually is. No amount of adjusting
     * the radius fixes that: a radius can only push the chin further out, and past a
     * point that is a caricature rather than a jaw.
     */
    private static void headPoint(float dx, float dy, float dz, float[] out) {
        float r = headRadius(dx, dy, dz);
        float down = Math.max(0f, -dy);
        float front = Math.max(0f, dz);
        float shear = SkinBinders.smoothstep(0.10f, 0.95f, down) * 0.022f;
        float drop = SkinBinders.smoothstep(0.30f, 0.95f, down)
                * SkinBinders.smoothstep(0.10f, 0.60f, front) * 0.011f;
        out[0] = dx * r * HEAD_WIDTH;
        out[1] = dy * r * HEAD_HEIGHT - drop;
        out[2] = dz * r * HEAD_DEPTH + shear;
    }

    /**
     * Outward normal of the <em>shaped</em> head at a direction, by central
     * differences across the surface.
     *
     * <p>Using the underlying sphere's direction as the normal instead — which is
     * the obvious thing to do, since it is already to hand — shades the head as
     * though none of the shaping existed. The nose is still there in the geometry;
     * it just catches exactly the same light as the cheek beside it, so it cannot be
     * seen from the front at all. Every bit of modelling in {@link #headRadius} is
     * invisible until this function exists.
     */
    private static void headNormal(float dx, float dy, float dz, float[] out) {
        double phi = Math.acos(Math.max(-1f, Math.min(1f, dy)));
        double theta = Math.atan2(dz, dx);
        final double e = 2e-3;
        float[] a = new float[3], c = new float[3], d = new float[3], f = new float[3];
        sphereDirection(phi, theta + e, tmpDir); headPoint(tmpDir[0], tmpDir[1], tmpDir[2], a);
        sphereDirection(phi, theta - e, tmpDir); headPoint(tmpDir[0], tmpDir[1], tmpDir[2], c);
        sphereDirection(Math.min(Math.PI, phi + e), theta, tmpDir);
        headPoint(tmpDir[0], tmpDir[1], tmpDir[2], d);
        sphereDirection(Math.max(0, phi - e), theta, tmpDir);
        headPoint(tmpDir[0], tmpDir[1], tmpDir[2], f);

        float tx = a[0] - c[0], ty = a[1] - c[1], tz = a[2] - c[2];
        float px = d[0] - f[0], py = d[1] - f[1], pz = d[2] - f[2];
        float nx = ty * pz - tz * py;
        float ny = tz * px - tx * pz;
        float nz = tx * py - ty * px;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-9f) {
            // At the poles the theta derivative vanishes; fall back to straight up.
            out[0] = 0f; out[1] = dy >= 0f ? 1f : -1f; out[2] = 0f;
            return;
        }
        nx /= length; ny /= length; nz /= length;
        if (nx * dx + ny * dy + nz * dz < 0f) { nx = -nx; ny = -ny; nz = -nz; }
        out[0] = nx; out[1] = ny; out[2] = nz;
    }

    private static final float[] tmpDir = new float[3];

    private static void sphereDirection(double phi, double theta, float[] out) {
        float ring = (float) Math.sin(phi);
        out[0] = (float) Math.cos(theta) * ring;
        out[1] = (float) Math.cos(phi);
        out[2] = (float) Math.sin(theta) * ring;
    }

    /** A constant radius array, for tubes of even thickness. */
    private static float[] fill(int count, float value) {
        float[] radii = new float[count];
        for (int i = 0; i < count; i++) radii[i] = value;
        return radii;
    }

    // -------------------------------------------------------------- lower body

    private void buildPelvisAndLegs() {
        b.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        b.mat(Materials.DENIM).occlusion(0.94f).noTint().uv(UV_DENIM);
        loft(PELVIS, 0f, TORSO_SEGMENTS, true, true);

        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int thigh = left ? Skeleton.THIGH_L : Skeleton.THIGH_R;
            int shin = left ? Skeleton.SHIN_L : Skeleton.SHIN_R;
            int foot = left ? Skeleton.FOOT_L : Skeleton.FOOT_R;
            float lx = Skeleton.bindX(thigh);
            float kneeY = Skeleton.bindY(shin);
            float ankleY = Skeleton.bindY(foot);
            float mirror = left ? 1f : -1f;

            // Thigh: heaviest just below the hip, tucking hard into the knee. It
            // starts up inside the pelvis so there is no seam at the hip.
            b.skin(new SkinBinders.Segment(Skeleton.HIPS, thigh,
                    lx, 0.995f, 0f, lx, kneeY, 0f, 0.10f, 0.46f));
            b.mat(Materials.DENIM).occlusion(0.93f).uv(UV_DENIM);
            float[] thighPath = {
                    lx - mirror * 0.004f, 1.000f, -0.008f,
                    lx,                   0.900f, -0.002f,
                    lx + mirror * 0.003f, 0.780f,  0.002f,
                    lx + mirror * 0.005f, 0.650f,  0.004f,
                    lx + mirror * 0.006f, 0.545f,  0.004f,
                    lx + mirror * 0.006f, kneeY + 0.012f, 0.002f,
            };
            b.tube(thighPath,
                    new float[]{0.090f, 0.092f, 0.082f, 0.068f, 0.057f, 0.054f},
                    new float[]{0.094f, 0.097f, 0.087f, 0.073f, 0.061f, 0.057f},
                    LIMB_SEGMENTS, false);

            // Knee, then the calf: its belly sits behind the bone and a third of the
            // way down, which is what separates a leg from a length of pipe.
            b.skin(new SkinBinders.Segment(thigh, shin,
                    lx, kneeY + 0.05f, 0f, lx, ankleY, 0f, 0.06f, 0.40f));
            float[] shinPath = {
                    lx + mirror * 0.006f, kneeY + 0.014f,  0.002f,
                    lx + mirror * 0.004f, kneeY - 0.028f,  0.006f,
                    lx + mirror * 0.003f, kneeY - 0.098f, -0.010f,
                    lx + mirror * 0.002f, kneeY - 0.188f, -0.006f,
                    lx + mirror * 0.001f, 0.200f,          0.002f,
                    lx,                   ankleY + 0.030f, 0.004f,
            };
            b.tube(shinPath,
                    new float[]{0.054f, 0.050f, 0.057f, 0.049f, 0.038f, 0.033f},
                    new float[]{0.057f, 0.053f, 0.063f, 0.053f, 0.040f, 0.034f},
                    LIMB_SEGMENTS, false);

            // Trouser hem breaking over the shoe.
            b.push();
            b.translate(lx, ankleY + 0.056f, 0.004f);
            b.cylinder(0.050f, 0.046f, 0.052f, LIMB_SEGMENTS, false, false);
            b.pop();

            buildShoe(foot, lx, ankleY, mirror);
        }
        b.skin(null);
    }

    /** Sole, upper, toe box and heel, rigid to the foot bone. */
    private void buildShoe(int footBone, float lx, float ankleY, float mirror) {
        b.skin(new SkinBinders.Rigid(footBone));
        b.uv(UV_LEATHER);

        b.push();
        b.translate(lx, ankleY - 0.040f, 0.040f);
        // Feet point very slightly outwards. Two shoes dead parallel read as a
        // mannequin on a stand however good everything above them is.
        b.rotateY(-mirror * 0.13f);

        b.mat(Materials.RUBBER_MAT).occlusion(0.78f).tint(0.74f, 0.74f, 0.76f);
        b.push();
        b.translate(0f, -0.014f, 0.006f);
        b.roundedBox(0.092f, 0.022f, 0.262f, 0.010f, 1);
        b.pop();

        b.mat(Materials.LEATHER).occlusion(0.90f).tint(0.32f, 0.28f, 0.26f);
        b.push();
        b.translate(0f, 0.038f, 0.008f);
        b.roundedBox(0.090f, 0.074f, 0.212f, 0.032f, 2);
        b.pop();
        // Toe box, lifted slightly at the front the way a shoe's last is.
        b.push();
        b.translate(0f, 0.020f, 0.094f);
        b.rotateX(-0.12f);
        b.scale(1f, 0.76f, 1f);
        b.ellipsoid(0.044f, 0.044f, 0.054f, 10, 6);
        b.pop();
        // Ankle opening, giving the trouser hem something to break over.
        b.push();
        b.translate(0f, 0.080f, -0.032f);
        b.roundedBox(0.076f, 0.048f, 0.086f, 0.022f, 1);
        b.pop();
        b.pop();
        b.noTint();
    }

    // -------------------------------------------------------------- upper body

    private void buildTorso() {
        b.skin(new SkinBinders.HeightChain(
                new int[]{Skeleton.HIPS, Skeleton.SPINE, Skeleton.CHEST},
                new float[]{0.960f, 1.108f, 1.300f}));

        // The shirt is the only torso layer: a skin body underneath would never be
        // seen through it, and the neck fills the collar opening on its own.
        b.mat(Materials.SHIRT).occlusion(0.96f).noTint().uv(UV_FABRIC);
        loft(TORSO, SHIRT, TORSO_SEGMENTS, true, true, 0.958f);

        // Hem lip, so the shirt ends in an edge rather than a cut.
        b.occlusion(0.88f);
        b.push();
        b.translate(0f, 0.966f, -0.010f);
        b.scale(1f, 1f, 0.72f);
        b.cylinder(0.172f, 0.178f, 0.034f, TORSO_SEGMENTS, false, false);
        b.pop();

        // Waistband and belt.
        b.skin(new SkinBinders.Rigid(Skeleton.HIPS));
        b.mat(Materials.LEATHER).occlusion(0.80f).tint(0.24f, 0.19f, 0.16f).uv(UV_LEATHER);
        b.push();
        b.translate(0f, 1.000f, -0.008f);
        b.scale(1f, 1f, 0.66f);
        b.cylinder(0.154f, 0.152f, 0.040f, 16, false, false);
        b.pop();
        b.noTint();
        b.skin(null);
    }

    /**
     * The trapezius: the shoulder line from one acromion, over the base of the neck,
     * to the other.
     *
     * <p>This is the piece that gives the figure a neck. Without it the torso loft
     * has to run all the way to collar height to close the body off, which puts a
     * flat shelf at 1.50 m with the head sitting on it and no neck in between. Swept
     * as its own form the shoulders slope, the ribcage can stop at the ribs, and the
     * neck rises out of the middle.
     */
    private void buildShoulderYoke() {
        b.skin(new SkinBinders.Rigid(Skeleton.CHEST));
        b.mat(Materials.SHIRT).occlusion(0.92f).noTint().uv(UV_FABRIC);

        float[] path = {
                -0.186f, 1.374f, -0.004f,
                -0.170f, 1.392f, -0.006f,
                -0.112f, 1.418f, -0.012f,
                -0.048f, 1.434f, -0.016f,
                 0.000f, 1.437f, -0.016f,
                 0.048f, 1.434f, -0.016f,
                 0.112f, 1.418f, -0.012f,
                 0.170f, 1.392f, -0.006f,
                 0.186f, 1.374f, -0.004f,
        };
        // The frame runs along +X, so the first radius is the vertical half-extent
        // and the second the depth: shoulders are far deeper than they are tall. The
        // ends shrink rather than stopping square, so the trapezius rolls into the
        // deltoid instead of meeting it at a corner.
        b.tube(path,
                new float[]{0.038f, 0.048f, 0.055f, 0.060f, 0.061f, 0.060f, 0.055f, 0.048f, 0.038f},
                new float[]{0.052f, 0.066f, 0.078f, 0.090f, 0.092f, 0.090f, 0.078f, 0.066f, 0.052f},
                14, true);
        b.skin(null);
    }

    private void buildArms() {
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int upper = left ? Skeleton.UPPERARM_L : Skeleton.UPPERARM_R;
            int fore = left ? Skeleton.FOREARM_L : Skeleton.FOREARM_R;
            int hand = left ? Skeleton.HAND_L : Skeleton.HAND_R;
            float sx = Skeleton.bindX(upper);
            float shoulderY = Skeleton.bindY(upper);
            float elbowY = Skeleton.bindY(fore);
            float wristY = Skeleton.bindY(hand);
            float mirror = left ? 1f : -1f;

            // Deltoid: the arm grows out of the torso as a swept tube that starts
            // inside it. A separate deltoid sphere always ends up proud of the
            // shoulder line, which is what reads as a puffed sleeve — and it has to
            // be there at all only because an open tube end would show a hole.
            b.skin(new SkinBinders.Segment(Skeleton.CHEST, upper,
                    sx * 0.35f, shoulderY + 0.05f, 0f, sx * 1.02f, shoulderY - 0.07f, 0f,
                    0.22f, 0.96f));
            b.mat(Materials.SHIRT).occlusion(0.92f).noTint().uv(UV_FABRIC);
            // The path turns downward before it ends, so the last rings lie level
            // and the arm is widest where a deltoid is. Ending while still heading
            // outwards leaves the end cap standing on edge, and that flat facet is
            // the square corner a silhouette picks out first.
            float[] deltoid = {
                    sx * 0.34f, shoulderY + 0.018f, -0.008f,
                    sx * 0.62f, shoulderY + 0.010f, -0.004f,
                    sx * 0.88f, shoulderY - 0.018f,  0.000f,
                    sx * 1.00f, shoulderY - 0.060f,  0.001f,
                    sx * 1.02f, shoulderY - 0.105f,  0.002f,
            };
            b.tube(deltoid,
                    new float[]{0.062f, 0.068f, 0.064f, 0.056f, 0.051f},
                    new float[]{0.070f, 0.074f, 0.068f, 0.059f, 0.054f},
                    LIMB_SEGMENTS, false);

            // Upper arm down to the elbow: biceps belly, narrowing hard at the joint.
            b.skin(new SkinBinders.Segment(upper, fore,
                    sx, shoulderY, 0f, sx, elbowY, 0f, 0.64f, 0.98f));
            float[] upperPath = {
                    sx * 1.02f,           shoulderY - 0.105f, 0.002f,
                    sx + mirror * 0.006f, 1.248f,             0.003f,
                    sx + mirror * 0.010f, 1.180f,             0.004f,
                    sx + mirror * 0.014f, elbowY + 0.018f,    0.005f,
            };
            float[] upperN = {0.051f, 0.048f, 0.044f, 0.040f};
            float[] upperB = {0.054f, 0.051f, 0.046f, 0.043f};
            b.mat(Materials.SKIN).occlusion(0.95f).uv(UV_SKIN);
            b.tube(upperPath, upperN, upperB, LIMB_SEGMENTS, false);

            // Short sleeve over the top two thirds of it, ending in a hem lip.
            b.mat(Materials.SHIRT).occlusion(0.93f).uv(UV_FABRIC);
            float[] sleeve = {
                    sx * 1.00f,           shoulderY - 0.060f, 0.001f,
                    sx * 1.02f,           shoulderY - 0.105f, 0.002f,
                    sx + mirror * 0.006f, 1.248f,             0.003f,
            };
            b.tube(sleeve,
                    new float[]{0.058f, 0.053f, 0.051f},
                    new float[]{0.061f, 0.056f, 0.054f},
                    LIMB_SEGMENTS, false);
            b.push();
            b.translate(sx + mirror * 0.006f, 1.248f, 0.003f);
            b.cylinder(0.054f, 0.050f, 0.022f, LIMB_SEGMENTS, false, false);
            b.pop();

            // Forearm, bare: full just below the elbow where the muscle bellies sit,
            // then down to a wrist barely half that thickness.
            b.skin(new SkinBinders.Segment(fore, hand,
                    sx, elbowY, 0f, sx, wristY, 0f, 0.74f, 1f));
            float[] forePath = {
                    sx + mirror * 0.014f, elbowY + 0.016f, 0.005f,
                    sx + mirror * 0.016f, 1.060f,          0.009f,
                    sx + mirror * 0.020f, 0.980f,          0.012f,
                    sx + mirror * 0.024f, 0.900f,          0.013f,
                    sx + mirror * 0.028f, wristY + 0.012f, 0.014f,
            };
            b.mat(Materials.SKIN).occlusion(0.96f).uv(UV_SKIN);
            b.tube(forePath,
                    new float[]{0.041f, 0.045f, 0.040f, 0.033f, 0.029f},
                    new float[]{0.043f, 0.042f, 0.037f, 0.030f, 0.026f},
                    LIMB_SEGMENTS, false);
        }
        b.skin(null);
    }

    /**
     * A relaxed hand: a tapered palm, four fingers curling towards it, and a thumb
     * on the forward edge.
     *
     * <p>With the arms at rest the palms face the thighs and the thumbs point
     * forward, so the fingers curl inward along −x, not forward along +z. Splaying
     * them straight down instead gives five parallel sausages, which is what a hand
     * looks like when it was built in the wrong frame.
     */
    private void buildHands() {
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int hand = left ? Skeleton.HAND_L : Skeleton.HAND_R;
            float hx = Skeleton.bindX(hand);
            float hy = Skeleton.bindY(hand);
            float hz = Skeleton.bindZ(hand);
            float mirror = left ? 1f : -1f;

            b.skin(new SkinBinders.Rigid(hand));
            b.mat(Materials.SKIN).occlusion(0.94f).noTint().uv(UV_SKIN);

            // Palm: narrow at the wrist, spreading to the knuckles.
            // The first ring has to match the forearm's last one exactly. Off by a
            // centimetre and the silhouette shows a notch at the wrist.
            float wx = Skeleton.bindX(left ? Skeleton.UPPERARM_L : Skeleton.UPPERARM_R)
                    + mirror * 0.028f;
            float[] palm = {
                    wx,                   hy + 0.012f, hz,
                    wx + mirror * 0.004f, hy - 0.026f, hz + 0.002f,
                    wx + mirror * 0.004f, hy - 0.068f, hz + 0.003f,
                    wx + mirror * 0.002f, hy - 0.094f, hz + 0.002f,
            };
            b.tube(palm,
                    new float[]{0.029f, 0.031f, 0.029f, 0.026f},
                    new float[]{0.026f, 0.038f, 0.042f, 0.040f},
                    12, true);

            final float[] length = {0.073f, 0.080f, 0.075f, 0.060f};
            final float[] radius = {0.0094f, 0.0098f, 0.0092f, 0.0082f};
            for (int f = 0; f < 4; f++) {
                // Index finger forward, little finger back, across the knuckle line.
                // They converge slightly as they run out, because a relaxed hand is a
                // loose curl. Four parallel fingers with air between them is a rake.
                float fz = hz + 0.026f - f * 0.0180f;
                float tipZ = hz + 0.018f - f * 0.0130f;
                float curl = 0.62f + f * 0.06f;
                float bx = wx + mirror * 0.002f;
                float by = hy - 0.094f;
                float len = length[f];
                float[] path = {
                        bx,                                 by,                 fz,
                        bx - mirror * len * 0.09f * curl,   by - len * 0.42f,
                                MathUtil.lerp(fz, tipZ, 0.40f),
                        bx - mirror * len * 0.30f * curl,   by - len * 0.76f,
                                MathUtil.lerp(fz, tipZ, 0.76f),
                        bx - mirror * len * 0.58f * curl,   by - len * 0.94f, tipZ,
                };
                float r = radius[f];
                b.tube(path, new float[]{r, r * 0.96f, r * 0.88f, r * 0.74f}, 6, true);
            }

            // Thumb, on the forward edge and angled across the palm.
            float[] thumb = {
                    wx + mirror * 0.004f, hy - 0.022f, hz + 0.030f,
                    wx - mirror * 0.002f, hy - 0.046f, hz + 0.044f,
                    wx - mirror * 0.012f, hy - 0.066f, hz + 0.052f,
            };
            b.tube(thumb, new float[]{0.0138f, 0.0124f, 0.0100f}, 6, true);
        }
        b.skin(null);
    }

    // ------------------------------------------------------------- head & face

    private void buildNeckAndHead() {
        float neckY = Skeleton.bindY(Skeleton.NECK);
        float headY = Skeleton.bindY(Skeleton.HEAD);
        float centreY = headY + HEAD_RISE;
        float centreZ = Skeleton.bindZ(Skeleton.HEAD);

        // The neck leans forward, as a real one does, and starts well down inside the
        // yoke so its bottom end is buried rather than showing as a ring.
        b.skin(new SkinBinders.Segment(Skeleton.CHEST, Skeleton.NECK,
                0f, neckY - 0.12f, 0f, 0f, neckY + 0.08f, 0f, 0.12f, 0.86f));
        // Dark at the top: the jaw overhangs the neck, and the shadow it casts there
        // is most of what separates a head from the column holding it up.
        b.mat(Materials.SKIN).occlusion(0.58f).noTint().uv(UV_SKIN);
        float[] neck = {
                0f, 1.356f, -0.030f,
                0f, 1.440f, -0.020f,
                0f, 1.520f, -0.008f,
                0f, 1.572f, -0.002f,
        };
        b.tube(neck,
                new float[]{0.060f, 0.053f, 0.045f, 0.041f},
                new float[]{0.068f, 0.060f, 0.050f, 0.045f},
                14, false);

        // Open collar, flaring away from the neck.
        b.skin(new SkinBinders.Rigid(Skeleton.CHEST));
        b.mat(Materials.SHIRT).occlusion(0.86f).uv(UV_FABRIC);
        float[] collar = {
                0f, 1.424f, -0.020f,
                0f, 1.452f, -0.016f,
                0f, 1.480f, -0.010f,
        };
        b.tube(collar,
                new float[]{0.068f, 0.074f, 0.084f},
                new float[]{0.076f, 0.082f, 0.092f},
                16, false);

        b.skin(new SkinBinders.Segment(Skeleton.NECK, Skeleton.HEAD,
                0f, neckY, 0f, 0f, headY, 0f, 0.05f, 0.60f));
        b.mat(Materials.SKIN).occlusion(1f).uv(UV_SKIN);
        buildHead(0f, centreY, centreZ);

        b.skin(new SkinBinders.Rigid(Skeleton.HEAD));
        buildFace(0f, centreY, centreZ);
        b.skin(null);
    }

    private void buildHead(float cx, float cy, float cz) {
        int[][] grid = new int[HEAD_RINGS + 1][HEAD_SEGMENTS + 1];
        float[] normal = new float[3];
        float[] point = new float[3];
        for (int i = 0; i <= HEAD_RINGS; i++) {
            double phi = Math.PI * i / HEAD_RINGS;
            float ny = (float) Math.cos(phi);
            float ring = (float) Math.sin(phi);
            for (int j = 0; j <= HEAD_SEGMENTS; j++) {
                double theta = Math.PI * 2 * j / HEAD_SEGMENTS;
                float nx = (float) Math.cos(theta) * ring;
                float nz = (float) Math.sin(theta) * ring;
                headPoint(nx, ny, nz, point);
                headNormal(nx, ny, nz, normal);
                grid[i][j] = b.vertex(
                        cx + point[0], cy + point[1], cz + point[2],
                        normal[0], normal[1], normal[2],
                        (float) (theta * HEAD_WIDTH), (float) (phi * HEAD_HEIGHT));
            }
        }
        for (int i = 0; i < HEAD_RINGS; i++) {
            for (int j = 0; j < HEAD_SEGMENTS; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
    }

    /**
     * Places a feature on the head's actual surface.
     *
     * <p>The head is a deformed ellipsoid, so its surface is nowhere near a fixed
     * depth: guessing one puts the eyes and nose <em>inside</em> the skull, where
     * they are simply invisible. This evaluates the same shaping function the head
     * mesh uses and returns the point and its outward normal.
     *
     * @return position xyz followed by normal xyz
     */
    private static float[] onFace(float cx, float cy, float cz,
                                  float dx, float dy, float dz, float outward) {
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length; dy /= length; dz /= length;

        float[] p = new float[3];
        float[] n = new float[3];
        headPoint(dx, dy, dz, p);
        headNormal(dx, dy, dz, n);

        return new float[]{
                cx + p[0] + n[0] * outward,
                cy + p[1] + n[1] * outward,
                cz + p[2] + n[2] * outward,
                n[0], n[1], n[2],
        };
    }

    /**
     * Eyes, brows, nostrils and lip colour.
     *
     * <p>The nose and the lips are gone from here: they are part of the head's own
     * surface now, shaped by {@link #headRadius}. What is left is the handful of
     * things that are a different <em>colour</em> from skin, plus the eyeballs, which
     * genuinely are separate objects sitting in sockets.
     *
     * <p>Everything hangs off one rule of thumb: the eye line sits halfway from chin
     * to crown, and the nose and mouth divide the half below it into thirds. Put the
     * features any higher and a long blank jaw is left underneath, which reads as a
     * face tipped back even when the head is level.
     */
    private void buildFace(float cx, float cy, float cz) {
        for (int side = -1; side <= 1; side += 2) {
            // Eyeball, sitting in the socket with the cornea just proud of the lids.
            float[] eye = onFace(cx, cy, cz, side * 0.42f, 0.02f, 0.90f, -0.0086f);
            b.mat(Materials.CERAMIC).occlusion(0.72f).tint(0.93f, 0.93f, 0.91f).uv(4f);
            b.push();
            b.translate(eye[0], eye[1], eye[2]);
            b.sphere(0.0128f, 9, 6);
            b.pop();

            // Iris and pupil sit where the eye is looking, which is forward. Putting
            // them on the socket's outward normal instead makes the character
            // wall-eyed, with each iris drifting to the outside of its own ball.
            final float gazeX = side * 0.07f, gazeY = 0.02f, gazeZ = 1f;
            float gazeLength = (float) Math.sqrt(gazeX * gazeX + gazeY * gazeY + gazeZ * gazeZ);
            float gx = gazeX / gazeLength, gy = gazeY / gazeLength, gz = gazeZ / gazeLength;

            b.mat(Materials.PLASTIC).occlusion(0.84f).tint(0.26f, 0.40f, 0.48f).uv(6f);
            b.push();
            b.translate(eye[0] + gx * 0.0118f, eye[1] + gy * 0.0118f, eye[2] + gz * 0.0118f);
            b.scale(1f, 1f, 0.40f);
            b.sphere(0.0091f, 9, 5);
            b.pop();
            b.mat(Materials.PLASTIC).tint(0.05f, 0.05f, 0.06f);
            b.push();
            b.translate(eye[0] + gx * 0.0132f, eye[1] + gy * 0.0132f, eye[2] + gz * 0.0132f);
            b.scale(1f, 1f, 0.34f);
            b.sphere(0.0034f, 6, 3);
            b.pop();

            // Lids. They have to clear the iris or the eye becomes a slit: the upper
            // one rests on the top of the iris, the lower one below it.
            b.mat(Materials.SKIN).occlusion(0.86f).noTint().uv(UV_SKIN);
            float[] upper = onFace(cx, cy, cz, side * 0.41f, 0.115f, 0.88f, -0.004f);
            b.push();
            b.translate(upper[0], upper[1], upper[2]);
            b.rotateY(side * -0.28f);
            b.rotateX(0.40f);
            b.scale(1.55f, 0.30f, 0.90f);
            b.ellipsoid(0.0150f, 0.0150f, 0.0150f, 8, 4);
            b.pop();
            float[] lower = onFace(cx, cy, cz, side * 0.41f, -0.078f, 0.89f, -0.005f);
            b.push();
            b.translate(lower[0], lower[1], lower[2]);
            b.rotateY(side * -0.28f);
            b.rotateX(-0.30f);
            b.scale(1.40f, 0.24f, 0.86f);
            b.ellipsoid(0.0145f, 0.0145f, 0.0145f, 8, 4);
            b.pop();

            // Brow: a strip of hair lying along the ridge, tilted the way a brow is.
            float[] brow = onFace(cx, cy, cz, side * 0.40f, 0.225f, 0.86f, 0.001f);
            b.mat(Materials.HAIR).occlusion(0.74f).noTint().uv(UV_HAIR);
            b.push();
            b.translate(brow[0], brow[1], brow[2]);
            b.rotateY(side * -0.30f);
            b.rotateZ(side * -0.10f);
            b.rotateX(-0.30f);
            b.scale(2.30f, 0.42f, 0.40f);
            b.ellipsoid(0.0135f, 0.0135f, 0.0135f, 7, 3);
            b.pop();

            // Nostril, dark and set into the underside of the nose.
            float[] nostril = onFace(cx, cy, cz, side * 0.15f, -0.56f, 0.84f, -0.004f);
            b.mat(Materials.SKIN).occlusion(0.62f).tint(0.42f, 0.28f, 0.24f).uv(UV_SKIN);
            b.push();
            b.translate(nostril[0], nostril[1], nostril[2]);
            b.scale(0.72f, 0.50f, 0.70f);
            b.ellipsoid(0.0072f, 0.0072f, 0.0072f, 5, 3);
            b.pop();
        }

        // Lip colour, laid on the swell the head's own surface already makes there,
        // with a darker seam between the two.
        float[] mouth = onFace(cx, cy, cz, 0f, -0.68f, 0.82f, -0.001f);
        b.push();
        b.translate(mouth[0], mouth[1], mouth[2]);
        b.rotateX(-0.14f);
        b.mat(Materials.SKIN).occlusion(0.86f).tint(0.88f, 0.64f, 0.58f).uv(UV_SKIN);
        b.push();
        b.translate(0f, 0.0052f, 0.0004f);
        b.scale(2.50f, 0.32f, 0.26f);
        b.ellipsoid(0.0122f, 0.0122f, 0.0122f, 8, 4);
        b.pop();
        b.push();
        b.translate(0f, -0.0050f, 0.0008f);
        b.scale(2.30f, 0.38f, 0.30f);
        b.ellipsoid(0.0122f, 0.0122f, 0.0122f, 8, 4);
        b.pop();
        b.mat(Materials.SKIN).tint(0.46f, 0.29f, 0.27f);
        b.push();
        b.translate(0f, 0.0001f, 0.0016f);
        b.scale(2.42f, 0.070f, 0.16f);
        b.ellipsoid(0.0122f, 0.0122f, 0.0122f, 8, 3);
        b.pop();
        b.pop();
        b.noTint();
    }

    // ---------------------------------------------------------------- lofting

    private void loft(float[][] sections, float inflate, int segments,
                      boolean capBottom, boolean capTop) {
        loft(sections, inflate, segments, capBottom, capTop, -1f);
    }

    /**
     * Lofts a closed shell through the given cross-sections.
     *
     * @param floorY sections below this height are lifted to it, which is how the
     *               shirt gets a hem at the hip instead of running to the crotch
     */
    private void loft(float[][] sections, float inflate, int segments,
                      boolean capBottom, boolean capTop, float floorY) {
        int rows = sections.length;
        int[][] grid = new int[rows][segments + 1];
        float[][] used = new float[rows][];

        for (int i = 0; i < rows; i++) {
            float y = Math.max(sections[i][0], floorY);
            float halfWidth = sections[i][1] + inflate;
            float halfDepth = sections[i][2] + inflate;
            float centerZ = sections[i][3];
            used[i] = new float[]{y, halfWidth, halfDepth, centerZ};

            for (int j = 0; j <= segments; j++) {
                double theta = Math.PI * 2 * j / segments;
                float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
                float px = c * halfWidth;
                float pz = s * halfDepth + centerZ;
                float nx = c / halfWidth, nz = s / halfDepth;
                float ny = 0f;
                if (i > 0 && i < rows - 1) {
                    float dr = sections[i + 1][1] - sections[i - 1][1];
                    float dy = sections[i + 1][0] - sections[i - 1][0];
                    ny = dy > 1e-5f ? -dr / dy * 0.55f : 0f;
                }
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1e-6f) length = 1f;
                grid[i][j] = b.vertex(px, y, pz, nx / length, ny / length, nz / length,
                        (float) (theta * (halfWidth + halfDepth) * 0.5), y);
            }
        }
        for (int i = 0; i < rows - 1; i++) {
            // Skip rows collapsed onto the floor height by the hem.
            if (Math.abs(used[i][0] - used[i + 1][0]) < 1e-5f) continue;
            for (int j = 0; j < segments; j++) {
                b.quadOriented(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]);
            }
        }
        if (capBottom) cap(firstVisible(used), -1f, segments);
        if (capTop) cap(used[rows - 1], 1f, segments);
    }

    private static float[] firstVisible(float[][] used) {
        for (int i = 0; i < used.length - 1; i++) {
            if (Math.abs(used[i][0] - used[i + 1][0]) > 1e-5f) return used[i];
        }
        return used[0];
    }

    /** Closes a loft end with its own fan, carrying the cap's normal. */
    private void cap(float[] section, float facing, int segments) {
        float y = section[0], halfWidth = section[1], halfDepth = section[2];
        float centerZ = section[3];
        int centre = b.vertex(0f, y, centerZ, 0f, facing, 0f, 0f, 0f);
        int prev = -1;
        for (int j = 0; j <= segments; j++) {
            double theta = Math.PI * 2 * j / segments;
            float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
            int v = b.vertex(c * halfWidth, y, s * halfDepth + centerZ, 0f, facing, 0f,
                    c * halfWidth, s * halfDepth);
            if (prev >= 0) b.triangleOriented(centre, prev, v);
            prev = v;
        }
    }
}
