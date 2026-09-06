// Fragment-only helpers.
//
// Anything in here uses screen-space derivatives (fwidth, dFdx, dFdy), which the GLSL spec allows
// only in fragment shaders. They used to live in magic_common.glsl, which both stages import - the
// NVIDIA compiler accepted that silently, but Intel and AMD reject it outright and every mod shader
// fails to compile, which drops the whole resource pack and leaves the game on a black screen.
// Keep derivative-using code in this file and import it only from .fsh.

// Anti-aliased stroke: 1 inside |d| < w, soft over one pixel.
float strokeAA(float d, float w) {
    float aa = fwidth(d) * 1.2;
    return 1.0 - smoothstep(w - aa, w + aa, abs(d));
}
