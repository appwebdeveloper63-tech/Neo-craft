// NeoCraft v4 — Water Vertex Shader
// Realistic multi-layer Gerstner waves + surface normal perturbation

precision highp float;

attribute vec3  a_Position;
attribute vec3  a_Normal;
attribute vec2  a_TexCoord;
attribute float a_AO;

uniform mat4  u_MVP;
uniform vec3  u_SunDir;
uniform vec2  u_FogRange;
uniform float u_Time;
uniform float u_SunBrightness;
uniform float u_Ambient;

varying vec2  v_TexCoord;
varying vec2  v_UV2;        // second UV set scrolling opposite direction
varying float v_Light;
varying float v_FogFactor;
varying float v_Depth;
varying vec3  v_WorldPos;
varying vec3  v_SunDir;

// Gerstner wave: returns y displacement
float gerstner(vec2 pos, vec2 dir, float wavelength, float steepness, float t) {
    float k   = 6.28318 / wavelength;
    float c   = sqrt(9.8 / k);
    float d   = dot(dir, pos);
    return steepness * sin(k * d - c * t);
}

void main() {
    float wave = 0.0;
    if (a_Normal.y > 0.5) {
        // Four overlapping Gerstner wave layers — different directions/scales
        vec2 p = a_Position.xz;
        wave += gerstner(p, normalize(vec2(1.0, 0.8)),  3.2, 0.06, u_Time);
        wave += gerstner(p, normalize(vec2(-0.6, 1.0)), 2.1, 0.04, u_Time * 0.85);
        wave += gerstner(p, normalize(vec2(0.3, -1.0)), 1.4, 0.025, u_Time * 1.2);
        wave += gerstner(p, normalize(vec2(-1.0, -0.5)),4.8, 0.05, u_Time * 0.6);
    }

    vec4 worldPos = vec4(a_Position.x, a_Position.y + wave, a_Position.z, 1.0);
    v_WorldPos    = worldPos.xyz;
    gl_Position   = u_MVP * worldPos;
    v_SunDir      = u_SunDir;

    // Light
    float nDotL = max(dot(normalize(a_Normal), normalize(u_SunDir)), 0.0);
    v_Light = u_Ambient + (1.0 - u_Ambient) * nDotL * u_SunBrightness;

    // Two UV layers scrolling at different speeds/angles for interference pattern
    v_TexCoord = a_TexCoord + vec2(u_Time * 0.04, u_Time * 0.025);
    v_UV2      = a_TexCoord * 1.3 + vec2(-u_Time * 0.03, u_Time * 0.018);

    float dist  = length(gl_Position.xyz);
    v_FogFactor = clamp((u_FogRange.y - dist) / (u_FogRange.y - u_FogRange.x), 0.0, 1.0);
    v_Depth     = clamp(dist / 20.0, 0.0, 1.0);
}
