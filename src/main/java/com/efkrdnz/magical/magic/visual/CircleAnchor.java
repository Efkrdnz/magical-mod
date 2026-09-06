package com.efkrdnz.magical.magic.visual;

/** Where a skill's cast circle is placed. */
public enum CircleAnchor {
    /** Flat under the caster, following. */
    GROUND,
    /** A small ring at the hand, perpendicular to the look vector; the delivery passes through it. */
    EYE_FORWARD,
    /** Both a ground circle and an eye-forward muzzle ring. */
    BOTH,
    /** On the aimed block face, tilted to its normal. */
    AIM_SURFACE,
    /** Following the locked target. */
    TARGET_FOLLOW,
    /** Inverted, above the target point. */
    SKY,
    NONE
}
