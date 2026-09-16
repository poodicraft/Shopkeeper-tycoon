package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES30;

/** An off-screen render target: one colour attachment, with an optional depth buffer. */
public final class Framebuffer {

    private int fbo = 0;
    private int colorTexture = 0;
    private int depthTexture = 0;
    private int width, height;

    private final boolean wantsDepth;
    /** True for a shadow map: depth only, no colour attachment. */
    private final boolean depthOnly;

    public Framebuffer(boolean wantsDepth, boolean depthOnly) {
        this.wantsDepth = wantsDepth || depthOnly;
        this.depthOnly = depthOnly;
    }

    public int width() { return width; }

    public int height() { return height; }

    public int colorTexture() { return colorTexture; }

    public int depthTexture() { return depthTexture; }

    /** (Re)allocates at the given size. Safe to call every time the surface changes. */
    public void resize(int newWidth, int newHeight) {
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);
        if (fbo != 0 && newWidth == width && newHeight == height) return;
        dispose();
        width = newWidth;
        height = newHeight;

        int[] ids = new int[1];
        GLES30.glGenFramebuffers(1, ids, 0);
        fbo = ids[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);

        if (!depthOnly) {
            GLES30.glGenTextures(1, ids, 0);
            colorTexture = ids[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture);
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, width, height);
            setClampLinear();
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, colorTexture, 0);
        }

        if (wantsDepth) {
            GLES30.glGenTextures(1, ids, 0);
            depthTexture = ids[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture);
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_DEPTH_COMPONENT24,
                    width, height);
            // Depth formats are not texture-filterable in ES 3.0 unless a comparison
            // sampler is used. Asking for LINEAR here makes the texture incomplete,
            // and every shadow lookup silently returns zero - the whole shop in
            // shadow, with no error to point at. The shader does its own PCF across
            // neighbouring texels instead.
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE);
            // Sampled as a plain texture and compared by hand in the shader.
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE,
                    GLES30.GL_NONE);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                    GLES30.GL_TEXTURE_2D, depthTexture, 0);
        }

        if (depthOnly) {
            GLES30.glDrawBuffers(1, new int[]{GLES30.GL_NONE}, 0);
            GLES30.glReadBuffer(GLES30.GL_NONE);
        }

        int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer incomplete: 0x" + Integer.toHexString(status));
        }
    }

    private void setClampLinear() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
    }

    public void bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        GLES30.glViewport(0, 0, width, height);
    }

    public static void bindDefault(int screenWidth, int screenHeight) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenWidth, screenHeight);
    }

    public void bindColor(int unit) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture);
    }

    public void bindDepth(int unit) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture);
    }

    public void invalidate() {
        fbo = 0; colorTexture = 0; depthTexture = 0;
        width = 0; height = 0;
    }

    public void dispose() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        if (colorTexture != 0) GLES30.glDeleteTextures(1, new int[]{colorTexture}, 0);
        if (depthTexture != 0) GLES30.glDeleteTextures(1, new int[]{depthTexture}, 0);
        fbo = 0; colorTexture = 0; depthTexture = 0;
        width = 0; height = 0;
    }
}
