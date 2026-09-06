#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in float riftPulse;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.31, 289.17))) * 97123.1337);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float time = GameTime * 920.0;
    float verticalFade = smoothstep(1.0, 0.72, abs(p.y));
    float core = exp(-abs(p.x + sin(p.y * 10.0 + time) * 0.035) * 12.0);
    float edgeL = exp(-abs(p.x + 0.22 + sin(p.y * 7.0 - time * 0.7) * 0.05) * 18.0);
    float edgeR = exp(-abs(p.x - 0.22 + cos(p.y * 8.0 + time * 0.6) * 0.05) * 18.0);
    float fracture = step(0.82, hash(floor(vec2(p.x * 7.0 + time * 0.07, p.y * 28.0 - time * 0.13))));
    float scan = pow(abs(sin(p.y * 38.0 - time * 1.8)), 18.0) * smoothstep(0.82, 0.0, abs(p.x));
    float voidMask = smoothstep(0.28, 0.03, abs(p.x)) * verticalFade;

    vec3 voidBlack = vec3(0.0, 0.003, 0.018);
    vec3 cyan = vec3(0.30, 0.90, 1.0);
    vec3 white = vec3(0.92, 1.0, 1.0);
    vec3 violet = vec3(0.42, 0.22, 1.0);

    vec3 color = voidBlack * voidMask;
    color += cyan * core * (0.85 + riftPulse * 0.55);
    color += white * scan * 0.62;
    color += violet * (edgeL + edgeR) * 0.72;
    color += white * fracture * 0.30 * verticalFade * smoothstep(0.62, 0.02, abs(p.x));

    float alpha = max(voidMask * 0.72, core * 0.96);
    alpha = max(alpha, (edgeL + edgeR) * 0.55);
    alpha = max(alpha, scan * 0.34);
    alpha *= verticalFade * vertexColor.a;
    if (alpha <= 0.01) {
        discard;
    }
    fragColor = vec4(color, alpha) * ColorModulator;
}
