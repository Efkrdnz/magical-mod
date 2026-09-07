#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // the unit disc's UV: centre at (0.5, 0.5), rim at radius 1
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

// The impact disc: a cracked rim ring out at radius 1, a hot core at radius 0, and six spokes
// turning slowly around the middle so a heavy landing reads as a shock, not a decal.
void main() {
    float time = GameTime * 1200.0;
    vec2 d = texCoord0 * 2.0 - 1.0;
    float radius = length(d);
    float angle = atan(d.y, d.x);

    // Everything is masked to the unit disc, softly, so the quad's corners never show.
    float disc = smoothstep(1.02, 0.96, radius);

    // A ring of broken rim near radius 1; the noise breaks it into cracks rather than a clean band.
    float cracks = fbm(vec2(angle * 3.5, radius * 2.0 - time * 0.05));
    float rim = smoothstep(0.70, 0.95, radius) * (0.45 + 1.0 * cracks);

    float core = pow(1.0 - clamp(radius, 0.0, 1.0), 3.2);

    // Six spokes: |cos| repeats twice per turn, so three turns of the angle gives six arms.
    float spokes = pow(abs(cos(angle * 3.0 + time * 0.6)), 12.0) * smoothstep(1.0, 0.15, radius) * 0.9;

    float glow = (core * 1.4 + rim * 1.1 + spokes) * disc;
    vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(core * 0.8, 0.0, 0.9));

    float alpha = clamp(glow, 0.0, 1.0) * vertexColor.a;
    fragColor = vec4(col * glow, alpha) * ColorModulator;
}
