#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // local surface coordinate: u = mirrored angle01 / x01, v = height01 / latitude01
in vec4 vertexColor; // rgb tint, a = base translucency x CPU-baked fresnel
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

const int HEX_LATTICE = 0;
const int MAGMA_CRACKS = 1;
const int FROST_CELLS = 2;
const int RIPPLE_WATER = 3;
const int FILIGREE = 4;
const int VOID_INK = 5;
const int PRISM_GRID = 6;
const int SCALE_PLATES = 7;
const int ORGANIC_CELLS = 8;
const int STATIC_SCANLINES = 9;
const int RUNE_PAPER = 10;
const int HOLY_GLASS = 11;
const int EMBER_CRUST = 12;
const int MIRROR_SHEEN = 13;
const int SPORE_MOSS = 14;
const int HONEYCOMB_SHELL = 15;

float hexDist(vec2 p) {
    p = abs(p);
    return max(dot(p, normalize(vec2(1.0, 1.7320508))), p.x);
}

// hex cell lattice edge distance in a tiling frame
float hexEdge(vec2 uv, float freq) {
    vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(uv * freq, r) - h;
    vec2 b = mod(uv * freq - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    return 0.5 - hexDist(gv);
}

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    int paramB = magicA.z;
    int mode = magicA.w;
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 120.0) + seed * 20.0;

    vec2 uv = texCoord0;
    vec3 tint = vertexColor.rgb;
    float baseAlpha = vertexColor.a;
    float lineWeight = 0.02 + float(paramB & 15) * 0.006;
    bool fromCentre = (paramB & 16) != 0;

    // build/decay: grows from bottom (or centre), sustains, erodes after 0.8
    float growth = smoothstep(0.0, 1.0, phase * 1.25);
    float coord = fromCentre ? length(uv - 0.5) * 2.0 : uv.y;
    float built = step(coord, growth);
    float leading = exp(-abs(coord - growth) * 25.0) * step(growth, 0.999) * 2.0;
    float erode = clamp((phase - 0.8) * 5.0, 0.0, 1.0);

    float pattern = 0.0;   // bright line/feature coverage
    float fill = 0.25;     // body translucency multiplier
    vec3 col = tint;
    bool dark = false;

    if (kind == HEX_LATTICE) {
        float e = hexEdge(uv, count);
        pattern = 1.0 - smoothstep(0.0, lineWeight * 2.0, e);
        fill = 0.18;
    } else if (kind == MAGMA_CRACKS) {
        float f = fbmTap(Sampler0, uv * count * 0.3 + seed);
        float crack = 1.0 - smoothstep(0.0, 0.05, abs(f - 0.5));
        float pulse = 0.7 + 0.3 * sin(t * 0.2 + uv.y * 6.0);
        pattern = crack * pulse * 1.5;
        col = mix(vec3(0.05, 0.01, 0.0), tint, clamp(pattern, 0.0, 1.0));
        fill = 0.8;
        dark = true;
    } else if (kind == FROST_CELLS) {
        float w = worley(Sampler0, uv * count * 0.25 + seed);
        pattern = (1.0 - smoothstep(0.0, 0.06, abs(w - 0.5))) * 0.9 + pow(w, 3.0) * 0.5;
        col = mix(tint, vec3(0.9, 0.97, 1.0), 0.4);
        fill = 0.35;
    } else if (kind == RIPPLE_WATER) {
        float caustic = 1.0 - smoothstep(0.0, 0.07, abs(fbmTap(Sampler0, uv * count * 0.2 + vec2(t * 0.01, 0.0)) - 0.5));
        float rings = pow(abs(sin(length(uv - 0.5) * count * 6.0 - t * 0.3)), 12.0) * 0.5;
        pattern = caustic * 0.9 + rings;
        fill = 0.4;
        dark = true;
        col = mix(tint * 0.7, vec3(1.0), caustic * 0.4);
    } else if (kind == FILIGREE) {
        float f = fbmTap(Sampler0, uv * count * 0.35 + seed);
        float f2 = fbmTap(Sampler0, uv * count * 0.35 * 1.7 + seed + 3.0);
        pattern = (1.0 - smoothstep(0.0, lineWeight * 1.5, abs(f - 0.5))) + (1.0 - smoothstep(0.0, lineWeight, abs(f2 - 0.55))) * 0.7;
        fill = 0.12;
    } else if (kind == VOID_INK) {
        float motes = step(0.93, nz(Sampler0, uv * count * 0.5 + vec2(0.0, -t * 0.01) + seed));
        float vein = 1.0 - smoothstep(0.0, 0.04, abs(fbmTap(Sampler0, uv * count * 0.25 + seed + t * 0.002) - 0.5));
        float flicker = step(0.7, magicHash(floor(t * 0.5) + seed));
        pattern = motes * 0.8 + vein * flicker * 1.2;
        col = mix(vec3(0.01, 0.0, 0.02), tint, clamp(pattern, 0.0, 1.0));
        fill = 0.92;
        dark = true;
    } else if (kind == PRISM_GRID) {
        vec2 g = abs(fract(uv * count) - 0.5);
        float line = 1.0 - smoothstep(0.0, lineWeight * 1.5, min(g.x, g.y));
        vec2 gr = abs(fract(uv * count + vec2(0.004, 0.0)) - 0.5);
        vec2 gb = abs(fract(uv * count - vec2(0.004, 0.0)) - 0.5);
        float lr = 1.0 - smoothstep(0.0, lineWeight * 1.5, min(gr.x, gr.y));
        float lb = 1.0 - smoothstep(0.0, lineWeight * 1.5, min(gb.x, gb.y));
        col = vec3(lr, line, lb) * 0.5 + tint * 0.5;
        pattern = max(line, max(lr, lb));
        fill = 0.1;
    } else if (kind == SCALE_PLATES) {
        vec2 sp = uv * count;
        sp.x += step(1.0, mod(floor(sp.y), 2.0)) * 0.5;
        vec2 cell = fract(sp) - vec2(0.5, 0.0);
        float d = length(cell) - 0.5;
        pattern = 1.0 - smoothstep(0.0, lineWeight * 2.0, abs(d));
        fill = 0.3;
    } else if (kind == ORGANIC_CELLS) {
        float w = worley(Sampler0, uv * count * 0.2 + seed + t * 0.001);
        pattern = (1.0 - smoothstep(0.0, 0.08, abs(w - 0.45))) * 0.9;
        col = mix(tint * 0.6, tint, w);
        fill = 0.35;
        dark = true;
    } else if (kind == STATIC_SCANLINES) {
        float scan = pow(abs(sin(uv.y * count * 8.0 - t * 0.6)), 14.0);
        float glitch = step(0.96, nz(Sampler0, vec2(floor(uv.y * 40.0) * 0.05, floor(t * 0.8) * 0.03)));
        pattern = scan * 0.8 + glitch;
        fill = 0.2;
    } else if (kind == RUNE_PAPER) {
        vec2 cell = floor(uv * count);
        float h = magicHash(cell + seed);
        vec2 q = fract(uv * count) * 2.0 - 1.0;
        float mark = step(0.5, h) * (1.0 - smoothstep(0.3, 0.5, max(abs(q.x), abs(q.y)))) * (1.0 - smoothstep(0.1, 0.25, min(abs(q.x), abs(q.y))));
        pattern = mark;
        fill = 0.3;
    } else if (kind == HOLY_GLASS) {
        float w = worley(Sampler0, uv * count * 0.2 + seed);
        float lead = 1.0 - smoothstep(0.0, 0.05, abs(w - 0.5));
        float h = magicHash(floor(uv * count * 0.6) + seed);
        col = mix(tint, vec3(1.0, 0.85, 0.6) * (0.6 + 0.4 * h), 0.5);
        pattern = lead * 1.4 + 0.3;
        fill = 0.55;
    } else if (kind == EMBER_CRUST) {
        float f = fbmTap(Sampler0, uv * count * 0.3 + seed);
        float crack = 1.0 - smoothstep(0.0, 0.03, abs(f - 0.5));
        float embers = step(0.9, nz(Sampler0, uv * count * 0.8 + floor(t * 0.2) * 0.01));
        pattern = crack * 1.2 + embers;
        col = mix(vec3(0.03), tint, clamp(pattern, 0.0, 1.0));
        fill = 0.9;
        dark = true;
    } else if (kind == MIRROR_SHEEN) {
        float band = pow(abs(sin((uv.x + uv.y) * 2.0 - t * 0.1)), 16.0);
        vec2 gr = abs(fract(uv * 2.0 + vec2(0.004, 0.0)) - 0.5);
        float split = 1.0 - smoothstep(0.0, 0.01, min(gr.x, gr.y));
        pattern = band * 1.4 + split * 0.3;
        col = mix(vec3(0.04, 0.02, 0.06), tint, clamp(band, 0.0, 1.0));
        fill = 0.7;
        dark = true;
    } else if (kind == SPORE_MOSS) {
        float w = worley(Sampler0, uv * count * 0.3 + seed);
        float spores = step(0.88, nz(Sampler0, uv * count * 1.2 + seed + t * 0.002));
        pattern = pow(w, 2.0) * 0.5 + spores;
        col = mix(tint * 0.5, tint, w);
        fill = 0.5;
        dark = true;
    } else {                                     // HONEYCOMB_SHELL
        float e = hexEdge(uv, count);
        float cellLight = magicHash(floor(uv * count) + floor(t * 0.3) * 0.1);
        pattern = (1.0 - smoothstep(0.0, lineWeight * 2.5, e)) + step(0.8, cellLight) * 0.4;
        fill = 0.22;
    }

    // rim softening so slabs never print a hard rectangle
    vec2 edge = min(uv, 1.0 - uv);
    float edgeFade = smoothstep(0.0, 0.06, min(edge.x, edge.y) + nz(Sampler0, uv * 3.0 + seed) * 0.03);
    float erodeKeep = step(erode * 1.05, nz(Sampler0, uv * 2.0 + seed * 7.0));

    float coverage = (fill + clamp(pattern, 0.0, 1.5) * 0.9 + leading) * built * edgeFade * erodeKeep;
    vec3 outCol = mix(col, hotOf(col), clamp(leading * 0.5 + pattern * 0.15, 0.0, 1.0));
    if (dark || mode == 1) {
        fragColor = straightOut(outCol, coverage * baseAlpha) * ColorModulator;
    } else {
        // translucent glow: colour weighted by pattern, alpha by coverage
        fragColor = straightOut(outCol * (0.6 + clamp(pattern, 0.0, 1.0) * 0.8), coverage * baseAlpha) * ColorModulator;
    }
    if (fragColor.a <= 0.003) {
        discard;
    }
}
