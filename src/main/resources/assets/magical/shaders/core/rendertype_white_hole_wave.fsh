#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in float wavePulse;

out vec4 fragColor;

float ring(float r, float center, float width) {
    return exp(-abs(r - center) / width);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.15) {
        discard;
    }

    float time = GameTime * 760.0;
    float angle = atan(p.y, p.x);
    float core = smoothstep(0.38, 0.02, r);
    float waveA = ring(fract(r * 1.25 - time * 0.55), 0.35, 0.045) * smoothstep(0.2, 1.05, r);
    float waveB = ring(fract(r * 1.85 - time * 0.82), 0.48, 0.055) * smoothstep(0.16, 1.0, r);
    float radial = pow(abs(sin(angle * 16.0 + time * 2.2)), 12.0) * smoothstep(0.2, 0.96, r);
    float chroma = ring(r + sin(angle * 8.0 - time) * 0.035, 0.72, 0.08);

    vec3 tint = max(vertexColor.rgb, vec3(0.65, 0.92, 1.0));
    vec3 color = vec3(1.0, 0.98, 0.9) * core * (1.35 + wavePulse * 0.35);
    color += tint * (waveA + waveB) * 0.95;
    color += vec3(0.58, 0.82, 1.0) * chroma * 0.65;
    color += vec3(1.0) * radial * 0.42;
    color += vec3(0.95, 0.55, 1.0) * waveB * 0.18;

    float alpha = max(core * 0.95, waveA * 0.58);
    alpha = max(alpha, waveB * 0.46);
    alpha = max(alpha, radial * 0.26);
    alpha = max(alpha, chroma * 0.28);
    alpha *= smoothstep(1.15, 0.94, r) * vertexColor.a;
    if (alpha <= 0.01) {
        discard;
    }

    fragColor = vec4(color, alpha) * ColorModulator;
}
