package com.efkrdnz.magical.client.hud;

/**
 * What a frame of HUD is allowed to cost. These numbers are what "one batch" means in practice,
 * and a test renders the maximal HUD into a counting sink to hold the line.
 */
public final class HudBudget {
    /** Every element on screen at once, with headroom. The fixed buffer holds 292. */
    public static final int MAX_QUADS = 120;
    /** The steady state: rings, the core, four cards with spokes and tags, the caption plate. */
    public static final int IDLE_QUADS = 24;

    private HudBudget() {}
}
