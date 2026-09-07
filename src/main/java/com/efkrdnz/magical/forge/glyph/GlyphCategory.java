package com.efkrdnz.magical.forge.glyph;

/** The five kinds of glyph a rune chain is built from. */
public enum GlyphCategory {
    GRADE,
    ELEMENT,
    FORM,
    TEMPER,
    MODIFIER;

    /** Score a drawing must reach before a template of this category is accepted at all. */
    public float defaultAcceptScore() {
        return 0.55f;
    }

    /** How far the best score must lead the runner-up before the match counts as unambiguous. */
    public float ambiguityMargin() {
        return this == GRADE ? 0.08f : 0.06f;
    }
}
