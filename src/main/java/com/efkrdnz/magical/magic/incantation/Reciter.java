package com.efkrdnz.magical.magic.incantation;

import java.util.Objects;

/** One press. Pure: the same session, breath, mana, scale and world give the same plan. */
public final class Reciter {

    private Reciter() {
    }

    /**
     * The press. The breath is clamped to {@link ReciteCaps#MIN_BREATH}..{@link ReciteCaps#MAX_BREATH}
     * and the mana floored at nothing, so a caller that has not read the caps cannot hand the draw a
     * budget the machine does not own; the session and the world are required. Uses spent in play are
     * written back to the incantation the session was built from before the plan comes out, so a press
     * is the whole transaction.
     */
    public static RecitePlan recite(ReciteSession session, int breath, int mana, double costScale, ReciteWorld world) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(world, "world");
        int budget = Math.max(ReciteCaps.MIN_BREATH, Math.min(ReciteCaps.MAX_BREATH, breath));
        RecitePlan plan = new Recital(session, world, Math.max(0, mana), costScale).recite(budget);
        session.writeBack();
        return plan;
    }
}
