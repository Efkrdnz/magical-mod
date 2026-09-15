#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

// What one layer may contribute at its very hottest, before its own opacity is applied. A strike is
// not one layer: the solid shows a near and a far face, and the trail draws four to six lagged
// copies of the whole thing. Additive blending sums all of them, so this has to leave room for the
// stack - but the falloff on those copies does the rest of the work now that it is no longer being
// discarded.
// Set from the stack, not by eye: a pixel at the heart of a strike is covered by both faces of the
// solid and by every trail copy, whose falloff sums to about 2.5, at an opacity near 0.7 - so the
// total is roughly three and a half times this. At 0.30 the hottest pixel lands near white and
// everything short of it keeps the element's own colour instead of clipping to grey.
const float CEILING = 0.30;

in vec2 texCoord0;   // x = along the arc (0..1), y = across the ribbon (0..1)
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * amp;
        p = p * 2.03 + vec2(8.31, 2.17);
        amp *= 0.5;
    }
    return v;
}

// The blade ribbon: a white-hot centreline down the middle of the band, a harder and brighter
// cutting edge on the leading side, and filaments streaming along the length of the cut.
void main() {
    float time = GameTime * 1200.0;
    float along = texCoord0.x;
    float across = texCoord0.y;

    // Distance out from the centreline, 0 on the spine and 1 at either lip of the ribbon.
    float offCentre = abs(across - 0.5) * 2.0;
    float spine = pow(1.0 - offCentre, 3.0);
    float body = (1.0 - offCentre) * 0.5;

    // The leading half of the ribbon carries the cut: it sharpens to a hard line at across = 1.
    float lead = smoothstep(0.5, 1.0, across);
    float cut = pow(lead, 6.0) * 1.6;

    // A hard, thin filament of white down the exact centre. Every term above falls off smoothly,
    // and a picture built only of smooth falloffs reads as fog however bright it is - there is
    // nothing in it with an edge, so there is nothing to judge its size against. This is the one
    // part of a strike with a hard boundary.
    float core = pow(1.0 - offCentre, 22.0);

    // Filaments streaking along the arc; they slide over time so the cut never sits still. Two
    // octaves at different rates rather than one: a single frequency at this scale reads as a
    // smear, and the fine one is what stops the body looking airbrushed.
    float flow = fbm(vec2(along * 7.0 - time * 0.45, across * 3.0 + time * 0.06));
    float grain = fbm(vec2(along * 23.0 - time * 1.30, across * 6.0));
    float streak = 0.55 + 0.55 * flow + 0.30 * grain * grain;

    // Cut both ends off so a ribbon never shows a hard cap where its quads stop.
    float ends = smoothstep(0.0, 0.04, along) * smoothstep(1.0, 0.96, along);

    // The body carries the element's colour over the whole blade, the spine lifts the centreline,
    // and the cut is the hot line on the leading lip. Weighted so the coloured body dominates by
    // area and the white-hot part is thin - the reverse of what it was, which is why every element
    // looked the same.
    // The core is deliberately outside the streak: the filaments may thin the body and shade the
    // spine, but the white line down the middle of a cut does not flicker, and letting the noise
    // eat it was most of why the whole thing looked soft.
    float glow = (body * 0.45 + spine * 0.55 + cut * 0.85) * streak * ends + core * 1.10 * ends;

    // Additive blending sums every layer that covers a pixel, and one strike is several: two faces
    // of the solid, an ornament, and four to six lagged copies of the whole thing. Left raw, glow
    // peaks near 3.5 on the spine alone and the sum saturates every channel, which is why a fire
    // strike used to come out a white cloud with an orange fringe instead of fire. This curve
    // asymptotes to 1, so no single layer can blow the frame out on its own and the stack climbs
    // towards white gently instead of arriving there immediately.
    glow = glow / (1.0 + glow) * CEILING;

    // Keep the hue. Only the hot core goes white, and it is a narrow core: squaring the spine term
    // pulls the white in to the centreline, and the ceiling leaves the element's own colour in the
    // brightest pixel rather than washing it out.
    // Only the cutting lip goes white. The spine stays the element's own colour, so a fire strike
    // is orange along its whole body with a white edge, rather than white with an orange fringe.
    vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(cut * 0.26 + core * 0.55, 0.0, 0.62));

    // Fold the vertex alpha into the emitted colour, the way magic_common.glsl's additiveOut does
    // for every other additive effect in the mod. This render type blends ONE, ONE: the alpha
    // channel is discarded outright, so until now BODY_ALPHA and EDGE_ALPHA, the per-copy trail
    // falloff, and the strike's own fade-out were all being computed and then thrown away. Every
    // one of the four to six lagged copies drew at full strength on top of the head, which is most
    // of why a strike was a saturated white mass, and a strike did not fade as it died - it simply
    // stopped existing.
    float opacity = vertexColor.a;
    fragColor = vec4(col * glow * opacity, clamp(glow, 0.0, 1.0) * opacity) * ColorModulator;
}
