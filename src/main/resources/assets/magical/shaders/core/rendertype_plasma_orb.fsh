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

const int PLASMA = 0;
const int HOLLOW_SHELL = 1;
const int SPARK_BURST = 2;
const int IRIS = 3;
const int CRESCENT = 4;
const int THIN_HALO = 5;
const int WORLEY_CELLS = 6;
const int EMBER_CLUSTER = 7;
const int HEX_LENS = 8;
const int BLOOM_FLASH = 9;
const int VOID_CORE = 10;
const int LIQUID_DROP = 11;
const int CHARGE_SPHERE = 12;
const int LENS_STREAKS = 13;
const int SHARD_DIAMOND = 14;
const int EYE_SLIT = 15;

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    float paramB = float(magicA.z);
    int mode = magicA.w;
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 200.0) + seed * 40.0;

    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) {
        discard;
    }
    float ang = atan(p.y, p.x);
    float soft = 1.4 + paramB * 0.1;      // core softness exponent
    float env = (kind == BLOOM_FLASH || kind == CHARGE_SPHERE || kind == HOLLOW_SHELL)
            ? 1.0 : smoothstep(0.0, 0.15, phase) * smoothstep(1.0, 0.7, phase);
    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;

    float glow = 0.0;
    float core = 0.0;
    vec3 col = tint;
    bool dark = false;

    if (kind == PLASMA) {
        float n = nz(Sampler0, vec2(ang * 0.3 + t * 0.01, r * 0.55 - t * 0.02));
        float body = (1.0 - r) * (0.55 + 0.85 * n);
        core = pow(1.0 - r, 2.4) * 1.8;
        float halo = (1.0 - smoothstep(0.35, 1.0, r)) * 0.5;
        float rim = exp(-pow((r - 0.82) / 0.10, 2.0)) * (0.4 + 0.4 * n);
        glow = core + body + halo * 0.6 + rim;
    } else if (kind == HOLLOW_SHELL) {
        float radius = mix(0.95, 0.55, phase);
        glow = exp(-pow((r - radius) / 0.07, 2.0)) * 1.6 + pow(max(1.0 - r, 0.0), 6.0) * 0.4;
        core = exp(-pow((r - radius) / 0.03, 2.0));
    } else if (kind == SPARK_BURST) {
        float spikes = pow(abs(cos(ang * count * 0.5 + seed * 3.0)), 14.0);
        glow = spikes * (1.0 - r) * 2.0 + pow(1.0 - r, 3.0) * 1.5;
        core = pow(1.0 - r, 4.0);
    } else if (kind == IRIS) {
        float slit = 0.12 + paramB * 0.02;
        float pupil = 1.0 - smoothstep(slit, slit + 0.05, abs(p.x) * 0.6 + r * 0.6);
        float fibres = pow(abs(sin(ang * 22.0 + nz(Sampler0, p * 0.4 + seed) * 5.0)), 3.0);
        glow = fibres * (1.0 - smoothstep(0.6, 0.95, r)) * (1.0 - pupil) + exp(-pow((r - 0.9) / 0.05, 2.0));
        dark = true;
        col = mix(tint * 0.05, tint, 1.0 - pupil);
    } else if (kind == CRESCENT) {
        float offset = count / 16.0;
        float d1 = r - 0.9;
        float d2 = length(p - vec2(offset, 0.0)) - 0.85;
        float crescent = (1.0 - smoothstep(-0.02, 0.02, d1)) * smoothstep(-0.02, 0.02, d2);
        glow = crescent * 1.4 + exp(-abs(d1) * 20.0) * crescent;
        core = crescent * 0.6;
    } else if (kind == THIN_HALO) {
        glow = exp(-pow((r - 0.88) / 0.035, 2.0)) * 1.8;
        core = glow * 0.5;
    } else if (kind == WORLEY_CELLS) {
        float w = worley(Sampler0, p * 0.35 + seed + t * 0.002);
        glow = (1.0 - r) * (0.3 + 1.4 * pow(w, 2.0));
        core = pow(1.0 - r, 3.0) * 0.8;
    } else if (kind == EMBER_CLUSTER) {
        vec2 g = floor(p * 5.0 + seed * 10.0);
        float h = magicHash(g + floor(t * 0.05));
        vec2 c = fract(p * 5.0 + seed * 10.0) - 0.5;
        float dot_ = step(0.55, h) * (1.0 - smoothstep(0.12, 0.3, length(c)));
        glow = dot_ * (1.0 - smoothstep(0.7, 1.0, r)) * 1.8 + pow(1.0 - r, 3.0) * 0.4;
        core = dot_ * 0.5;
        if (paramB < 8.0) dark = true;
    } else if (kind == HEX_LENS) {
        float d = sdNgon(p, 0.75, 6.0);
        glow = strokeAA(d, 0.03) * 1.5 + pow(max(1.0 - r, 0.0), 2.0) * 0.8 + strokeAA(sdNgon(p, 0.45, 6.0), 0.02);
        core = pow(max(1.0 - r * 1.5, 0.0), 2.0);
    } else if (kind == BLOOM_FLASH) {
        float radius = smoothstep(0.0, 0.3, phase) * (1.0 - smoothstep(0.55, 1.0, phase));
        float rr = r / max(radius, 0.001);
        glow = rr < 1.0 ? pow(1.0 - rr, 1.2) * 2.5 : 0.0;
        core = rr < 1.0 ? pow(1.0 - rr, 3.0) * 2.0 : 0.0;
    } else if (kind == VOID_CORE) {
        float pit = 1.0 - smoothstep(0.35, 0.72, r);
        float rim = exp(-pow((r - 0.8) / 0.06, 2.0));
        glow = rim * 1.4;
        col = mix(vec3(0.01, 0.0, 0.02), tint, rim);
        dark = true;
        core = pit;
    } else if (kind == LIQUID_DROP) {
        float wob = 1.0 + 0.07 * sin(ang * 3.0 + t * 0.15) + 0.05 * sin(ang * 5.0 - t * 0.11);
        float radius = 0.85 * wob;
        float inside = 1.0 - smoothstep(radius - 0.02, radius + 0.02, r);
        float rim = exp(-pow((r - radius) / 0.05, 2.0));
        float caustic = 1.0 - smoothstep(0.0, 0.08, abs(fbmTap(Sampler0, p * 0.3 + t * 0.005) - 0.5));
        glow = rim * 1.5 + inside * (0.25 + caustic * 0.5);
        core = rim;
    } else if (kind == CHARGE_SPHERE) {
        float rings = pow(abs(sin(r * 10.0 * (1.0 + phase * 3.0) + t * 0.4 * (1.0 + phase))), 10.0);
        glow = rings * (1.0 - smoothstep(0.7, 1.0, r)) * 1.4 + pow(1.0 - r, 2.0) * phase * 1.5;
        core = pow(1.0 - r, 3.0) * (0.5 + phase);
    } else if (kind == LENS_STREAKS) {
        float streak = pow(abs(cos(ang * count * 0.5 + seed)), 60.0) * (1.0 - r);
        glow = streak * 3.0 + pow(max(1.0 - r * 2.5, 0.0), 2.0) * 1.5 + exp(-pow((r - 0.35) / 0.03, 2.0)) * 0.3;
        core = pow(max(1.0 - r * 3.0, 0.0), 2.0);
    } else if (kind == SHARD_DIAMOND) {
        float aspect = 0.35 + paramB * 0.03;
        float d = abs(p.x) / aspect + abs(p.y);
        float inside = 1.0 - smoothstep(0.92, 1.0, d);
        float edge = exp(-abs(d - 0.96) * 30.0);
        glow = inside * 0.7 + edge * 1.4;
        core = pow(max(1.0 - d, 0.0), 2.0);
    } else {                            // EYE_SLIT
        float lid = 1.0 - smoothstep(0.0, 0.04, abs(p.y) - (0.55 * (1.0 - p.x * p.x)) * phase);
        float pupil = 1.0 - smoothstep(0.08, 0.14, abs(p.x) * 0.5 + abs(p.y));
        glow = lid * (0.8 - pupil * 0.8) + exp(-abs(abs(p.y) - 0.55 * (1.0 - p.x * p.x) * phase) * 25.0) * 1.2;
        col = mix(tint, vec3(0.02), pupil);
        dark = true;
        core = lid * 0.3;
    }

    glow *= env;
    core *= env;
    vec3 outCol = mix(col, vec3(1.0), clamp(core * 0.6, 0.0, 0.85)) + vec3(1.0) * core * 0.4;
    if (mode == 1 || dark) {
        fragColor = straightOut(outCol, clamp(glow + core, 0.0, 1.0) * opacity) * ColorModulator;
    } else {
        fragColor = additiveOut(outCol, glow, opacity) * ColorModulator;
    }
    if (fragColor.a <= 0.003) {
        discard;
    }
}
