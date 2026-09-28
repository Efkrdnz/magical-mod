package com.efkrdnz.magical.magic.mind;

/** The fast ways down: evidence that the thing is not there. */
public enum Contradiction {
    TOUCH(0.60F),
    PROJECTILE(0.35F),
    WITNESS(0.20F),
    HOLLOW_STRIKE(0.25F);

    private final float penalty;

    Contradiction(float penalty) {
        this.penalty = penalty;
    }

    public float penalty() {
        return penalty;
    }
}
