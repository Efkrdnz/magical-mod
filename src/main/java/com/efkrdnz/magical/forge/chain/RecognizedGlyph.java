package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.glyph.GlyphCategory;

/**
 * One committed glyph of a rune chain, after recognition.
 *
 * @param id       bare template path, e.g. {@code "fire"}
 * @param category which slot of the chain it fills
 * @param quality  0..100 drawing quality
 */
public record RecognizedGlyph(String id, GlyphCategory category, int quality) {
}
