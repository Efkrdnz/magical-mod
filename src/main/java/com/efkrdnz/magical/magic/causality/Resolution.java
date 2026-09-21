package com.efkrdnz.magical.magic.causality;

import java.util.List;

/**
 * What the {@link Weaver} decided, and the whole of it.
 *
 * <p>{@code magnitude} is the consequence that started this, as the Weave left it - which is the
 * number the caller writes back into the damage event. Everything that <em>reduced</em> it appears
 * in {@code actions} as well, so the pair is never out of step: a Store that took four points off a
 * hit leaves {@code magnitude} four lower and a {@link Effect#STORE} action carrying four.
 *
 * <p>{@code gatesPassed} is the list of {@link Condition#ONCE_PER} pins that let a signal through
 * this time, for the service to stamp with the tick. The engine reads those clocks and never sets
 * them, which is what keeps it pure.
 *
 * <p>{@code starved} says the walk ran out of mana part way, and {@code clipped} that it ran into
 * the depth ceiling. Neither is an error - both are how a Weave that is too ambitious for its
 * wielder degrades, and both are worth telling them about.
 */
public record Resolution(
        float magnitude,
        List<CausalAction> actions,
        float paradox,
        float mana,
        List<Integer> gatesPassed,
        boolean starved,
        boolean clipped) {

    public static final Resolution NOTHING =
            new Resolution(0.0F, List.of(), 0.0F, 0.0F, List.of(), false, false);

    public Resolution {
        actions = actions == null ? List.of() : List.copyOf(actions);
        gatesPassed = gatesPassed == null ? List.of() : List.copyOf(gatesPassed);
    }

    /** Nothing fired. The common case, and the one the caller can drop on the floor cheaply. */
    public boolean idle() {
        return actions.isEmpty() && paradox <= 0.0F && mana <= 0.0F;
    }

    public static Resolution nothing(float magnitude) {
        return new Resolution(magnitude, List.of(), 0.0F, 0.0F, List.of(), false, false);
    }
}
