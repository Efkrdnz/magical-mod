#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // screen 0..1
in vec4 vertexColor; // rgb payload colour, a = maxAlpha
flat in ivec4 magicA; // kind, strength(0-63), hit yaw bucket (0-31, 32 = omni), mode
flat in vec2 magicB;  // phase = progress, seed = aspect*16/63

out vec4 fragColor;

const int VIGNETTE = 0;
const int FLASH = 1;
const int SHOCK_RING = 2;
const int TUNNEL = 3;
const int FROST_EDGES = 4;
const int HEAT_SHIMMER = 5;
const int INK_BLEED = 6;
const int BLOOM_RAYS = 7;
const int PRISM_RING = 8;
const int HEX_PULSE = 9;
const int CRACKED_GLASS = 10;
const int IRIS_CLOSE = 11;
const int HEARTBEAT = 12;
const int STATIC_GLITCH = 13;
const int WATER_DROPLETS = 14;
const int EMBER_DRIFT = 15;

void main() {
    int kind = magicA.x;
    float strength = float(magicA.y) / 63.0;
    int yawBucket = magicA.w == 1 ? 32 : magicA.z; // mode 1 = omnidirectional
    float progress = magicB.x;
    float aspect = max(magicB.y * 63.0 / 16.0, 0.5);
    float t = magicTime(GameTime, 200.0);

    vec2 p = texCoord0 * 2.0 - 1.0;
    p.x *= aspect;
    float r = length(p) / max(aspect, 1.0);
    float ang = atan(p.y, p.x);
    float fade = sin((1.0 - progress) * MAGIC_PI * 0.5);
    vec3 col = vertexColor.rgb;
    float maxAlpha = vertexColor.a;

    // directional weight toward where the hit came from
    float dirW = 1.0;
    if (yawBucket < 32) {
        float hitAng = float(yawBucket) / 32.0 * MAGIC_TWO_PI;
        vec2 hd = vec2(cos(hitAng), sin(hitAng));
        dirW = 0.5 + 0.5 * max(0.0, dot(normalize(p + 1e-4), hd));
    }

    float a = 0.0;
    if (kind == VIGNETTE) {
        a = smoothstep(0.46, 1.08, r) * dirW;
    } else if (kind == FLASH) {
        a = 1.0;
    } else if (kind == SHOCK_RING) {
        a = exp(-pow((r - progress * 1.2) / 0.12, 2.0)) * 1.2;
    } else if (kind == TUNNEL) {
        float lines = pow(abs(sin(ang * 24.0 + t * 0.5)), 8.0);
        a = smoothstep(0.3, 1.0, r) * (0.5 + lines);
    } else if (kind == FROST_EDGES) {
        float n = fbmTap(Sampler0, p * 0.4);
        a = smoothstep(0.35 + n * 0.3, 1.1, r) * (0.6 + n);
        col = mix(col, vec3(0.9, 0.97, 1.0), 0.5);
    } else if (kind == HEAT_SHIMMER) {
        float n = nz(Sampler0, vec2(p.x * 0.3, p.y * 0.2 - t * 0.02));
        a = pow(abs(sin(p.y * 8.0 + n * 6.0 + t * 0.4)), 6.0) * smoothstep(0.3, 1.0, r) * 0.7;
    } else if (kind == INK_BLEED) {
        float w = worley(Sampler0, p * 0.3 + progress * 0.1);
        a = smoothstep(0.4 - progress * 0.2, 1.0, r + (w - 0.5) * 0.4);
        col *= 0.15;
    } else if (kind == BLOOM_RAYS) {
        float rays = pow(abs(sin(ang * float(max(magicA.y, 4)) * 0.25)), 6.0) * nz(Sampler0, vec2(ang * 0.5, t * 0.01));
        a = rays * (1.0 - smoothstep(0.0, 1.2, r)) + smoothstep(0.5, 1.1, r) * 0.4;
    } else if (kind == PRISM_RING) {
        float ring = exp(-pow((r - 0.75) / 0.06, 2.0));
        col = vec3(exp(-pow((r - 0.78) / 0.05, 2.0)), ring, exp(-pow((r - 0.72) / 0.05, 2.0))) * 0.7 + col * 0.3;
        a = max(ring, max(col.r, col.b));
    } else if (kind == HEX_PULSE) {
        vec2 hp = p * 6.0;
        vec2 rr = vec2(1.0, 1.7320508);
        vec2 hh = rr * 0.5;
        vec2 ca = mod(hp, rr) - hh;
        vec2 cb = mod(hp - hh, rr) - hh;
        vec2 gv = dot(ca, ca) < dot(cb, cb) ? ca : cb;
        float e = 0.5 - max(dot(abs(gv), normalize(vec2(1.0, 1.7320508))), abs(gv.x));
        a = (1.0 - smoothstep(0.0, 0.06, e)) * smoothstep(0.3, 1.0, r);
    } else if (kind == CRACKED_GLASS) {
        float w = worley(Sampler0, p * 0.35 + 0.3);
        float edge = 1.0 - smoothstep(0.0, 0.03, abs(w - 0.5));
        a = edge * smoothstep(0.1, 0.9, r) * 0.9;
        col = mix(col, vec3(1.0), 0.5);
    } else if (kind == IRIS_CLOSE) {
        float radius = mix(1.4, 0.35, progress);
        a = smoothstep(radius - 0.15, radius, r);
        col *= 0.05;
    } else if (kind == HEARTBEAT) {
        float beat = pow(max(sin(t * 0.25), 0.0), 8.0) + pow(max(sin(t * 0.25 - 0.6), 0.0), 12.0) * 0.6;
        a = smoothstep(0.4, 1.1, r) * (0.4 + beat);
    } else if (kind == STATIC_GLITCH) {
        float band = floor(texCoord0.y * 40.0);
        float g = step(0.85, magicHash(band + floor(t * 0.7)));
        float scan = pow(abs(sin(texCoord0.y * 300.0)), 8.0) * 0.3;
        a = g * 0.5 + scan + smoothstep(0.5, 1.1, r) * 0.3;
    } else if (kind == WATER_DROPLETS) {
        vec2 c = fract(p * 3.0 + vec2(0.0, progress * 0.5)) - 0.5;
        float drop = 1.0 - smoothstep(0.08, 0.16, length(c * vec2(1.0, 0.7)));
        a = drop * smoothstep(0.2, 1.0, r) * 0.8 + smoothstep(0.6, 1.1, r) * 0.3;
        col = mix(col, vec3(0.85, 0.95, 1.0), 0.5);
    } else {                                     // EMBER_DRIFT
        vec2 g = floor(p * 5.0 + vec2(0.0, -progress * 2.0));
        vec2 c = fract(p * 5.0 + vec2(0.0, -progress * 2.0)) - 0.5;
        float ember = step(0.8, magicHash(g)) * (1.0 - smoothstep(0.05, 0.2, length(c)));
        a = ember * 1.5 * smoothstep(0.2, 1.0, r) + smoothstep(0.55, 1.1, r) * 0.35;
    }

    fragColor = vec4(col, clamp(a, 0.0, 1.0) * maxAlpha * fade * (0.5 + strength * 0.5)) * ColorModulator;
    if (fragColor.a <= 0.002) {
        discard;
    }
}
