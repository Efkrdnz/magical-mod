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
    float time = GameTime * 360.0;
    vec2 uv = texCoord0;

    // Slow, heavy churn - the surface grinds rather than flows.
    float band = sin(uv.y * 3.14159);
    vec2 flow = vec2(uv.x + time * 0.014 * (0.3 + band), uv.y - time * 0.005);

    float stormA = fbm(flow * 4.0);
    float stormB = fbm(flow * 8.5 + vec2(3.9, 7.3));
    float churn = smoothstep(0.22, 0.88, stormA);

    // Double-thump heartbeat driving the ember glow.
    float beatPhase = time * 0.085;
    float heartbeat = pow(max(sin(beatPhase), 0.0), 6.0)
            + 0.55 * pow(max(sin(beatPhase - 0.5), 0.0), 6.0);

    // Glowing ember veins that surge with the heartbeat.
    float emberVein = smoothstep(0.05, 0.0, abs(stormB - 0.62));
    emberVein *= 0.5 + 0.5 * sin(time * 2.3 + uv.x * 18.0 + stormA * 7.0);

    // Pitch-black cracks torn across the surface.
    float crack = smoothstep(0.045, 0.0, abs(stormB - 0.3));

    // Red energy discharge: sharp rays that stream across the surface, radiating
    // away from the heart along the expanding shells.
    float rays = pow(abs(sin(uv.x * 26.0 + stormA * 5.0 + time * 0.4)), 10.0);
    rays *= max(0.0, 0.45 + 0.55 * sin(time * 1.6 - uv.y * 30.0 + stormB * 6.0));

    vec3 voidRed = vec3(0.05, 0.0, 0.006);
    vec3 blood = vec3(0.46, 0.02, 0.03);
    vec3 ember = vec3(1.0, 0.24, 0.07);
    vec3 ash = vec3(0.13, 0.08, 0.09);

    vec3 color = voidRed;
    color += blood * (0.35 + churn * 0.8);
    color += ash * smoothstep(0.5, 0.9, stormB) * 0.35;
    color += ember * emberVein * (0.7 + heartbeat * 0.9);
    color += ember * rays * (0.55 + heartbeat * 0.65);
    color = mix(color, vec3(0.0), crack * 0.85);
    color *= vertexColor.rgb;

    float alpha = vertexColor.a * (0.78 + churn * 0.12 + emberVein * 0.22 + rays * 0.2) * (0.9 + 0.1 * heartbeat);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
