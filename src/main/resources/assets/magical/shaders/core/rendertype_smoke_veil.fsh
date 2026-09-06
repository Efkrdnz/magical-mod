#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0; // noise
uniform sampler2D Sampler1; // emblem atlas (RUNE_MOTE)
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // billboard 0..1; for SPARK_STREAK / DROPLET the quad is stretched along velocity (v = along)
in vec4 vertexColor;
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

const int SMOKE_PUFF = 0;
const int MIST_WISP = 1;
const int EMBER_CLUSTER = 2;
const int ASH_FLAKE = 3;
const int INK_BLOOM = 4;
const int SPORE_DOTS = 5;
const int PETAL = 6;
const int FEATHER = 7;
const int HEX_FRAGMENT = 8;
const int FROST_CRYSTAL = 9;
const int GLASS_SPLINTER = 10;
const int SPARK_STREAK = 11;
const int RUNE_MOTE = 12;
const int DROPLET = 13;
const int LENS_SPARKLE = 14;
const int DUST = 15;

void main() {
    int kind = magicA.x;
    int count = magicA.y;
    int paramB = magicA.z;
    int mode = magicA.w;
    float life = magicB.x;   // 0..1 particle age
    float seed = magicB.y;
    float t = magicTime(GameTime, 200.0);

    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    vec3 tint = vertexColor.rgb;
    float intensity = vertexColor.a;
    float softness = float(count) / 63.0;
    float shade = float(paramB) / 31.0;   // 0 dark ink .. 1 lit

    // rotation per particle
    float rot = seed * MAGIC_TWO_PI + life * (seed - 0.5) * 2.0;
    vec2 q = vec2(cos(rot) * p.x - sin(rot) * p.y, sin(rot) * p.x + cos(rot) * p.y);

    float shape = 0.0;
    bool dark = false;
    vec3 col = tint;

    if (kind == SMOKE_PUFF || kind == DUST) {
        float n = nz(Sampler0, q * 0.4 + seed * 5.0);
        float radius = 0.55 + softness * 0.35 + n * 0.25;
        shape = 1.0 - smoothstep(radius - 0.35, radius, r);
        dark = kind == SMOKE_PUFF;
        col = mix(tint * 0.15, tint * 0.6, shade);
        if (kind == DUST) { col = tint * 0.7; }
    } else if (kind == MIST_WISP) {
        float n = fbmTap(Sampler0, q * 0.5 + seed * 4.0 + t * 0.002);
        shape = (1.0 - smoothstep(0.3, 0.95, r)) * (0.4 + n * 0.8);
        col = mix(tint, vec3(1.0), 0.3);
    } else if (kind == EMBER_CLUSTER) {
        vec2 g = floor(q * 3.0 + seed * 10.0);
        vec2 c = fract(q * 3.0 + seed * 10.0) - 0.5;
        float h = magicHash(g + floor(t * 0.1));
        shape = step(0.5, h) * (1.0 - smoothstep(0.1, 0.3, length(c))) * (1.0 - smoothstep(0.6, 1.0, r)) * 2.0;
        col = mix(tint, vec3(1.0, 0.95, 0.7), 0.4);
    } else if (kind == ASH_FLAKE) {
        float d = sdBox(q, vec2(0.45, 0.25));
        shape = 1.0 - smoothstep(-0.02, 0.05, d);
        dark = true;
        col = mix(vec3(0.08), tint * 0.5, shade);
    } else if (kind == INK_BLOOM) {
        float n = fbmTap(Sampler0, q * 0.45 + seed * 6.0);
        float radius = 0.4 + life * 0.5 + n * 0.3;
        shape = 1.0 - smoothstep(radius - 0.25, radius, r);
        dark = true;
        col = mix(vec3(0.01, 0.0, 0.02), tint * 0.35, shade * 0.5);
    } else if (kind == SPORE_DOTS) {
        vec2 c = fract(q * 2.5 + seed * 7.0) - 0.5;
        shape = (1.0 - smoothstep(0.08, 0.2, length(c))) * (1.0 - smoothstep(0.5, 1.0, r));
        col = tint;
    } else if (kind == PETAL) {
        shape = 1.0 - smoothstep(-0.02, 0.06, sdPetal(q * 1.2, 1.2, 0.35) - 0.12);
        col = mix(tint, vec3(1.0), 0.2);
    } else if (kind == FEATHER) {
        float spine = strokeAA(q.x, 0.03) * step(abs(q.y), 0.9);
        float vane = (1.0 - smoothstep(0.0, 0.05, abs(q.x) - 0.45 * (1.0 - abs(q.y)))) * (0.5 + 0.5 * pow(abs(sin(q.y * 30.0)), 2.0));
        shape = max(spine, vane * 0.8);
        col = mix(tint, vec3(1.0), 0.4);
    } else if (kind == HEX_FRAGMENT) {
        shape = 1.0 - smoothstep(-0.02, 0.05, sdNgon(q, 0.7, 6.0));
        shape = max(shape * 0.5, strokeAA(sdNgon(q, 0.7, 6.0), 0.04));
        col = tint;
    } else if (kind == FROST_CRYSTAL) {
        vec2 f = foldAngle(q, 6.0);
        float arm = strokeAA(f.y, 0.04) * step(f.x, 0.9);
        float branch = strokeAA(min(sdSegment(f - vec2(0.5, 0.0), vec2(0.0), vec2(0.2, 0.2)), sdSegment(f - vec2(0.5, 0.0), vec2(0.0), vec2(0.2, -0.2))), 0.03);
        shape = max(arm, branch);
        col = mix(tint, vec3(0.92, 0.98, 1.0), 0.6);
    } else if (kind == GLASS_SPLINTER) {
        float d = abs(q.x) / 0.18 + abs(q.y);
        shape = 1.0 - smoothstep(0.85, 1.0, d);
        shape = max(shape * 0.4, exp(-abs(d - 0.95) * 20.0));
        col = mix(tint, vec3(1.0), 0.5);
    } else if (kind == SPARK_STREAK) {
        // comet: head at v=1 (top), tail toward v=0
        float along = texCoord0.y;
        float width = 0.25 * along + 0.02;
        float body = (1.0 - smoothstep(width, width + 0.1, abs(p.x))) * pow(along, 1.5);
        float head = 1.0 - smoothstep(0.0, 0.35, length(vec2(p.x, (1.0 - along) * 2.0)));
        shape = body + head * 1.5;
        col = mix(tint, vec3(1.0), head);
    } else if (kind == RUNE_MOTE) {
        int id = count | (paramB << 6);
        float cx = float(id & 15);
        float cy = float(id >> 4);
        vec2 uv = (vec2(cx, cy) + 0.5 + q * 0.42) / 16.0;
        float d = texture(Sampler1, uv).r;
        shape = smoothstep(0.45, 0.55, d) + smoothstep(0.2, 0.5, d) * 0.3;
        col = mix(tint, vec3(1.0), 0.3);
    } else if (kind == DROPLET) {
        float along = texCoord0.y;
        float d = length(vec2(p.x, (along - 0.4) * 1.2)) - 0.45 + along * 0.25;
        shape = 1.0 - smoothstep(-0.02, 0.06, d);
        shape = max(shape * 0.5, exp(-abs(d) * 25.0));
        col = mix(tint, vec3(1.0), 0.5);
    } else {                                     // LENS_SPARKLE
        float star = pow(abs(cos(atan(q.y, q.x) * 2.0)), 40.0) * (1.0 - r);
        shape = star * 2.0 + pow(max(1.0 - r * 3.0, 0.0), 2.0);
        col = mix(tint, vec3(1.0), 0.6);
    }

    // erosion over life so particles dissolve instead of popping
    float n = nz(Sampler0, q * 0.6 + seed * 9.0);
    float erode = smoothstep(life, life + 0.2, n + 0.15);
    float sizeCurve = smoothstep(0.0, 0.1, life) * (1.0 - smoothstep(0.75, 1.0, life));
    float alpha = clamp(shape, 0.0, 1.0) * erode * sizeCurve * intensity;

    if (dark || mode == 1) {
        fragColor = straightOut(col, alpha) * ColorModulator;
    } else {
        fragColor = additiveOut(col, clamp(shape, 0.0, 1.5) * erode * sizeCurve, intensity) * ColorModulator;
    }
    if (fragColor.a <= 0.003) {
        discard;
    }
}
