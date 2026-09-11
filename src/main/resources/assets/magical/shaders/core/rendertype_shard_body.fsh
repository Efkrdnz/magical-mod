#version 150

#moj_import <magical:magic_common.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;   // per-face planar frame 0..1
in vec4 vertexColor;
in vec3 viewPos;
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

const int CRYSTAL = 0;
const int ICE = 1;
const int OBSIDIAN = 2;
const int MAGMA_ROCK = 3;
const int BONE_IVORY = 4;
const int GLASS = 5;
const int METAL_BANDS = 6;
const int WOOD_VINE = 7;
const int GOLD = 8;
const int STONE = 9;
const int AMBER = 10;
const int PEARL = 11;
const int BLOOD = 12;

void main() {
    int kind = magicA.x;
    float count = max(float(magicA.y), 1.0);
    float paramB = float(magicA.z);
    float phase = magicB.x;   // INTEGRITY: cracks from 0.7, dissolve by 1.0
    float seed = magicB.y;
    float t = magicTime(GameTime, 60.0) + seed * 10.0;

    vec2 uv = texCoord0;
    vec2 p = uv * 2.0 - 1.0;
    vec3 tint = vertexColor.rgb;
    float opacity = vertexColor.a;
    float gloss = 0.3 + paramB * 0.02;

    // fake normal: facet layout from a worley cell hash + a domed height function
    vec2 cellUv = uv * count * 0.5 + seed * 3.0;
    float w = worley(Sampler0, cellUv * 0.35);
    float cellId = magicHash(floor(cellUv) + seed);
    vec3 n = normalize(vec3((cellId - 0.5) * 0.9 + p.x * 0.35, (magicHash(cellId + 1.0) - 0.5) * 0.9 + p.y * 0.35, 1.0));
    vec3 key = normalize(vec3(0.4, 0.8, 0.3));
    vec3 viewDir = normalize(-viewPos);
    float lambert = 0.35 + 0.65 * max(dot(n, key), 0.0);
    float spec = pow(max(dot(reflect(-key, n), viewDir), 0.0), 24.0) * gloss * 2.0;
    float fresnel = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    float edgeDark = smoothstep(0.0, 0.08, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));

    vec3 base = tint;
    float alpha = 0.95;
    float facetLine = 1.0 - smoothstep(0.0, 0.05, abs(w - 0.5));
    float grain = nz(Sampler0, uv * 4.0 + seed) * 0.15;

    if (kind == CRYSTAL) {
        base = mix(tint * 0.6, tint, w) + facetLine * 0.3;
        alpha = 0.9;
    } else if (kind == ICE) {
        float bubbles = step(0.93, nz(Sampler0, uv * 3.0 + seed)) * 0.6;
        float frost = fbmTap(Sampler0, uv * 1.5 + seed) * 0.35;
        base = mix(tint, vec3(0.9, 0.97, 1.0), 0.5) * (0.75 + frost) + bubbles;
        alpha = 0.88;
    } else if (kind == OBSIDIAN) {
        base = vec3(0.03, 0.02, 0.05) + tint * facetLine * 0.6 + tint * fresnel * 0.4;
        alpha = 1.0;
    } else if (kind == MAGMA_ROCK) {
        float f = fbmTap(Sampler0, uv * 1.2 + seed);
        float crack = 1.0 - smoothstep(0.0, 0.05, abs(f - 0.5));
        float pulse = 0.6 + 0.4 * sin(t * 0.5 + uv.y * 5.0);
        base = mix(vec3(0.06, 0.04, 0.04) - grain, tint * (0.8 + pulse * 0.6), crack);
        alpha = 1.0;
    } else if (kind == BONE_IVORY) {
        base = vec3(0.92, 0.88, 0.78) * (0.85 + grain) * mix(vec3(1.0), tint, 0.25);
        alpha = 1.0;
    } else if (kind == GLASS) {
        vec3 chroma = vec3(fresnel * 1.2, fresnel * 0.9, fresnel * 1.4);
        base = tint * 0.35 + chroma * 0.6 + spec;
        alpha = 0.55 + fresnel * 0.4;
    } else if (kind == METAL_BANDS) {
        float bands = pow(abs(sin(uv.y * count * 2.0 * MAGIC_PI)), 6.0);
        base = mix(tint * 0.5, tint, bands) * (0.7 + grain) + spec * 0.5;
        alpha = 1.0;
    } else if (kind == WOOD_VINE) {
        float ring = pow(abs(sin(uv.x * 14.0 + fbmTap(Sampler0, uv + seed) * 4.0)), 3.0);
        base = mix(tint * 0.55, tint, ring) * (0.8 + grain);
        alpha = 1.0;
    } else if (kind == GOLD) {
        float streak = pow(abs(sin((uv.x + uv.y) * 9.0 + t * 0.2)), 12.0);
        base = mix(vec3(0.85, 0.62, 0.2), vec3(1.0, 0.95, 0.7), streak * 0.7 + spec) * mix(vec3(1.0), tint, 0.3);
        alpha = 1.0;
    } else if (kind == STONE) {
        base = vec3(0.45, 0.46, 0.48) * (0.75 + grain * 2.0) * mix(vec3(1.0), tint, 0.2) + facetLine * 0.1;
        alpha = 1.0;
    } else if (kind == AMBER) {
        float inc = step(0.95, nz(Sampler0, uv * 5.0 + seed)) * 0.4;
        base = mix(vec3(0.85, 0.5, 0.1), vec3(1.0, 0.8, 0.35), fresnel) * mix(vec3(1.0), tint, 0.3) + inc;
        alpha = 0.85;
    } else if (kind == PEARL) {
        vec3 iri = vec3(0.5 + 0.5 * sin(fresnel * 6.0), 0.5 + 0.5 * sin(fresnel * 6.0 + 2.0), 0.5 + 0.5 * sin(fresnel * 6.0 + 4.0));
        base = mix(vec3(0.95), iri, 0.35) * mix(vec3(1.0), tint, 0.2);
        alpha = 1.0;
    } else {                                     // BLOOD
        // One flat colour, straight off the vertex. The chain used to end in a bare else holding
        // PEARL, which meant every kind past the end of the enum rendered as white nacre with
        // nothing logged - splitting it is what makes adding a material a compile-time act.
        //
        // No noise tap, no fresnel rim, no specular. Those read as a wet gem, and a wet gem is what
        // made a field of cubes look like a heap of different-coloured beads instead of blood. The
        // only variation left is the per-face brightness the emitter bakes into the vertex colour,
        // which is one colour lit six ways rather than six colours.
        base = tint;
        // Opaque on purpose. shardBody writes depth and flushes first, so a half-transparent cube
        // punches a hole in every glow behind it; the fade is the cube shrinking, not this.
        alpha = 1.0;
    }

    // integrity: crack isolines from 0.7, faces dissolve along worley edges by 1.0
    float cracks = 0.0;
    float keep = 1.0;
    if (phase > 0.7) {
        float k = (phase - 0.7) / 0.3;
        float f = fbmTap(Sampler0, uv * 2.0 + seed * 3.0);
        cracks = (1.0 - smoothstep(0.0, 0.03 + k * 0.03, abs(f - 0.5))) * k;
        // The general form leaves everything with w >= 0.8 alive at phase 1.0 - about a fifth of
        // the surface, and the same fifth every cast, since the seed only has six bits. On a big
        // faceted body that reads as leftover shards. On a thousand cubes it is two hundred of them
        // that never leave, so blood gets a dissolve that actually finishes.
        keep = kind == BLOOD ? step(k, w) : step(k * 0.9, w + 0.1);
    }

    vec3 col;
    if (kind == BLOOD) {
        // Every term in the general form - the lambert term, the specular, the fresnel wash, the
        // edge darkening - exists to make a facetted body read as a cut solid. On a cube six pixels
        // wide they only add colour the material does not have.
        col = base;
    } else {
        col = base * lambert + spec + tint * fresnel * 0.35 + vec3(1.0) * cracks * 0.8;
        col *= edgeDark * 0.4 + 0.6;
    }
    fragColor = vec4(col, alpha * opacity * keep) * ColorModulator;
    if (fragColor.a <= 0.01) {
        discard;
    }
}
