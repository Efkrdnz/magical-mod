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
const int KIND_HORIZON = 1;
const int KIND_MERIDIAN = 2;
const int KIND_TICK = 3;
const int KIND_MARK = 4;
const int KIND_CROWN = 5;
const int KIND_RIPPLE = 6;

// The optics, computed here in floats and again in SubspaceOptics in doubles where a test can
// reach them. Both copies have to be the same curve.
const float K = 3.0;
const float K_SEALED = 2.4;
const float A_FACE = 0.07;
const float A_RIM = 0.58;
const float A_RIM_SEALED = 0.8;
const float NEAR_LIFT = 0.1;
const float PX_MAIN = 2.2;
const float PX_HAIR = 1.6;
const float PX_BURN = 1.4;
const float INK_LINE = 0.8;
const float INK_TICK = 0.58;
const float INK_MARK = 0.72;
const float SHOULDER_SPREAD = 3.2;
const float SHOULDER_INK = 0.34;
const float MIN_GRAD = 0.0001;
const float CROWN_LO = 0.955;
const float CROWN_HI = 0.985;
const float CROWN_DIM = 0.18;
const float GLOW_CEILING = 0.3;
const float GLOW_KNEE = 3.0;
const float LINE_TINT = 0.22;
const float INK_CROWN = 0.55;
const float INK_RIPPLE = 0.45;

const vec3 INK = vec3(8.0, 12.0, 18.0) / 255.0;
const vec3 BURNISH = vec3(87.0, 188.0, 255.0) / 255.0;

const int GRAVITY_LEVEL = 0;
const int GRAVITY_INVERTED = 1;
const int GRAVITY_DISSOLVED = 2;

// A meridian is either the graduated line standing due north or one of the three plain cardinals
// that give the inside of the dome a compass to be read against.
const int MERIDIAN_NORTH = 0;
const int MERIDIAN_CARDINAL = 1;

// A line a fixed number of pixels wide however far off the wall is. The derivative of a coordinate
// is exactly how much of it one pixel covers, which is the exchange rate between the two. The
// floor matters: at the limb the shell's parameters stop changing across a pixel, the derivative
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

    vec3 material = vertexColor.rgb;
    vec3 glowColour = BURNISH;
    float glow = 0.0;
    float alpha = 0.0;

    if (kind == KIND_MEMBRANE) {
        // Inverted Fresnel. Where the wall faces you it is all but absent; where it turns away it
        // gathers. That is what a pane of glass does, and it is the only arrangement that can be
        // see-through in the middle and findable at the edge at the same time - a flat alpha is
        // either a haze over everything you are trying to look at or nothing at all.
        float c = abs(dot(normalize(shellNormal), normalize(viewDir)));
        bool sealedWall = (param & 1) != 0;
        bool twoWalls = (param & 2) != 0;
        float rim = sealedWall ? A_RIM_SEALED : A_RIM;
        float k = sealedWall ? K_SEALED : K;
        float delivered = A_FACE + (rim - A_FACE) * pow(max(0.0, 1.0 - c), k);

        // Outside the domain a sight line crosses two walls and inside it crosses one, so each of
        // the two paints the root of what the pair owes. The two pictures then agree at every
        // angle, instead of the domain doubling in weight the moment you step out of it - which
        // would be exactly backwards, since the person deciding whether to walk in is the one who
        // needs to see through it.
        alpha = twoWalls ? 1.0 - sqrt(max(0.0, 1.0 - delivered)) : delivered;

        // Within arm's reach the wall gains a little body, so the caster can feel where it is
        // without having to sight along it.
        alpha += NEAR_LIFT * (1.0 - c) * (1.0 - smoothstep(1.0, 4.0, length(viewDir)));

        // The zenith thins away to almost nothing. A domain is a wall, not a lid: from inside you
        // can always find your own sky straight up, and the cap that would otherwise sit over your
        // head is the part of a dome that reads most like a bubble.
        float height = abs(texCoord0.y * 2.0 - 1.0);
        alpha *= mix(1.0, CROWN_DIM, smoothstep(CROWN_LO, CROWN_HI, height));

        // Only the last sliver of the limb lights. Everywhere else the wall darkens what is behind
        // it, and darkening is the one thing that reads over daylight sand as well as over a night
        // sky; an additive wash reads over neither.
        glow = smoothstep(0.82, 1.0, 1.0 - c) * sunLift;
    } else {
        // Everything written on the wall is a ribbon carrying its own across-coordinate, so one
        // stroke serves the horizon, the meridian, a graduation, a law mark and a ripple. Each
        // branch says only how far this fragment is from the nearest line and how much of the line
        // is showing here; the stroke itself is cut once, below, so every mark in the domain is the
        // same kind of mark.
        float across = texCoord0.y - 0.5;
        float along = texCoord0.x;
        float distance = abs(across);
        float showing = 1.0;
        float pixels = PX_MAIN;
        float ink = INK_LINE;
        float shoulder = SHOULDER_INK;
        // A line cut into the wall is the wall's own light gone dark, not black.
        vec3 paint = mix(INK, BURNISH, LINE_TINT);

        if (kind == KIND_HORIZON) {
            // Gravity read a second time. A level line is the thing that says which way is down,
            // so the most consequential law in the domain is legible before a single mark has been
            // learned: inverted it doubles, dissolved it breaks into dashes.
            if (param == GRAVITY_INVERTED) {
                distance = min(abs(across - 0.16), abs(across + 0.16));
                pixels = PX_HAIR;
            } else if (param == GRAVITY_DISSOLVED) {
                showing = step(0.42, fract(along * 96.0));
            }
        } else if (kind == KIND_MERIDIAN) {
            // Only north is graduated and only north is drawn full; the other three cardinals are
            // there so that north means something, and are deliberately the quieter lines.
            if (param == MERIDIAN_CARDINAL) {
                pixels = PX_HAIR;
                ink = INK_TICK;
            }
        } else if (kind == KIND_TICK) {
            pixels = PX_HAIR;
            ink = INK_TICK;
        } else if (kind == KIND_CROWN) {
            // The one hard edge the domain owns, and one of the three things in it that paint
            // rather than darken - see below.
            pixels = PX_BURN;
            ink = INK_CROWN;
            shoulder *= 0.5;
            paint = vertexColor.rgb;
            glowColour = vertexColor.rgb;
        } else if (kind == KIND_RIPPLE) {
            ink = INK_RIPPLE;
            showing = sin(phase * MAGIC_PI);
            shoulder *= 0.5;
            paint = vertexColor.rgb;
            glowColour = vertexColor.rgb;
        } else {
            // A law mark. The settle runs its form out from its own centre once and then stops:
            // a law arriving is a thing that happens, not a thing that keeps happening.
            vec2 p = vec2(along, texCoord0.y) * 2.0 - 1.0;
            float settle = easeInOut(phase);
            distance = markForm(param & 7, p / max(settle, 0.04));
            showing = settle;
            pixels = PX_BURN;
            ink = INK_MARK;
            shoulder *= 0.5;

            // A mark is the one thing in the domain allowed to be brighter than the sky, and it
            // has to be: its colour is what says which way a law bends, and a colour carried only
            // in the glow is a colour daylight takes away - every law came out the same grey. A
            // line running right round the wall cannot do this, because anything covering that
            // much of the frame and lighting will wash the sky out. A mark is six degrees wide,
            // and an over-blend cannot clip however bright it is, so a mark may paint.
            paint = vertexColor.rgb;
            glowColour = vertexColor.rgb;
        }

        // The stroke and the shoulder under it. The crisp line is what the eye measures against
        // and the shoulder is what keeps it from disappearing into a busy background.
        float line = showing * max(pixelLine(distance, pixels),
                pixelLine(distance, pixels * SHOULDER_SPREAD) * shoulder);

        material = paint;
        alpha = line * ink;
        glow = line * sunLift;
    }

    alpha *= vertexColor.a;
    glow *= vertexColor.a;

    // A soft knee under a hard ceiling. No stack of strokes along one sight line can reach white,
    // which is the way every additive effect in this mod has always failed over daylight.
    glow = GLOW_CEILING * (1.0 - exp(-max(glow, 0.0) * GLOW_KNEE));

    // Four and a half percent alpha across a gradient bands visibly at eight bits, and a dome is
    // one enormous gradient. Half a bit of blue noise costs one tap and removes every ring.
    alpha += (blueNoise(Sampler0, gl_FragCoord.xy / 64.0) - 0.5) / 255.0 * step(0.002, alpha);
    alpha = clamp(alpha, 0.0, 1.0);

    // Written out by hand rather than through additiveOut, which premultiplies for a ONE/ONE blend
    // where the alpha channel is discarded. Here alpha is how much of the world the fragment takes
    // away, so that helper would cut a hole in the frame wherever the wall was brightest.
    fragColor = vec4(material * alpha + glowColour * glow, alpha) * ColorModulator;
}
