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

const int GRAVITY_LENS = 0;
const int HEAT_HAZE = 1;
const int REFRACTION_BUBBLE = 2;
const int TIME_RIPPLE = 3;
const int PRISM_SPLIT = 4;
const int SHOCK_LENS = 5;
const int MIRAGE_SHEET = 6;

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    float phase = magicB.x;
    float seed = magicB.y;
    float t = magicTime(GameTime, 100.0) + seed * 20.0;

    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0 && kind != HEAT_HAZE && kind != MIRAGE_SHEET) {
        discard;
    }
    vec3 tint = vertexColor.rgb;
    vec3 hot = mix(tint, vec3(1.0), 0.7);
    float opacity = vertexColor.a;
    float grow = 0.5 * smoothstep(0.0, 0.4, phase);
    float collapse = 1.0 - smoothstep(0.85, 1.0, phase);

    vec3 col = tint;
    float alpha = 0.0;

    if (kind == GRAVITY_LENS) {
        vec2 bent = p * (1.0 + 0.38 / (r * r + 0.075));
        float stars = step(0.975, magicHash(floor(bent * 28.0 + seed * 10.0)));
        float horizon = 1.0 - smoothstep(grow * 0.7, grow, r);
        float photon = exp(-pow((r - grow * 1.1) / 0.03, 2.0)) * 1.5;
        float band = exp(-pow((r - grow * 1.5) / 0.05, 2.0)) * 0.5;
        col = mix(vec3(0.0), hot, photon + band) + stars * vec3(0.9, 0.95, 1.0) * (1.0 - horizon);
        alpha = max(horizon, max(photon, band)) + stars * 0.6 * (1.0 - horizon);
        alpha *= (1.0 - smoothstep(0.85, 1.0, r));
    } else if (kind == HEAT_HAZE) {
        float n = nz(Sampler0, vec2(p.x * 0.6 + seed, p.y * 0.4 - t * 0.02));
        float bands = pow(abs(sin(p.y * 12.0 + n * 6.0 + t * 0.3)), 6.0);
        float soot = step(0.9, nz(Sampler0, p * 1.5 + seed + t * 0.01)) * 0.3;
        col = mix(tint, vec3(1.0), bands * 0.6);
        alpha = (bands * 0.35 + soot) * (1.0 - smoothstep(0.6, 1.0, abs(p.x))) * (1.0 - smoothstep(0.7, 1.0, abs(p.y)));
    } else if (kind == REFRACTION_BUBBLE) {
        float caustics = 1.0 - smoothstep(0.0, 0.06, abs(fbmTap(Sampler0, p * 0.5 + t * 0.004 + seed) - 0.5));
        float rim = exp(-pow((r - 0.9) / 0.05, 2.0));
        float wob = 1.0 + 0.04 * sin(atan(p.y, p.x) * 3.0 + t * 0.2);
        float inside = 1.0 - smoothstep(0.9 * wob, 0.95 * wob, r);
        col = mix(tint, vec3(1.0), caustics * 0.7 + rim * 0.5);
        alpha = inside * (0.12 + caustics * 0.5) + rim * 1.2;
    } else if (kind == TIME_RIPPLE) {
        float rings = pow(abs(sin(r * count * 3.0 - t * 0.5)), 10.0);
        col = mix(tint, hot, rings);
        alpha = rings * (1.0 - r) * 1.2 + 0.05;
    } else if (kind == PRISM_SPLIT) {
        float ring = exp(-pow((r - grow * 1.6) / 0.05, 2.0));
        float ringR = exp(-pow((r - grow * 1.6 - 0.03) / 0.04, 2.0));
        float ringB = exp(-pow((r - grow * 1.6 + 0.03) / 0.04, 2.0));
        col = vec3(ringR, ring, ringB) * 0.8 + tint * 0.3;
        alpha = max(ring, max(ringR, ringB)) * 1.5;
    } else if (kind == SHOCK_LENS) {
        float radius = phase;
        float ring = exp(-pow((r - radius) / 0.08, 2.0));
        float inner = exp(-pow((r - radius + 0.06) / 0.04, 2.0)) * 0.5;
        col = mix(tint, vec3(1.0), ring * 0.6);
        alpha = (ring + inner) * (1.0 - phase * 0.6) * 1.6;
        collapse = 1.0;
    } else {                                     // MIRAGE_SHEET
        float n = fbmTap(Sampler0, vec2(p.x * 0.5 + t * 0.006 + seed, p.y * 0.5));
        float sheen = pow(abs(sin(p.y * 6.0 + n * 8.0)), 8.0);
        col = mix(tint, vec3(1.0), sheen * 0.5);
        alpha = (0.08 + sheen * 0.3) * (1.0 - smoothstep(0.7, 1.0, abs(p.x))) * (1.0 - smoothstep(0.7, 1.0, abs(p.y)));
    }

    fragColor = vec4(col, clamp(alpha, 0.0, 1.0) * opacity * collapse) * ColorModulator;
    if (fragColor.a <= 0.003) {
        discard;
    }
}
