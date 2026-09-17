package com.poodicraft.shopkeeper.gl;

/**
 * Every GLSL ES 3.00 source in the game.
 *
 * <p>Kept as text in one place so {@code tools/extract_shaders.py} can pull them out
 * and run them through a real compiler as part of the test suite, rather than
 * discovering a syntax error as a black screen on a device.
 */
public final class Shaders {
    private Shaders() { }

    private static final String VERSION = "#version 300 es\n";

    public static String sceneVertex(boolean skinned) {
        return VERSION + (skinned ? "#define SKINNED 1\n" : "") + SCENE_VERTEX;
    }

    public static String sceneFragment() { return VERSION + SCENE_FRAGMENT; }

    public static String depthVertex(boolean skinned) {
        return VERSION + (skinned ? "#define SKINNED 1\n" : "") + DEPTH_VERTEX;
    }

    public static String depthFragment() { return VERSION + DEPTH_FRAGMENT; }

    public static String fullscreenVertex() { return VERSION + FULLSCREEN_VERTEX; }

    public static String brightPassFragment() { return VERSION + BRIGHT_PASS_FRAGMENT; }

    public static String blurFragment() { return VERSION + BLUR_FRAGMENT; }

    public static String compositeFragment() { return VERSION + COMPOSITE_FRAGMENT; }

    // ------------------------------------------------------------ scene pass

    private static final String SCENE_VERTEX =
            "layout(location = 0) in vec3 aPos;\n" +
            "layout(location = 1) in vec3 aNormal;\n" +
            "layout(location = 2) in vec3 aTangent;\n" +
            "layout(location = 3) in vec2 aUV;\n" +
            "layout(location = 4) in float aMaterial;\n" +
            "layout(location = 5) in float aAO;\n" +
            "layout(location = 6) in vec3 aTint;\n" +
            "#ifdef SKINNED\n" +
            "layout(location = 7) in vec4 aBoneIndex;\n" +
            "layout(location = 8) in vec4 aBoneWeight;\n" +
            "uniform mat4 uBones[19];\n" +
            "#endif\n" +
            "uniform mat4 uViewProj;\n" +
            "uniform mat4 uModel;\n" +
            "uniform mat4 uLightViewProj;\n" +
            "out vec3 vWorld;\n" +
            "out vec3 vNormal;\n" +
            "out vec3 vTangent;\n" +
            "out vec2 vUV;\n" +
            "flat out int vMaterial;\n" +
            "out float vAO;\n" +
            "out vec3 vTint;\n" +
            "out vec4 vLightSpace;\n" +
            "void main() {\n" +
            "    vec4 local = vec4(aPos, 1.0);\n" +
            "    vec3 localNormal = aNormal;\n" +
            "    vec3 localTangent = aTangent;\n" +
            "#ifdef SKINNED\n" +
            "    mat4 skin = uBones[int(aBoneIndex.x)] * aBoneWeight.x\n" +
            "              + uBones[int(aBoneIndex.y)] * aBoneWeight.y;\n" +
            "    local = skin * local;\n" +
            "    mat3 skin3 = mat3(skin);\n" +
            "    localNormal = skin3 * aNormal;\n" +
            "    localTangent = skin3 * aTangent;\n" +
            "#endif\n" +
            "    vec4 world = uModel * local;\n" +
            "    vWorld = world.xyz;\n" +
            "    mat3 model3 = mat3(uModel);\n" +
            "    vNormal = model3 * localNormal;\n" +
            "    vTangent = model3 * localTangent;\n" +
            "    vUV = aUV;\n" +
            "    vMaterial = int(aMaterial + 0.5);\n" +
            "    vAO = aAO;\n" +
            "    vTint = aTint;\n" +
            "    vLightSpace = uLightViewProj * world;\n" +
            "    gl_Position = uViewProj * world;\n" +
            "}\n";

    private static final String SCENE_FRAGMENT =
            "precision highp float;\n" +
            "precision highp sampler2DArray;\n" +
            "in vec3 vWorld;\n" +
            "in vec3 vNormal;\n" +
            "in vec3 vTangent;\n" +
            "in vec2 vUV;\n" +
            "flat in int vMaterial;\n" +
            "in float vAO;\n" +
            "in vec3 vTint;\n" +
            "in vec4 vLightSpace;\n" +
            "uniform sampler2DArray uAlbedo;\n" +
            "uniform sampler2DArray uNormalMap;\n" +
            "uniform sampler2D uShadowMap;\n" +
            "uniform vec4 uMaterialParams[34];\n" +
            "uniform vec3 uMaterialTint[34];\n" +
            "uniform vec3 uSunDir;\n" +
            "uniform vec3 uSunColor;\n" +
            "uniform vec3 uSkyColor;\n" +
            "uniform vec3 uGroundColor;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogDensity;\n" +
            "uniform vec3 uEye;\n" +
            "uniform float uShadowTexel;\n" +
            "uniform float uShadowStrength;\n" +
            "uniform vec4 uPointPos[8];\n" +
            "uniform vec4 uPointColor[8];\n" +
            "uniform int uPointCount;\n" +
            "uniform float uExposure;\n" +
            "uniform vec3 uGlobalTint;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float shadowFactor(vec3 N, vec3 L) {\n" +
            "    vec3 proj = vLightSpace.xyz / vLightSpace.w;\n" +
            "    proj = proj * 0.5 + 0.5;\n" +
            "    if (proj.z > 1.0 || proj.x < 0.0 || proj.x > 1.0\n" +
            "        || proj.y < 0.0 || proj.y > 1.0) return 1.0;\n" +
            // Slope-scaled bias: a surface edge-on to the light needs more.
            "    float bias = max(0.0022 * (1.0 - dot(N, L)), 0.0006);\n" +
            "    float sum = 0.0;\n" +
            "    for (int y = -1; y <= 1; ++y) {\n" +
            "        for (int x = -1; x <= 1; ++x) {\n" +
            "            vec2 offset = vec2(float(x), float(y)) * uShadowTexel;\n" +
            "            float depth = texture(uShadowMap, proj.xy + offset).r;\n" +
            "            sum += (proj.z - bias) > depth ? 0.0 : 1.0;\n" +
            "        }\n" +
            "    }\n" +
            "    return sum / 9.0;\n" +
            "}\n" +
            "\n" +
            "vec3 acesTonemap(vec3 x) {\n" +
            "    const float a = 2.51; const float b = 0.03;\n" +
            "    const float c = 2.43; const float d = 0.59; const float e = 0.14;\n" +
            "    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 params = uMaterialParams[vMaterial];\n" +
            "    vec3 layerUV = vec3(vUV, float(vMaterial));\n" +
            "    vec3 albedo = texture(uAlbedo, layerUV).rgb * vTint * uMaterialTint[vMaterial];\n" +
            "\n" +
            "    vec3 N = normalize(vNormal);\n" +
            "    vec3 T = normalize(vTangent - N * dot(N, vTangent));\n" +
            "    vec3 B = cross(N, T);\n" +
            "    vec3 tangentNormal = texture(uNormalMap, layerUV).xyz * 2.0 - 1.0;\n" +
            "    tangentNormal.xy *= params.z;\n" +
            "    N = normalize(mat3(T, B, N) * tangentNormal);\n" +
            "\n" +
            "    vec3 V = normalize(uEye - vWorld);\n" +
            "    vec3 L = normalize(uSunDir);\n" +
            "    float ndl = max(dot(N, L), 0.0);\n" +
            "    float shade = mix(1.0, shadowFactor(N, L), uShadowStrength);\n" +
            "\n" +
            "    vec3 ambient = mix(uGroundColor, uSkyColor, N.y * 0.5 + 0.5) * vAO;\n" +
            "    vec3 diffuse = uSunColor * ndl * shade;\n" +
            "    vec3 H = normalize(L + V);\n" +
            "    float spec = pow(max(dot(N, H), 0.0), params.y) * params.x;\n" +
            "    vec3 lit = albedo * (ambient + diffuse) + uSunColor * spec * ndl * shade;\n" +
            "\n" +
            "    for (int i = 0; i < 8; ++i) {\n" +
            "        if (i >= uPointCount) break;\n" +
            "        vec3 toLight = uPointPos[i].xyz - vWorld;\n" +
            "        float dist = length(toLight);\n" +
            "        float atten = clamp(1.0 - dist / uPointPos[i].w, 0.0, 1.0);\n" +
            "        atten *= atten;\n" +
            "        vec3 PL = toLight / max(dist, 0.001);\n" +
            "        float pndl = max(dot(N, PL), 0.0);\n" +
            "        vec3 PH = normalize(PL + V);\n" +
            "        float pspec = pow(max(dot(N, PH), 0.0), params.y) * params.x;\n" +
            "        lit += (albedo * pndl + pspec) * uPointColor[i].rgb * uPointColor[i].a * atten;\n" +
            "    }\n" +
            "\n" +
            "    lit += albedo * params.w * 1.8;\n" +
            "    float viewDistance = length(uEye - vWorld);\n" +
            "    float fog = 1.0 - exp(-uFogDensity * uFogDensity * viewDistance * viewDistance);\n" +
            "    lit = mix(lit, uFogColor, clamp(fog, 0.0, 1.0));\n" +
            "    lit *= uGlobalTint * uExposure;\n" +
            // Lighting runs in linear space; encode for an 8-bit target on the way out.
            "    fragColor = vec4(pow(acesTonemap(lit), vec3(1.0 / 2.2)), 1.0);\n" +
            "}\n";

    // ----------------------------------------------------------- shadow pass

    private static final String DEPTH_VERTEX =
            "layout(location = 0) in vec3 aPos;\n" +
            "#ifdef SKINNED\n" +
            "layout(location = 7) in vec4 aBoneIndex;\n" +
            "layout(location = 8) in vec4 aBoneWeight;\n" +
            "uniform mat4 uBones[19];\n" +
            "#endif\n" +
            "uniform mat4 uLightViewProj;\n" +
            "uniform mat4 uModel;\n" +
            "void main() {\n" +
            "    vec4 local = vec4(aPos, 1.0);\n" +
            "#ifdef SKINNED\n" +
            "    mat4 skin = uBones[int(aBoneIndex.x)] * aBoneWeight.x\n" +
            "              + uBones[int(aBoneIndex.y)] * aBoneWeight.y;\n" +
            "    local = skin * local;\n" +
            "#endif\n" +
            "    gl_Position = uLightViewProj * uModel * local;\n" +
            "}\n";

    private static final String DEPTH_FRAGMENT =
            "precision mediump float;\n" +
            "void main() { }\n";

    // -------------------------------------------------------- post-processing

    /** Covers the screen with one oversized triangle; needs no vertex buffer. */
    private static final String FULLSCREEN_VERTEX =
            "out vec2 vUV;\n" +
            "void main() {\n" +
            "    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));\n" +
            "    vUV = corner;\n" +
            "    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);\n" +
            "}\n";

    private static final String BRIGHT_PASS_FRAGMENT =
            "precision mediump float;\n" +
            "in vec2 vUV;\n" +
            "uniform sampler2D uScene;\n" +
            "uniform float uThreshold;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec3 c = texture(uScene, vUV).rgb;\n" +
            "    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));\n" +
            "    float contribution = max(0.0, luma - uThreshold) / max(luma, 0.0001);\n" +
            "    fragColor = vec4(c * contribution, 1.0);\n" +
            "}\n";

    private static final String BLUR_FRAGMENT =
            "precision mediump float;\n" +
            "in vec2 vUV;\n" +
            "uniform sampler2D uSource;\n" +
            "uniform vec2 uDirection;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            // Five-tap Gaussian using linear filtering to reach nine samples' worth.
            "    vec3 sum = texture(uSource, vUV).rgb * 0.227027;\n" +
            "    vec2 o1 = uDirection * 1.3846154;\n" +
            "    vec2 o2 = uDirection * 3.2307692;\n" +
            "    sum += (texture(uSource, vUV + o1).rgb + texture(uSource, vUV - o1).rgb) * 0.3162162;\n" +
            "    sum += (texture(uSource, vUV + o2).rgb + texture(uSource, vUV - o2).rgb) * 0.0702703;\n" +
            "    fragColor = vec4(sum, 1.0);\n" +
            "}\n";

    private static final String COMPOSITE_FRAGMENT =
            "precision mediump float;\n" +
            "in vec2 vUV;\n" +
            "uniform sampler2D uScene;\n" +
            "uniform sampler2D uBloom;\n" +
            "uniform vec2 uInvResolution;\n" +
            "uniform float uBloomIntensity;\n" +
            "uniform float uVignette;\n" +
            "uniform float uAntialias;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }\n" +
            "\n" +
            "vec3 fxaa(sampler2D tex, vec2 uv, vec2 inv) {\n" +
            "    vec3 rgbM  = texture(tex, uv).rgb;\n" +
            "    vec3 rgbNW = texture(tex, uv + vec2(-1.0, -1.0) * inv).rgb;\n" +
            "    vec3 rgbNE = texture(tex, uv + vec2( 1.0, -1.0) * inv).rgb;\n" +
            "    vec3 rgbSW = texture(tex, uv + vec2(-1.0,  1.0) * inv).rgb;\n" +
            "    vec3 rgbSE = texture(tex, uv + vec2( 1.0,  1.0) * inv).rgb;\n" +
            "    float lNW = luma(rgbNW), lNE = luma(rgbNE);\n" +
            "    float lSW = luma(rgbSW), lSE = luma(rgbSE), lM = luma(rgbM);\n" +
            "    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));\n" +
            "    float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));\n" +
            "    if (lMax - lMin < 0.035) return rgbM;\n" +
            "    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), ((lNW + lSW) - (lNE + lSE)));\n" +
            "    float reduce = max((lNW + lNE + lSW + lSE) * 0.03125, 0.0078125);\n" +
            "    float scale = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);\n" +
            "    dir = clamp(dir * scale, -8.0, 8.0) * inv;\n" +
            "    vec3 rgbA = 0.5 * (texture(tex, uv + dir * -0.1666667).rgb\n" +
            "                     + texture(tex, uv + dir *  0.1666667).rgb);\n" +
            "    vec3 rgbB = rgbA * 0.5 + 0.25 * (texture(tex, uv + dir * -0.5).rgb\n" +
            "                                   + texture(tex, uv + dir *  0.5).rgb);\n" +
            "    float lB = luma(rgbB);\n" +
            "    return (lB < lMin || lB > lMax) ? rgbA : rgbB;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec3 scene = uAntialias > 0.5\n" +
            "        ? fxaa(uScene, vUV, uInvResolution)\n" +
            "        : texture(uScene, vUV).rgb;\n" +
            "    vec3 bloom = texture(uBloom, vUV).rgb;\n" +
            "    vec3 color = scene + bloom * uBloomIntensity;\n" +
            "    vec2 d = vUV - 0.5;\n" +
            "    color *= clamp(1.0 - dot(d, d) * uVignette, 0.0, 1.0);\n" +
            // A gentle S-curve; flat output reads as washed out on a phone screen.
            "    color = mix(color, color * color * (3.0 - 2.0 * color), 0.20);\n" +
            "    fragColor = vec4(color, 1.0);\n" +
            "}\n";
}
