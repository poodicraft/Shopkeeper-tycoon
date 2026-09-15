package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES20;
import android.util.Log;

import java.util.HashMap;

/** Compiles and owns a GLSL program, caching uniform/attribute locations by name. */
public final class ShaderProgram {
    private static final String TAG = "ShaderProgram";

    private final int programId;
    private final HashMap<String, Integer> uniforms = new HashMap<String, Integer>();
    private final HashMap<String, Integer> attributes = new HashMap<String, Integer>();

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vs);
        GLES20.glAttachShader(programId, fs);
        GLES20.glLinkProgram(programId);

        int[] status = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(programId);
            GLES20.glDeleteProgram(programId);
            throw new RuntimeException("Shader link failed: " + log);
        }
        // The program keeps its own copy of the compiled code once linked.
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = GLES20.glCreateShader(type);
        GLES20.glShaderSource(id, src);
        GLES20.glCompileShader(id);
        int[] status = new int[1];
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(id);
            GLES20.glDeleteShader(id);
            Log.e(TAG, "Compile failed: " + log);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return id;
    }

    public void use() { GLES20.glUseProgram(programId); }

    public int uniform(String name) {
        Integer cached = uniforms.get(name);
        if (cached != null) return cached.intValue();
        int loc = GLES20.glGetUniformLocation(programId, name);
        uniforms.put(name, Integer.valueOf(loc));
        return loc;
    }

    public int attribute(String name) {
        Integer cached = attributes.get(name);
        if (cached != null) return cached.intValue();
        int loc = GLES20.glGetAttribLocation(programId, name);
        attributes.put(name, Integer.valueOf(loc));
        return loc;
    }

    public void setMat4(String name, float[] m) {
        GLES20.glUniformMatrix4fv(uniform(name), 1, false, m, 0);
    }

    public void setVec3(String name, float x, float y, float z) {
        GLES20.glUniform3f(uniform(name), x, y, z);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        GLES20.glUniform4f(uniform(name), x, y, z, w);
    }

    public void setFloat(String name, float v) {
        GLES20.glUniform1f(uniform(name), v);
    }

    public void dispose() { GLES20.glDeleteProgram(programId); }
}
