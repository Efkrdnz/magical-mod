package com.efkrdnz.magical.magic.visual;

/** glyph_ink shader kinds. Ids below 16 are BAND kinds (unit annulus geometry), 16+ are PLANAR kinds. */
public enum GlyphKind {
    SOLID_RING(0),
    DASHED_RING(1),
    TICK_BAND(2),
    RUNE_BAND(3),
    WAVE_BAND(4),
    BRAID_BAND(5),
    CHAIN_BAND(6),
    PETAL_BAND(7),
    TOOTH_BAND(8),
    FACET_BAND(9),
    STAMP_BAND(10),
    ARC_SWEEP(11),
    FRAME(16),
    STAR(17),
    PENTAGRAM(18),
    SPOKES(19),
    LATTICE(20),
    ORBIT_SEAL(21),
    CORE(22),
    EMBLEM(23);

    private final int id;

    GlyphKind(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public boolean isBand() {
        return id < 16;
    }

    public boolean isPrimaryBandCandidate() {
        return isBand() && this != ARC_SWEEP;
    }
}
