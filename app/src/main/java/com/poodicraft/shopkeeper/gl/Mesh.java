package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A non-indexed triangle soup uploaded to a single VBO.
 *
 * <p>Vertices are interleaved as position(3), normal(3), colour(3). Skipping an
 * index buffer keeps the upload simple and sidesteps the 65535-vertex ceiling
 * that unsigned-short indices impose on GLES 2.0.
 */
public final class Mesh {
    public static final int FLOATS_PER_VERTEX = 9;
    private static final int STRIDE = FLOATS_PER_VERTEX * 4;

    private int vbo = 0;
    private final int vertexCount;
    /** Kept for the life of the mesh so it can be re-uploaded after a context loss. */
    private final FloatBuffer data;

    public Mesh(float[] interleaved, int floatCount) {
        this.vertexCount = floatCount / FLOATS_PER_VERTEX;
        ByteBuffer bb = ByteBuffer.allocateDirect(Math.max(4, floatCount * 4));
        bb.order(ByteOrder.nativeOrder());
        data = bb.asFloatBuffer();
        data.put(interleaved, 0, floatCount);
        data.position(0);
    }

    /** Uploads the vertex data. Must run on the GL thread. */
    public void upload() {
        if (vbo != 0 || vertexCount == 0) return;
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vbo = ids[0];
        data.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.capacity() * 4, data, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Forgets the GPU buffer without touching the vertex data.
     *
     * <p>Called when the EGL context is recreated: the old buffer name belongs to a
     * dead context, so the next {@link #upload()} must allocate a fresh one.
     */
    public void invalidate() {
        vbo = 0;
    }

    public int vertexCount() { return vertexCount; }

    /** Binds the buffer and points the three standard attributes at it. */
    public void bind(int posLoc, int normalLoc, int colorLoc) {
        if (vbo == 0) upload();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
        GLES20.glEnableVertexAttribArray(normalLoc);
        GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, STRIDE, 12);
        GLES20.glEnableVertexAttribArray(colorLoc);
        GLES20.glVertexAttribPointer(colorLoc, 3, GLES20.GL_FLOAT, false, STRIDE, 24);
    }

    public void draw() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
    }

    public void dispose() {
        if (vbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vbo}, 0);
            vbo = 0;
        }
    }
}
