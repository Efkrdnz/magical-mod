// Shared library for every magical FX core shader.
//
// MagicVertex contract (all ten library shaders):
//   Position           world/local position
//   Color.rgb          resolved tint (CPU side), Color.a = opacity (8-bit, interpolated)
//   UV0                pure shape coordinate (never carries data)
//   UV2 (ivec2)        32 bits of integer data, decoded once in the vertex shader:
//       x = kind:5 | count:6 << 5 | paramB:5 << 11
//       y = phase:8 | seed:6 << 8 | mode:2 << 14
//   magicA = ivec4(kind, count, paramB, mode)   (flat)
//   magicB = vec2(phase01, seed01)               (flat)

const float MAGIC_PI = 3.14159265359;
const float MAGIC_TWO_PI = 6.28318530718;

void decodeMagicVertex(ivec2 uv2, out ivec4 a, out vec2 b) {
    int x = uv2.x & 0xFFFF;
    int y = uv2.y & 0xFFFF;
    a = ivec4(x & 31, (x >> 5) & 63, (x >> 11) & 31, (y >> 14) & 3);
    b = vec2(float(y & 255) / 255.0, float((y >> 8) & 63) / 63.0);
}

// Integer-frequency periodic time: GameTime wraps once per day, so every animation must be
// periodic in GameTime with an integer number of cycles to avoid a visible pop at the wrap.
float magicTime(float gameTime, float cycles) {
    return gameTime * MAGIC_TWO_PI * cycles;
}

float magicHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float magicHash(float p) {
    return fract(sin(p * 91.3458) * 47453.5453);
}

// Noise atlas fetches. R = value noise, G = fbm, B = worley F1, A = blue noise. Coordinates wrap.
vec4 nzTap(sampler2D s, vec2 p) {
    return texture(s, fract(p));
}

float nz(sampler2D s, vec2 p) {
    return texture(s, fract(p)).r;
}

float fbmTap(sampler2D s, vec2 p) {
    return texture(s, fract(p)).g;
}

float worley(sampler2D s, vec2 p) {
    return texture(s, fract(p)).b;
}

float blueNoise(sampler2D s, vec2 p) {
    return texture(s, fract(p)).a;
}

// ---- signed distance helpers (2D, p in a centred frame) ----
float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

// Regular N-gon outline distance (radius r = apothem of the circumscribed polygon).
float sdNgon(vec2 p, float r, float n) {
    float a = atan(p.x, p.y) + MAGIC_PI;
    float sector = MAGIC_TWO_PI / n;
    float d = cos(floor(0.5 + a / sector) * sector - a) * length(p);
    return d - r;
}

float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

// Fold the plane into one angular sector of N so radial features cost one evaluation.
vec2 foldAngle(vec2 p, float n) {
    float sector = MAGIC_TWO_PI / n;
    float a = atan(p.y, p.x);
    float k = floor(a / sector + 0.5);
    float na = a - k * sector;
    return vec2(cos(na), sin(na)) * length(p);
}

// Star polygon outline (n points, inner radius ri, outer radius ro).
float sdStar(vec2 p, float ro, float ri, float n) {
    vec2 q = foldAngle(p, n);
    vec2 tip = vec2(ro, 0.0);
    float halfAng = MAGIC_PI / n;
    vec2 valley = vec2(cos(halfAng), sin(halfAng)) * ri;
    float d1 = sdSegment(q, tip, valley);
    float d2 = sdSegment(q, tip, vec2(valley.x, -valley.y));
    return min(d1, d2);
}

float sdPetal(vec2 p, float len, float width) {
    vec2 q = vec2(abs(p.x), p.y);
    float d = length(q - vec2(width, len * 0.5)) - len * 0.5;
    return abs(d);
}

// ---- palette derivation shared with the CPU (MagicCircleRenderer's bright/hot/dim rules) ----
vec3 brightOf(vec3 c) { return clamp(c + vec3(36.0, 28.0, 68.0) / 255.0, 0.0, 1.0); }
vec3 hotOf(vec3 c) { return clamp(c + vec3(78.0, 66.0, 98.0) / 255.0, 0.0, 1.0); }
vec3 dimOf(vec3 c) { return clamp(c - vec3(28.0, 40.0, 28.0) / 255.0, 0.0, 1.0); }

// ---- envelopes ----
float easeInOut(float t) {
    t = clamp(t, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float bellEnvelope(float phase, float inEnd, float outStart) {
    return smoothstep(0.0, inEnd, phase) * (1.0 - smoothstep(outStart, 1.0, phase));
}

// strokeAA lives in magic_frag.glsl: it calls fwidth, which the GLSL spec allows only in fragment
// shaders. Both stages import this file, so a derivative call here fails to compile every vertex
// shader on a strict driver (Intel, AMD) even though NVIDIA lets it through.

float glowOf(float d, float k) {
    return exp(-max(d, 0.0) * k);
}

// ---- outputs ----
vec4 additiveOut(vec3 col, float glow, float opacity) {
    float a = clamp(glow, 0.0, 1.0) * opacity;
    return vec4(col * glow * opacity, a);
}

vec4 straightOut(vec3 col, float alpha) {
    return vec4(col, clamp(alpha, 0.0, 1.0));
}
