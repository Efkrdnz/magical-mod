package com.efkrdnz.magical.forge.art;

/**
 * The arithmetic behind the flurry Arts that build up on the same body: Kindling's burn stacks and
 * Fang Storm's fangs. Total, side-effect free and free of Minecraft types, so the rules can be
 * pinned by a plain unit test instead of being taken on trust.
 *
 * <p>Everything else an Art computes is a single named constant read at the point of use; only
 * these two carry a rule that can be got wrong quietly.</p>
 */
public final class ArtMath {

    /** Kindling: "each tick adds a burn stack (max 3)". */
    public static final int KINDLING_MAX_STACKS = 3;

    /** Fang Storm: "each tick adds a poison stack (max 4)". */
    public static final int FANG_MAX_STACKS = 4;

    /** Fang Storm: "amplifier +1 per 2 stacks". */
    public static final int FANG_STACKS_PER_AMPLIFIER = 2;

    private ArtMath() {}

    /**
     * How many stacks a flurry has laid on one body after {@code touches} pulses, capped.
     *
     * <p>{@code touches} is the body's own touch count for this press, so a body that walked into
     * the flurry late carries only the pulses it actually took.</p>
     */
    public static int stacks(int touches, int cap) {
        return Math.max(0, Math.min(cap, touches));
    }

    /**
     * The poison amplifier Fang Storm's stacks are worth: {@code 0} (Poison I) for one fang, then
     * one more level for every second fang - two fangs are Poison II and four are Poison III.
     *
     * <p>The obvious {@code (stacks - 1) / 2} is off by one against the table: it holds Poison I
     * until the third fang and never reaches Poison III at all. A light flurry lands three pulses
     * ({@code ForgeStrikeMath.flurryPulseTicks}), so three fangs and Poison II are what the Art
     * actually reaches in play; the cap stays at the table's four because it is the stack cap, and
     * a fourth pulse from any future light flurry has to be worth the level the table promises
     * rather than nothing.</p>
     */
    public static int fangAmplifier(int stacks) {
        return Math.max(0, stacks) / FANG_STACKS_PER_AMPLIFIER;
    }
}
