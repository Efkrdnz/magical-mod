package com.efkrdnz.magical.client.hud;

/**
 * What a frame of HUD is allowed to cost. These numbers are what "one batch" means in practice,
 * and a test renders the maximal HUD into a counting sink to hold the line.
 */
public final class HudBudget {
    /**
     * Every element on screen at once - two bars, four glyphs, a stamp for each of eight readings,
     * six status chips - with headroom. The fixed buffer holds 292.
     */
    public static final int MAX_QUADS = 22;
    /** The steady state: the two bars and four glyphs. Cooling and charging cost no quad; a reading costs its stamp. */
    public static final int IDLE_QUADS = 6;
    /** The two counts, four cells, eight readings, the "+n" and the debug line, with headroom. */
    public static final int MAX_TEXT_DRAWS = 18;
    /** The rule flash while it is live: its plate, the mark and the caption plate, with headroom. */
    public static final int FLASH_QUADS = 6;
    /** The rule flash's strings: the formula in three pieces, a zero over a struck symbol, the caption. */
    public static final int FLASH_TEXT_DRAWS = 5;

    private HudBudget() {}
}
