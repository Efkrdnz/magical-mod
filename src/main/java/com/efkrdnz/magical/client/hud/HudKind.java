package com.efkrdnz.magical.client.hud;

/**
 * The quad kinds {@code rendertype_hud_sigil.fsh} knows how to draw. Ids mirror the {@code const int}
 * block at the top of that shader; {@code HudShaderAssetsTest} reads both and fails if they drift.
 */
public enum HudKind {
    /** A bare emblem: count | paramB << 6 = cell. The codex and the creator paint with it. */
    EMBLEM(4),
    /** A status chip: emblem + draining ring, paramB >> 1 = amplifier pips, phase = remaining. */
    CHIP(6),
    /** A rounded plate: count = corner radius in 32nds of the short half, paramB = accent edges, phase = fade. */
    PLATE(7),
    /**
     * The rule flash's mark behind the changed symbol: count = the symbol's width at twice the
     * text size in GUI px (the quad is forty wide), paramB = SpaceRuleChange id | aim variant << 3,
     * phase = the flash's lifetime fraction, MODE_ALT = reduced motion (marks drawn settled).
     */
    RULE_MARK(10),
    /**
     * A corner-block skill glyph over a hard black shadow: count | (paramB & 3) << 6 = cell,
     * paramB 4 = charging, seed = the glyph's side in GUI units, phase = one less the line between
     * grey and lit, down the ink box: grey below it while cooling, lit below it while charging.
     * {@code SigilRenderer.inkPhase} puts that line on the rows the cell's ink covers, so a short
     * glyph greys for its whole cooldown. A readout's stamp is the same glyph at nine units with
     * phase 0: whole.
     */
    SLOT(11),
    /** A corner-block pool bar: a dark track, the fill inset one pixel inside it; phase = fill. */
    BAR(12);

    /**
     * The grey a cooling or charging glyph's unlit rows are drawn in, whole and over the glyph's
     * shadow: greyed out rather than faded out, because a faded glyph over a night sky is gone.
     * Mirrors the shader.
     */
    public static final float SLOT_GHOST_GREY = 0.49F;
    /** The opacity of a bar's dark track where the fill has not reached. Mirrors the shader. */
    public static final float BAR_TRACK_ALPHA = 0.85F;

    /**
     * The rule flash's timeline as fractions of its lifetime: the plate pops in until POP_END, the
     * mark plays until MARK_END, everything dissolves from OUT_START. The shader declares the same
     * three numbers; {@code HudShaderAssetsTest} keeps them equal.
     */
    public static final float FLASH_POP_END = 0.10F;
    public static final float FLASH_MARK_END = 0.28F;
    public static final float FLASH_OUT_START = 0.72F;

    /** The mode bit that switches a quad to the ink register: near-black body, tinted rim. */
    public static final int MODE_INK = 1;
    /** The kind-specific second mode bit (pulse, flash, selected ...). */
    public static final int MODE_ALT = 2;

    private final int id;

    HudKind(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
