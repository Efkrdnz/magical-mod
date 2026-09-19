package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/**
 * A verse as it sits in the piles: {@code clone_action} plus {@code deck_index} and
 * {@code uses_remaining}. The uses are the incantation's entry, written back after every press.
 */
public final class VerseCard {

    private final Verse verse;
    private final int deckIndex;
    private int usesRemaining;

    public VerseCard(Verse verse, int deckIndex, int usesRemaining) {
        this.verse = verse;
        this.deckIndex = deckIndex;
        this.usesRemaining = usesRemaining;
    }

    public Verse verse() {
        return verse;
    }

    public ResourceLocation id() {
        return verse.id();
    }

    public VerseType type() {
        return verse.type();
    }

    public int deckIndex() {
        return deckIndex;
    }

    public int usesRemaining() {
        return usesRemaining;
    }

    /** {@code uses_remaining == 0}: written, never castable, skipped by the draw. */
    public boolean spent() {
        return usesRemaining == 0;
    }

    /** Only a limited card with uses left loses one; -1 is unlimited and 0 stays 0. */
    public void consumeUse() {
        if (usesRemaining > 0) {
            usesRemaining--;
        }
    }

    @Override
    public String toString() {
        return verse.path() + "#" + deckIndex + (usesRemaining < 0 ? "" : "(" + usesRemaining + ")");
    }
}
