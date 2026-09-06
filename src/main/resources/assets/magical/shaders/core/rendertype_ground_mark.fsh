#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // planar disc 0..1
in vec4 vertexColor;
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

const int SHOCK_RING = 0;
const int CRACK_WEB = 1;
const int SCORCH_DECAL = 2;
const int FROST_BLOOM = 3;
const int OVERGROWTH = 4;
const int LATTICE_GRID = 5;
const int RIPPLES = 6;
const int RAY_BURST = 7;
const int VORTEX_SPIRAL = 8;
const int MAW = 9;
const int CLOCK_SPOKES = 10;
const int INK_STAIN = 11;
const int SIGIL_SLAM_FLASH = 12;
const int EMBER_FIELD = 13;
const int HEX_CELLS = 14;
const int SPIRAL_DRAIN = 15;

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    float paramB = float(magicA.z);
    int mode = magicA.w;
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 120.0) + seed * 20.0;

    float rot = seed * 5.6 * (MAGIC_PI / 180.0) * 10.0;
    vec2 p0 = texCoord0 * 2.0 - 1.0;
    vec2 p = vec2(cos(rot) * p0.x - sin(rot) * p0.y, sin(rot) * p0.x + cos(rot) * p0.y);
    float r = length(p);
    if (r > 1.0) {
        discard;
    }
    float ang = atan(p.y, p.x);
    float softness = 0.05 + paramB * 0.02;
    float rim = 1.0 - smoothstep(0.9 - softness, 1.0, r);
    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;

    float bright = 0.0;   // additive-ish feature coverage
    float body = 0.0;     // dark body coverage (mode 1)
    vec3 col = tint;
    bool dark = false;

    if (kind == SHOCK_RING) {
        float radius = phase;
        float thick = mix(0.12, 0.03, phase);
        bright = exp(-pow((r - radius) / thick, 2.0)) * (1.0 - phase * 0.7) * 2.0;
    } else if (kind == CRACK_WEB) {
        float w = worley(Sampler0, p * 0.6 * count * 0.5 + seed);
        float edge = 1.0 - smoothstep(0.0, 0.05, abs(w - 0.5));
        float reach = step(r, phase * 1.05);
        bright = edge * reach * 1.4;
        body = (1.0 - edge) * 0.6 * reach;
        dark = true;
        col = mix(vec3(0.02), tint, edge);
    } else if (kind == SCORCH_DECAL) {
        float f = fbmTap(Sampler0, p * 0.7 + seed);
        float crust = smoothstep(0.35, 0.6, f) * (1.0 - smoothstep(0.6, 1.0, r));
        float embers = step(0.9, nz(Sampler0, p * 2.0 + seed + floor(t * 0.2) * 0.01)) * crust;
        float erodeKeep = step(phase, 1.0 - r * 0.9 + f * 0.2);
        body = crust * erodeKeep;
        bright = embers * (1.0 - phase) * 2.0;
        col = mix(vec3(0.02), tint, bright);
        dark = true;
    } else if (kind == FROST_BLOOM) {
        vec2 q = foldAngle(p, 6.0);
        float arm = strokeAA(q.y, 0.02) * step(q.x, phase);
        float branch = 0.0;
        for (int i = 1; i <= 4; i++) {
            float bx = float(i) * 0.2;
            vec2 bp = q - vec2(bx, 0.0);
            float d = min(sdSegment(bp, vec2(0.0), vec2(0.12, 0.12)), sdSegment(bp, vec2(0.0), vec2(0.12, -0.12)));
            branch = max(branch, strokeAA(d, 0.015) * step(bx, phase));
        }
        float crystals = pow(worley(Sampler0, p * 1.5 + seed), 4.0) * step(r, phase) * 0.5;
        bright = (arm + branch) * 1.4 + crystals;
        body = step(r, phase) * 0.25;
        col = mix(tint, vec3(0.9, 0.98, 1.0), 0.5);
        dark = true;
    } else if (kind == OVERGROWTH) {
        float f = fbmTap(Sampler0, p * 0.8 + seed);
        float spread = smoothstep(phase + 0.1, phase - 0.1, r - f * 0.3);
        float veins = 1.0 - smoothstep(0.0, 0.04, abs(f - 0.5));
        body = spread * 0.6;
        bright = veins * spread * 0.8;
        dark = true;
        col = mix(tint * 0.4, tint, veins);
    } else if (kind == LATTICE_GRID) {
        vec2 g = abs(fract(p * count * 0.5 + 0.5) - 0.5);
        float line = 1.0 - smoothstep(0.0, 0.03, min(g.x, g.y));
        float scan = exp(-abs(p.y - (fract(t * 0.05) * 2.0 - 1.0)) * 12.0);
        bright = line * 0.9 + scan * 0.8;
    } else if (kind == RIPPLES) {
        float rings = 0.0;
        for (int i = 0; i < 6; i++) {
            if (float(i) >= count) break;
            float radius = fract(phase + float(i) / count);
            rings += exp(-pow((r - radius) / 0.04, 2.0)) * (1.0 - radius);
        }
        bright = rings * 1.5;
    } else if (kind == RAY_BURST) {
        vec2 q = foldAngle(p, count);
        float ray = strokeAA(q.y, 0.01 + 0.03 * (1.0 - q.x)) * step(q.x, phase) * step(0.08, q.x);
        bright = ray * 1.6 + pow(max(1.0 - r * 4.0, 0.0), 2.0);
    } else if (kind == VORTEX_SPIRAL) {
        float swirl = ang * count * 0.5 + log(max(r, 0.02)) * 4.0 - t * 0.3;
        float arms = pow(0.5 + 0.5 * sin(swirl), 6.0);
        float pit = 1.0 - smoothstep(0.0, 0.3, r);
        bright = arms * (1.0 - r) * 1.4;
        body = pit * 0.7 + arms * 0.2;
        dark = true;
        col = mix(vec3(0.02), tint, clamp(arms, 0.0, 1.0));
    } else if (kind == MAW) {
        float lipR = phase;
        float serr = 0.04 * sin(ang * count * 1.0 + seed * 6.0) + 0.02 * sin(ang * count * 2.3);
        float lip = exp(-pow((r - lipR - serr) / 0.03, 2.0)) * 2.0;
        float pit = 1.0 - smoothstep(lipR - 0.1, lipR + serr, r);
        body = pit;
        bright = lip;
        dark = true;
        col = mix(vec3(0.01, 0.0, 0.02), tint, clamp(lip, 0.0, 1.0));
    } else if (kind == CLOCK_SPOKES) {
        vec2 q = foldAngle(p, count);
        float spoke = strokeAA(q.y, 0.012) * step(0.15, q.x) * step(q.x, 0.92);
        float ring = strokeAA(r - 0.92, 0.015);
        float handA = phase * MAGIC_TWO_PI;
        vec2 hd = vec2(cos(handA), sin(handA));
        float hand = strokeAA(sdSegment(p, vec2(0.0), hd * 0.8), 0.02) * 1.5;
        bright = spoke * 0.8 + ring + hand;
    } else if (kind == INK_STAIN) {
        float f = fbmTap(Sampler0, p * 0.6 + seed);
        float stain = 1.0 - smoothstep(0.6, 0.95, r + (f - 0.5) * 0.5);
        body = stain * 0.85;
        bright = (1.0 - smoothstep(0.0, 0.03, abs(f - 0.5))) * stain * 0.3;
        dark = true;
        col = mix(vec3(0.01), tint * 0.6, bright);
    } else if (kind == SIGIL_SLAM_FLASH) {
        float radius = mix(0.2, 1.0, phase);
        float whiten = 1.0 - smoothstep(0.0, 0.5, phase);
        float disc = 1.0 - smoothstep(radius - 0.1, radius, r);
        bright = disc * (1.0 - phase) * 2.5;
        col = mix(tint, vec3(1.0), whiten);
    } else if (kind == EMBER_FIELD) {
        vec2 g = floor(p * 8.0 + seed * 10.0);
        float h = magicHash(g + floor(t * 0.15));
        vec2 c = fract(p * 8.0 + seed * 10.0) - 0.5;
        float ember = step(0.7, h) * (1.0 - smoothstep(0.05, 0.25, length(c)));
        bright = ember * 2.0;
        body = 0.3;
        dark = true;
        col = mix(vec3(0.03), tint, clamp(bright, 0.0, 1.0));
    } else if (kind == HEX_CELLS) {
        vec2 rr = vec2(1.0, 1.7320508);
        vec2 hh = rr * 0.5;
        vec2 uvh = p * count * 0.5;
        vec2 a = mod(uvh, rr) - hh;
        vec2 b = mod(uvh - hh, rr) - hh;
        vec2 gv = dot(a, a) < dot(b, b) ? a : b;
        vec2 id = uvh - gv;
        float e = 0.5 - max(dot(abs(gv), normalize(vec2(1.0, 1.7320508))), abs(gv.x));
        float line = 1.0 - smoothstep(0.0, 0.05, e);
        float lit = step(0.75, magicHash(id + floor(t * 0.3) + seed));
        bright = line * 0.9 + lit * (1.0 - line) * 0.6;
    } else {                                    // SPIRAL_DRAIN
        float swirl = ang * 2.0 + log(max(r, 0.02)) * 6.0 + t * 0.4;
        float arms = pow(0.5 + 0.5 * sin(swirl), 4.0);
        float hole = 1.0 - smoothstep(0.0, 0.18, r);
        bright = arms * (1.0 - r) * 1.2;
        body = hole + arms * 0.3;
        dark = true;
        col = mix(vec3(0.02), tint, clamp(arms, 0.0, 1.0));
    }

    if (dark || mode == 1) {
        float cov = clamp(body + bright * 0.6, 0.0, 1.0) * rim * opacity;
        vec3 outCol = mix(col, hotOf(tint), clamp(bright * 0.4, 0.0, 1.0));
        fragColor = straightOut(outCol, cov) * ColorModulator;
    } else {
        fragColor = additiveOut(mix(col, hotOf(tint), clamp(bright * 0.3, 0.0, 1.0)), bright * rim, opacity) * ColorModulator;
    }
    if (fragColor.a <= 0.003) {
        discard;
    }
}
