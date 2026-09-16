package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A {@link MeshData} uploaded to the GPU as a vertex array object.
 *
 * <p>The CPU-side data is kept so the mesh can be rebuilt after the EGL context is
 * lost, and so dynamic meshes (shelf stock, carried goods) can be re-uploaded when
 * the world changes.
 */
public final class GpuMesh {

    public static final int ATTRIB_POSITION = 0;
    public static final int ATTRIB_NORMAL = 1;
    public static final int ATTRIB_TANGENT = 2;
    public static final int ATTRIB_UV = 3;
    public static final int ATTRIB_MATERIAL = 4;
    public static final int ATTRIB_AO = 5;
    public static final int ATTRIB_TINT = 6;
    public static final int ATTRIB_BONE_INDEX = 7;
    public static final int ATTRIB_BONE_WEIGHT = 8;

    private final boolean dynamic;
    private MeshData data;

    private int vao = 0, vbo = 0, ibo = 0;
    private int uploadedIndexCount = 0;
    private boolean dirty = true;

    public GpuMesh(MeshData data) { this(data, false); }

    public GpuMesh(MeshData data, boolean dynamic) {
        this.data = data;
        this.dynamic = dynamic;
    }

    public MeshData data() { return data; }

    public int indexCount() { return uploadedIndexCount; }

    /** Replaces the geometry; the next draw re-uploads it. */
    public void update(MeshData replacement) {
        this.data = replacement;
        this.dirty = true;
    }

    public void markDirty() { dirty = true; }

    /** Forgets GPU names after a context loss, without discarding the geometry. */
    public void invalidate() {
        vao = 0; vbo = 0; ibo = 0;
        dirty = true;
    }

    private void upload() {
        if (data == null || data.indexCount == 0) {
            uploadedIndexCount = 0;
            dirty = false;
            return;
        }
        int stride = data.floatsPerVertex * 4;

        if (vao == 0) {
            int[] ids = new int[1];
            GLES30.glGenVertexArrays(1, ids, 0);
            vao = ids[0];
            GLES30.glGenBuffers(1, ids, 0);
            vbo = ids[0];
            GLES30.glGenBuffers(1, ids, 0);
            ibo = ids[0];
        }

        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(data.vertexFloats * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(data.vertices, 0, data.vertexFloats).position(0);
        IntBuffer indexBuffer = ByteBuffer.allocateDirect(data.indexCount * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        indexBuffer.put(data.indices, 0, data.indexCount).position(0);

        int usage = dynamic ? GLES30.GL_DYNAMIC_DRAW : GLES30.GL_STATIC_DRAW;
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vertexFloats * 4, vertexBuffer, usage);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.indexCount * 4, indexBuffer, usage);

        attrib(ATTRIB_POSITION, 3, MeshData.OFFSET_POSITION, stride);
        attrib(ATTRIB_NORMAL, 3, MeshData.OFFSET_NORMAL, stride);
        attrib(ATTRIB_TANGENT, 3, MeshData.OFFSET_TANGENT, stride);
        attrib(ATTRIB_UV, 2, MeshData.OFFSET_UV, stride);
        attrib(ATTRIB_MATERIAL, 1, MeshData.OFFSET_MATERIAL, stride);
        attrib(ATTRIB_AO, 1, MeshData.OFFSET_AO, stride);
        attrib(ATTRIB_TINT, 3, MeshData.OFFSET_TINT, stride);
        if (data.skinned) {
            attrib(ATTRIB_BONE_INDEX, 4, MeshData.OFFSET_BONE_INDEX, stride);
            attrib(ATTRIB_BONE_WEIGHT, 4, MeshData.OFFSET_BONE_WEIGHT, stride);
        }

        GLES30.glBindVertexArray(0);
        uploadedIndexCount = data.indexCount;
        dirty = false;
    }

    private void attrib(int location, int size, int floatOffset, int stride) {
        GLES30.glEnableVertexAttribArray(location);
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, stride, floatOffset * 4);
    }

    public void draw() {
        if (dirty) upload();
        if (uploadedIndexCount == 0) return;
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, uploadedIndexCount, GLES30.GL_UNSIGNED_INT, 0);
    }

    public void dispose() {
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, new int[]{vao}, 0);
            GLES30.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
            vao = 0; vbo = 0; ibo = 0;
        }
    }
}
