#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // x = along the beam (0..1), y = across (0..1)
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

// Additive energy tube: a white-hot spine tapering out sideways, with streaming filaments
// flowing down its length and soft feathered ends.
void main() {
    float time = GameTime * 1200.0;
    float along = texCoord0.x;
    float across = abs(texCoord0.y * 2.0 - 1.0); // 0 spine, 1 edge

    float spine = pow(1.0 - across, 2.6);
    float body = 1.0 - across;

    // Filaments streaking along the length; they slide over time so the beam feels alive.
    float flow = fbm(vec2(along * 6.0 - time * 0.35, texCoord0.y * 3.5 + time * 0.05));
    float streak = 0.55 + 0.75 * flow;

    // Feather both ends so a segment never shows a hard cap.
    float ends = smoothstep(0.0, 0.10, along) * smoothstep(1.0, 0.88, along);

    float glow = (body * 0.55 + spine * 1.5) * streak * ends;
    vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(spine * 0.7, 0.0, 0.9));

    float alpha = clamp(glow, 0.0, 1.0) * vertexColor.a;
    fragColor = vec4(col * glow, alpha) * ColorModulator;
}
