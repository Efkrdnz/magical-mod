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
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amp;
        p = p * 2.02 + vec2(7.1, 3.3);
        amp *= 0.5;
    }
    return value;
}

void main() {
    float time = GameTime * 1200.0;

    // Centered polar coordinates over the disc.
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) {
        discard; // circular cutout: the square quad never shows its corners
    }
    float ang = atan(p.y, p.x);
    float level = vertexColor.a;

    // Logarithmic spiral: arms wind into the eye and flow inward over time.
    float logr = log(max(r, 0.0007));
    float swirl = ang * 5.0 + logr * 6.5 - time * 0.7;
    float arms = pow(0.5 + 0.5 * sin(swirl), 2.2);

    // Turbulence dragged around the spiral so the arms churn like gas instead of looking mechanical.
    vec2 flow = p * 3.2 + vec2(cos(ang), sin(ang)) * time * 0.015;
    float turb = fbm(flow + vec2(swirl * 0.12, -time * 0.03));
    float body = mix(arms, arms * turb * 1.9, 0.6);

    // The eye and its hot event horizon.
    float pit = smoothstep(0.0, 0.24, r);                 // 0 inside the maw, 1 outside
    float horizon = smoothstep(0.34, 0.18, r) * smoothstep(0.09, 0.17, r);

    // Colors taken from the theme-shifted vertex color; the core burns brighter.
    vec3 base = vertexColor.rgb;
    vec3 glow = mix(base, vec3(1.0), 0.7);
    vec3 color = base * body * 1.25;
    color += glow * horizon * 1.6;
    color *= pit;                                          // swallow light into the pit
    color += base * (1.0 - pit) * 0.06;                   // faint ember at the center

    // Soft outer falloff into the sky.
    float edge = smoothstep(1.0, 0.5, r);
    float alpha = (body * 0.6 + horizon * 0.95) * pit + horizon * 0.5;
    alpha = clamp(alpha * edge * level, 0.0, 1.0);

    fragColor = vec4(color, alpha) * ColorModulator;
}
