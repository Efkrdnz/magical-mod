#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // x = along the arc (0..1), y = across the ribbon (0..1)
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

// The blade ribbon: a white-hot centreline down the middle of the band, a harder and brighter
// cutting edge on the leading side, and filaments streaming along the length of the cut.
void main() {
    float time = GameTime * 1200.0;
    float along = texCoord0.x;
    float across = texCoord0.y;

    // Distance out from the centreline, 0 on the spine and 1 at either lip of the ribbon.
    float offCentre = abs(across - 0.5) * 2.0;
    float spine = pow(1.0 - offCentre, 3.0);
    float body = (1.0 - offCentre) * 0.5;

    // The leading half of the ribbon carries the cut: it sharpens to a hard line at across = 1.
    float lead = smoothstep(0.5, 1.0, across);
    float cut = pow(lead, 6.0) * 1.6;

    // Filaments streaking along the arc; they slide over time so the cut never sits still.
    float flow = fbm(vec2(along * 7.0 - time * 0.45, across * 3.0 + time * 0.06));
    float streak = 0.6 + 0.7 * flow;

    // Cut both ends off so a ribbon never shows a hard cap where its quads stop.
    float ends = smoothstep(0.0, 0.04, along) * smoothstep(1.0, 0.96, along);

    float glow = (body + spine * 1.3 + cut) * streak * ends;
    vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(spine * 0.75 + cut * 0.5, 0.0, 0.92));

    float alpha = clamp(glow, 0.0, 1.0) * vertexColor.a;
    fragColor = vec4(col * glow, alpha) * ColorModulator;
}
