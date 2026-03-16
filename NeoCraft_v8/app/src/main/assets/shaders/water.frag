// NeoCraft v4 — Water Fragment Shader
// Fresnel reflection, dual-layer normals, specular highlight, depth fade,
// caustic hints, subsurface scatter, ACES tone mapping

precision highp float;

uniform sampler2D u_Texture;
uniform vec3      u_SkyColor;
uniform vec3      u_SunDir;
uniform float     u_Alpha;
uniform float     u_Underwater;
uniform float     u_SunBrightness;
uniform float     u_Time;
uniform float     u_Rain;

varying vec2  v_TexCoord;
varying vec2  v_UV2;
varying float v_Light;
varying float v_FogFactor;
varying float v_Depth;
varying vec3  v_WorldPos;
varying vec3  v_SunDir;

// ACES tone mapping
vec3 aces(vec3 x) {
    return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14), 0.0, 1.0);
}

// Hash for noise
float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float hash3(vec3 p) { return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }

// Pseudo normal from texture (normal map approximation using 2 UV samples)
vec2 waterNormal(sampler2D tex, vec2 uv, float str) {
    float h0 = texture2D(tex, uv).r;
    float hx = texture2D(tex, uv + vec2(0.01, 0.0)).r;
    float hy = texture2D(tex, uv + vec2(0.0, 0.01)).r;
    return vec2(h0 - hx, h0 - hy) * str;
}

void main() {
    // ── Perturbed normals from two scrolling UV layers ────────────────────
    vec2 n1 = waterNormal(u_Texture, v_TexCoord, 4.0);
    vec2 n2 = waterNormal(u_Texture, v_UV2,      3.0);
    vec2 nXZ = (n1 + n2) * 0.5;
    vec3 pertNorm = normalize(vec3(nXZ.x, 1.0, nXZ.y));

    // ── Fresnel (more reflective at grazing angles) ───────────────────────
    // Approximate view-up angle (camera looks slightly down in typical gameplay)
    vec3 viewDir   = normalize(vec3(0.0, 1.0, 0.3));
    float cosTheta = clamp(dot(pertNorm, viewDir), 0.0, 1.0);
    // Schlick approximation — F0=0.02 for water
    float F0       = 0.02;
    float fresnel  = F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
    fresnel        = clamp(fresnel, 0.0, 0.85);

    // ── Sun specular on water ─────────────────────────────────────────────
    vec3 halfV     = normalize(normalize(v_SunDir) + viewDir);
    float spec     = pow(max(dot(pertNorm, halfV), 0.0), 256.0);
    spec          *= u_SunBrightness * 1.8;
    // Specular disc colour: warm white at noon, orange at sunset
    vec3 specCol   = mix(vec3(1.0, 0.95, 0.8), vec3(1.0, 0.55, 0.1),
                         clamp(1.0 - u_SunBrightness, 0.0, 1.0));

    // ── Deep water colour ─────────────────────────────────────────────────
    // Shallow = lighter teal, Deep = dark blue
    vec3 shallowCol = vec3(0.22, 0.65, 0.72);
    vec3 deepCol    = vec3(0.04, 0.12, 0.38);
    vec3 waterCol   = mix(shallowCol, deepCol, v_Depth);

    // ── Subsurface scattering hint ────────────────────────────────────────
    // Bright light from below the surface at wave crests
    float sss = clamp(nXZ.x + nXZ.y + 0.5, 0.0, 1.0) * u_SunBrightness;
    waterCol += vec3(0.05, 0.20, 0.15) * sss * 0.4;

    // ── Caustic pattern on subsurface ─────────────────────────────────────
    float cx = sin(v_WorldPos.x * 2.8 + u_Time * 2.5) * 0.5 + 0.5;
    float cz = sin(v_WorldPos.z * 3.3 + u_Time * 2.0 + 0.9) * 0.5 + 0.5;
    float caustic = cx * cz * 0.15 * u_SunBrightness;
    waterCol += vec3(0.2, 0.5, 0.6) * caustic;

    // ── Rain adds chop: more foam, brighter normals ───────────────────────
    waterCol = mix(waterCol, waterCol * 1.1 + vec3(0.04), u_Rain * 0.3);

    // ── Combine: water base + Fresnel reflection of sky ───────────────────
    vec3 reflectCol = u_SkyColor * 1.1;   // sky reflection
    vec3 lit = mix(waterCol * v_Light, reflectCol, fresnel);
    lit += specCol * spec;

    // ── Underwater view ───────────────────────────────────────────────────
    if (u_Underwater > 0.5) {
        // Looking up at surface from below — brighten and add caustic
        lit = mix(lit, vec3(0.1, 0.45, 0.7), 0.5);
        lit += vec3(0.05, 0.1, 0.05) * caustic;
    }

    // ── Tone map ──────────────────────────────────────────────────────────
    lit = aces(lit * 1.15);

    // ── Fog ───────────────────────────────────────────────────────────────
    vec3 col = mix(u_SkyColor, lit, v_FogFactor);

    // ── Alpha: deeper = more opaque; always some see-through ──────────────
    float alpha = mix(0.55, 0.88, v_Depth) + fresnel * 0.1;
    alpha = clamp(alpha * u_Alpha, 0.3, 0.92);

    gl_FragColor = vec4(col, alpha);
}
