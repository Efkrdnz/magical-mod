package com.efkrdnz.magical.magic.visual;

/** Centre glyph styles for {@link GlyphKind#CORE} (paramB). */
public enum CoreKind {
    DISC_GLOW,
    IRIS,
    HEX_LENS,
    VOID_PIT,
    EMBER_PIT,
    RIPPLE,
    CROSS,
    SUNBURST;

    public int id() {
        return ordinal();
    }

    /** VOID_PIT is drawn as dark ink (straight alpha) so it can darken the ground. */
    public boolean dark() {
        return this == VOID_PIT;
    }
}
