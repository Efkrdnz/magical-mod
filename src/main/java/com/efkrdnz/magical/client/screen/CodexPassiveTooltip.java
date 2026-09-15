package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.magic.MagicPassiveDefinition;

/**
 * How one passive reads in its tooltip, which is not always what it is registered as.
 *
 * <p>Both halves of a pact are registered as normal passives, because only a normal passive can
 * carry a clock. Asking the definition what it is therefore gives the wrong answer twice over: a
 * price would print the toggle hint and a level it does not have, and a boon would promise a
 * checkbox that {@link com.efkrdnz.magical.magic.PlayerMagicState#togglePassive} refuses. A price
 * is not a curse either - it has no dispel cost, and printing one would advertise an escape hatch
 * that is not there, which is the whole of what the pact was paid for.
 *
 * <p>So the clock decides, the same way it decides everywhere else: anything carrying one reads as
 * a pact half, and everything else reads as what it is. Out of the screen because the screen
 * extends {@code AbstractContainerScreen} and cannot be stood up in a unit test, and because
 * getting the height wrong prints a line over the frame with nothing to catch it.
 */
public final class CodexPassiveTooltip {

    /** The block of lines a tooltip prints under the description. */
    public enum Body {
        /** A pact half: one line saying the clock is the only way out of it. */
        PACT,
        /** A curse: what it costs to dispel, and whether that is affordable yet. */
        CURSE,
        /** An ordinary passive: how to turn it off. */
        PASSIVE
    }

    /** The height of one line of tooltip body text plus the gap above it. */
    private static final int LINE = 14;

    private static final int ONE_LINE = 22;
    private static final int TWO_LINES = 34;

    private CodexPassiveTooltip() {}

    /** What the row reads as, given what is left of its pact clock. Zero for anything without one. */
    public static Body body(MagicPassiveDefinition definition, int ritualTicks) {
        if (ritualTicks > 0) {
            return Body.PACT;
        }
        return definition.curse() ? Body.CURSE : Body.PASSIVE;
    }

    /**
     * The room the body needs under the description.
     *
     * <p>{@code sinLinked} only moves a curse: it is the line naming the passive that lifts a sin
     * curse, and it comes from a lookup that answers for any id at all, so the other two bodies
     * have to ignore it rather than grow a gap where a dispel block would have gone.
     */
    public static int extraHeight(Body body, boolean sinLinked) {
        if (body != Body.CURSE) {
            return ONE_LINE;
        }
        return sinLinked ? TWO_LINES + LINE : TWO_LINES;
    }
}
