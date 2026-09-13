package com.efkrdnz.magical.client.hud;

/**
 * The quad kinds {@code rendertype_hud_sigil.fsh} knows how to draw. Ids mirror the {@code const int}
 * block at the top of that shader; {@code HudShaderAssetsTest} reads both and fails if they drift.
 */
public enum HudKind {
    /** A ring meter: count = notches, paramB = width class | 8 hot head | 16 rungs, phase = fill. */
    RING_METER(0),
    /** A magic-circle core glyph: paramB = CoreKind ordinal, count = spin class, phase = pulse. */
    CORE(1),
    /** A rune card: count|paramB&1 = emblem cell, paramB 2 empty, 4 held, 8 locked; phase = cooldown remaining. */
    CARD(2),
    /** A charge ring over a card: count = ticks, phase = charge. */
    CHARGE_RING(3),
    /** A bare emblem: count | paramB << 6 = cell. */
    EMBLEM(4),
    /** A sin satellite: emblem + gauge ring, phase = gauge. */
    SATELLITE(5),
    /** A status chip: emblem + draining ring, paramB >> 1 = amplifier pips, phase = remaining. */
    CHIP(6),
    /** A rounded plate: count = corner radius in 32nds of the short half, paramB = accent edges, phase = fade. */
    PLATE(7),
    /** A hairline: count = dashes (0 solid), phase = drawn fraction. */
    LINE(8),
    /** An announcement emblem with the ink-on / dissolve lifecycle in phase. */
    ANNOUNCE(9),
    /**
     * The rule flash's mark behind the changed symbol: count = the symbol's width at twice the
     * text size in GUI px (the quad is forty wide), paramB = SpaceRuleChange id | aim variant << 3,
     * phase = the flash's lifetime fraction, MODE_ALT = reduced motion (marks drawn settled).
     */
    RULE_MARK(10);

    /** Where a ring's outer edge sits, as a fraction of its quad's half-size. Mirrors the shader. */
    public static final float RING_OUTER = 0.88F;

    /**
     * The rule flash's timeline as fractions of its lifetime: the plate pops in until POP_END, the
     * mark plays until MARK_END, everything dissolves from OUT_START. The shader declares the same
     * three numbers; {@code HudShaderAssetsTest} keeps them equal.
     */
    public static final float FLASH_POP_END = 0.10F;
    public static final float FLASH_MARK_END = 0.28F;
    public static final float FLASH_OUT_START = 0.72F;

    /** Ring widths the shader can draw, as fractions of the quad's half-size, by class index. */
    public static final float[] WIDTH_CLASSES = {0.03F, 0.06F, 0.10F, 0.16F, 0.20F, 0.26F, 0.32F, 0.40F};

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

    /** The width class whose fraction of {@code halfGui} is closest to {@code widthGui}. */
    public static int widthClass(float widthGui, float halfGui) {
        float want = widthGui / Math.max(1.0F, halfGui);
        int best = 0;
        float bestError = Float.MAX_VALUE;
        for (int i = 0; i < WIDTH_CLASSES.length; i++) {
            float error = Math.abs(WIDTH_CLASSES[i] - want);
            if (error < bestError) {
                bestError = error;
                best = i;
            }
        }
        return best;
    }
}
