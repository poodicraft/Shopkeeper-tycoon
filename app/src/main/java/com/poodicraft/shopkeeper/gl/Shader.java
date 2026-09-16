package com.poodicraft.shopkeeper.gl;

import android.opengl.GLES30;

import java.util.HashMap;

/** Compiles and owns a GLSL ES 3.00 program, caching uniform locations by name. */
public final class Shader {

    private final int program;
    private final HashMap<String, Integer> uniforms = new HashMap<String, Integer>();
    private final String name;

    public Shader(String name, String vertexSource, String fragmentSource) {
        this.name = name;
        int vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vs);
        GLES30.glAttachShader(program, fs);
        GLES30.glLinkProgram(program);

        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new RuntimeException("Link failed for " + name + ": " + log);
        }
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
    }

    private int compile(int type, String source) {
        int id = GLES30.glCreateShader(type);
        GLES30.glShaderSource(id, source);
        GLES30.glCompileShader(id);
        int[] status = new int[1];
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(id);
            GLES30.glDeleteShader(id);
            throw new RuntimeException("Compile failed for " + name
                    + " (" + (type == GLES30.GL_VERTEX_SHADER ? "vertex" : "fragment") + "): " + log);
        }
        return id;
    }

    public void use() { GLES30.glUseProgram(program); }

    public int location(String uniform) {
        Integer cached = uniforms.get(uniform);
        if (cached != null) return cached.intValue();
        int loc = GLES30.glGetUniformLocation(program, uniform);
        uniforms.put(uniform, Integer.valueOf(loc));
        return loc;
    }

    public void setMat4(String uniform, float[] value) {
        GLES30.glUniformMatrix4fv(location(uniform), 1, false, value, 0);
    }

    public void setMat4Array(String uniform, float[] value, int count) {
        GLES30.glUniformMatrix4fv(location(uniform), count, false, value, 0);
    }

    public void setVec2(String uniform, float x, float y) {
        GLES30.glUniform2f(location(uniform), x, y);
    }

    public void setVec3(String uniform, float x, float y, float z) {
        GLES30.glUniform3f(location(uniform), x, y, z);
    }

    public void setVec3Array(String uniform, float[] value, int count) {
        GLES30.glUniform3fv(location(uniform), count, value, 0);
    }

    public void setVec4Array(String uniform, float[] value, int count) {
        GLES30.glUniform4fv(location(uniform), count, value, 0);
    }

    public void setFloat(String uniform, float value) {
        GLES30.glUniform1f(location(uniform), value);
    }

    public void setInt(String uniform, int value) {
        GLES30.glUniform1i(location(uniform), value);
    }

    public void dispose() { GLES30.glDeleteProgram(program); }
}
