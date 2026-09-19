package com.efkrdnz.magical.magic.incantation;

/** One press. Pure: the same session, breath, mana, scale and world give the same plan. */
public final class Reciter {

    private Reciter() {
    }

    public static RecitePlan recite(ReciteSession session, int breath, int mana, double costScale, ReciteWorld world) {
        return new Recital(session, world, mana, costScale).recite(breath);
    }
}
