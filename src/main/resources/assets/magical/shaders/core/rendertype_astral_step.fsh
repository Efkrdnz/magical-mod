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
    float time = GameTime * 280.0;
    vec2 flow = texCoord0 + vec2(time * 0.018, -time * 0.011);
    float nebulaA = fbm(flow * 3.0);
    float nebulaB = fbm((flow + vec2(19.2, 7.4)) * 6.5);
    float mist = smoothstep(0.30, 0.92, nebulaA) * 0.55 + smoothstep(0.42, 0.86, nebulaB) * 0.35;

    vec2 starGridA = floor((texCoord0 + vec2(time * 0.004, -time * 0.002)) * 58.0);
    vec2 starGridB = floor((texCoord0 + vec2(-time * 0.006, time * 0.003)) * 123.0);
    float stars = step(0.982, hash(starGridA)) * 0.75 + step(0.993, hash(starGridB)) * 1.15;
    float twinkle = 0.72 + 0.28 * sin(time * 4.0 + hash(starGridB) * 36.0);

    vec3 voidBlue = vec3(0.015, 0.04, 0.13);
    vec3 deepBlue = vec3(0.04, 0.25, 0.55);
    vec3 astralBlue = vec3(0.18, 0.72, 1.0);
    vec3 paleBlue = vec3(0.62, 0.92, 1.0);
    vec3 violet = vec3(0.21, 0.12, 0.48);

    vec3 color = voidBlue;
    color = mix(color, deepBlue, 0.35 + mist * 0.65);
    color += astralBlue * (0.18 + mist * 0.58);
    color += violet * smoothstep(0.55, 0.95, nebulaB) * 0.32;
    color += paleBlue * stars * twinkle;

    float alpha = vertexColor.a * (0.58 + mist * 0.28 + min(stars, 1.0) * 0.14);
    alpha = clamp(alpha, 0.0, vertexColor.a);

    fragColor = vec4(color, alpha) * ColorModulator;
}
