#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // 0..1 across the tooltip's inner box, whatever size that box is
in vec4 vertexColor; // the weapon's accent; alpha is how opaque the whole panel should be

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
        p = p * 2.07 + vec2(4.71, 9.13);
        amp *= 0.5;
    }
    return v;
}

// The panel behind a magical weapon's tooltip.
//
// Two coordinate spaces on purpose. The drift is read off gl_FragCoord, so the field is the same
// size in a one-line tooltip and a twenty-line one and the box reads as a window onto something
// standing still behind the screen rather than a texture stretched to fit. The shape - gradient,
// rim, sweep - is read off texCoord0, because those have to know where the edges are.
void main() {
    float time = GameTime * 1200.0;
    vec2 uv = texCoord0;
    vec2 screen = gl_FragCoord.xy * 0.016;

    // How far this pixel is from the nearest edge, in framebuffer pixels. The derivative is the
    // only thing here that knows how big the box is, and it costs nothing to ask.
    vec2 perUv = vec2(1.0 / max(fwidth(uv.x), 1e-6), 1.0 / max(fwidth(uv.y), 1e-6));
    vec2 toEdge = min(uv, vec2(1.0) - uv) * perUv;
    float edge = min(toEdge.x, toEdge.y);

    // Slow smoke, and a second layer crawling the other way so the two never settle into a pattern.
    float smokeA = fbm(screen + vec2(time * 0.035, -time * 0.021));
    float smokeB = fbm(screen * 1.73 - vec2(time * 0.017, time * 0.029));
    float smoke = smokeA * 0.65 + smokeB * 0.35;

    // Filaments: thin bright threads where the two smoke layers happen to agree.
    float seam = 1.0 - abs(smokeA - smokeB) * 4.0;
    float filament = pow(max(seam, 0.0), 6.0);

    // A sheen that crosses the box every twelve seconds or so, brightest at its leading edge.
    float sweepAt = fract(time * 0.085);
    float sweep = pow(max(0.0, 1.0 - abs(uv.x - sweepAt * 1.4 + 0.2) * 5.0), 3.0);

    // Darker at the bottom, so the panel has a direction and the first line reads brightest.
    float fall = 1.0 - uv.y * 0.45;

    vec3 deep = vec3(0.035, 0.022, 0.070);
    vec3 accent = vertexColor.rgb;
    vec3 col = mix(deep, deep + accent * 0.22, smoke) * fall;
    col += accent * filament * 0.55;
    col += accent * sweep * 0.20;

    // The inside of the border: a band of accent light hugging the frame.
    float rim = 1.0 - smoothstep(0.0, 9.0, edge);
    col += accent * rim * rim * 0.45;

    // And a hard fade in the outermost pixel or two, so the panel never shows a cut edge under the
    // frame sprite drawn on top of it.
    float feather = smoothstep(0.0, 1.6, edge);

    float alpha = clamp(vertexColor.a * (0.80 + smoke * 0.16 + rim * 0.20), 0.0, 1.0) * feather;
    fragColor = vec4(col, alpha) * ColorModulator;
}
