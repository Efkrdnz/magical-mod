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
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * amp;
        p = p * 2.03 + vec2(8.31, 2.17);
        amp *= 0.5;
    }
    return v;
}

// Additive energy orb: a hot core, a churning plasma body, and a soft outer halo.
void main() {
    float time = GameTime * 1200.0;
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) {
        discard; // mask the quad to a disc
    }
    float ang = atan(p.y, p.x);

    // Roiling plasma: radial noise dragged around the orb and pulled inward over time.
    float swirl = fbm(vec2(ang * 1.9 + time * 0.05, r * 3.4 - time * 0.14));
    float body = (1.0 - r) * (0.55 + 0.85 * swirl);
    float core = pow(1.0 - r, 2.4) * 1.8;
    float halo = (1.0 - smoothstep(0.35, 1.0, r)) * 0.5;
    float rim = exp(-pow((r - 0.82) / 0.10, 2.0)) * (0.4 + 0.4 * swirl);

    float glow = core + body + halo * 0.6 + rim;
    vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(core * 0.6, 0.0, 0.85));
    col += vec3(1.0) * core * 0.4;

    float alpha = clamp(glow, 0.0, 1.0) * vertexColor.a;
    fragColor = vec4(col * glow, alpha) * ColorModulator;
}
