#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // x = along 0..1 (base..tip), y = across 0..1 (spine at 0.5)
in vec4 vertexColor;
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

const int PLASMA_TUBE = 0;
const int HELIX = 1;
const int LIGHTNING = 2;
const int CHAIN = 3;
const int RIBBON = 4;
const int BLADE_RIM = 5;
const int THREAD_KNOTS = 6;
const int DASH_TRAIN = 7;
const int ROPE = 8;
const int VEIN = 9;
const int RUNE_THREAD = 10;
const int LIQUID_ROPE = 11;
const int FLAME_TONGUE = 12;
const int CRYSTAL_SHARD = 13;
const int WATER = 14;
const int INK_TENDRIL = 15;

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    float paramB = float(magicA.z);
    int mode = magicA.w;
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 200.0) + seed * 30.0;

    float along = texCoord0.x;
    float c = texCoord0.y * 2.0 - 1.0;       // -1..1 across
    float across = abs(c);                   // 0 spine, 1 edge
    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;

    // taper profile: paramB 0 = uniform, 31 = full taper to the tip
    float taper = 1.0 - (paramB / 31.0) * along;
    float widthScale = max(taper, 0.02);
    float a2 = across / widthScale;          // across in the tapered frame
    if (a2 > 1.0) {
        discard;
    }

    // REVEAL / RECEDE window: visible where along in [max(0, 2p-1), min(1, 2p)]
    float lo = max(0.0, 2.0 * phase - 1.0);
    float hi = min(1.0, 2.0 * phase);
    float window = step(lo, along) * step(along, hi);
    float head = exp(-abs(along - hi) * 30.0) * step(hi, 0.999) * 2.0;
    float tail = exp(-abs(along - lo) * 30.0) * step(0.001, lo) * 1.0;

    float spine = pow(1.0 - a2, 2.6);
    float body = 1.0 - a2;
    float ends = smoothstep(0.0, 0.10, along) * smoothstep(1.0, 0.88, along);
    float glow = 0.0;
    float dark = 0.0;           // 1 = straight-alpha dark body
    vec3 col = tint;

    if (kind == PLASMA_TUBE) {
        float flow = fbmTap(Sampler0, vec2(along * 1.5 - t * 0.01, texCoord0.y * 0.8 + t * 0.002));
        float streak = 0.55 + 0.75 * flow;
        glow = (body * 0.55 + spine * 1.5) * streak;
    } else if (kind == HELIX) {
        float g = 0.0;
        for (int i = 0; i < 4; i++) {
            if (float(i) >= count) break;
            float ph = float(i) / count * MAGIC_TWO_PI + seed * 6.0;
            float strand = sin(along * 22.0 * (1.0 + count * 0.15) + ph + t * 0.1) * 0.7;
            g = max(g, strokeAA(c - strand, 0.12) * 1.4 + exp(-abs(c - strand) * 6.0) * 0.4);
        }
        glow = g;
    } else if (kind == LIGHTNING) {
        float reroll = floor(t * 0.4 + seed * 10.0);
        float off = (nz(Sampler0, vec2(along * count * 0.2, reroll * 0.013 + seed)) - 0.5) * 1.3;
        float off2 = (nz(Sampler0, vec2(along * count * 0.55 + 3.0, reroll * 0.017 + seed)) - 0.5) * 0.4;
        float centre = off + off2;
        float coreLine = strokeAA(c - centre, 0.06) * 2.2;
        float halo = exp(-abs(c - centre) * 5.0) * 0.6;
        float branch = step(0.86, nz(Sampler0, vec2(along * 3.0 + reroll * 0.01, seed + 0.5))) * strokeAA(c - centre * 0.3 - (c > centre ? 0.5 : -0.5) * along, 0.04);
        glow = coreLine + halo + branch;
    } else if (kind == CHAIN) {
        float lu = fract(along * count) * 2.0 - 1.0;
        float link = abs(length(vec2(lu * 1.2, c)) - 0.7);
        glow = strokeAA(link, 0.12) * 1.5 + exp(-link * 8.0) * 0.3;
    } else if (kind == RIBBON) {
        float sheen = pow(abs(sin(along * 9.0 - t * 0.2 + c * 2.0)), 8.0);
        glow = body * 0.8 + sheen * 0.9 + exp(-(1.0 - a2) * 0.0) * 0.0;
        glow *= 1.0 - smoothstep(0.85, 1.0, a2);
    } else if (kind == BLADE_RIM) {
        float rim = exp(-(1.0 - a2) * 9.0) * 2.0;
        float centre = (1.0 - a2) * 0.15;
        glow = rim + centre;
        col = mix(tint * 0.3, tint, clamp(rim, 0.0, 1.0));
    } else if (kind == THREAD_KNOTS) {
        float thread = strokeAA(c, 0.08) * 1.4;
        float lu = fract(along * count) - 0.5;
        float knot = exp(-(lu * lu * 60.0 + c * c * 12.0)) * 2.0;
        glow = thread + knot;
    } else if (kind == DASH_TRAIN) {
        float travel = fract(along * count - phase * 2.0);
        float dash = step(0.35, travel);
        glow = (spine * 1.4 + body * 0.4) * dash;
        window = 1.0; head = 0.0; tail = 0.0;
    } else if (kind == ROPE) {
        float g = 0.0;
        for (int i = 0; i < 3; i++) {
            float ph = float(i) * 2.0944 + seed * 6.0;
            float strand = sin(along * 30.0 + ph) * 0.5;
            g = max(g, strokeAA(c - strand, 0.2) * (0.7 + 0.3 * cos(along * 30.0 + ph)));
        }
        glow = g * 1.2;
    } else if (kind == VEIN) {
        float crack = 1.0 - smoothstep(0.0, 0.05, abs(fbmTap(Sampler0, vec2(along * 1.2, texCoord0.y * 0.5 + seed)) - 0.5));
        float pulse = 0.6 + 0.4 * sin(t * 0.3 - along * 8.0);
        glow = crack * pulse * 1.5 * (1.0 - smoothstep(0.7, 1.0, a2));
        dark = 1.0;
        col = mix(tint * 0.05, tint, clamp(glow, 0.0, 1.0));
    } else if (kind == RUNE_THREAD) {
        float cellIdx = floor(along * count);
        float lu = fract(along * count) * 2.0 - 1.0;
        float h = magicHash(cellIdx + seed * 64.0);
        float mark = step(0.4, h) * (1.0 - smoothstep(0.35, 0.5, abs(lu))) * (1.0 - smoothstep(0.5, 0.7, a2));
        float thread = strokeAA(c, 0.05) * 1.2;
        glow = thread + mark * 1.4;
    } else if (kind == LIQUID_ROPE) {
        float bulge = 0.5 + 0.5 * sin(along * count * MAGIC_TWO_PI - t * 0.4);
        float w = 0.45 + 0.5 * bulge;
        float inside = 1.0 - smoothstep(w - 0.05, w + 0.05, a2);
        float rim = exp(-abs(a2 - w) * 18.0);
        glow = inside * 0.5 + rim * 1.3;
        dark = 1.0;
        col = mix(tint * 0.5, tint, rim);
    } else if (kind == FLAME_TONGUE) {
        float lick = nz(Sampler0, vec2(along * 2.0 + seed, t * 0.02)) * 0.8;
        float topEdge = 1.0 - smoothstep(lick, lick + 0.15, texCoord0.y);
        float hot = pow(1.0 - texCoord0.y, 2.0);
        glow = topEdge * (0.5 + hot * 1.5) * (1.0 - smoothstep(0.7, 1.0, a2 * 0.5));
        col = mix(tint, vec3(1.0, 0.95, 0.7), hot * 0.6);
    } else if (kind == CRYSTAL_SHARD) {
        float w = worley(Sampler0, vec2(along * 2.5, texCoord0.y) + seed);
        float facet = 1.0 - smoothstep(0.0, 0.08, abs(w - 0.5));
        float inside = 1.0 - smoothstep(0.85, 0.92, a2);
        glow = inside * (0.35 + facet * 0.9) + exp(-abs(a2 - 0.9) * 20.0) * 0.8;
        col = mix(tint, vec3(0.85, 0.95, 1.0), 0.4);
        ends = 1.0;
        dark = 1.0;
    } else if (kind == WATER) {
        float caustic = 1.0 - smoothstep(0.0, 0.07, abs(fbmTap(Sampler0, vec2(along * 1.5 - t * 0.015, texCoord0.y * 0.6)) - 0.5));
        float inside = 1.0 - smoothstep(0.8, 0.95, a2);
        glow = inside * (0.45 + caustic * 0.9) + exp(-abs(a2 - 0.9) * 14.0) * 0.5;
        dark = 1.0;
        col = mix(tint * 0.6, vec3(1.0), caustic * 0.5);
    } else {                                    // INK_TENDRIL
        float wob = sin(along * 12.0 + t * 0.2 + seed * 6.0) * 0.25;
        float d = abs(c - wob);
        float w = 0.55 * (1.0 - along * 0.6);
        float inside = 1.0 - smoothstep(w - 0.05, w + 0.05, d);
        glow = inside;
        dark = 1.0;
        col = mix(tint * 0.05, tint * 0.35, exp(-d * 6.0));
    }

    glow = glow * ends * window + (head + tail) * (1.0 - a2) * step(0.5, window);
    vec3 outCol = mix(col, vec3(1.0), clamp(spine * 0.7 * (1.0 - dark), 0.0, 0.9));
    if (mode == 1 || dark > 0.5) {
        fragColor = straightOut(outCol, clamp(glow, 0.0, 1.0) * opacity) * ColorModulator;
    } else {
        fragColor = additiveOut(outCol, glow, opacity) * ColorModulator;
    }
    if (fragColor.a <= 0.003) {
        discard;
    }
}
