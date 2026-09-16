package com.poodicraft.shopkeeper.art;

/**
 * Tiling noise for the procedural textures.
 *
 * <p>Every function wraps on a lattice period, which is what lets a 256x256 texture
 * repeat across a floor without a visible seam.
 */
public final class Noise {

    private final int[] permutation = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        // Deterministic shuffle: the same seed must give the same texture every run.
        long state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        for (int i = 255; i > 0; i--) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((state >>> 33) % (i + 1));
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) permutation[i] = p[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6f - 15f) + 10f); }

    private float lattice(int x, int y, int period) {
        int px = ((x % period) + period) % period;
        int py = ((y % period) + period) % period;
        int h = permutation[(permutation[px & 255] + py) & 511];
        return (h & 255) / 255f;
    }

    /** Value noise in 0..1, tiling every {@code period} cells. */
    public float value(float x, float y, int period) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = fade(x - x0), fy = fade(y - y0);
        float a = lattice(x0, y0, period);
        float b = lattice(x0 + 1, y0, period);
        float c = lattice(x0, y0 + 1, period);
        float d = lattice(x0 + 1, y0 + 1, period);
        float top = a + (b - a) * fx;
        float bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    /** Sum of octaves; {@code period} is the period of the first octave. */
    public float fbm(float x, float y, int period, int octaves, float gain) {
        float sum = 0f, amplitude = 1f, total = 0f;
        int p = period;
        float fx = x, fy = y;
        for (int i = 0; i < octaves; i++) {
            sum += value(fx, fy, p) * amplitude;
            total += amplitude;
            amplitude *= gain;
            fx *= 2f; fy *= 2f; p *= 2;
        }
        return total > 0f ? sum / total : 0f;
    }

    /** Ridged noise, good for grain and fibres. */
    public float ridged(float x, float y, int period, int octaves) {
        float sum = 0f, amplitude = 1f, total = 0f;
        int p = period;
        float fx = x, fy = y;
        for (int i = 0; i < octaves; i++) {
            float n = Math.abs(value(fx, fy, p) * 2f - 1f);
            sum += (1f - n) * amplitude;
            total += amplitude;
            amplitude *= 0.5f;
            fx *= 2f; fy *= 2f; p *= 2;
        }
        return total > 0f ? sum / total : 0f;
    }

    /**
     * Distance to the nearest of a set of jittered cell points, tiling on the grid.
     * Used for stone speckle, leather grain and tile aggregate.
     */
    public float cellular(float x, float y, int period) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        float best = 4f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx, gy = cy + dy;
                float jitterX = lattice(gx, gy, period);
                float jitterY = lattice(gx + 71, gy + 31, period);
                float px = gx + jitterX;
                float py = gy + jitterY;
                float ddx = px - x, ddy = py - y;
                float dist = ddx * ddx + ddy * ddy;
                if (dist < best) best = dist;
            }
        }
        return (float) Math.sqrt(best);
    }
}
