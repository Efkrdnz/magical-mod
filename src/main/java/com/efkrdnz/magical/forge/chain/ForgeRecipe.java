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
 * @param program           forms and modifiers together, in the order they were drawn
 * @param meanGlyphQuality  mean 0..100 quality of every glyph in the chain
 */
public record ForgeRecipe(
        String element,
        ForgeGrade grade,
        Optional<String> temper,
        List<String> forms,
        List<String> modifiers,
        List<String> program,
        int meanGlyphQuality) {

    public ForgeRecipe {
        forms = List.copyOf(forms);
        modifiers = List.copyOf(modifiers);
        program = List.copyOf(program);
    }

    /**
     * The chain as the weapon will fire it.
     *
     * <p>{@link #forms} and {@link #modifiers} remain the flat view the tooltip, the cost totals and
     * the kept-rune strip read; this is the ordered one.
     */
    public ForgeProgram compiled() {
        return ForgeChainCompiler.compile(program);
    }
}
