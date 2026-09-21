package com.efkrdnz.magical.magic.sword;

/**
 * What the frame's origin is currently attached to.
 *
 * <p>Five values and no sixth, and note what is <em>not</em> here: nothing about a blade. The bind
 * is a property of the one frame the whole Array is rigid to, which is why re-anchoring it is a
 * verb the structure already had rather than a skill that needed writing. Loose does not fire
 * blades at a body; it moves the origin onto the body and then takes the forward half of a shape
 * that has not changed at all.
 */
public enum Bind {

    /** Origin is the wielder's body centre, facing is their look, scale 1. The resting state. */
    HELD,

    /** Origin and facing frozen where they were. You walk out of your own formation and it stays. */
    SET,

    /** Origin moves along the wielder's look and the wielder is pinned to it. */
    RIDDEN,

    /** Origin is a living body, and the scale is driven by how far away it has got. */
    BOUND,

    /** Origin is a point recorded once, with every pitch reflected, so the Array hangs beneath it. */
    SUNK;

    /** True where the origin is not the wielder's body, which is what Sword Rider unlocks. */
    public boolean offTheBody() {
        return this == SET || this == BOUND || this == SUNK;
    }
}
