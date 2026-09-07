package com.efkrdnz.magical.forge.strike;

/**
 * Immutable state of a weapon's combo chain: chain position, the timing windows that gate the
 * next press, and light bookkeeping (telegraph entity, primary target, guard) carried between
 * server casts. Every instance is replaced, never mutated.
 */
public record ComboState(int index, int chainLength, int weaponHash, long lastStrikeTick, long windowEndTick,
        long readyTick, long pressTick, long guardUntilTick, int telegraphEntityId, int primaryTargetId) {

    private static final long NONE_TICK = -1L;

    private static final int NONE_ID = -1;

    private static final long RATE_LIMIT_TICKS = 3L;

    /** A fresh chain: index 0, every "none" tick or id at -1. */
    public static ComboState idle(int weaponHash, int chainLength) {
        return new ComboState(0, chainLength, weaponHash, NONE_TICK, NONE_TICK, NONE_TICK, NONE_TICK, NONE_TICK,
                NONE_ID, NONE_ID);
    }

    /**
     * A fresh chain, exactly like {@link #idle}, except this state's {@code primaryTargetId} survives
     * onto it. A lapsed window or a weapon swap resets the chain position and every timer, but it says
     * nothing about who vanilla's own attack just hit this tick - {@code notePrimaryHit} runs from
     * {@code AttackEntityEvent} before the strike payload arrives, so the target can already be sitting
     * on {@code this} state when it gets replaced. Losing it here would let that target take the full
     * vanilla swing plus an uncorrected strike, the mirror of a rejected press. This is the only field
     * a chain reset carries forward; a genuine forget-everything reset (logout, respawn, dimension
     * change) removes the state outright and must never call this.
     */
    public ComboState idleCarryingPrimaryTarget(int weaponHash, int chainLength) {
        return idle(weaponHash, chainLength).withPrimaryTarget(primaryTargetId);
    }

    public boolean matches(int weaponHash) {
        return this.weaponHash == weaponHash;
    }

    public boolean windowExpired(long now) {
        return windowEndTick >= 0 && now > windowEndTick;
    }

    public boolean recovering(long now) {
        return now < readyTick;
    }

    public boolean rateLimited(long now) {
        return lastStrikeTick >= 0 && now - lastStrikeTick < RATE_LIMIT_TICKS;
    }

    public boolean finisherAt(int index) {
        return index == chainLength - 1;
    }

    /** Advances the chain after a strike lands, wrapping and resetting the window on a finisher. */
    public ComboState afterStrike(long now, int recovery, long windowEnd) {
        long newReadyTick = now + recovery;
        if (finisherAt(index)) {
            long resetWindowEnd = now + recovery + ForgeStrikeMath.RESET_AFTER_FINISHER;
            return new ComboState(0, chainLength, weaponHash, now, resetWindowEnd, newReadyTick, pressTick,
                    guardUntilTick, NONE_ID, NONE_ID);
        }
        return new ComboState(index + 1, chainLength, weaponHash, now, windowEnd, newReadyTick, pressTick,
                guardUntilTick, NONE_ID, NONE_ID);
    }

    /** Back to idle, but the guard timer survives (a guard proc should not be lost by a drop). */
    public ComboState reset() {
        return new ComboState(0, chainLength, weaponHash, NONE_TICK, NONE_TICK, NONE_TICK, NONE_TICK, guardUntilTick,
                NONE_ID, NONE_ID);
    }

    public ComboState withPress(long tick) {
        return new ComboState(index, chainLength, weaponHash, lastStrikeTick, windowEndTick, readyTick, tick,
                guardUntilTick, telegraphEntityId, primaryTargetId);
    }

    public ComboState withGuard(long untilTick) {
        return new ComboState(index, chainLength, weaponHash, lastStrikeTick, windowEndTick, readyTick, pressTick,
                untilTick, telegraphEntityId, primaryTargetId);
    }

    public ComboState withTelegraph(int entityId) {
        return new ComboState(index, chainLength, weaponHash, lastStrikeTick, windowEndTick, readyTick, pressTick,
                guardUntilTick, entityId, primaryTargetId);
    }

    public ComboState withPrimaryTarget(int entityId) {
        return new ComboState(index, chainLength, weaponHash, lastStrikeTick, windowEndTick, readyTick, pressTick,
                guardUntilTick, telegraphEntityId, entityId);
    }

    public ComboState withoutTelegraph() {
        return new ComboState(index, chainLength, weaponHash, lastStrikeTick, windowEndTick, readyTick, pressTick,
                guardUntilTick, NONE_ID, primaryTargetId);
    }

    public boolean guardActive(long now) {
        return now < guardUntilTick;
    }
}
