package com.efkrdnz.magical.forge.chain;

/**
 * When a nested payload fires relative to the strike that carries it.
 *
 * <p>The three match the trigger cards a Noita wand can hold, and they are genuinely different
 * bets. IMPACT pays out only if the carrier touches something. TIMER pays out on a clock whatever
 * happens. EXPIRY pays out at the end of the carrier's life, hit or miss - the "late trigger" that
 * turns a flying crescent into a delivery system.
 */
public enum TriggerKind {

    /** Fires at the first body the carrier touches. Nothing happens if it touches nothing. */
    IMPACT,

    /** Fires a fixed number of ticks after the carrier spawns, wherever it happens to be. */
    TIMER,

    /** Fires when the carrier's life runs out, whether or not it ever hit anything. */
    EXPIRY
}
