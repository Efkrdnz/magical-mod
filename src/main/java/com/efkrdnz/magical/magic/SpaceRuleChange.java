package com.efkrdnz.magical.magic;

/**
 * How a Manipulate Space operation bends the symbol in its category's formula. The rule flash
 * keys every visual, wash and sound off this, not off the fifty-seven operations; the shader packs
 * {@link #id()} into three bits, so there are never more than eight.
 */
public enum SpaceRuleChange {
    /** The quantity goes up: the symbol lifts and rays climb through it. */
    RAISE,
    /** The quantity goes down: the symbol sinks and the rays fall. */
    LOWER,
    /** The term is struck out and reads as zero. */
    ZERO,
    /** The sign reverses: the symbol mirrors through itself. */
    FLIP,
    /** The quantity is pinned, held or controlled: brackets close round it. */
    LOCK,
    /** The quantity runs off the scale: the symbol overshoots and a ring bursts. */
    SURGE,
    /** A direction is set: an arrow sweeps into place. */
    AIM,
    /** The rule is cleared and the symbol drains back to plain. */
    RESTORE;

    public int id() {
        return ordinal();
    }
}
