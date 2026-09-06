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
    // GameTime cycles [0,1) per day; scale up for per-second motion.
    float time = GameTime * 6000.0;
    vec2 uv = texCoord0; // u: mirrored angle, v: 0 at ground, 1 at crown

    // Rising haze: dark energy streaming up the shell.
    vec2 flow = vec2(uv.x * 3.0 + time * 0.006, uv.y * 6.0 - time * 0.045);
    float hazeA = fbm(flow * 2.2);
    float hazeB = fbm(flow * 5.1 + vec2(7.7, 3.1));
    float haze = smoothstep(0.28, 0.85, hazeA);

    // Pulse rings sweeping from the ground to the crown.
    float ringPhase = uv.y * 7.0 - time * 0.05;
    float ring = pow(abs(sin(ringPhase * 3.14159)), 22.0);
    ring *= 0.45 + 0.55 * sin(time * 0.7 + uv.x * 11.0);

    // Crackling veins climbing the surface.
    float vein = smoothstep(0.05, 0.0, abs(hazeB - 0.5));
    vein *= 0.45 + 0.55 * sin(time * 1.6 + uv.y * 46.0 + hazeA * 8.0);

    // Power surges: irregular discharge bursts that erupt, blast a jagged shockwave
    // outward, and die. Rows are staggered and edges are torn by noise so no burst
    // is square, aligned, or symmetric.
    float row = floor(uv.y * 22.0);
    vec2 cellUv = vec2(uv.x * 9.0 + hash(vec2(row, 7.1)) * 4.0, uv.y * 22.0);
    vec2 cell = floor(cellUv);
    float gate = hash(cell + floor(time * 0.35));
    float active = step(0.94, gate);
    vec2 local = fract(cellUv) - 0.5;
    float tear = fbm(uv * vec2(21.0, 34.0) + gate * 37.0 + time * 0.09);
    float dist = max(0.0, length(local) + (tear - 0.5) * 0.6);
    float life = fract(time * 0.35 + gate * 5.0);
    float shockwave = exp(-abs(dist - life * 0.8) * 9.0) * (1.0 - life) * (1.0 - life);
    float core = exp(-dist * 7.0) * max(0.0, 1.0 - life * 1.6);
    float surge = active * clamp(shockwave + core, 0.0, 1.4);

    vec3 abyss = vec3(0.05, 0.012, 0.10);
    vec3 violet = vec3(0.30, 0.11, 0.58);
    vec3 arcane = vec3(0.28, 0.72, 1.0);
    vec3 emberRed = vec3(0.80, 0.10, 0.05);
    vec3 whiteHot = vec3(1.0, 0.82, 0.55);

    vec3 color = abyss;
    color += violet * (0.30 + haze * 0.62);
    color += arcane * vein * (0.85 + surge * 1.6);
    color += arcane * ring * 0.75;
    color += emberRed * active * shockwave * 1.1;
    color += whiteHot * active * core * 0.95;
    color += violet * smoothstep(0.62, 0.95, hazeB) * 0.30;

    // Aura, not a wall: low base opacity that spikes where the energy does.
    float alpha = vertexColor.a * (0.14 + haze * 0.12 + ring * 0.30 + vein * (0.30 + surge * 0.35) + surge * 0.45);
    fragColor = vec4(color, clamp(alpha, 0.0, 0.85)) * ColorModulator;
}
