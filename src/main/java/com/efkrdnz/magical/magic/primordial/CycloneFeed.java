package com.efkrdnz.magical.magic.primordial;

/**
 * What a Cyclone becomes when it is fed. Pure.
 *
 * <p>Its owner's spell deepens the element it already is (up to {@link #MAX_STACKS}) or replaces
 * it with its own at one stack; a spell that feeds {@link StormElement#DUST} (anything chaotic)
 * cleanses it back to calm. The land feeds only a calm storm, and never deepens it: magic is the
 * only thing that makes a storm worse than the ground made it.
 */
public final class CycloneFeed {
    public static final int MAX_STACKS = 3;
    private static final float POTENCY_PER_STACK = 0.5F;
    private static final float WIDEN_PER_STACK = 0.1F;

    public record State(StormElement element, int stacks) {
        public static final State CALM = new State(StormElement.DUST, 0);

        public boolean calm() {
            return element == StormElement.DUST;
        }
    }

    private CycloneFeed() {}

    public static State swallow(State now, StormElement fed) {
        if (fed == null || fed == StormElement.DUST) {
            return State.CALM;
        }
        if (fed == now.element()) {
            return new State(fed, Math.min(MAX_STACKS, now.stacks() + 1));
        }
        return new State(fed, 1);
    }

    public static State land(State now, StormElement ground) {
        if (ground == null || ground == StormElement.DUST || !now.calm()) {
            return now;
        }
        return new State(ground, 1);
    }

    /** 1x at one stack (and when calm), 1.5x at two, 2x at three. */
    public static float potency(int stacks) {
        int s = Math.max(1, Math.min(MAX_STACKS, stacks));
        return 1.0F + POTENCY_PER_STACK * (s - 1);
    }

    /** Each stack widens the funnel by a tenth. */
    public static float widen(int stacks) {
        return 1.0F + WIDEN_PER_STACK * Math.max(0, Math.min(MAX_STACKS, stacks));
    }
}
