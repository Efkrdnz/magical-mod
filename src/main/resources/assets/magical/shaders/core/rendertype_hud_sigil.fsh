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
// lit register (the codex emblems, the rule marks), the ink register (plates) and the corner
// block's paint-over glyphs and bars share one batch.
//
// Kind ids mirror HudKind.java; a test checks they agree.
const int EMBLEM = 4;
const int CHIP = 6;
const int PLATE = 7;
const int RULE_MARK = 10;
const int SLOT = 11;
const int BAR = 12;

// A cooling or charging glyph's unlit rows: this grey, whole, over the glyph's shadow - greyed
// out, not faded out, so a cooling glyph still reads over a night sky.
const float SLOT_GHOST_GREY = 0.49;
// How much of the world a bar's dark track covers where the fill has not reached.
const float BAR_TRACK_ALPHA = 0.85;
// The track under every bar and the lit register's plate body: HudPalette.CHROME_INK.
const vec3 TRACK_RGB = vec3(0.028, 0.04, 0.07);

// The rule flash's timeline, as fractions of its lifetime; HudKind declares the same three and a
// test keeps them equal. The plate pops in until POP_END, the mark plays until MARK_END, and
// everything dissolves from OUT_START.
const float FLASH_POP_END = 0.10;
const float FLASH_MARK_END = 0.28;
const float FLASH_OUT_START = 0.72;

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

// Two-Hz blink, integer-periodic in GameTime.
float blink() {
    return 0.5 + 0.5 * sin(magicTime(GameTime, 2400.0));
}

vec2 rotate(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// ---- the rule flash's marks: strokes built from segments -------------------------------------

float seg(vec2 p, vec2 a, vec2 b, float w) {
    return strokeAA(sdSegment(p, a, b), w);
}

// A stem from a to b with a two-stroke head at b, `h` long.
float arrow(vec2 p, vec2 a, vec2 b, float w, float h) {
    vec2 d = normalize(b - a);
    vec2 n = vec2(-d.y, d.x);
    float s = seg(p, a, b, w);
    s = max(s, seg(p, b, b - d * h + n * h, w));
    return max(s, seg(p, b, b - d * h - n * h, w));
}

// A square bracket standing at x, its serifs pointing `dir` (+1 right, -1 left), gh+0.12 tall.
float bracket(vec2 p, float x, float dir, float gh, float w) {
    float top = -gh - 0.12;
    float bottom = gh + 0.12;
    float s = seg(p, vec2(x, top), vec2(x, bottom), w);
    s = max(s, seg(p, vec2(x, top), vec2(x + dir * 0.12, top), w));
    return max(s, seg(p, vec2(x, bottom), vec2(x + dir * 0.12, bottom), w));
}

// The hex lattice of the first-person HEX_PULSE overlay, at the caller's scale.
float hexLattice(vec2 hp) {
    vec2 rr = vec2(1.0, 1.7320508);
    vec2 hh = rr * 0.5;
    vec2 ca = mod(hp, rr) - hh;
    vec2 cb = mod(hp - hh, rr) - hh;
    vec2 gv = dot(ca, ca) < dot(cb, cb) ? ca : cb;
    float e = 0.5 - max(dot(abs(gv), normalize(vec2(1.0, 1.7320508))), abs(gv.x));
    return 1.0 - smoothstep(0.0, 0.06, e);
}

// The mark behind the changed symbol of a rule flash. The quad is centred on the symbol, forty
// GUI px wide; the symbol's box is gw half-wide (from count) and gh half-tall. Every kind plays
// on `mk`, the mark's own window of the timeline, and erodes on `outp`.
void ruleMark(int change, int variant, float gw, float mk, float outp, vec2 p, float r, float seed, float tSlow,
        inout float stroke, inout float halo) {
    float gh = 0.4;
    float w = 0.035;
    float box = sdBox(p, vec2(gw, gh));
    float inBox = step(box, 0.0);
    if (change == 0 || change == 1) {
        // RAISE / LOWER: rays climb (or fall) through the symbol, an arrow points the way.
        vec2 q = change == 1 ? vec2(p.x, -p.y) : p;
        float reach = mix(gh, -1.0, mk);
        // Thin and dim through the glyph box, so the glyph drawn over them still reads.
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            float x = mix(-gw, gw, (fi + 0.5) / 5.0) + 0.06 * (magicHash(seed * 7.0 + fi) - 0.5);
            float top = reach + 0.25 * magicHash(fi + seed);
            stroke += seg(q, vec2(x, gh + 0.05), vec2(x, top), 0.016) * mix(0.55, 0.3, inBox);
        }
        stroke += arrow(q, vec2(0.0, -gh - 0.2), vec2(0.0, -gh - 0.62), w, 0.12) * mk;
        // An outline glow along the box's edge, not a fill over the glyph.
        halo += smoothstep(0.3, 0.0, abs(box)) * mk * 0.4;
    } else if (change == 2) {
        // ZERO: a strike draws across the symbol, then it burns, and a null badge stamps beside it.
        float strike = strokeAA(p.y - 0.02 * p.x, 0.045) * step(p.x, mix(-gw - 0.15, gw + 0.15, mk)) * step(-gw - 0.15, p.x);
        stroke += strike;
        halo += step(0.6, mk) * step(0.88, nz(Sampler0, p * 3.0 + seed * 3.0)) * inBox * 1.2;
        vec2 c = vec2(gw + 0.32, -gh - 0.22);
        float badge = strokeAA(length(p - c) - 0.11, 0.028) + seg(p, c + vec2(-0.14, 0.14), c + vec2(0.14, -0.14), 0.028);
        stroke += badge * smoothstep(0.5, 1.0, mk);
    } else if (change == 3) {
        // FLIP: a mirror plane flares through the symbol, a two-way arrow sits under it.
        halo += strokeAA(p.x, 0.03) * step(abs(p.y), gh + 0.15) * sin(mk * MAGIC_PI) * 1.5;
        float y = gh + 0.32;
        stroke += (arrow(p, vec2(-0.05, y - 0.06), vec2(-gw - 0.1, y - 0.06), w, 0.1)
                + arrow(p, vec2(0.05, y + 0.06), vec2(gw + 0.1, y + 0.06), w, 0.1)) * mk;
    } else if (change == 4) {
        // LOCK: brackets slide in from the edges and a lattice settles over the symbol.
        float x = mix(1.0, gw + 0.18, easeInOut(mk));
        stroke += bracket(p, -x, 1.0, gh, w) + bracket(p, x, -1.0, gh, w);
        if (mk >= 0.999) {
            stroke += hexLattice(p * 5.0) * inBox * 0.25;
        }
    } else if (change == 5) {
        // SURGE: a shock ring bursts out of the symbol's box behind rays, then a slow pulse remains.
        float ring = exp(-pow((r - mix(gw, 1.3, mk)) / 0.08, 2.0)) * (1.0 - mk * 0.6);
        float rays = pow(abs(sin(atan(p.y, p.x) * 4.0)), 12.0) * (1.0 - r) * (1.0 - mk);
        halo += ring * 1.4 + rays + (1.0 - r) * 0.25 * (0.5 + 0.5 * sin(tSlow * 2.0)) * mk;
    } else if (change == 6) {
        // AIM: the direction sweeps in - north, south, round, or inward from all four sides.
        float ease = easeInOut(mk);
        vec2 q = rotate(p, (1.0 - ease) * MAGIC_PI * 0.5);
        float mark = 0.0;
        if (variant == 0) {
            mark = arrow(q, vec2(0.0, -gh - 0.15), vec2(0.0, -gh - 0.6), w, 0.12);
        } else if (variant == 1) {
            mark = arrow(q, vec2(0.0, gh + 0.15), vec2(0.0, gh + 0.6), w, 0.12);
        } else if (variant == 2) {
            float ang = angle01(q);
            mark = band(r, 0.85, 0.06) * step(ang, 0.75) * step(0.05, ang);
            vec2 tip = vec2(-0.82, 0.0);
            mark = max(mark, seg(q, tip, tip + vec2(-0.11, 0.13), w));
            mark = max(mark, seg(q, tip, tip + vec2(0.11, 0.13), w));
        } else {
            mark = arrow(foldAngle(q, 4.0), vec2(0.88, 0.0), vec2(0.55, 0.0), w, 0.1);
        }
        stroke += mark * mk;
    } else {
        // RESTORE: a ring clears outward and a return badge stamps beside the symbol.
        float ring = exp(-pow((r - mix(gw, 1.2, mk)) / 0.06, 2.0)) * (1.0 - mk);
        halo += ring * 1.2 + (1.0 - mk) * 0.4 * (1.0 - smoothstep(0.0, 0.3, max(box, 0.0)));
        vec2 c = vec2(gw + 0.32, -gh - 0.22);
        vec2 d = p - c;
        float arc = strokeAA(length(d) - 0.13, 0.028) * step(0.15, angle01(d));
        vec2 top = c + vec2(0.0, -0.13);
        float head = seg(p, top, top + vec2(-0.09, -0.08), w) + seg(p, top, top + vec2(-0.09, 0.08), w);
        stroke += (arc + head) * smoothstep(0.4, 1.0, mk);
    }
    if (outp > 0.0) {
        // Erode into glow, the way an announcement dissolves; what goes flares as it goes.
        float keep = step(outp * 1.05, nz(Sampler0, p * 1.5 + seed * 5.0));
        float gone = (1.0 - keep) * stroke;
        stroke *= keep;
        halo = (halo + gone * 2.0) * (1.0 - outp);
    }
}

// Premultiplied output. `body` is painted OVER with `coverage`; `glowCol * glow` is ADDED.
vec4 hudOut(vec3 body, float coverage, vec3 glowCol, float glow, float opacity) {
    coverage = clamp(coverage, 0.0, 1.0) * opacity;
    return vec4(body * coverage + glowCol * clamp(glow, 0.0, 4.0) * opacity, coverage);
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

    // ---- SLOT and BAR: paint-over, no clock ----
    // The corner block's two kinds. Neither glows, blinks or reads the clock: both paint over the
    // world, so they read on a noon sky by being dark where the world is bright and bright where
    // it is dark, not by adding light to it.
    if (kind == SLOT) {
        // A bare skill glyph over a hard black shadow one GUI unit down and right. The quad is the
        // glyph's cell, seed carries its side in GUI units, and the ink keeps a one-unit margin
        // so the shadow has somewhere to land.
        int id = count | ((paramB & 3) << 6);
        bool charging = (paramB & 4) != 0;
        float g = max(floor(seed * 63.0 + 0.5), 1.0);
        float fillP = 1.0 - 2.0 / g;
        vec2 unit = vec2(2.0 / g);
        vec2 qi = p / fillP;
        vec2 qs = (p - unit) / fillP;
        float inkA = (abs(qi.x) <= 1.0 && abs(qi.y) <= 1.0) ? atlasInk(Sampler1, id, qi, 0.06) : 0.0;
        float shade = (abs(qs.x) <= 1.0 && abs(qs.y) <= 1.0) ? atlasInk(Sampler1, id, qs, 0.06) : 0.0;
        // Cooling: everything below the line at 1 - phase is grey, and the line falls as the time
        // runs out, vanilla's item-cooldown convention. Charging: lit below it, from the bottom
        // up. vInk is 0 at the ink box's first row and 1 at its last, and SigilRenderer.inkPhase
        // puts the line on the rows this cell's ink actually covers (FxTextures.inkRows): counted
        // over the whole box, a bar across the middle read as ready for the last two fifths of its
        // cooldown. A readout stamp is this glyph at nine units with phase 0, so it is drawn whole.
        float vInk = (texCoord0.y - 1.0 / g) / fillP;
        float cover = max(inkA, shade);
        float lit = charging ? step(1.0 - phase, vInk) : step(vInk, 1.0 - phase);
        vec3 col = mix(vec3(SLOT_GHOST_GREY), tint, lit) * (inkA / max(cover, 0.0001));
        fragColor = hudOut(col, cover, vec3(0.0), 0.0, opacity) * ColorModulator;
        if (fragColor.a < 0.003) discard;
        return;
    }
    if (kind == BAR) {
        // A dark track over the whole rect and the fill inset one framebuffer pixel on every side,
        // so every fill carries a dark frame even against the sky. The rect lands on whole GUI
        // units, so the edges are crisp; the fill's leading edge is anti-aliased across one pixel.
        vec2 px = fwidth(texCoord0);
        float inY = step(px.y, texCoord0.y) * step(texCoord0.y, 1.0 - px.y);
        float right = px.x + (1.0 - 2.0 * px.x) * phase;
        float inX = step(px.x, texCoord0.x) * clamp((right - texCoord0.x) / px.x + 0.5, 0.0, 1.0);
        float fill = phase > 0.0 ? inX * inY : 0.0;
        fragColor = hudOut(mix(TRACK_RGB, tint, fill), mix(BAR_TRACK_ALPHA, 1.0, fill), vec3(0.0), 0.0, opacity) * ColorModulator;
        if (fragColor.a < 0.003) discard;
        return;
    }
    // ---- end SLOT and BAR ----

    float stroke = 0.0;   // crisp lit strokes
    float halo = 0.0;     // soft additive glow
    float body = 0.0;     // plate coverage (paints over)

    if (kind == EMBLEM) {
        int id = count | (paramB << 6);
        stroke = atlasInk(Sampler1, id, p, 0.05);
        halo = atlasGlow(Sampler1, id, p);
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
    } else if (kind == RULE_MARK) {
        bool still = (mode & 2) != 0;
        float mk = still ? 1.0 : clamp((phase - FLASH_POP_END) / (FLASH_MARK_END - FLASH_POP_END), 0.0, 1.0);
        float outp = clamp((phase - FLASH_OUT_START) / (1.0 - FLASH_OUT_START), 0.0, 1.0);
        ruleMark(paramB & 7, paramB >> 3, max(float(count) / 40.0, 0.12), mk, outp, p, r, seed, tSlow, stroke, halo);
    }

    vec3 hot = hotOf(tint);
    if (ink) {
        // INK register: near-black body; the strokes (rim, emblem, rungs) carry the full tint and a
        // little glow so a forbidden card's emblem still reads on its own dark plate.
        vec3 bodyCol = mix(tint * 0.08, tint, clamp(halo * 0.6 + stroke, 0.0, 1.0));
        fragColor = hudOut(bodyCol, body + stroke * 0.9 + halo * 0.3, tint, halo * 0.35 + stroke * 0.3, opacity);
    } else {
        vec3 plate = TRACK_RGB;
        vec3 lit = mix(tint, hot, clamp(halo * 0.3, 0.0, 1.0));
        fragColor = hudOut(plate, body, lit, stroke * 1.15 + halo * 0.55, opacity);
    }
    fragColor *= ColorModulator;
    // Not alpha-only: a pure glow fragment has alpha 0 and must survive.
    if (max(fragColor.a, max(fragColor.r, max(fragColor.g, fragColor.b))) < 0.003) {
        discard;
    }
}
