#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in float phase;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(59.7, 193.3))) * 19157.91);
}

float grid(float value, float width) {
    float d = abs(fract(value) - 0.5);
    return smoothstep(width, 0.0, d);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.08) {
        discard;
    }

    float time = GameTime * 530.0;
    float sphere = sqrt(max(0.0, 1.0 - r * r));
    float edge = smoothstep(0.48, 1.02, r);
    float boundary = exp(-abs(r - 0.93) / 0.026);
    float innerBoundary = exp(-abs(r - 0.72) / 0.07) * 0.34;
    float longitude = grid(atan(p.y, p.x) / 6.28318 * 24.0 + sphere * 1.7 + time * 0.08, 0.04);
    float latitude = grid((p.y + sphere * 0.4) * 8.0 + sin(time * 0.6) * 0.2, 0.035);
    float shear = pow(abs(sin((p.x * 17.0 - p.y * 11.0) + time * 1.65)), 18.0) * smoothstep(0.16, 1.0, r);
    float motes = step(0.986, hash(floor((p + vec2(time * 0.023, -time * 0.019)) * 44.0))) * smoothstep(0.18, 1.0, r);
    float lens = 0.28 / (0.14 + r * r);

    vec3 tint = max(vertexColor.rgb, vec3(0.07, 0.18, 0.28));
    vec3 color = tint * (0.10 + edge * 0.36);
    color += vec3(0.70, 0.98, 1.0) * boundary * (1.15 + phase * 0.35);
    color += vec3(0.42, 0.78, 1.0) * innerBoundary;
    color += vec3(0.95, 1.0, 1.0) * longitude * edge * 0.26;
    color += vec3(0.28, 0.70, 1.0) * latitude * edge * 0.22;
    color += vec3(0.54, 0.34, 1.0) * shear * 0.24;
    color += vec3(0.88, 1.0, 1.0) * motes * 0.34;
    color += tint.bgr * lens * 0.07;

    float alpha = edge * 0.16 + boundary * 0.76 + innerBoundary * 0.22;
    alpha = max(alpha, longitude * edge * 0.12);
    alpha = max(alpha, latitude * edge * 0.11);
    alpha = max(alpha, shear * 0.14);
    alpha = max(alpha, motes * 0.18);
    alpha *= smoothstep(1.08, 0.96, r) * vertexColor.a;
    if (alpha <= 0.01) {
        discard;
    }

    fragColor = vec4(color, alpha) * ColorModulator;
}
