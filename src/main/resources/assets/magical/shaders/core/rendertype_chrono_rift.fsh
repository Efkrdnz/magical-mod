#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
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
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amp;
        p = p * 2.02 + vec2(7.1, 3.3);
        amp *= 0.5;
    }
    return value;
}

void main() {
    float time = GameTime * 1200.0;
    vec2 uv = texCoord0 * 2.0 - 1.0;   // x:[-1,1] horizontal, y:[-1,1] vertical
    float level = vertexColor.a;

    // Sequence carried by a single level: the hairline snaps in, then the wound rips wide.
    float lineForm = smoothstep(0.0, 0.08, level);
    float open = smoothstep(0.12, 1.0, level);

    // Profile: near-uniform width down the body, tapering to long thin points at the tips.
    float prof = 1.0 - smoothstep(0.5, 1.0, abs(uv.y));

    // Waving centerline; it writhes a touch more once the wound is open.
    float wob = sin(uv.y * 2.7 + time * 0.25) * 0.020
              + sin(uv.y * 6.3 - time * 0.40) * 0.008
              + (fbm(vec2(uv.y * 3.0, time * 0.05)) - 0.5) * 0.025;
    float center = wob * (0.35 + 0.65 * open);
    float d = abs(uv.x - center);

    // Jagged, serrated lips: two scales of tearing so the edge reads as ripped, not sliced.
    float serr = fbm(vec2(uv.y * 18.0, time * 0.10)) - 0.5;
    float rip  = fbm(vec2(uv.y * 44.0 - time * 0.22, 5.3)) - 0.5;
    float edgeJag = 1.0 + serr * 0.85 + rip * 0.45;

    // Wound half-width at this height, plus the pre-open hairline; epsilon keeps edges valid at the tips.
    float woundW = open * 0.17 * prof * max(edgeJag, 0.15);
    float lineW = (0.006 + 0.004 * sin(time * 0.6)) * prof * lineForm;
    float w = max(max(woundW, lineW), 0.0008);

    // Ominous heartbeat: a slow double-thump that swells every glowing part in unison.
    float bar = fract(time / 46.0);
    float thump = pow(max(0.0, 1.0 - bar * 3.2), 2.0)
                + 0.55 * pow(max(0.0, 1.0 - abs(bar - 0.42) * 7.0), 2.0);
    float pulse = 1.0 + thump * 0.5;

    // --- Aura tint (theme-shifted vertex color) ---
    vec3 aura = vertexColor.rgb;
    vec3 lipColor = mix(aura, vec3(1.0), 0.35);

    // --- Inside the wound: a void abyss - nearly black depths, veined with molten cracks
    //     that flare white-hot on the heartbeat. ---
    vec2 fp = vec2((uv.x - center) * 6.0, uv.y * 3.0 - time * 0.07);
    float warp = fbm(fp + time * 0.045);
    float depthN = fbm(fp * 1.4 + warp * 1.8 + vec2(0.0, -time * 0.11));
    float veinN = fbm(fp * 2.6 + warp * 2.2 + vec2(time * 0.02, -time * 0.16));
    float veins = 1.0 - smoothstep(0.0, 0.09, abs(veinN - 0.5));
    veins = pow(veins, 1.6);

    vec3 realm = aura * (0.04 + 0.10 * depthN);            // near-black depths
    realm += aura * veins * (0.9 + 0.9 * thump);           // molten veins crawl through the dark
    realm = mix(realm, vec3(1.0), veins * thump * 0.35);   // and peak hot on the beat
    realm += lipColor * (1.0 - smoothstep(0.0, w * 0.45, d)) * 0.7; // searing core at the deepest point

    float inside = 1.0 - smoothstep(w * 0.7, w, d);

    // --- Serrated lips: a thin searing rim along the torn edge ---
    float lip = smoothstep(w * 0.72, w, d) * (1.0 - smoothstep(w, w * 1.30, d));

    // --- Lightning veins arcing out of the wound and crawling across the sky ---
    float arcField = fbm(vec2(uv.y * 7.0 + time * 0.05, (d - w) * 9.0 + time * 0.03));
    float arcs = 1.0 - smoothstep(0.0, 0.05, abs(arcField - 0.5));
    float arcReach = smoothstep(w * 5.0, w * 1.05, d) * prof;
    float arcFlicker = 0.4 + 0.6 * step(0.45, hash(vec2(floor(time * 0.9), 3.7)));
    arcs = pow(arcs, 1.7) * arcReach * arcFlicker * open;

    // --- Black corona: the sky itself darkens and dies around the cut. It must hug the wound
    //     tightly - a slow falloff would still carry alpha at the quad boundary and print a
    //     visible rectangle onto the sky. ---
    float shadow = exp(-max(0.0, d - w) * 4.5) * prof * open;
    shadow *= 0.5 + 0.5 * fbm(vec2(uv.y * 2.5 - time * 0.03, d * 4.0));
    shadow *= 1.0 - inside;

    // Kill everything well before the quad edge, with a noisy border so no straight
    // seam survives even where the corona or arcs would otherwise reach it.
    float edgeN = fbm(vec2(uv.y * 2.3, uv.x * 2.3) + time * 0.008) - 0.5;
    float edgeFade = (1.0 - smoothstep(0.45, 0.88 + edgeN * 0.16, abs(uv.x)))
                   * (1.0 - smoothstep(0.72, 0.97, abs(uv.y)));

    // --- Thin lit halo hugging the lips, flickering like a dying flame ---
    float halo = exp(-max(0.0, d - w) * 9.0) * prof;
    halo *= 0.5 + 0.6 * fbm(vec2(uv.y * 3.5 - time * 0.06, d * 5.0));

    // Bright flash running along the hairline before it opens.
    float lineFlash = lineForm * (1.0 - open) * (1.0 - smoothstep(0.0, lineW * 2.5, d)) * prof;

    // --- Compose: the corona contributes darkness (alpha without color), everything else light ---
    vec3 color = realm * inside;
    color += lipColor * lip * 1.6 * pulse;
    color += aura * halo * 0.85 * pulse * (1.0 - inside);
    color += mix(aura, vec3(1.0), 0.55) * arcs * 1.5;
    color += lipColor * lineFlash * 2.2;

    float alpha = inside * 0.97 + lip * pulse + halo * 0.55 + arcs + shadow * 0.65 + lineFlash * 1.5;
    alpha = clamp(alpha, 0.0, 1.0) * level * edgeFade;

    fragColor = vec4(color, alpha) * ColorModulator;
}
