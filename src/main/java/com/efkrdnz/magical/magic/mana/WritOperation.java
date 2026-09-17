package com.efkrdnz.magical.magic.mana;

/**
 * What a writ does to the aspect it names.
 *
 * <p>The factor is applied to whatever number the aspect stands for. INVERT is allowed to go
 * negative because a negative cost is mana handed back, which is the whole point of being able to
 * legislate a price rather than merely raise one.
 */
public enum WritOperation {
    RAISE(3.0F),
    LOWER(0.5F),
    ZERO(0.0F),
    INVERT(-1.0F),
    /** Pins the aspect at its written value and refuses every other hand, the author's included. */
    LOCK(1.0F),
    /** Strikes the writ out. The only operation that removes rather than declares. */
    RESTORE(1.0F);

    private final float factor;

    WritOperation(float factor) {
        this.factor = factor;
    }

    public float factor() {
        return factor;
    }

    /** True for the one operation that erases a standing writ instead of adding one. */
    public boolean clears() {
        return this == RESTORE;
    }

    public String translationKey() {
        return "writ.magical.operation." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
