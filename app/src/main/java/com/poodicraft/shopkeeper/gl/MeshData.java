package com.poodicraft.shopkeeper.gl;

/**
 * CPU-side indexed mesh.
 *
 * <p>Deliberately free of any OpenGL reference so geometry can be generated on a
 * worker thread during loading, and validated off-device in tests.
 *
 * <p>Vertex layout (floats): position 3, normal 3, tangent 3, uv 2, material 1,
 * ambient occlusion 1, tint 3. Skinned meshes append bone indices 4 and bone
 * weights 4. The tint multiplies the sampled albedo, which is how one baked world
 * mesh carries painted props in many colours without extra draw calls.
 */
public final class MeshData {

    public static final int STATIC_FLOATS = 16;
    public static final int SKINNED_FLOATS = 24;

    public static final int OFFSET_POSITION = 0;
    public static final int OFFSET_NORMAL = 3;
    public static final int OFFSET_TANGENT = 6;
    public static final int OFFSET_UV = 9;
    public static final int OFFSET_MATERIAL = 11;
    public static final int OFFSET_AO = 12;
    public static final int OFFSET_TINT = 13;
    public static final int OFFSET_BONE_INDEX = 16;
    public static final int OFFSET_BONE_WEIGHT = 20;

    public final int floatsPerVertex;
    public final boolean skinned;

    public float[] vertices;
    public int vertexFloats;
    public int[] indices;
    public int indexCount;

    public MeshData(boolean skinned) { this(skinned, 1024); }

    public MeshData(boolean skinned, int expectedVertices) {
        this.skinned = skinned;
        this.floatsPerVertex = skinned ? SKINNED_FLOATS : STATIC_FLOATS;
        vertices = new float[Math.max(64, expectedVertices) * floatsPerVertex];
        indices = new int[Math.max(64, expectedVertices) * 3];
    }

    public int vertexCount() { return vertexFloats / floatsPerVertex; }

    public int triangleCount() { return indexCount / 3; }

    public void clear() {
        vertexFloats = 0;
        indexCount = 0;
    }

    /** Appends a vertex and returns its index. Tangents are filled in later. */
    public int addVertex(float px, float py, float pz,
                         float nx, float ny, float nz,
                         float u, float v, float material, float ao,
                         float tintR, float tintG, float tintB) {
        ensureVertexSpace();
        int base = vertexFloats;
        vertices[base] = px; vertices[base + 1] = py; vertices[base + 2] = pz;
        vertices[base + 3] = nx; vertices[base + 4] = ny; vertices[base + 5] = nz;
        vertices[base + 6] = 0f; vertices[base + 7] = 0f; vertices[base + 8] = 0f;
        vertices[base + 9] = u; vertices[base + 10] = v;
        vertices[base + 11] = material;
        vertices[base + 12] = ao;
        vertices[base + 13] = tintR; vertices[base + 14] = tintG; vertices[base + 15] = tintB;
        if (skinned) {
            vertices[base + 16] = 0f; vertices[base + 17] = 0f;
            vertices[base + 18] = 0f; vertices[base + 19] = 0f;
            vertices[base + 20] = 1f; vertices[base + 21] = 0f;
            vertices[base + 22] = 0f; vertices[base + 23] = 0f;
        }
        vertexFloats += floatsPerVertex;
        return vertexFloats / floatsPerVertex - 1;
    }

    /** Overwrites the skin binding of an already-added vertex. */
    public void setSkin(int vertexIndex, int b0, int b1, float w0, float w1) {
        if (!skinned) return;
        int base = vertexIndex * floatsPerVertex;
        float total = w0 + w1;
        if (total < 1e-5f) { w0 = 1f; w1 = 0f; } else { w0 /= total; w1 /= total; }
        vertices[base + OFFSET_BONE_INDEX] = b0;
        vertices[base + OFFSET_BONE_INDEX + 1] = b1;
        vertices[base + OFFSET_BONE_INDEX + 2] = 0f;
        vertices[base + OFFSET_BONE_INDEX + 3] = 0f;
        vertices[base + OFFSET_BONE_WEIGHT] = w0;
        vertices[base + OFFSET_BONE_WEIGHT + 1] = w1;
        vertices[base + OFFSET_BONE_WEIGHT + 2] = 0f;
        vertices[base + OFFSET_BONE_WEIGHT + 3] = 0f;
    }

    public void addTriangle(int a, int b, int c) {
        if (indexCount + 3 > indices.length) {
            int[] grown = new int[Math.max(indices.length * 2, indexCount + 3)];
            System.arraycopy(indices, 0, grown, 0, indexCount);
            indices = grown;
        }
        indices[indexCount++] = a;
        indices[indexCount++] = b;
        indices[indexCount++] = c;
    }

    public void addQuad(int a, int b, int c, int d) {
        addTriangle(a, b, c);
        addTriangle(a, c, d);
    }

    public float get(int vertexIndex, int offset) {
        return vertices[vertexIndex * floatsPerVertex + offset];
    }

    public void set(int vertexIndex, int offset, float value) {
        vertices[vertexIndex * floatsPerVertex + offset] = value;
    }

    private void ensureVertexSpace() {
        if (vertexFloats + floatsPerVertex <= vertices.length) return;
        float[] grown = new float[Math.max(vertices.length * 2, vertexFloats + floatsPerVertex)];
        System.arraycopy(vertices, 0, grown, 0, vertexFloats);
        vertices = grown;
    }

    /**
     * Derives per-vertex tangents from the UV layout.
     *
     * <p>Normal mapping needs a consistent tangent basis; deriving it from the
     * triangles rather than hand-authoring it per primitive keeps every generator
     * honest. Tangents are accumulated per vertex and then orthonormalised against
     * the normal (Gram-Schmidt).
     */
    public void computeTangents() {
        int count = vertexCount();
        float[] accum = new float[count * 3];

        for (int i = 0; i < indexCount; i += 3) {
            int i0 = indices[i], i1 = indices[i + 1], i2 = indices[i + 2];
            int b0 = i0 * floatsPerVertex, b1 = i1 * floatsPerVertex, b2 = i2 * floatsPerVertex;

            float x0 = vertices[b0], y0 = vertices[b0 + 1], z0 = vertices[b0 + 2];
            float e1x = vertices[b1] - x0, e1y = vertices[b1 + 1] - y0, e1z = vertices[b1 + 2] - z0;
            float e2x = vertices[b2] - x0, e2y = vertices[b2 + 1] - y0, e2z = vertices[b2 + 2] - z0;

            float u0 = vertices[b0 + 9], v0 = vertices[b0 + 10];
            float du1 = vertices[b1 + 9] - u0, dv1 = vertices[b1 + 10] - v0;
            float du2 = vertices[b2 + 9] - u0, dv2 = vertices[b2 + 10] - v0;

            float det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1e-9f) continue;
            float r = 1f / det;
            float tx = (e1x * dv2 - e2x * dv1) * r;
            float ty = (e1y * dv2 - e2y * dv1) * r;
            float tz = (e1z * dv2 - e2z * dv1) * r;

            accum[i0 * 3] += tx; accum[i0 * 3 + 1] += ty; accum[i0 * 3 + 2] += tz;
            accum[i1 * 3] += tx; accum[i1 * 3 + 1] += ty; accum[i1 * 3 + 2] += tz;
            accum[i2 * 3] += tx; accum[i2 * 3 + 1] += ty; accum[i2 * 3 + 2] += tz;
        }

        for (int v = 0; v < count; v++) {
            int base = v * floatsPerVertex;
            float nx = vertices[base + 3], ny = vertices[base + 4], nz = vertices[base + 5];
            float tx = accum[v * 3], ty = accum[v * 3 + 1], tz = accum[v * 3 + 2];

            float dot = tx * nx + ty * ny + tz * nz;
            tx -= nx * dot; ty -= ny * dot; tz -= nz * dot;
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-6f) {
                // Degenerate UVs: any vector perpendicular to the normal will do.
                if (Math.abs(nx) < 0.9f) { tx = 1f; ty = 0f; tz = 0f; }
                else { tx = 0f; ty = 1f; tz = 0f; }
                dot = tx * nx + ty * ny + tz * nz;
                tx -= nx * dot; ty -= ny * dot; tz -= nz * dot;
                len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            }
            vertices[base + 6] = tx / len;
            vertices[base + 7] = ty / len;
            vertices[base + 8] = tz / len;
        }
    }

    /** Appends another mesh's geometry, offsetting its indices. */
    public void append(MeshData other) {
        int offset = vertexCount();
        int otherVerts = other.vertexCount();
        for (int v = 0; v < otherVerts; v++) {
            ensureVertexSpace();
            System.arraycopy(other.vertices, v * other.floatsPerVertex,
                    vertices, vertexFloats, Math.min(floatsPerVertex, other.floatsPerVertex));
            vertexFloats += floatsPerVertex;
        }
        for (int i = 0; i < other.indexCount; i++) {
            addIndex(other.indices[i] + offset);
        }
    }

    private void addIndex(int value) {
        if (indexCount + 1 > indices.length) {
            int[] grown = new int[Math.max(indices.length * 2, indexCount + 1)];
            System.arraycopy(indices, 0, grown, 0, indexCount);
            indices = grown;
        }
        indices[indexCount++] = value;
    }
}
