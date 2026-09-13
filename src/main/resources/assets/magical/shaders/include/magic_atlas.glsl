// SDF emblem atlas lookups, shared by every shader that stamps a glyph (glyph_ink, hud_sigil).
//
// The atlas is 16x16 cells; a cell's content spans +-0.42 of the cell so neighbours never bleed.
// No derivatives here: this file is safe to import from either stage.

// One cell lookup. id = cell 0..255, q = local coordinate in -1..1, aa = edge softness in SDF units.
float atlasInk(sampler2D atlas, int id, vec2 q, float aa) {
    vec2 uv = (vec2(float(id & 15), float(id >> 4)) + 0.5 + q * 0.42) / 16.0;
    return smoothstep(0.5 - aa, 0.5 + aa, texture(atlas, uv).r);
}

// A soft halo around the strokes of the same cell.
float atlasGlow(sampler2D atlas, int id, vec2 q) {
    vec2 uv = (vec2(float(id & 15), float(id >> 4)) + 0.5 + q * 0.42) / 16.0;
    return smoothstep(0.15, 0.5, texture(atlas, uv).r) * 0.45;
}
