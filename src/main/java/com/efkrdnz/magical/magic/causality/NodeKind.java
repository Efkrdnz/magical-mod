package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * Which of the three vocabularies a node is drawn from, and therefore where it may sit in a chain.
 *
 * <p>The ordering is the grammar: a wire may only run {@code CAUSE -> CONDITION}, {@code CONDITION
 * -> CONDITION}, {@code CAUSE -> EFFECT} or {@code CONDITION -> EFFECT}. Nothing may run into a
 * cause and nothing may run out of an effect, which is what makes every chain a sentence with a
 * subject and a verb.
 *
 * <p>That leaves exactly one way to draw a loop - a run of conditions that bites its own tail - and
 * {@code Weave.connect} refuses it by asking whether the far end can already reach the near one. So
 * the wielder is told no while drawing rather than after saving, and the engine may walk a chain
 * with a plain depth counter instead of a visited set.
 */
public enum NodeKind {
    CAUSE(0),
    CONDITION(1),
    EFFECT(2);

    private final int rank;

    NodeKind(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** Whether a wire may run from a node of this kind to a node of that one. Forward only. */
    public boolean mayFeed(NodeKind other) {
        if (this == EFFECT || other == CAUSE) {
            return false;
        }
        return other.rank >= rank;
    }

    public String translationKey() {
        return "nodekind.magical." + name().toLowerCase(Locale.ROOT);
    }
}
