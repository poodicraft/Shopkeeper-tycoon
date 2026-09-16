package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * A 2D texture array holding every material's albedo (or normal) in one object.
 *
 * <p>Because a material is a vertex attribute indexing into this array, the whole
 * shop - tiles, plaster, wood, metal, packaging - draws in one call rather than one
 * per texture.
 */
public final class TextureArray {

    private int handle = 0;
    private final int size;
    private final int layers;

    public TextureArray(int size, int layers) {
        this.size = size;
        this.layers = layers;
    }

    public int handle() { return handle; }

    /**
     * Uploads the pixel data. Must run on the GL thread.
     *
     * @param pixels one ARGB array per layer, each {@code size * size} long
     * @param srgb   true for colour data, which should be sampled through sRGB decode
     */
    public void upload(int[][] pixels, boolean srgb) {
        if (handle == 0) {
            int[] ids = new int[1];
            GLES30.glGenTextures(1, ids, 0);
            handle = ids[0];
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, handle);

        int levels = 1;
        int dimension = size;
        while (dimension > 1) { dimension >>= 1; levels++; }

        int internalFormat = srgb ? GLES30.GL_SRGB8_ALPHA8 : GLES30.GL_RGBA8;
        GLES30.glTexStorage3D(GLES30.GL_TEXTURE_2D_ARRAY, levels, internalFormat,
                size, size, layers);

        ByteBuffer buffer = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
        IntBuffer asInts = buffer.asIntBuffer();
        for (int layer = 0; layer < layers; layer++) {
            int[] source = layer < pixels.length ? pixels[layer] : null;
            asInts.position(0);
            if (source == null) {
                for (int i = 0; i < size * size; i++) asInts.put(0xFF808080);
            } else {
                // Android packs ARGB; GL_RGBA with UNSIGNED_BYTE wants RGBA in memory,
                // which on a little-endian device means swapping R and B.
                for (int i = 0; i < size * size; i++) {
                    int argb = source[i];
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    asInts.put((a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            buffer.position(0);
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer,
                    size, size, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer);
        }

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_REPEAT);

        // Sharpen grazing-angle detail, but only where the extension exists: querying
        // an unsupported pname raises GL_INVALID_ENUM and leaves a stale error behind.
        String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        if (extensions != null && extensions.contains("GL_EXT_texture_filter_anisotropic")) {
            final int MAX_ANISOTROPY = 0x84FF;
            final int TEXTURE_MAX_ANISOTROPY = 0x84FE;
            float[] maxAniso = new float[1];
            GLES30.glGetFloatv(MAX_ANISOTROPY, maxAniso, 0);
            if (maxAniso[0] > 1f) {
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D_ARRAY, TEXTURE_MAX_ANISOTROPY,
                        Math.min(4f, maxAniso[0]));
            }
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0);
    }

    public void bind(int unit) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, handle);
    }

    public void invalidate() { handle = 0; }

    public void dispose() {
        if (handle != 0) {
            GLES30.glDeleteTextures(1, new int[]{handle}, 0);
            handle = 0;
        }
    }
}
