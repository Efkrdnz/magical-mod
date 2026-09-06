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
    float time = GameTime * 6000.0;
    vec2 uv = texCoord0;

    // Molten time-energy flowing along every surface.
    float bands = fbm(uv * 4.0 + vec2(time * 0.012, -time * 0.007));
    // Fine "time static" grain that reshuffles constantly.
    float grain = hash(floor(uv * 90.0) + floor(time * 1.7));

    vec3 base = vertexColor.rgb;
    vec3 teal = vec3(0.35, 0.9, 0.85);
    vec3 color = base * (0.72 + bands * 0.5);
    color += teal * smoothstep(0.74, 0.95, bands) * 0.4;
    color *= 0.88 + grain * 0.24;

    // (The dying-star specks are no longer painted here: ChronosEndRenderer draws them as
    // real star quads so they follow the theme shift, zones, and palette like the shapes do.)
    float alpha = vertexColor.a * (0.8 + bands * 0.2);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
