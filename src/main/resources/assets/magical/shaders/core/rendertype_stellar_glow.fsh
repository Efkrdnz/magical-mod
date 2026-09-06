#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in float plasmaPulse;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(269.5, 183.3))) * 57143.17);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float ring(float r, float center, float width) {
    return exp(-abs(r - center) / width);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.16) {
        discard;
    }

    float time = GameTime * 1050.0;
    float angle = atan(p.y, p.x);
    vec2 flow = vec2(cos(angle + time * 0.32), sin(angle - time * 0.25));
    float turbulence = noise(p * 7.0 + flow * 1.6);
    turbulence += noise(p * 17.0 - flow * 2.3) * 0.45;
    float core = smoothstep(0.54, 0.04, r);
    float corona = smoothstep(1.12, 0.24, r);
    float boilingRim = ring(r + sin(angle * 9.0 - time * 1.1) * 0.045, 0.58, 0.065);
    float flare = pow(abs(sin(angle * 7.0 + time * 1.8)), 20.0) * smoothstep(0.18, 1.0, r);
    float sparks = step(0.978, hash(floor((p + vec2(time * 0.025, -time * 0.019)) * 31.0))) * smoothstep(0.3, 1.0, r);

    vec3 tint = max(vertexColor.rgb, vec3(0.1, 0.18, 0.28));
    vec3 hot = vec3(1.0, 0.94, 0.72);
    vec3 white = vec3(1.0);
    vec3 color = tint * corona * (0.42 + turbulence * 0.55);
    color += hot * core * (1.35 + plasmaPulse * 0.3);
    color += white * smoothstep(0.18, 0.0, r) * 1.35;
    color += mix(tint, hot, 0.55) * boilingRim * 0.78;
    color += tint.bgr * flare * 0.52;
    color += white * sparks * 0.32;

    float alpha = max(core * 0.98, corona * (0.3 + turbulence * 0.24));
    alpha = max(alpha, boilingRim * 0.54);
    alpha = max(alpha, flare * 0.4);
    alpha = max(alpha, sparks * 0.18);
    alpha *= smoothstep(1.16, 0.94, r) * vertexColor.a;
    if (alpha <= 0.01) {
        discard;
    }

    fragColor = vec4(color, alpha) * ColorModulator;
}
