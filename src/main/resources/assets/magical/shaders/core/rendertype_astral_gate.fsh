#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += noise(p) * amp;
        p = p * 2.03 + vec2(8.31, 2.17);
        amp *= 0.5;
    }
    return value;
}

void main() {
    float time = GameTime * 6000.0;
    // u: mirrored angle around the disc, v: 0 at the center, 1 at the rim.
    vec2 uv = texCoord0;
    float r = uv.y;

    // Turbulence that flows INWARD: features drift toward r = 0 over time,
    // wound sideways by the vortex so the streaks read as spiral arms.
    vec2 flow = vec2(uv.x * 3.0 + (1.0 - r) * 2.2, r * 7.0 + time * 0.045);
    float stormA = fbm(flow * 2.4);
    float stormB = fbm(flow * 5.2 + vec2(3.7, 8.2));
    float bands = smoothstep(0.30, 0.90, stormA);

    // Bright spiral arms being dragged into the throat.
    float arm = smoothstep(0.05, 0.0, abs(stormB - 0.5));
    arm *= 0.5 + 0.5 * sin(time * 0.9 + r * 24.0 + uv.x * 9.0);

    // Event horizon: the throat swallows all light.
    float core = smoothstep(0.34, 0.08, r);
    // Hot accretion ring right around the throat.
    float horizonRing = exp(-abs(r - 0.30) * 20.0) * (0.8 + 0.2 * sin(time * 1.3));
    // Outer rim glow at the portal mouth.
    float rim = exp(-abs(r - 0.92) * 15.0);

    // Star specks falling inward in visible steps.
    float speck = step(0.976, hash(floor(vec2(uv.x * 42.0, r * 26.0 + time * 0.16))));

    vec3 voidCore = vec3(0.006, 0.002, 0.032);
    vec3 violet = vec3(0.30, 0.10, 0.62);
    vec3 astralBlue = vec3(0.16, 0.55, 1.0);
    vec3 pale = vec3(0.74, 0.92, 1.0);

    vec3 color = voidCore;
    color += violet * (0.26 + bands * 0.72) * (1.0 - core);
    color += astralBlue * arm * (1.0 - core * 0.8);
    color += astralBlue * horizonRing * 1.1;
    color += pale * rim * 0.9;
    color += pale * speck * (1.0 - core) * 0.75;
    color = mix(color, voidCore, core * 0.95);

    float alpha = vertexColor.a * (0.55 + bands * 0.18 + arm * 0.22 + horizonRing * 0.35 + rim * 0.35 + core * 0.35);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
