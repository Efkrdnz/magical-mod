package com.efkrdnz.magical.forge.glyph;

/** The kinds of glyph a rune chain is built from. */
public enum GlyphCategory {
    GRADE,
    ELEMENT,
    FORM,
    TEMPER,
    MODIFIER,

    /**
     * Runes that change how the runes around them are read rather than adding a property of their
     * own: fork binds several forms into one press, and the triggers nest one step inside another.
     *
     * <p>Appended, never inserted. The category ordinal is not sent over the wire, but the kept
     * rune resolution and the strip both read it, and reordering would silently re-bucket a chain.
     */
    OPERATOR;

    /** Score a drawing must reach before a template of this category is accepted at all. */
    public float defaultAcceptScore() {
        return 0.55f;
    }

    /** How far the best score must lead the runner-up before the match counts as unambiguous. */
    public float ambiguityMargin() {
        return this == GRADE ? 0.08f : 0.06f;
    }
}
