// NeoCraft v4 — Sky Fragment Shader
// Physically-based atmospheric scattering (Rayleigh + Mie),
// procedural volumetric clouds, realistic sun/moon/stars/Milky Way

precision highp float;

uniform vec3  u_SunDir;
uniform vec3  u_ZenithColor;
uniform vec3  u_HorizonColor;
uniform float u_DayFactor;
uniform float u_Time;
uniform float u_Rain;

varying vec3  v_Dir;
varying float v_Up;

// ── Hash / noise helpers ──────────────────────────────────────────────────
float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float hash3(vec3 p) { return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }

float noise2(vec2 p) {
    vec2 i=floor(p), f=fract(p);
    vec2 u=f*f*(3.0-2.0*f);
    return mix(mix(hash(i), hash(i+vec2(1,0)),u.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)),u.x), u.y);
}

float fbm2(vec2 p, int oct) {
    float v=0.0, a=0.5, f=1.0, m=0.0;
    for(int i=0;i<6;i++){
        if(i>=oct) break;
        v+=noise2(p*f)*a; m+=a; a*=0.5; f*=2.1;
    }
    return v/m;
}

// ── Atmospheric scattering ────────────────────────────────────────────────
// Approximation of Rayleigh & Mie scattering (Preetham/Hosek-Wilkie inspired)

// Rayleigh phase — scatters blue light preferentially
vec3 rayleigh(float cosAngle) {
    float phase = 0.75 * (1.0 + cosAngle * cosAngle);
    // Rayleigh coefficients for RGB wavelengths: blue scatters most
    vec3 beta = vec3(5.8e-3, 1.35e-3, 0.33e-3) * 25.0;
    return phase * beta;
}

// Mie phase — forward-scattering haze around sun
float mie(float cosAngle, float g) {
    float g2 = g*g;
    return (1.0 - g2) / (4.0 * 3.14159 * pow(1.0 + g2 - 2.0*g*cosAngle, 1.5));
}

// ── Cloud function ───────────────────────────────────────────────────────
float cloudDensity(vec3 dir) {
    if (dir.y < 0.04) return 0.0;
    // Project direction onto a flat cloud plane
    vec2 uv = dir.xz / (dir.y + 0.001) * 0.28 + vec2(u_Time * 0.006, u_Time * 0.004);
    float base = fbm2(uv, 5);
    float detail= fbm2(uv * 3.2 + vec2(1.3, 0.7), 3) * 0.35;
    float cloud = smoothstep(0.42, 0.72, base + detail);
    // Thin out at horizon
    cloud *= smoothstep(0.05, 0.22, dir.y);
    return cloud;
}

// ── Stars & Milky Way ─────────────────────────────────────────────────────
vec3 stars(vec3 dir, float dayFactor) {
    if (dayFactor > 0.35) return vec3(0.0);
    float night = clamp(1.0 - dayFactor * 2.86, 0.0, 1.0);

    // Point stars
    vec2 g1 = floor(dir.xz * 250.0 + dir.y * 130.0);
    float star = step(0.992, hash(g1));
    float twinkle = 0.5 + 0.5 * sin(u_Time * (2.5 + hash(g1)*4.0));
    vec3  starCol = vec3(star * twinkle * night);
    // Star colour variation
    float hue = hash(g1 + 0.5);
    starCol *= mix(vec3(1.0, 0.8, 0.6), mix(vec3(0.7, 0.8, 1.0), vec3(1.0), hue*0.5), hue);

    // Milky Way band — faint gradient across sky
    float mw = fbm2(dir.xz * 1.5 + vec2(0.3, 0.7), 4) * night * 0.08;
    // Milky Way is a band roughly horizontal
    float band = exp(-abs(dir.y - 0.0) * 6.0) * smoothstep(0.0, 0.1, abs(dir.x));
    starCol += vec3(0.5, 0.6, 0.8) * mw * band;

    return starCol;
}

void main() {
    vec3 dir    = normalize(v_Dir);
    vec3 sunN   = normalize(u_SunDir);
    float cosS  = dot(dir, sunN);
    float cosM  = dot(dir, -sunN);   // moon is opposite sun

    float up    = clamp(dir.y, 0.0, 1.0);

    // ── Rayleigh + Mie sky ────────────────────────────────────────────────
    vec3  ray   = rayleigh(cosS);
    float mieV  = mie(cosS, 0.76) * 0.012 * u_DayFactor;  // forward sun haze
    float mieH  = mie(cosS, 0.40) * 0.006 * u_DayFactor;  // wider glow

    // Blue sky base from Rayleigh scatter
    vec3 raySky = ray * clamp(u_DayFactor * 2.5, 0.0, 1.0)
                * mix(vec3(0.2, 0.5, 1.2), vec3(1.2, 0.5, 0.1), max(-sunN.y,0.0));
    raySky *= pow(1.0 - up * 0.5, 2.0);   // horizon brighter

    // Combined sky
    vec3 skyCol = mix(u_HorizonColor, u_ZenithColor, pow(up, 0.55));
    skyCol      = mix(skyCol, skyCol + raySky, 0.55);

    // ── Sunset / sunrise colour wash on horizon ────────────────────────────
    float horizonAngle = 1.0 - abs(dir.y);
    float sunHorizon   = clamp(1.0 - abs(sunN.y) / 0.5, 0.0, 1.0);
    // Orange/red wash near horizon when sun is low
    float sunset = sunHorizon * horizonAngle * horizonAngle * u_DayFactor;
    skyCol = mix(skyCol, skyCol * vec3(2.0, 1.0, 0.3), sunset * 0.5);

    // ── Mie haze around sun ────────────────────────────────────────────────
    skyCol += vec3(1.0, 0.85, 0.5) * mieV;
    skyCol += vec3(1.0, 0.70, 0.3) * mieH;

    // ── Sun disc ──────────────────────────────────────────────────────────
    if (u_DayFactor > 0.05) {
        // Inner disc: white hot core
        float disc = smoothstep(0.9998, 0.99995, cosS);
        skyCol += vec3(1.4, 1.3, 1.0) * disc * u_DayFactor;
        // Glow ring
        float glow1 = pow(max(cosS, 0.0), 180.0) * 0.8 * u_DayFactor;
        float glow2 = pow(max(cosS, 0.0), 40.0)  * 0.25 * u_DayFactor;
        skyCol += mix(vec3(1.0, 0.9, 0.5), vec3(1.0, 0.4, 0.1), 1.0-u_DayFactor) * (glow1 + glow2);
        // Atmospheric corona (very wide scattering at sunrise/sunset)
        float corona = pow(max(cosS, 0.0), 8.0) * sunHorizon * 0.5;
        skyCol += vec3(1.0, 0.3, 0.05) * corona;
    }

    // ── Moon ─────────────────────────────────────────────────────────────
    if (u_DayFactor < 0.6) {
        float moonF  = clamp(1.0 - u_DayFactor * 1.67, 0.0, 1.0);
        // Moon disc
        float moon   = smoothstep(0.9997, 0.99985, cosM);
        skyCol      += vec3(0.9, 0.93, 1.0) * moon * moonF;
        // Moon glow
        float mGlow  = pow(max(cosM, 0.0), 80.0) * 0.12 * moonF;
        skyCol      += vec3(0.5, 0.55, 0.7) * mGlow;
        // Crescent shadow (fake by subtracting offset disc)
        float shadow = smoothstep(0.9997, 0.99985,
            dot(dir, normalize(-sunN + vec3(0.012, 0.004, 0.0))));
        skyCol -= vec3(0.9, 0.93, 1.0) * shadow * moonF * 0.85;
    }

    // ── Stars & Milky Way ──────────────────────────────────────────────────
    skyCol += stars(dir, u_DayFactor);

    // ── Procedural clouds ─────────────────────────────────────────────────
    float cloudD = cloudDensity(dir);
    // Rain makes clouds darker and heavier
    cloudD = mix(cloudD, cloudD * 1.6, u_Rain);
    if (cloudD > 0.0) {
        // Cloud lighting: brighter on top, darker base
        float cloudLight = mix(0.55, 1.0, dir.y) * (0.7 + u_DayFactor * 0.3);
        // Edge glow when sun is behind cloud
        float rim = pow(max(cosS, 0.0), 6.0) * cloudD * u_DayFactor * 0.8;
        vec3 cloudCol = vec3(cloudLight) + vec3(1.0, 0.95, 0.7) * rim;
        // Rain clouds are grey
        cloudCol = mix(cloudCol, vec3(0.45, 0.45, 0.48), u_Rain * 0.7);
        skyCol   = mix(skyCol, cloudCol, cloudD);
    }

    // ── Rain haze on horizon ──────────────────────────────────────────────
    if (u_Rain > 0.1) {
        float rainHaze = u_Rain * (1.0 - up) * 0.6;
        skyCol = mix(skyCol, vec3(0.5, 0.52, 0.55), rainHaze);
    }

    // ── ACES tone map for HDR sky values ──────────────────────────────────
    skyCol = clamp((skyCol*(2.51*skyCol+0.03))/(skyCol*(2.43*skyCol+0.59)+0.14), 0.0, 1.0);

    gl_FragColor = vec4(skyCol, 1.0);
}
