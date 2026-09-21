package com.efkrdnz.magical.magic.sword;

/**
 * What happened when the wielder called a blade, and every refusal has a name.
 *
 * <p>Call the Blade is the only write operation the class has and the entire authoring surface of
 * the build, so a press that does nothing has to say <em>which</em> rule stopped it. Five of these
 * seven are printed on the actionbar by name, and a refusal costs nothing.
 *
 * <p>The order {@code SwordArray.plant} tests them in is itself a decision and is pinned: a
 * bearing that is not on the lattice is {@link #OUT_OF_REACH} before anything else is asked, a
 * bearing already written is topped up before the caps are consulted, and then {@link #FULL},
 * {@link #TOO_CLOSE}, {@link #NO_EDGE}, {@link #TOO_DEAR} in that order - hard caps first, the
 * budget last, because the budget is the only one the wielder can fix by aiming closer.
 */
public enum PlantResult {

    /** A new bearing was written and metal put on it. */
    PLANTED,

    /** The bearing was already authored, so its Edge went up and its reach stayed as written. */
    TOPPED_UP,

    /** This rung holds no more stations. Pull one with the Bearing. */
    FULL,

    /** Within {@code SEPARATION_MIN} of a bearing already written, and coincidence is not granted. */
    TOO_CLOSE,

    /** {@code sum of reach * edge} would pass the draw. Aim nearer or carry less. */
    TOO_DEAR,

    /** No loose Edge to put on it. Every point is already bound, spent, or lying in the world. */
    NO_EDGE,

    /** Not a place on the lattice at all - a yaw, pitch or reach outside it. */
    OUT_OF_REACH;

    /** True for the two outcomes that changed the shape. Everything else left the Array untouched. */
    public boolean accepted() {
        return this == PLANTED || this == TOPPED_UP;
    }
}
