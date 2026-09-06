#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

const int VERTICAL_WOUND = 0;
const int HORIZONTAL_SLIT_EYE = 1;
const int BLADE = 2;
const int IRIS_MOUTH = 3;
const int SPIRAL_TEAR = 4;
const int SHATTER = 5;
const int SEAM_HAIRLINE = 6;
const int ZIPPER = 7;
const int RADIAL_STAR_CRACK = 8;
const int RING_WOUND = 9;
const int GLITCH_CUT = 10;

// interior styles (paramB): 0 void black, 1 lensed stars, 2 white light, 3 tinted realm with veins
vec3 interior(int style, vec2 p, vec3 tint, float t) {
    if (style == 1) {
        vec2 bent = p * (1.0 + 0.38 / (dot(p, p) + 0.075));
        float stars = step(0.975, magicHash(floor(bent * 40.0 + t * 0.001)));
        return vec3(0.01, 0.0, 0.03) + stars * vec3(0.9, 0.95, 1.0);
    } else if (style == 2) {
        return vec3(1.0, 0.98, 0.9);
    } else if (style == 3) {
        float f = fbmTap(Sampler0, p * 0.5 + t * 0.003);
        float veins = 1.0 - smoothstep(0.0, 0.05, abs(f - 0.5));
        float beat = pow(max(sin(t * 0.13), 0.0), 6.0);
        return mix(tint * 0.06, mix(tint, vec3(1.0), beat), veins);
    }
    return vec3(0.005, 0.002, 0.01);
}

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    int paramB = magicA.z;
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 140.0) + seed * 25.0;

    vec2 p = texCoord0 * 2.0 - 1.0;
    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;

    // open curve: 0-0.1 hairline, 0.1-0.5 rip open, 0.5-0.8 hold (heartbeat), 0.8-1 heal
    float lineForm = smoothstep(0.0, 0.1, phase);
    float open = smoothstep(0.1, 0.5, phase) * (1.0 - smoothstep(0.8, 1.0, phase));
    float beat = pow(max(sin(t * 0.13), 0.0), 8.0) * step(0.5, phase) * step(phase, 0.8);
    float serr = (fbmTap(Sampler0, vec2(p.y * 0.35 * count * 0.2, seed)) - 0.5) * 0.25
               + (nz(Sampler0, vec2(p.y * 1.4 * count * 0.2 + 3.0, seed + t * 0.002)) - 0.5) * 0.08;

    float inside = 0.0;   // interior coverage
    float lip = 0.0;      // bright lip coverage
    float corona = 0.0;   // dark corona alpha
    float veins = 0.0;

    if (kind == VERTICAL_WOUND || kind == BLADE) {
        float centre = sin(p.y * 3.0 + t * 0.15) * 0.05 * open + (fbmTap(Sampler0, vec2(0.3, p.y * 0.3 + seed)) - 0.5) * 0.2 * open;
        float prof = 1.0 - pow(abs(p.y), 2.5);          // tapered tips
        float w = (0.02 + open * 0.42) * prof + serr * open * prof;
        float d = abs(p.x - centre);
        inside = 1.0 - smoothstep(w - 0.01, w + 0.01, d);
        lip = exp(-abs(d - w) * (kind == BLADE ? 40.0 : 22.0)) * (0.5 + lineForm) * prof;
        corona = exp(-max(d - w, 0.0) * 4.5) * open * 0.7 * prof;
        veins = (1.0 - smoothstep(0.0, 0.04, abs(fbmTap(Sampler0, vec2(p.x * 0.6, p.y * 0.4 + seed)) - 0.5))) * (1.0 - inside) * open * prof * exp(-max(d - w, 0.0) * 3.0);
        if (kind == BLADE) {
            float scan = pow(abs(sin(p.y * 38.0 - t * 1.8)), 18.0);
            lip += scan * inside * 0.5;
        }
    } else if (kind == HORIZONTAL_SLIT_EYE) {
        float lidH = (0.05 + 0.5 * open) * (1.0 - p.x * p.x) + serr * open * 0.5;
        float d = abs(p.y);
        inside = 1.0 - smoothstep(lidH - 0.01, lidH + 0.01, d);
        float pupilX = (seed - 0.5) * 0.6 + sin(t * 0.05) * 0.1;
        float pupil = 1.0 - smoothstep(0.18, 0.24, length(vec2((p.x - pupilX) * 0.7, p.y)) );
        lip = exp(-abs(d - lidH) * 18.0) * (0.6 + lineForm);
        corona = exp(-max(d - lidH, 0.0) * 5.0) * open * 0.6;
        veins = pupil * inside;
    } else if (kind == IRIS_MOUTH) {
        float r = length(p);
        float ang = atan(p.y, p.x);
        float lobes = 0.06 * sin(ang * count + seed * 6.0);
        float radius = (0.02 + open * 0.8) + lobes * open;
        inside = 1.0 - smoothstep(radius - 0.02, radius + 0.02, r);
        lip = exp(-abs(r - radius) * 22.0) * (0.6 + lineForm);
        corona = exp(-max(r - radius, 0.0) * 5.0) * open * 0.7;
    } else if (kind == SPIRAL_TEAR) {
        float r = length(p);
        float ang = atan(p.y, p.x);
        float spiral = fract((ang / MAGIC_TWO_PI) + r * 2.5 - t * 0.05);
        float arm = 1.0 - smoothstep(0.0, 0.12 * open + 0.01, abs(spiral - 0.5));
        float radius = 0.05 + open * 0.85;
        inside = arm * (1.0 - smoothstep(radius - 0.05, radius, r));
        lip = exp(-abs(spiral - 0.5) * 30.0) * (1.0 - smoothstep(radius - 0.1, radius, r)) * (0.6 + lineForm);
        corona = (1.0 - smoothstep(radius, radius + 0.3, r)) * open * 0.5;
    } else if (kind == SHATTER) {
        float w = worley(Sampler0, p * 0.7 * count * 0.2 + seed);
        float edge = 1.0 - smoothstep(0.0, 0.05, abs(w - 0.5));
        float reach = step(length(p), phase * 1.1);
        lip = edge * reach * 1.6;
        inside = (1.0 - edge) * reach * pow(w, 3.0) * 0.6;
        corona = reach * 0.25;
    } else if (kind == SEAM_HAIRLINE) {
        float d = abs(p.y + (nz(Sampler0, vec2(p.x * 0.3 + seed, 0.2)) - 0.5) * 0.1);
        float len = step(abs(p.x), phase);
        lip = exp(-d * 60.0) * 2.0 * len + exp(-abs(abs(p.x) - phase) * 30.0) * 1.5 * step(phase, 0.999);
        inside = 0.0;
        corona = exp(-d * 6.0) * len * 0.3;
    } else if (kind == ZIPPER) {
        float teeth = step(0.5, fract(p.x * count * 0.5 + 0.25));
        float side = sign(p.y);
        float gap = 0.03 + open * 0.35 * (1.0 - p.x * p.x);
        float toothH = 0.12;
        float toothMask = teeth * step(abs(p.y), gap + toothH) * step(gap, abs(p.y)) * (side > 0.0 ? step(0.5, fract(p.x * count * 0.5)) : step(fract(p.x * count * 0.5), 0.5));
        inside = 1.0 - smoothstep(gap - 0.01, gap + 0.01, abs(p.y));
        lip = toothMask * 1.5 + exp(-abs(abs(p.y) - gap) * 25.0) * 0.8;
        corona = exp(-max(abs(p.y) - gap, 0.0) * 5.0) * open * 0.5;
    } else if (kind == RADIAL_STAR_CRACK) {
        vec2 q = foldAngle(p, count);
        float crackW = (0.01 + open * 0.06) * (1.0 - q.x);
        float wob = (nz(Sampler0, vec2(q.x * 0.5 + seed, 0.4)) - 0.5) * 0.06;
        float d = abs(q.y - wob);
        float reach = step(q.x, 0.15 + open * 0.85);
        inside = (1.0 - smoothstep(crackW - 0.005, crackW + 0.005, d)) * reach;
        lip = exp(-abs(d - crackW) * 40.0) * reach * (0.6 + lineForm);
        veins = exp(-d * 10.0) * reach * open * 0.7;
        float centreHole = 1.0 - smoothstep(0.05 + open * 0.12, 0.08 + open * 0.15, length(p));
        inside = max(inside, centreHole * open);
        corona = exp(-max(d - crackW, 0.0) * 6.0) * reach * open * 0.4;
    } else if (kind == RING_WOUND) {
        float r = length(p);
        float radius = 0.6;
        float w = (0.01 + open * 0.12) + serr * open * 0.4;
        float d = abs(r - radius);
        inside = 1.0 - smoothstep(w - 0.01, w + 0.01, d);
        lip = exp(-abs(d - w) * 25.0) * (0.6 + lineForm);
        corona = exp(-max(d - w, 0.0) * 6.0) * open * 0.6;
        veins = (1.0 - smoothstep(0.0, 0.05, abs(fbmTap(Sampler0, p * 0.5 + seed) - 0.5))) * open * exp(-max(d - w, 0.0) * 3.0);
    } else {                                     // GLITCH_CUT
        float band = floor(p.y * 9.0 + seed * 3.0);
        float reroll = floor(t * 0.6);
        float shift = (magicHash(band + reroll) - 0.5) * 0.5 * open;
        float seam = abs(p.x - shift * 0.4);
        float d = seam;
        inside = 1.0 - smoothstep(0.02 + open * 0.1, 0.03 + open * 0.11, d);
        float slice = step(0.6, magicHash(band * 3.1 + reroll)) * step(abs(p.x - shift), 0.9) * 0.35 * open;
        lip = exp(-d * 30.0) * (0.5 + lineForm) + slice;
        corona = slice * 0.5;
    }

    // noisy edge fade well inside the quad so nothing prints a rectangle
    vec2 edge = 1.0 - abs(p);
    float edgeFade = smoothstep(0.0, 0.25, min(edge.x, edge.y) + (nz(Sampler0, p * 1.7 + seed) - 0.5) * 0.15);

    vec3 realm = interior(paramB & 3, p, tint, t);
    vec3 lipCol = mix(tint, vec3(1.0), 0.5 + beat * 0.5);
    vec3 col = realm * inside + lipCol * lip + tint * veins * 0.8;
    float alpha = clamp(inside + lip + corona + veins * 0.6, 0.0, 1.0) * edgeFade * opacity;
    fragColor = vec4(col, alpha) * ColorModulator;
    if (fragColor.a <= 0.003) {
        discard;
    }
}
