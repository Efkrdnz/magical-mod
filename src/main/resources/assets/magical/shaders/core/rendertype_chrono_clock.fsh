#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float PI  = 3.14159265359;
const float TAU = 6.28318530718;

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
    for (int i = 0; i < 5; i++) {
        v += noise(p) * amp;
        p = p * 2.02 + vec2(7.1, 3.3);
        amp *= 0.5;
    }
    return v;
}

// Bright ring band centred on 'radius' with half-width 'w'.
float ringBand(float r, float radius, float w) {
    return 1.0 - smoothstep(0.0, w, abs(r - radius));
}

// A radial band that is 1 between [lo,hi] and feathers over 'aa'.
float radialBand(float r, float lo, float hi, float aa) {
    return smoothstep(lo - aa, lo + aa, r) * smoothstep(hi + aa, hi - aa, r);
}

// A clock hand: a tapered spoke from the hub out to 'len', with a short counterweight tail.
// 'ang' is measured clockwise from 12 o'clock. Returns coverage 0..1.
float hand(vec2 p, float ang, float backLen, float len, float baseW, float tipW) {
    vec2 dir = vec2(sin(ang), cos(ang));
    vec2 nrm = vec2(dir.y, -dir.x);
    float along = dot(p, dir);
    float perp = dot(p, nrm);
    float t = clamp((along + backLen) / (len + backLen), 0.0, 1.0);
    float w = mix(baseW, tipW, t);
    float body = 1.0 - smoothstep(w * 0.55, w, abs(perp));
    float ends = smoothstep(-backLen - 0.015, -backLen + 0.015, along)
               * smoothstep(len + 0.02, len - 0.02, along);
    return body * ends;
}

void main() {
    float time = GameTime * 1200.0;
    float level = vertexColor.a;

    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) {
        discard; // circular cutout: the square quad never shows its corners
    }

    // Clock-space angle: 0 at 12 o'clock, increasing clockwise.
    float ca = atan(p.x, p.y);            // -PI..PI, 0 at top (+y)
    float ca01 = fract(ca / TAU + 1.0);   // 0..1 clockwise from 12

    // Theme-shifted vertex tint, its incandescent highlight, and an icy frozen wash.
    vec3 base  = vertexColor.rgb;
    vec3 glow  = mix(base, vec3(1.0), 0.8);
    vec3 frost = mix(base, vec3(0.72, 0.9, 1.0), 0.62);
    vec3 amber = mix(base, vec3(1.0, 0.42, 0.28), 0.55); // second-hand accent

    // A slow, straining pulse - the dial is alive but held fast by the freeze.
    float grip = clamp(level, 0.0, 1.0);
    float pulse = 0.86 + 0.14 * sin(time * 0.5);

    vec3 col = vec3(0.0);
    float ink = 0.0;   // opaque engraving coverage
    float haze = 0.0;  // soft translucent fill/glow

    // -- disc mask & face fill (frosted glass with fine grain) --
    float disc = smoothstep(1.0, 0.965, r);
    float faceEdge = smoothstep(1.0, 0.86, r);
    float grain = fbm(p * 7.0 + vec2(3.0, 7.0));
    col += mix(base * 0.10, frost * 0.20, grain) * faceEdge;
    haze = max(haze, 0.16 * faceEdge);

    // Radiating frost fractures across the frozen face.
    float fract1 = fbm(vec2(ca01 * 15.0, r * 5.5 + 1.0));
    float frostLines = smoothstep(0.58, 0.92, fract1) * smoothstep(0.92, 0.35, r);
    col += frost * frostLines * 0.55 * grip;
    haze = max(haze, frostLines * 0.22 * grip);

    // -- outer bezel: twin bright rings with a groove between them --
    float bezelOuter = ringBand(r, 0.952, 0.020);
    float bezelInner = ringBand(r, 0.888, 0.012);
    float bezel = max(bezelOuter, bezelInner * 0.82);
    col += glow * bezel * 1.7;
    ink = max(ink, bezel);
    float groove = ringBand(r, 0.920, 0.006);
    col += base * groove * 0.7;
    ink = max(ink, groove * 0.5);

    // Luminous rim halo just inside the bezel.
    float rimGlow = exp(-pow((r - 0.905) / 0.05, 2.0));
    col += glow * rimGlow * 0.45;
    haze = max(haze, rimGlow * 0.30);

    // -- 60 minute ticks --
    float mx = ca01 * 60.0;
    float md = abs(mx - floor(mx + 0.5));
    float mBand = radialBand(r, 0.822, 0.862, 0.004);
    float minuteTick = (1.0 - smoothstep(0.06, 0.11, md)) * mBand;
    col += glow * minuteTick * 1.35;
    ink = max(ink, minuteTick);

    // -- 12 hour ticks: longer and heavier --
    float hx = ca01 * 12.0;
    float hd = abs(hx - floor(hx + 0.5));
    float hBand = radialBand(r, 0.772, 0.872, 0.005);
    float hourTick = (1.0 - smoothstep(0.10, 0.18, hd)) * hBand;
    col += glow * hourTick * 1.8;
    ink = max(ink, hourTick);

    // -- hour pips: a bright bead over each hour mark --
    float hi = floor(hx + 0.5);
    float ha = hi * TAU / 12.0;
    vec2 pip = vec2(sin(ha), cos(ha)) * 0.705;
    float pd = length(p - pip);
    float pipDot = 1.0 - smoothstep(0.018, 0.045, pd);
    col += glow * pipDot * 2.0;
    ink = max(ink, pipDot);

    // -- decorative concentric grooves engraved across the inner face --
    float grooves = ringBand(r, 0.640, 0.004) + ringBand(r, 0.520, 0.004) + ringBand(r, 0.395, 0.004);
    col += base * grooves * 0.55;
    ink = max(ink, grooves * 0.45);

    // -- central gearwork: a toothed hub with a bright bore ring --
    float toothRim = 0.300 + 0.030 * step(0.5, fract(ca01 * 24.0));
    float gearBody = smoothstep(toothRim, toothRim - 0.018, r);
    col += mix(base * 0.35, glow, 0.32) * gearBody * 0.95;
    ink = max(ink, gearBody * 0.85);
    float gearEdge = ringBand(r, toothRim, 0.010) * step(0.26, r);
    col += glow * gearEdge * 1.5;
    ink = max(ink, gearEdge);
    float bore = ringBand(r, 0.150, 0.012);
    col += glow * bore * 1.6;
    ink = max(ink, bore);

    // -- the three hands, frozen at an elegant pose (~10:10:35) --
    // A faint tremble and a rare glitch-jump, as if time strains against the freeze but cannot move.
    float tremble = sin(time * 2.3) * 0.014 * grip + sin(time * 7.0) * 0.004;
    float glitch = step(0.985, fract(time * 0.017)) * 0.09;
    float hourA = radians(300.0) + tremble * 0.4 + glitch;
    float minA  = radians(60.0)  + tremble * 1.0 + glitch * 1.3;
    float secA  = radians(210.0) + tremble * 1.7 + glitch * 2.1;

    float hourHand = hand(p, hourA, 0.06, 0.44, 0.030, 0.012);
    float minHand  = hand(p, minA,  0.07, 0.66, 0.022, 0.008);
    float secHand  = hand(p, secA,  0.18, 0.74, 0.010, 0.004);

    col += glow * hourHand * 2.1;
    ink = max(ink, hourHand);
    col += glow * minHand * 2.1;
    ink = max(ink, minHand);
    col += amber * secHand * 2.0;
    ink = max(ink, secHand);

    // Central hub cap over the hands' pivot.
    float cap = 1.0 - smoothstep(0.038, 0.052, r);
    col += glow * cap * 2.3;
    ink = max(ink, cap);
    float capRing = ringBand(r, 0.060, 0.006);
    col += glow * capRing * 1.4;
    ink = max(ink, capRing);

    float alpha = clamp(max(ink, haze), 0.0, 1.0) * disc * level * pulse;
    col *= pulse;

    fragColor = vec4(col, alpha) * ColorModulator;
}
