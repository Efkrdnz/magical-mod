package com.efkrdnz.magical.forge.chain;

import java.util.List;

/**
 * A forged weapon's chain, read as a program rather than a bag of properties: an ordered list of
 * steps, one consumed per press, wrapping back to the start after the finisher.
 *
 * <p>This is the deck a Noita wand holds. What makes a weapon interesting is the order its runes
 * were drawn in and what nests inside what - not how long the list is.
 */
public record ForgeProgram(List<ForgeStep> steps) {

    public static final ForgeProgram EMPTY = new ForgeProgram(List.of());

    public ForgeProgram {
        steps = List.copyOf(steps);
    }

    /** How many presses the chain takes before it wraps. Never zero for a forged weapon. */
    public int length() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * The step a press at {@code index} fires, clamped to the last step.
     *
     * <p>Clamping rather than wrapping mirrors what the combo service already did with the flat form
     * list: a stale index left over from a weapon swap resolves to something valid instead of
     * throwing mid-swing.
     */
    public ForgeStep stepAt(int index) {
        if (steps.isEmpty()) {
            throw new IllegalStateException("an empty program has no steps to fire");
        }
        return steps.get(Math.max(0, Math.min(index, steps.size() - 1)));
    }
}
