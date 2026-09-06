#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in float dragPulse;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(83.17, 219.43))) * 71359.31);
}

float ring(float r, float center, float width) {
    return exp(-abs(r - center) / width);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.12) {
        discard;
    }

    float time = GameTime * 820.0;
    float angle = atan(p.y, p.x);
    float voidCore = smoothstep(0.46, 0.2, r);
    float horizon = ring(r, 0.48 + sin(time * 0.2) * 0.012, 0.035);
    float coldRing = ring(r + sin(angle * 4.0 + time * 0.55) * 0.04, 0.72, 0.08);
    float infall = pow(abs(sin(angle * 10.0 - time * 1.6)), 18.0) * smoothstep(0.32, 1.0, r);
    vec2 bent = p * (1.0 + 0.48 / (r * r + 0.06));
    float dust = step(0.975, hash(floor((bent + vec2(time * 0.012, -time * 0.017)) * 34.0))) * smoothstep(0.42, 1.0, r);

    vec3 tint = max(vertexColor.rgb, vec3(0.1, 0.2, 0.55));
    vec3 color = vec3(0.0, 0.0, 0.01);
    color += tint * coldRing * 0.72;
    color += vec3(0.65, 0.78, 1.0) * horizon * (1.0 + dragPulse * 0.45);
    color += vec3(0.28, 0.38, 1.0) * infall * 0.46;
    color += vec3(0.88, 0.96, 1.0) * dust * 0.28;
    color = mix(color, vec3(0.0), voidCore * 0.94);

    float alpha = max(voidCore * 0.84, horizon * 0.86);
    alpha = max(alpha, coldRing * 0.34);
    alpha = max(alpha, infall * 0.25);
    alpha = max(alpha, dust * 0.14);
    alpha *= smoothstep(1.12, 0.95, r) * vertexColor.a;
    if (alpha <= 0.01) {
        discard;
    }

    fragColor = vec4(color, alpha) * ColorModulator;
}
