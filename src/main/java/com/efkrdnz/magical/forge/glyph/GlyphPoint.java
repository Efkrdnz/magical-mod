package com.efkrdnz.magical.forge.glyph;

/**
 * One sampled point of a drawn glyph, in normalized canvas coordinates
 * (x right, y down, both in [0, 1] before normalization).
 *
 * @param x        horizontal position
 * @param y        vertical position (down positive)
 * @param strokeId index of the stroke this point belongs to
 */
public record GlyphPoint(float x, float y, int strokeId) {
}
