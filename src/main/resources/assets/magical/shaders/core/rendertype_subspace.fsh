#version 150

#moj_import <magical:magic_common.glsl>
#moj_import <magical:magic_frag.glsl>

uniform vec4 ColorModulator;
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 shellNormal;
in vec3 viewDir;
flat in ivec4 magicA;
flat in vec2 magicB;

out vec4 fragColor;

// What this fragment belongs to. Mirrored in SubspaceKind, pinned by SubspaceShaderAssetsTest.
const int KIND_MEMBRANE = 0;
const int KIND_FOOT = 1;
const int KIND_SPRING = 2;
const int KIND_NOTCH = 3;
const int KIND_RIB = 4;
const int KIND_BOSS = 5;
const int KIND_OCULUS = 6;
const int KIND_RIPPLE = 7;

// The optics, computed here in floats and again in SubspaceOptics in doubles where a test can
// reach them. Both copies have to be the same numbers, and a test reads both files to check.
const float A_PANE = 0.03;
const float SEAL_LIFT = 0.03;
const float A_LIMB = 0.62;
const float A_LIMB_SEALED = 0.84;
const float K_LIMB = 3.0;
const float K_SEALED = 2.4;
const float A_SHADOW = 0.62;
const float A_HIGHLIGHT = 0.52;
const float ARRIS_PIXELS = 1.2;
const float MIN_LIP_PIXELS = 1.5;
const float PX_RIB = 6.2;
const float RIB_LIT_GAIN = 1.34;
const float PX_SPRING = 6.2;
const float PX_FOOT = 7.0;
const float PX_OCULUS = 6.0;
const float PX_NOTCH = 6.0;
const float PX_GLYPH = 6.6;
const float PX_RIPPLE = 6.0;
const float NOTCH_DIM = 0.72;
const float BOSS_LIFT = 0.06;
const float APERTURE_SIN = 0.9781476007338057;
const float DASH_CYCLES = 72.0;
const float GLOW_CEILING = 0.3;
const float GLOW_KNEE = 3.0;
const float GLOW_TINT = 0.45;
const float GLYPH_LIGHT_SPREAD = 0.35;
const float MIN_GRAD = 0.0001;

const vec3 BODY = vec3(18.0, 26.0, 38.0) / 255.0;
const vec3 GLAZE = vec3(220.0, 235.0, 255.0) / 255.0;
const vec3 BURNISH = vec3(87.0, 188.0, 255.0) / 255.0;

const int GRAVITY_LEVEL = 0;
const int GRAVITY_INVERTED = 1;
const int GRAVITY_DISSOLVED = 2;

// The sun comes from up and slightly east, in the stroke own across-coordinate. One direction for
// every glyph in the domain, so no two law marks are lit from different places.
const vec2 GLYPH_LIGHT = vec2(0.42, 0.91);

// A line a fixed number of pixels wide however far off the wall is. The derivative of a coordinate
// is exactly how much of it one pixel covers, which is the exchange rate between the two. The
// floor matters: at the limb the shell parameters stop changing across a pixel, the derivative
// collapses toward zero, and without a floor the silhouette grows a ring of fireflies.
float pixelLine(float d, float pixels) {
    float g = max(fwidth(d), MIN_GRAD);
    return 1.0 - smoothstep((pixels - 1.0) * 0.5 * g, (pixels + 1.0) * 0.5 * g, abs(d));
}

float chevron(vec2 p, float dir) {
    vec2 tip = vec2(0.0, 0.34 * dir);
    return min(sdSegment(p, vec2(-0.52, -0.26 * dir), tip), sdSegment(p, tip, vec2(0.52, -0.26 * dir)));
}

float bracketPair(vec2 p) {
    vec2 q = vec2(abs(p.x), p.y);
    float upright = sdSegment(q, vec2(0.46, -0.44), vec2(0.46, 0.44));
    float lip = min(sdSegment(q, vec2(0.46, 0.44), vec2(0.20, 0.44)), sdSegment(q, vec2(0.46, -0.44), vec2(0.20, -0.44)));
    return min(upright, lip);
}

float arrowForm(vec2 p) {
    float shaft = sdSegment(p, vec2(-0.55, 0.0), vec2(0.45, 0.0));
    float head = min(sdSegment(p, vec2(0.45, 0.0), vec2(0.12, 0.26)), sdSegment(p, vec2(0.45, 0.0), vec2(0.12, -0.26)));
    return min(shaft, head);
}

// One device per kind of bend a law makes, in the order of SpaceRuleChange. They are read at a few
// degrees of arc on a curved wall, so each is three strokes at most and no two share a silhouette:
// up, down, struck out, mirrored, bracketed, overshooting, aimed, plain.
float markForm(int change, vec2 p) {
    if (change == 0) return chevron(p, 1.0);
    if (change == 1) return chevron(p, -1.0);
    if (change == 2) return min(abs(length(p) - 0.46), sdSegment(p, vec2(-0.38, -0.38), vec2(0.38, 0.38)));
    if (change == 3) return min(chevron(p - vec2(0.0, 0.26), 1.0), chevron(p + vec2(0.0, 0.26), -1.0));
    if (change == 4) return bracketPair(p);
    if (change == 5) return min(chevron(p + vec2(0.0, 0.22), 1.0), abs(length(p - vec2(0.0, -0.34)) - 0.62));
    if (change == 6) return arrowForm(p);
    return sdSegment(p, vec2(-0.55, 0.0), vec2(0.55, 0.0));
}

void main() {
    int kind = magicA.x;
    float sunLift = float(magicA.y) / 63.0;
    int param = magicA.z;
    float phase = clamp(magicB.x, 0.0, 1.0);

    vec3 accent = vertexColor.rgb;
    vec3 material = BODY;
    vec3 glowColour = mix(BURNISH, accent, GLOW_TINT);
    float glow = 0.0;
    float alpha = 0.0;

    if (kind == KIND_MEMBRANE) {
        bool sealedWall = (param & 1) != 0;
        if ((param & 2) != 0) {
            // From outside, and only from outside, the impact parameter genuinely sweeps the disc
            // and the limb genuinely is where a silhouette lives, so the Fresnel is not merely
            // valid here - it is the whole of what an opponent sees. A sight line crosses two
            // walls, so each paints the exact root of what the pair owes and the picture from
            // outside matches the picture from in.
            float c = abs(dot(normalize(shellNormal), normalize(viewDir)));
            float rim = sealedWall ? A_LIMB_SEALED : A_LIMB;
            float k = sealedWall ? K_SEALED : K_LIMB;
            float pane = sealedWall ? A_PANE + SEAL_LIFT : A_PANE;
            float delivered = pane + (rim - pane) * pow(max(0.0, 1.0 - c), k);
            alpha = 1.0 - sqrt(max(0.0, 1.0 - delivered));
        } else {
            // --- membrane, from inside: no view-angle term ---
            // A domain is a sphere centred on its caster, so from here every sight line runs along
            // the surface normal and any function of the angle between them is a constant. The old
            // wall was an inverted Fresnel and it varied by ten decimal places of nothing. The pane
            // is a tint and carries no reading; the vault standing on it is what is seen.
            alpha = sealedWall ? A_PANE + SEAL_LIFT : A_PANE;
            alpha *= 1.0 - step(APERTURE_SIN, abs(texCoord0.y * 2.0 - 1.0));
            // --- end membrane, from inside ---
        }
        material = BODY;
    } else if (kind == KIND_BOSS) {
        // A law glyph, embossed on a panel raised out of the pane. The settle runs its form out
        // from its own centre once and then stops: a law arriving is a thing that happens, not a
        // thing that keeps happening.
        vec2 p = vec2(texCoord0.x, texCoord0.y) * 2.0 - 1.0;
        float settle = easeInOut(phase);
        float d = markForm(param & 7, p / max(settle, 0.04));
        float band = pixelLine(d, PX_GLYPH);
        // Lit off the signed distance own gradient, so a stroke gets a lit edge and a shadow edge
        // rather than a symmetric halo - the same chamfer every other member has, on a curve.
        vec2 gradient = vec2(dFdx(d), dFdy(d));
        float slope = length(gradient);
        vec2 facing = slope > 1.0e-8 ? gradient / slope : vec2(0.0, 1.0);
        float lit = smoothstep(-GLYPH_LIGHT_SPREAD, GLYPH_LIGHT_SPREAD, dot(facing, normalize(GLYPH_LIGHT)));

        float ink = band * settle * mix(A_SHADOW, A_HIGHLIGHT, lit);
        float panel = BOSS_LIFT * settle * (1.0 - smoothstep(0.82, 1.0, max(abs(p.x), abs(p.y))));
        alpha = ink + panel * (1.0 - ink);
        material = alpha > 1.0e-5
                ? (mix(BODY, accent, lit) * ink + BODY * panel * (1.0 - ink)) / alpha
                : BODY;
        glow = band * settle * (1.0 - lit) * sunLift;
    } else {
        // Every member of the vault is a chamfer: a lip that darkens toward BODY meeting a lip that
        // lightens toward GLAZE at a one-pixel arris. The two move a background in opposite
        // directions, so whichever lip the background defeats the other one carries - which is why
        // this reads over noon sand and over blackstone with the same numbers, and why the floor
        // under its contrast has a closed form instead of a setting.
        float across = texCoord0.y - 0.5;
        float along = texCoord0.x;
        float showing = 1.0;
        float weight = 1.0;
        float pixels = PX_RIB;
        bool flip = false;

        if (kind == KIND_FOOT) {
            pixels = PX_FOOT;
            showing = param == GRAVITY_DISSOLVED ? step(0.45, fract(along * DASH_CYCLES)) : 1.0;
            flip = param == GRAVITY_INVERTED;
        } else if (kind == KIND_SPRING) {
            pixels = PX_SPRING;
            showing = param == GRAVITY_DISSOLVED ? step(0.45, fract(along * DASH_CYCLES)) : 1.0;
            flip = param == GRAVITY_INVERTED;
        } else if (kind == KIND_OCULUS) {
            // The one ring lit from the other side, because its lit lip has to face into the
            // opening rather than out of it. A hole reads as a hole when its rim catches the light.
            pixels = PX_OCULUS;
            flip = true;
        } else if (kind == KIND_NOTCH) {
            pixels = PX_NOTCH;
            weight = (param & 1) != 0 ? 1.0 : NOTCH_DIM;
        } else if (kind == KIND_RIPPLE) {
            pixels = PX_RIPPLE;
            showing = sin(phase * MAGIC_PI);
        } else {
            // A rib. A written law makes it heavier and gives it the operation own colour; an empty
            // slot is held back rather than left out, so the count has a denominator. The settle
            // runs the promotion up from the springing course once.
            pixels = (param & 1) != 0 ? PX_RIB * RIB_LIT_GAIN : PX_RIB;
            weight = (param & 1) != 0 ? 1.0 : NOTCH_DIM;
            showing = 1.0 - step(phase, along);
            flip = (param & 2) != 0;
        }

        float g = max(fwidth(across), MIN_GRAD);
        float band = pixelLine(across, pixels);
        // The arris is measured in the same derivative the stroke is, so at grazing incidence the
        // two blow up together and the chamfer can never flatten into one mid-blue line.
        float lit = smoothstep(-ARRIS_PIXELS * 0.5 * g, ARRIS_PIXELS * 0.5 * g, across);
        if (flip) {
            lit = 1.0 - lit;
        }

        material = mix(BODY, accent, lit);
        alpha = band * showing * weight * mix(A_SHADOW, A_HIGHLIGHT, lit);
        // The light sits in the groove rather than on the lip. The lit lip has already been pushed
        // toward white and has no headroom left; the shadow lip has just been pushed toward the
        // wall own slate and has all of it, and a dark groove with light in it is what a lit
        // engraving looks like.
        glow = band * showing * weight * (1.0 - lit) * sunLift;
    }

    alpha *= vertexColor.a;
    glow *= vertexColor.a;

    // A soft knee under a hard ceiling. No stack of strokes along one sight line can reach white,
    // which is the way every additive effect in this mod has always failed over daylight.
    glow = GLOW_CEILING * (1.0 - exp(-max(glow, 0.0) * GLOW_KNEE));

    // Three percent alpha across a gradient bands visibly at eight bits, and a dome is one enormous
    // gradient. Half a bit of blue noise costs one tap and removes every ring.
    alpha += (blueNoise(Sampler0, gl_FragCoord.xy / 64.0) - 0.5) / 255.0 * step(0.002, alpha);
    alpha = clamp(alpha, 0.0, 1.0);

    // Written out by hand rather than through additiveOut, which premultiplies for a ONE/ONE blend
    // where the alpha channel is discarded. Here alpha is how much of the world the fragment takes
    // away, so that helper would cut a hole in the frame wherever the wall was brightest.
    fragColor = vec4(material * alpha + glowColour * glow, alpha) * ColorModulator;
}
