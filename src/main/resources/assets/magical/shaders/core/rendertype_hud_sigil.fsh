#version 150

#moj_import <magical:magic_common.glsl>
#moj_import <magical:magic_frag.glsl>
#moj_import <magical:magic_atlas.glsl>

uniform sampler2D Sampler0; // noise atlas
uniform sampler2D Sampler1; // sigil emblem SDF atlas (16x16 cells)
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;    // unit square; p = uv * 2 - 1 is the only shape coordinate
in vec4 vertexColor;  // rgb = tint resolved on the CPU, a = opacity
flat in ivec4 magicA; // kind, count, paramB, mode
flat in vec2 magicB;  // phase, seed

out vec4 fragColor;

// The HUD sigil shader: every element of the magic HUD is one of these kinds on one quad. The
// CPU sizes the quad; all geometry below is a fraction of the quad's half-size, so the HUD scale
// option scales strokes with it and nothing here knows about pixels except the anti-aliasing.
//
// Output is premultiplied for a ONE / ONE_MINUS_SRC_ALPHA blend: a fragment with coverage 0 is
// pure additive glow, a fragment with coverage > 0 paints over what is under it. That is how the
// lit register (rings, emblems) and the ink register (plates, the reflection) share one batch.
//
// Kind ids mirror HudKind.java; a test checks they agree.
const int RING_METER = 0;
const int CORE = 1;
const int CARD = 2;
const int CHARGE_RING = 3;
const int EMBLEM = 4;
const int SATELLITE = 5;
const int CHIP = 6;
const int PLATE = 7;
const int LINE = 8;
const int ANNOUNCE = 9;

// Where a ring's outer edge sits, as a fraction of the quad's half-size. The margin outside it is
// room for the hot head and the ready flash. HudBatch sizes ring quads by the same constant.
const float RING_OUTER = 0.88;

// Ring width classes, as fractions of the quad's half-size (paramB & 7 for RING_METER).
float widthClass(int c) {
    if (c == 0) return 0.03;
    if (c == 1) return 0.06;
    if (c == 2) return 0.10;
    if (c == 3) return 0.16;
    if (c == 4) return 0.20;
    if (c == 5) return 0.26;
    if (c == 6) return 0.32;
    return 0.40;
}

// 0 at 12 o'clock, increasing clockwise (screen y points down), wrapping at 1.
float angle01(vec2 p) {
    return fract(atan(p.x, -p.y) / MAGIC_TWO_PI);
}

// A ring band whose OUTER edge is at radius outer and whose width is w (both in p units).
float band(float r, float outer, float w) {
    return strokeAA(r - (outer - w * 0.5), w * 0.5);
}

// A disc of radius R with a one-pixel soft edge.
float disc(float r, float R) {
    float aa = fwidth(r) * 1.2;
    return 1.0 - smoothstep(R - aa, R + aa, r);
}

// Notches cut across a band: count evenly spaced gaps, each `w` wide in angle01 units.
float notchGap(float a, float count, float w) {
    if (count <= 0.0) return 0.0;
    float cell = fract(a * count + 0.5) - 0.5;   // -0.5..0.5, 0 at each notch
    float d = abs(cell) / count;                 // angular distance to the nearest notch
    return 1.0 - smoothstep(w, w * 1.6, d);
}

// The glowing head at the leading edge of a fill.
float hotHead(float a, float fill) {
    if (fill <= 0.003 || fill >= 0.997) return 0.0;
    float d = abs(a - fill);
    d = min(d, 1.0 - d);
    return exp(-d * 90.0) * 1.6;
}

// Two-Hz blink, integer-periodic in GameTime.
float blink() {
    return 0.5 + 0.5 * sin(magicTime(GameTime, 2400.0));
}

// A cross of two strokes, for a locked / disabled mark.
float cross(vec2 p, float w) {
    return max(strokeAA(p.x, w) * step(abs(p.y), 0.55), strokeAA(p.y, w) * step(abs(p.x), 0.55));
}

vec2 rotate(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// Premultiplied output. `body` is painted OVER with `coverage`; `glowCol * glow` is ADDED.
vec4 hudOut(vec3 body, float coverage, vec3 glowCol, float glow, float opacity) {
    coverage = clamp(coverage, 0.0, 1.0) * opacity;
    return vec4(body * coverage + glowCol * clamp(glow, 0.0, 4.0) * opacity, coverage);
}

// A magic-circle core, ported from glyph_ink so the sigil's centre is the same glyph the spell
// circles carry. Returns ink in .x and glow in .y.
vec2 coreGlyph(int style, vec2 p, float r, float seed, float tSlow, float thickness) {
    float ink = 0.0;
    float glow = 0.0;
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
    } else if (style == 3) {    // VOID_PIT
        ink = 1.0 - smoothstep(0.3, 0.62, r);
        glow += strokeAA(r - 0.62, thickness * 1.5);
    } else if (style == 4) {    // EMBER_PIT
        float embers = step(0.86, nz(Sampler0, p * 2.0 + seed + floor(tSlow * 0.3) * 0.01));
        ink = max(strokeAA(r - 0.58, thickness), embers * (1.0 - smoothstep(0.4, 0.58, r)));
        glow += pow(max(1.0 - r * 1.8, 0.0), 1.5) * 0.9;
    } else if (style == 5) {    // RIPPLE
        ink = pow(abs(sin(r * 14.0 - tSlow * 0.6)), 8.0) * (1.0 - smoothstep(0.5, 0.65, r));
    } else if (style == 6) {    // CROSS
        float cx = strokeAA(abs(p.x), thickness) * step(abs(p.y), 0.6);
        float cy = strokeAA(abs(p.y), thickness) * step(abs(p.x), 0.6);
        ink = max(cx, cy);
    } else {                    // SUNBURST
        float rays = pow(abs(sin(atan(p.y, p.x) * 8.0 + tSlow * 0.2)), 12.0) * (1.0 - smoothstep(0.2, 0.7, r)) * step(0.15, r);
        ink = max(rays, strokeAA(r - 0.16, thickness));
        glow += pow(max(1.0 - r * 2.5, 0.0), 2.0);
    }
    return vec2(ink, glow);
}

void main() {
    int kind = magicA.x;
    int count = magicA.y;
    int paramB = magicA.z;
    int mode = magicA.w;
    float phase = magicB.x;
    float seed = magicB.y;

    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    float a = angle01(p);
    float tSlow = magicTime(GameTime, 24.0);

    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;
    bool ink = (mode & 1) != 0;

    float stroke = 0.0;   // crisp lit strokes
    float halo = 0.0;     // soft additive glow
    float body = 0.0;     // plate coverage (paints over)

    if (kind == RING_METER) {
        if (r > 1.0) discard;
        float w = widthClass(paramB & 7);
        float ring = band(r, RING_OUTER, w);
        float filled = step(a, phase);
        float gap = notchGap(a, float(count), 0.004);
        // A dark band sits under the whole ring, so the lit fill reads against a daytime sky as
        // well as a cave; the notches cut the band and the fill alike. The unfilled track stays
        // faintly lit so the ring still reads as a ring when empty.
        float cut = 1.0 - gap * 0.85;
        body = ring * cut * 0.66;
        stroke = ring * cut * mix(0.14, 0.72, filled);
        if ((paramB & 8) != 0) {
            halo += hotHead(a, phase) * band(r, RING_OUTER + w * 0.35, w * 1.7) * 0.7;
        }
        if ((paramB & 16) != 0) {
            // Rungs: the notches light up as the fill reaches them.
            float rung = notchGap(a, float(count), 0.006);
            float reached = step(floor(a * float(count) + 0.5) / float(count), phase + 0.001);
            stroke += rung * reached * ring * 0.9;
        }
        if ((mode & 2) != 0) {
            halo += ring * filled * blink() * 0.6;
        }
    } else if (kind == CORE) {
        if (r > 1.0) discard;
        vec2 cg = coreGlyph(paramB & 7, rotate(p, tSlow * (0.02 + 0.03 * float(count & 3))), r, seed, tSlow, 0.035);
        float pulse = 1.0 + 0.35 * phase;
        stroke = cg.x * pulse;
        halo = cg.y * pulse;
    } else if (kind == CARD) {
        if (r > 1.0) discard;
        int id = count | ((paramB & 1) << 6);
        bool empty = (paramB & 2) != 0;
        bool held = (paramB & 4) != 0;
        bool locked = (paramB & 8) != 0;
        float plateR = 0.815;
        float plate = disc(r, plateR);
        float rim = band(r, plateR, 0.07);
        if (empty) {
            rim *= 1.0 - notchGap(a, 8.0, 0.02) * 0.9;
        }
        // The cooldown sweep: the outer band of the plate, lit for the fraction still remaining.
        float sweep = band(r, plateR - 0.02, 0.11);
        bool flashing = mode >= 2 && (mode & 1) == 0;   // phase is the flash envelope, not a sweep
        float remaining = flashing ? 0.0 : step(a, phase);
        float cooling = flashing ? 0.0 : step(0.001, phase);
        vec2 q = p / 0.60;
        float em = 0.0;
        float emGlow = 0.0;
        if (!empty && abs(q.x) <= 1.0 && abs(q.y) <= 1.0) {
            em = atlasInk(Sampler1, id, q, 0.06);
            emGlow = atlasGlow(Sampler1, id, q);
        }
        float emDim = mix(1.0, 0.3, cooling);
        stroke = rim * (empty ? 0.45 : 0.75) + sweep * remaining * 0.9 + em * emDim;
        halo = emGlow * emDim * 0.8 + hotHead(a, phase) * sweep * 0.8;
        if (held) {
            halo += rim * 1.4;
        }
        if (locked) {
            stroke += cross(p * 3.2, 0.08) * 0.8;
        }
        if (flashing) {
            halo += plate * phase * 0.9 + rim * phase * 2.4;
        }
        // A cooling card's plate goes darker and more opaque, so the seconds numeral over it reads.
        body = plate * mix(empty ? 0.42 : 0.80, 0.94, cooling) + sweep * remaining * 0.25;
    } else if (kind == CHARGE_RING) {
        if (r > 1.0) discard;
        float ring = band(r, 0.965, 0.11);
        float filled = step(a, phase);
        float gap = notchGap(a, float(count), 0.005);
        stroke = ring * max(filled, 0.12) * (1.0 - gap * 0.8);
        halo = hotHead(a, phase) * band(r, 0.99, 0.2);
        if ((mode & 2) != 0) {
            halo += ring * (0.4 + 0.6 * blink());
        }
    } else if (kind == EMBLEM || kind == ANNOUNCE) {
        int id = (kind == EMBLEM) ? (count | (paramB << 6)) : (count | ((paramB & 1) << 6));
        float inkOn = clamp(phase * 4.0, 0.0, 1.0);
        float dissolve = clamp((phase - 0.75) * 4.0, 0.0, 1.0);
        if (kind == EMBLEM) {
            inkOn = 1.0;
            dissolve = 0.0;
        }
        float scale = mix(1.4, 1.0, easeInOut(inkOn));
        vec2 q = p * scale;
        if (abs(q.x) > 1.0 || abs(q.y) > 1.0) discard;
        stroke = atlasInk(Sampler1, id, q, 0.05) * smoothstep(0.0, 0.6, inkOn);
        halo = atlasGlow(Sampler1, id, q);
        if (kind == ANNOUNCE) {
            float pen = exp(-abs(a - inkOn) * 50.0) * 2.0 * (1.0 - step(0.999, inkOn));
            float ring = band(r, 0.96, 0.05) * step(a, inkOn);
            stroke += ring;
            halo += pen * band(r, 0.98, 0.16);
            if (dissolve > 0.0) {
                float n = nz(Sampler0, texCoord0 * 3.0 + magicHash(float(id)) * 5.0);
                float keep = step(dissolve * 1.05, n);
                stroke *= keep;
                halo = halo * (1.0 - dissolve) + (1.0 - keep) * stroke * 2.0 * (1.0 - dissolve);
            }
        }
    } else if (kind == SATELLITE) {
        if (r > 1.0) discard;
        int id = count | ((paramB & 1) << 6);
        float plateR = 0.77;
        float plate = disc(r, plateR);
        float ring = band(r, 0.98, 0.15);
        float filled = step(a, phase);
        vec2 q = p / 0.62;
        float em = 0.0;
        if (abs(q.x) <= 1.0 && abs(q.y) <= 1.0) {
            em = atlasInk(Sampler1, id, q, 0.07);
        }
        stroke = ring * max(filled, 0.14) + em * 0.95;
        halo = hotHead(a, phase) * band(r, 1.0, 0.22) * 0.7;
        if ((mode & 2) != 0) {
            halo += ring * blink() * 0.7;
        }
        body = plate * 0.82;
    } else if (kind == CHIP) {
        if (r > 1.0) discard;
        int id = count | ((paramB & 1) << 6);
        int pips = (paramB >> 1) & 15;
        float plate = disc(r, 0.8);
        float ring = band(r, 0.98, 0.13);
        float remaining = step(a, phase);
        vec2 q = p / 0.6;
        float em = 0.0;
        if (abs(q.x) <= 1.0 && abs(q.y) <= 1.0) {
            em = atlasInk(Sampler1, id, q, 0.07);
        }
        stroke = ring * max(remaining, 0.12) + em;
        // Amplifier pips: small dots along the bottom of the rim.
        for (int i = 0; i < 4; i++) {
            if (i >= pips) break;
            vec2 c = vec2((float(i) - float(pips - 1) * 0.5) * 0.28, 0.82);
            stroke += disc(length(p - c), 0.08);
        }
        if ((mode & 2) != 0) {
            halo += ring * blink() * 0.5;
        }
        body = plate * 0.84;
    } else if (kind == PLATE) {
        // Rounded rectangle in framebuffer pixels, so corners are round on any aspect ratio.
        vec2 g = vec2(fwidth(p.x), fwidth(p.y));
        vec2 q = p / g;
        vec2 halfExt = 1.0 / g;
        float minHalf = min(halfExt.x, halfExt.y);
        float rc = min(float(count) / 32.0, 1.0) * minHalf;
        float d = sdBox(q, halfExt - rc - 0.5) - rc;
        float plate = 1.0 - smoothstep(-0.8, 0.8, d);
        float rimW = max(0.12 * minHalf, 1.0);
        float rim = strokeAA(d + rimW * 0.5, rimW * 0.5);
        bool selected = (paramB & 1) != 0;
        stroke = rim * (selected ? 0.9 : 0.35);
        if ((paramB & 2) != 0) {
            stroke += strokeAA(q.y - (halfExt.y - 1.0), 0.8) * plate;   // accent underline
        }
        if ((paramB & 4) != 0) {
            stroke += strokeAA(q.y + (halfExt.y - 1.0), 0.8) * plate;   // accent top edge
        }
        if ((paramB & 8) != 0) {
            stroke += strokeAA(q.x + (halfExt.x - 1.0), 1.0) * plate;   // accent left bar
        }
        body = plate * (selected ? 0.9 : 0.78) * phase;
        stroke *= phase;
        ink = true;
        if (mode >= 2) {
            // tinted glass: let the tint through the body
            body *= 0.85;
        }
    } else if (kind == LINE) {
        vec2 g = vec2(fwidth(p.x), fwidth(p.y));
        float halfW = 1.0 / g.x;
        float qx = p.x / g.x;
        float ends = smoothstep(0.0, 4.0, halfW - abs(qx));
        float drawn = step(texCoord0.x, phase);
        float dash = count > 0 ? step(0.5, fract(texCoord0.x * float(count))) : 1.0;
        float core = 1.0 - smoothstep(0.35, 1.0, abs(p.y));
        stroke = core * ends * drawn * dash;
        halo = core * ends * drawn * 0.25;
    }

    vec3 hot = hotOf(tint);
    if (ink) {
        // INK register: near-black body; the strokes (rim, emblem, rungs) carry the full tint and a
        // little glow so a forbidden card's emblem still reads on its own dark plate.
        vec3 bodyCol = mix(tint * 0.08, tint, clamp(halo * 0.6 + stroke, 0.0, 1.0));
        fragColor = hudOut(bodyCol, body + stroke * 0.9 + halo * 0.3, tint, halo * 0.35 + stroke * 0.3, opacity);
    } else {
        vec3 plate = vec3(0.028, 0.04, 0.07);
        vec3 lit = mix(tint, hot, clamp(halo * 0.3, 0.0, 1.0));
        fragColor = hudOut(plate, body, lit, stroke * 1.15 + halo * 0.55, opacity);
    }
    fragColor *= ColorModulator;
    // Not alpha-only: a pure glow fragment has alpha 0 and must survive.
    if (max(fragColor.a, max(fragColor.r, max(fragColor.g, fragColor.b))) < 0.003) {
        discard;
    }
}
