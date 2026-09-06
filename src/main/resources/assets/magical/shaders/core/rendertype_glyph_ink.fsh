#version 150

#moj_import <magical:magic_common.glsl>
#moj_import <magical:magic_frag.glsl>

uniform sampler2D Sampler0; // noise atlas
uniform sampler2D Sampler1; // sigil emblem SDF atlas (16x16 cells)
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
flat in ivec4 magicA; // kind, count, paramB, mode
flat in vec2 magicB;  // phase, seed

out vec4 fragColor;

// Band kinds (geometry = unit annulus, u = angle01, v = across 0..1)
const int SOLID_RING = 0;
const int DASHED_RING = 1;
const int TICK_BAND = 2;
const int RUNE_BAND = 3;
const int WAVE_BAND = 4;
const int BRAID_BAND = 5;
const int CHAIN_BAND = 6;
const int PETAL_BAND = 7;
const int TOOTH_BAND = 8;
const int FACET_BAND = 9;
const int STAMP_BAND = 10;
const int ARC_SWEEP = 11;
// Planar kinds (geometry = square quad, uv 0..1 -> p in -1..1)
const int FRAME = 16;
const int STAR = 17;
const int PENTAGRAM = 18;
const int SPOKES = 19;
const int LATTICE = 20;
const int ORBIT_SEAL = 21;
const int CORE = 22;
const int EMBLEM = 23;

// One atlas cell lookup: id 0..255, local coordinate q in -1..1.
float atlasInk(int id, vec2 q, float aa) {
    float cx = float(id & 15);
    float cy = float(id >> 4);
    vec2 uv = (vec2(cx, cy) + 0.5 + q * 0.42) / 16.0;
    float d = texture(Sampler1, uv).r;
    return smoothstep(0.5 - aa, 0.5 + aa, d);
}

float atlasGlow(int id, vec2 q) {
    float cx = float(id & 15);
    float cy = float(id >> 4);
    vec2 uv = (vec2(cx, cy) + 0.5 + q * 0.42) / 16.0;
    float d = texture(Sampler1, uv).r;
    return smoothstep(0.15, 0.5, d) * 0.45;
}

// Rune cell: 3-5 pseudo-strokes chosen by hash, drawn in a -1..1 cell frame.
float runeCell(vec2 q, float cellSeed) {
    float ink = 0.0;
    int strokes = 3 + int(magicHash(cellSeed + 3.1) * 2.99);
    for (int i = 0; i < 5; i++) {
        if (i >= strokes) break;
        float h = cellSeed * 7.0 + float(i) * 13.7;
        vec2 a = vec2(magicHash(h) * 1.6 - 0.8, magicHash(h + 1.0) * 1.6 - 0.8);
        vec2 b = vec2(magicHash(h + 2.0) * 1.6 - 0.8, magicHash(h + 3.0) * 1.6 - 0.8);
        // snap to a 3x3 lattice so runes read as glyphs, not scribbles
        a = floor(a * 1.5 + 0.5) / 1.5;
        b = floor(b * 1.5 + 0.5) / 1.5;
        float d = sdSegment(q, a, b);
        ink = max(ink, strokeAA(d, 0.09));
    }
    return ink;
}

void main() {
    int kind = magicA.x;
    int count = max(magicA.y, 1);
    int paramB = magicA.z;
    int mode = magicA.w;
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 120.0);
    float tSlow = magicTime(GameTime, 24.0);

    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;
    float ink = 0.0;   // 0..1 stroke coverage
    float glow = 0.0;  // soft halo around strokes
    float penTip = 0.0;

    // lifecycle windows
    float inkOn = clamp(phase * 4.0, 0.0, 1.0);          // 0..0.25 -> 0..1
    float dissolve = clamp((phase - 0.75) * 4.0, 0.0, 1.0); // 0.75..1 -> 0..1

    if (kind < 16) {
        // ---------------- BAND family ----------------
        float u = texCoord0.x;
        float v = texCoord0.y;
        float across = abs(v * 2.0 - 1.0);          // 0 middle, 1 edge
        float thickness = 0.06 * pow(1.25, float(paramB)); // stroke half-width in band units
        float fc = float(count);

        if (kind == SOLID_RING) {
            ink = strokeAA(across, thickness * 2.0);
        } else if (kind == DASHED_RING) {
            float dash = step(0.5, fract(u * fc + seed));
            ink = strokeAA(across, thickness * 2.0) * dash;
        } else if (kind == TICK_BAND) {
            float cell = fract(u * fc);
            int idx = int(floor(u * fc));
            float notch = ((idx & 3) == 0) ? 1.0 : 0.55;
            float tick = 1.0 - smoothstep(0.05 * notch, 0.05 * notch + 0.02, abs(cell - 0.5));
            float within = 1.0 - smoothstep(notch, notch + 0.03, across);
            ink = tick * within;
        } else if (kind == RUNE_BAND) {
            float cellIdx = floor(u * fc);
            vec2 q = vec2(fract(u * fc) * 2.0 - 1.0, v * 2.0 - 1.0);
            q.x *= 1.15;
            ink = runeCell(q, cellIdx + seed * 64.0);
        } else if (kind == WAVE_BAND) {
            float wave = sin(u * fc * MAGIC_TWO_PI + tSlow * 0.35 + seed * 6.0) * 0.55;
            ink = strokeAA((v * 2.0 - 1.0) - wave, thickness * 1.6);
        } else if (kind == BRAID_BAND) {
            float w1 = sin(u * fc * MAGIC_TWO_PI + seed * 6.0) * 0.55;
            float w2 = sin(u * fc * MAGIC_TWO_PI + MAGIC_PI + seed * 6.0) * 0.55;
            float c = v * 2.0 - 1.0;
            ink = max(strokeAA(c - w1, thickness * 1.4), strokeAA(c - w2, thickness * 1.4));
        } else if (kind == CHAIN_BAND) {
            // lemniscate links: |sin| lobes alternating sides
            float lu = fract(u * fc) * 2.0 - 1.0;
            float c = v * 2.0 - 1.0;
            float lobe = sin(lu * MAGIC_PI) * 0.7;
            float d1 = abs(length(vec2(lu * 1.3, c - lobe * 0.5)) - 0.42);
            ink = strokeAA(d1, thickness * 1.3);
        } else if (kind == PETAL_BAND) {
            float lu = fract(u * fc) * 2.0 - 1.0;
            vec2 q = vec2(lu * 1.3, v * 2.0 - 1.0);
            ink = strokeAA(sdPetal(q, 1.5, 0.35), thickness * 1.6);
        } else if (kind == TOOTH_BAND) {
            float lu = fract(u * fc) * 2.0 - 1.0;
            float c = v * 2.0 - 1.0;
            float tooth = 1.0 - abs(lu) * 1.4; // triangle rising toward the outer edge
            float edge = strokeAA(c - (tooth * 1.6 - 1.0), thickness * 1.5);
            float base = strokeAA(c + 0.95, thickness * 1.2);
            ink = max(edge, base);
        } else if (kind == FACET_BAND) {
            float lu = fract(u * fc) * 2.0 - 1.0;
            float c = v * 2.0 - 1.0;
            float dia = abs(lu) + abs(c) * 0.75;
            ink = strokeAA(dia - 0.85, thickness * 1.5);
        } else if (kind == STAMP_BAND) {
            float cell = fract(u * fc);
            vec2 q = vec2((cell - 0.5) * 2.4, v * 2.0 - 1.0);
            ink = atlasInk(paramB, q, 0.06);
            glow += atlasGlow(paramB, q);
        } else if (kind == ARC_SWEEP) {
            // phase is the fill fraction of a charge meter
            float filled = step(u, phase);
            float head = exp(-abs(u - phase) * 40.0) * 2.0;
            ink = strokeAA(across, thickness * 2.4) * filled;
            glow += head * strokeAA(across, thickness * 3.0);
            inkOn = 1.0;
            dissolve = 0.0;
        }

        // ink-on: the pen writes around the band with a hot tip
        if (kind != ARC_SWEEP) {
            float written = step(u, inkOn);
            penTip = exp(-abs(u - inkOn) * 60.0) * 3.0 * (1.0 - step(0.999, inkOn));
            ink *= written;
            glow += penTip * (1.0 - across);
        }
    } else {
        // ---------------- PLANAR family ----------------
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r = length(p);
        if (r > 1.02 && kind != EMBLEM) {
            discard;
        }
        float fc = float(count);
        float thickness = 0.018 * pow(1.25, float(paramB));

        if (kind == FRAME) {
            float d = sdNgon(p, 0.92, fc);
            ink = strokeAA(d, thickness);
            // chord-by-chord ink-on
            float a = (atan(p.y, p.x) + MAGIC_PI) / MAGIC_TWO_PI;
            ink *= step(a, inkOn);
            penTip = exp(-abs(a - inkOn) * 50.0) * 2.0 * (1.0 - step(0.999, inkOn));
            glow += penTip * strokeAA(d, thickness * 4.0);
        } else if (kind == STAR) {
            float skip = max(float(paramB), 1.0);
            float inner = 0.92 * cos(skip * MAGIC_PI / fc) / cos(MAGIC_PI / fc) * 0.55;
            float d = sdStar(p, 0.92, max(inner, 0.2), fc);
            ink = strokeAA(d, thickness);
            float a = (atan(p.y, p.x) + MAGIC_PI) / MAGIC_TWO_PI;
            ink *= step(a, inkOn);
        } else if (kind == PENTAGRAM) {
            float d = 10.0;
            for (int i = 0; i < 5; i++) {
                float a0 = float(i) * MAGIC_TWO_PI / 5.0 - MAGIC_PI * 0.5;
                float a1 = float((i + 2) % 5) * MAGIC_TWO_PI / 5.0 - MAGIC_PI * 0.5;
                d = min(d, sdSegment(p, vec2(cos(a0), sin(a0)) * 0.9, vec2(cos(a1), sin(a1)) * 0.9));
            }
            ink = strokeAA(d, thickness);
            float a = (atan(p.y, p.x) + MAGIC_PI) / MAGIC_TWO_PI;
            ink *= step(a, inkOn);
        } else if (kind == SPOKES) {
            float innerR = float(paramB & 15) / 16.0;
            bool needle = (paramB & 16) != 0;
            vec2 q = foldAngle(p, fc);
            float w = needle ? thickness * (1.0 - smoothstep(innerR, 1.0, q.x)) * 1.6 + 0.004 : thickness;
            float d = sdSegment(q, vec2(innerR, 0.0), vec2(0.96, 0.0));
            ink = strokeAA(d, w);
            ink *= step(q.x, mix(innerR, 1.0, inkOn));
        } else if (kind == LATTICE) {
            vec2 g = abs(fract(p * fc * 0.5 + 0.5) - 0.5);
            float d = min(g.x, g.y) / (fc * 0.5);
            ink = strokeAA(d, thickness) * (1.0 - smoothstep(0.9, 1.0, r));
            ink *= step(abs(p.x) + abs(p.y), inkOn * 2.2);
        } else if (kind == ORBIT_SEAL) {
            float ring = strokeAA(r - 0.9, thickness * 1.4);
            float poly = strokeAA(sdNgon(p, 0.62, fc), thickness);
            float star = strokeAA(sdStar(p, 0.5, 0.2, fc), thickness * 0.8);
            float crossInk = min(strokeAA(abs(p.x), thickness * 0.8) * step(abs(p.y), 0.25), 1.0)
                        + strokeAA(abs(p.y), thickness * 0.8) * step(abs(p.x), 0.25);
            ink = max(max(ring, poly), max(star, clamp(crossInk, 0.0, 1.0)));
            ink *= smoothstep(0.0, 1.0, inkOn);
        } else if (kind == CORE) {
            int style = paramB & 7;
            if (style == 0) {           // DISC_GLOW
                glow += pow(max(1.0 - r, 0.0), 2.2) * 1.6;
                ink = strokeAA(r - 0.55, thickness * 1.2);
            } else if (style == 1) {    // IRIS
                float pupil = 1.0 - smoothstep(0.18, 0.26, r);
                float fibres = pow(abs(sin(atan(p.y, p.x) * 18.0 + nz(Sampler0, p * 0.5 + seed) * 4.0)), 4.0);
                ink = max(strokeAA(r - 0.62, thickness), fibres * smoothstep(0.26, 0.4, r) * (1.0 - smoothstep(0.55, 0.62, r)) * 0.8);
                glow += pupil * 0.6;
            } else if (style == 2) {    // HEX_LENS
                ink = max(strokeAA(sdNgon(p, 0.6, 6.0), thickness), strokeAA(sdNgon(p, 0.35, 6.0), thickness * 0.8));
                glow += pow(max(1.0 - r * 1.6, 0.0), 2.0) * 0.8;
            } else if (style == 3) {    // VOID_PIT (mode 1 darkens)
                ink = 1.0 - smoothstep(0.3, 0.62, r);
                glow += strokeAA(r - 0.62, thickness * 1.5);
            } else if (style == 4) {    // EMBER_PIT
                float embers = step(0.86, nz(Sampler0, p * 2.0 + seed + floor(tSlow * 0.3) * 0.01));
                ink = max(strokeAA(r - 0.58, thickness), embers * (1.0 - smoothstep(0.4, 0.58, r)));
                glow += pow(max(1.0 - r * 1.8, 0.0), 1.5) * 0.9;
            } else if (style == 5) {    // RIPPLE
                float rings = pow(abs(sin(r * 14.0 - tSlow * 0.6)), 8.0) * (1.0 - smoothstep(0.5, 0.65, r));
                ink = rings;
            } else if (style == 6) {    // CROSS
                float cx = strokeAA(abs(p.x), thickness) * step(abs(p.y), 0.6);
                float cy = strokeAA(abs(p.y), thickness) * step(abs(p.x), 0.6);
                ink = max(cx, cy);
            } else {                    // SUNBURST
                float rays = pow(abs(sin(atan(p.y, p.x) * 8.0 + tSlow * 0.2)), 12.0) * (1.0 - smoothstep(0.2, 0.7, r)) * step(0.15, r);
                ink = max(rays, strokeAA(r - 0.16, thickness));
                glow += pow(max(1.0 - r * 2.5, 0.0), 2.0);
            }
            ink *= smoothstep(0.0, 1.0, inkOn);
        } else if (kind == EMBLEM) {
            int id = count | (paramB << 6);
            float scale = mix(1.4, 1.0, easeInOut(inkOn)); // scale-in flourish
            vec2 q = p * scale;
            if (abs(q.x) > 1.0 || abs(q.y) > 1.0) {
                discard;
            }
            ink = atlasInk(id, q, 0.05);
            glow += atlasGlow(id, q);
            ink *= smoothstep(0.0, 0.6, inkOn);
        }
    }

    // sustain pulse
    float pulse = 1.0 + 0.08 * sin(t * 0.12 + seed * 6.28318);
    // dissolve: strokes burn away along a noise threshold rather than fading
    if (dissolve > 0.0) {
        float n = nz(Sampler0, texCoord0 * 3.0 + seed * 5.0);
        float keep = step(dissolve * 1.05, n);
        ink *= keep;
        glow += (1.0 - keep) * ink * 2.0 * (1.0 - dissolve);
        glow *= (1.0 - dissolve);
    }

    vec3 col = mix(tint, hotOf(tint), clamp(penTip * 0.5 + glow * 0.3, 0.0, 1.0));
    if (mode == 1) {
        // INK: near-black body with a tinted rim, straight alpha, may darken the ground
        vec3 body = tint * 0.08;
        vec3 rim = tint;
        float coverage = clamp(ink + glow * 0.35, 0.0, 1.0);
        vec3 c = mix(body, rim, clamp(glow * 0.6, 0.0, 1.0));
        fragColor = straightOut(c, coverage * opacity * pulse) * ColorModulator;
    } else {
        float g = (ink * 1.15 + glow * 0.55) * pulse;
        fragColor = additiveOut(col, g, opacity) * ColorModulator;
    }
    if (fragColor.a <= 0.003) {
        discard;
    }
}
