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
    TOOTH,
    /**
     * The Sword school: a blade, its crossguard and its pommel.
     *
     * <p>It is the thirty-third stamp, and so the first that does not fit the five-bit
     * STAMP_BAND field. SWORD bands with TICK_BAND rather than STAMP_BAND, so nothing asks
     * a band to carry it; a stamp added after this one must widen that field or band
     * differently. Adding it also pushed {@code EmblemId.FIRST_CELL} from 32 to 33, because
     * {@link #atlasCell()} is the ordinal and 32 was where the emblems began - a collision
     * that draws the wrong mark and fails nothing, which is why {@code FxAtlasCellsTest}
     * now holds the two ranges apart.</p>
     */
    EDGE;

    public int atlasCell() {
        return ordinal();
    }
}
