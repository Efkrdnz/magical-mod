package com.efkrdnz.magical.magic.cast;

/** Outcome of a cast handler. */
public enum CastResult {
    /** Mana stays spent, cooldown starts, sin/sync bookkeeping runs. */
    SUCCESS,
    /** Mana refunded, nothing else happens (the handler already told the player why). */
    FAILED,
    /** Mana stays spent but no cooldown (retry-style whiffs); a short retry cooldown may be set by the handler. */
    CONSUMED_NO_COOLDOWN,
    /** The handler managed mana/cooldown itself (kept skills with their own services). */
    HANDLED
}
