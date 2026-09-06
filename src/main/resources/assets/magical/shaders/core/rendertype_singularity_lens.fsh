#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in float gravityPulse;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float ring(float r, float target, float width) {
    return exp(-abs(r - target) / max(width, 0.0001));
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.08) {
        discard;
    }

    float angle = atan(p.y, p.x);
    float time = GameTime * 900.0;
    float eventHorizon = smoothstep(0.50, 0.34, r);
    float photonRing = ring(r, 0.49 + sin(time * 0.21) * 0.012, 0.032);
    float outerRing = ring(r, 0.72, 0.055);
    float lensBand = ring(r + sin(angle * 3.0 + time * 0.65) * 0.035, 0.62, 0.040);

    vec2 bent = p * (1.0 + 0.38 / (r * r + 0.075));
    float starA = step(0.982, hash(floor((bent + vec2(time * 0.013, -time * 0.007)) * 28.0)));
    float starB = step(0.965, hash(floor((bent * vec2(16.0, 42.0)) + vec2(-time * 0.021, time * 0.012))));
    float streaks = pow(abs(sin(angle * 9.0 + time * 1.2)), 26.0) * smoothstep(0.38, 0.95, r) * (1.0 - smoothstep(0.92, 1.08, r));

    vec3 deepBlack = vec3(0.0, 0.0, 0.004);
    vec3 coldBlue = vec3(0.24, 0.74, 1.0);
    vec3 violet = vec3(0.45, 0.24, 1.0);
    vec3 whiteHot = vec3(0.95, 1.0, 1.0);

    vec3 color = deepBlack;
    color += coldBlue * photonRing * (1.25 + gravityPulse * 0.45);
    color += violet * outerRing * 0.75;
    color += whiteHot * lensBand * 0.55;
    color += coldBlue * streaks * 0.65;
    color += whiteHot * starA * 0.38 * smoothstep(0.52, 1.02, r);
    color += violet * starB * 0.20 * smoothstep(0.48, 1.0, r);
    color = mix(color, deepBlack, eventHorizon * 0.96);

    float alpha = max(eventHorizon * 0.98, photonRing * 0.92);
    alpha = max(alpha, outerRing * 0.36);
    alpha = max(alpha, lensBand * 0.30);
    alpha = max(alpha, (starA + starB) * 0.22);
    alpha *= smoothstep(1.08, 0.92, r);
    alpha *= vertexColor.a;

    fragColor = vec4(color, alpha) * ColorModulator;
}
