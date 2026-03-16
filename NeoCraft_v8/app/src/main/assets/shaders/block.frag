// NeoCraft v5 — Block Fragment Shader
// PBR-lite: specular, AO, ACES tone map, color grade, wet surfaces,
// caustics, emissive blocks, dynamic light tint

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
varying float v_Light;
varying float v_FogFactor;
varying vec3  v_WorldPos;
varying vec3  v_Normal;
varying float v_AO;
varying vec3  v_DynLight;

vec3 aces(vec3 x) {
    return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14), 0.0, 1.0);
}

void main() {
    vec4 tex = texture2D(u_Texture, v_TexCoord);
    if (tex.a < 0.1) discard;

    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float ao  = v_AO * (0.85 + lum * 0.15);

    // Sun lighting
    vec3 lit = tex.rgb * v_Light * ao;

    // Dynamic point lights (torches, glowstone, etc.)
    // v_DynLight is already attenuated colour; multiply by texture
    lit += tex.rgb * v_DynLight;

    // Specular (Blinn-Phong)
    vec3  viewDir  = vec3(0.0, 0.2, -1.0);
    vec3  halfVec  = normalize(normalize(u_SunDir) + viewDir);
    float specAmt  = pow(max(dot(normalize(v_Normal), halfVec), 0.0), 48.0);
    float shininess= clamp(1.0 - lum * 0.7, 0.1, 0.7);
    float spec     = specAmt * u_SunBrightness * shininess * 0.2;
    spec          *= (1.0 + u_Rain * 2.8);
    lit           += vec3(spec);

    // Rain darkening + puddle sky reflection
    float wetness  = u_Rain * clamp(v_Normal.y, 0.0, 1.0) * 0.35;
    lit *= (1.0 - wetness);
    lit  = mix(lit, lit * vec3(0.85, 0.90, 1.06), wetness * 0.5);

    // Underwater caustics
    if (u_Underwater > 0.5) {
        float cx = sin(v_WorldPos.x * 3.1 + u_Time * 2.0) * 0.5 + 0.5;
        float cz = sin(v_WorldPos.z * 2.7 + u_Time * 1.7 + 1.3) * 0.5 + 0.5;
        lit += vec3(0.05, 0.18, 0.35) * (cx * cz * 0.3);
        lit  = mix(lit, lit * vec3(0.4, 0.65, 1.1), 0.45);
    }

    // Emissive blocks: lava/glowstone self-illuminate (bypass AO)
    // Detect by very high saturation red/orange or yellow (lava=red, glowstone=yellow)
    float rDom = tex.r - max(tex.g, tex.b);
    float gDom = tex.g - max(tex.r, tex.b);
    if (rDom > 0.3 && tex.r > 0.7) {          // lava / magma
        lit = mix(lit, tex.rgb * 1.8, 0.5);
    } else if (rDom > 0.15 && gDom > -0.1 && tex.r > 0.7) {  // glowstone / shroomlight
        lit = mix(lit, tex.rgb * 1.5, 0.35);
    }

    // ACES tone map
    lit = aces(lit * 1.2);

    // Colour grade: cool shadows, warm highlights
    float litL  = dot(lit, vec3(0.2126, 0.7152, 0.0722));
    lit = mix(lit * vec3(0.92, 0.96, 1.08), lit * vec3(1.05, 1.00, 0.92), litL);

    // Fog
    vec3 fogCol = mix(u_SkyColor, vec3(dot(u_SkyColor, vec3(0.33))), 0.15);
    vec3 col    = mix(fogCol, lit, v_FogFactor);

    gl_FragColor = vec4(col, tex.a * u_Alpha);
}
