package com.efkrdnz.magical.magic.causality;

/**
 * Everything the {@link Weaver} needs to know about the world, and nothing else.
 *
 * <p>Read-only, every method of it. The engine decides what should happen and hands the decisions
 * back as {@link CausalAction}s; it never reaches out and does one. That is what lets the whole of
 * the Authority - the grammar, the conservation rule, the price of a paradox, the order two
 * branches of one cause fire in - be pinned by unit tests on exact values, the way {@code Pile} is,
 * rather than watched in a dev client and hoped about.
 *
 * <p>{@link #lastPassed} is the one piece of memory in here, and it exists for
 * {@link Condition#ONCE_PER}. The engine reads it and reports which gates it went through in
 * {@link Resolution#gatesPassed()}; the service writes the clocks afterwards. So the engine still
 * has no state of its own and two runs over the same inputs give the same answer.
 */
public interface CausalWorld {

    /** The game tick. Every clock in the Authority is measured against this one. */
    long now();

    /** What the wielder is holding, in points of consequence. */
    float ledger();

    /** How far out of joint reality has been put, 0 to {@link Paradox#MAX}. */
    float paradox();

    /** Mana in the pool right now. The engine bills as it walks and stops when this runs out. */
    float mana();

    /** True when a mark is out there and the thing wearing it is alive in this dimension. */
    boolean markAlive();

    /** The health of the wielder as a fraction of their maximum, 0 to 1. */
    float selfHealth();

    /** Blocks from the wielder to that entity, or {@link Double#MAX_VALUE} when it is not here. */
    double distanceTo(int entityId);

    /** Whether the wielder is crouching. The one answer they can change mid-fight. */
    boolean crouching();

    /** The tick this gate last let a signal through, or {@link Long#MIN_VALUE} for never. */
    long lastPassed(int nodeId);

    /** True when the wielder has somewhere for a {@link Modifier#SHARED} to share among. */
    boolean crowded();
}
