package com.efkrdnz.magical.forge.chain;

import java.util.List;
import java.util.Optional;

/**
 * A validated rune chain, ready to be applied to a weapon.
 *
 * @param element           element glyph id
 * @param grade             grade sigil
 * @param temper            optional temper glyph id
 * @param forms             form glyph ids in draw order
 * @param modifiers         distinct modifier glyph ids in draw order
 * @param meanGlyphQuality  mean 0..100 quality of every glyph in the chain
 */
public record ForgeRecipe(
        String element,
        ForgeGrade grade,
        Optional<String> temper,
        List<String> forms,
        List<String> modifiers,
        int meanGlyphQuality) {

    public ForgeRecipe {
        forms = List.copyOf(forms);
        modifiers = List.copyOf(modifiers);
    }
}
