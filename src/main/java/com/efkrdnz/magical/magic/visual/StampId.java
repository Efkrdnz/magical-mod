package com.efkrdnz.magical.magic.visual;

/**
 * Small repeated marks for {@link GlyphKind#STAMP_BAND}. Stamps live in atlas cells 0..31 of the
 * sigil atlas (the STAMP_BAND paramB field is 5 bits wide); emblems start at cell 32.
 */
public enum StampId {
    NEEDLE,
    FOOTPRINT,
    KITE,
    TEARDROP,
    THORN,
    SNOWFLAKE,
    LEAF,
    FEATHER,
    HEX,
    CROSS,
    EYE,
    HOURGLASS,
    FEATHER_ARC,
    KEY,
    BONE,
    GEAR,
    ARROW,
    DOT,
    BAR,
    CHEVRON,
    WAVE,
    FLAME,
    DROP,
    STAR4,
    RING,
    TRIANGLE,
    SQUARE,
    DIAMOND,
    CRESCENT,
    SPIRAL,
    LINK,
    TOOTH;

    public int atlasCell() {
        return ordinal();
    }
}
