package com.efkrdnz.magical.magic.sword;

/**
 * What the frame's origin is currently attached to.
 *
 * <p>Four values and no fifth, and note what is <em>not</em> here: nothing about a sword. The bind
 * is a property of the one frame the whole formation is rigid to, which is why re-anchoring it is
 * a verb the structure already had rather than a skill that needed writing.
 *
 * <p>There used to be a {@code BOUND} - the origin on a living body, with the frame's scale driven
 * by how far that body had run. It existed to feed the bill, so a bound opponent's footwork spent
 * the wielder's budget, and it went with the budget: there is no bill, no draw and no strain any
 * more, so a scale driven by distance would have been an inflation of nothing.
 */
public enum Bind {

    /**
     * The resting state. Origin and facing come off the wielder, and <b>which part of the wielder
     * depends on the stance's anchor</b>: a {@code LOOK} stance is pinned to the eye and carries
     * their pitch, a {@code BODY} stance to the body centre at pitch zero. That rule lives in
     * {@code SwordService.frame} and is the only thing keeping Vanguard's rings centred on the
     * crosshair when the wielder looks up.
     */
    HELD,

    /** Origin and facing frozen where they were. You walk out of your own formation and it stays. */
    SET,

    /** Origin moves along the wielder's look and the wielder is pinned to it. */
    RIDDEN,

    /** Origin is a point recorded once, with the formation hung beneath it. */
    SUNK;

    /** True where the origin is not the wielder's body, which is what Sword Rider unlocks. */
    public boolean offTheBody() {
        return this == SET || this == SUNK;
    }
}
