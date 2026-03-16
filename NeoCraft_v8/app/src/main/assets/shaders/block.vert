// NeoCraft v5 — Block Vertex Shader
// PBR-lite lighting + up to 8 dynamic point lights (torches/glowstone)

precision highp float;

attribute vec3  a_Position;
attribute vec3  a_Normal;
attribute vec2  a_TexCoord;
attribute float a_AO;

uniform mat4  u_MVP;
uniform vec3  u_SunDir;
uniform vec2  u_FogRange;
uniform float u_SunBrightness;
uniform float u_Ambient;
uniform float u_Time;

// Dynamic point lights (up to 8)
uniform int   u_NumLights;
uniform vec3  u_LightPos[8];
uniform vec3  u_LightCol[8];
uniform float u_LightInt[8];

varying vec2  v_TexCoord;
varying float v_Light;
varying float v_FogFactor;
varying vec3  v_WorldPos;
varying vec3  v_Normal;
varying float v_AO;
varying vec3  v_DynLight;   // accumulated dynamic light colour

void main() {
    gl_Position = u_MVP * vec4(a_Position, 1.0);
    v_WorldPos  = a_Position;
    v_Normal    = a_Normal;
    v_TexCoord  = a_TexCoord;
    v_AO        = a_AO;

    vec3 normN  = normalize(a_Normal);
    vec3 sunN   = normalize(u_SunDir);

    float nDotL = max(dot(normN, sunN), 0.0);
    float skyAmbient = u_Ambient + 0.06 * max(normN.y, 0.0);
    float faceMult = 1.0;
    if      (a_Normal.y < -0.5)      faceMult = 0.45;
    else if (abs(a_Normal.x) > 0.5)  faceMult = 0.75;
    else if (abs(a_Normal.z) > 0.5)  faceMult = 0.82;

    float direct = nDotL * u_SunBrightness * faceMult;
    v_Light = clamp(skyAmbient + direct, 0.0, 1.2);

    // ── Dynamic point lights ──────────────────────────────────────────────
    v_DynLight = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= u_NumLights) break;
        vec3  toLight = u_LightPos[i] - a_Position;
        float dist    = length(toLight);
        if (dist > 12.0) continue;
        float atten   = u_LightInt[i] * (1.0 - dist / 12.0);
        atten        *= atten;  // quadratic falloff
        float nDotPL  = max(dot(normN, normalize(toLight)), 0.0) * 0.7 + 0.3;
        v_DynLight   += u_LightCol[i] * atten * nDotPL * faceMult;
    }

    // Fog with height fog component
    float dist    = length(gl_Position.xyz);
    float hFog    = clamp(1.0 - (a_Position.y - 10.0) / 70.0, 0.0, 1.0) * 0.3;
    float dFog    = clamp((u_FogRange.y - dist) / (u_FogRange.y - u_FogRange.x), 0.0, 1.0);
    v_FogFactor   = dFog * (1.0 - hFog * 0.4);
}
