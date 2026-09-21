package com.efkrdnz.magical.magic.causality;

/**
 * Something that happened, in the only terms the engine understands.
 *
 * <p>Every hook in the mod that a wielder of Causality can watch is boiled down to one of these
 * before the {@link Weaver} sees it, which is what keeps the engine pure: the graph walk never asks
 * the level a question, it is handed the answers. A new cause means a new translation at the edge
 * and nothing at all in the middle.
 *
 * <p>{@code magnitude} is the size of the consequence <b>as it stands at this moment</b> - after
 * armour, after a barrier, after whatever else in the mod already had a word - so a Weave that
 * stores half of a hit stores half of the hit that was actually going to land. {@code otherId} is
 * whoever else is in the event and -1 when nobody is.
 */
public record CausalEvent(Cause cause, float magnitude, int otherId, int flags) {

    /** The consequence came off a fist, a blade or a claw. */
    public static final int MELEE = 1;
    /** The consequence came off something that flew. */
    public static final int PROJECTILE = 1 << 1;
    /** The consequence was magic, of any school. */
    public static final int MAGIC = 1 << 2;
    /** The consequence was fire, lava or burning. */
    public static final int FIRE = 1 << 3;
    /** The other party in the event is the one wearing the mark. */
    public static final int FROM_MARKED = 1 << 4;

    public static CausalEvent of(Cause cause) {
        return new CausalEvent(cause, 0.0F, -1, 0);
    }

    public static CausalEvent of(Cause cause, float magnitude, int otherId, int flags) {
        return new CausalEvent(cause, magnitude, otherId, flags);
    }

    public boolean is(int flag) {
        return (flags & flag) != 0;
    }

    public boolean hasOther() {
        return otherId >= 0;
    }

    /** True when an effect that reaches back into this event has anything to reach back into. */
    public boolean rewritable() {
        return cause.rewritable();
    }

    public CausalEvent withMagnitude(float value) {
        return new CausalEvent(cause, value, otherId, flags);
    }

    public CausalEvent withFlag(int flag) {
        return new CausalEvent(cause, magnitude, otherId, flags | flag);
    }
}
