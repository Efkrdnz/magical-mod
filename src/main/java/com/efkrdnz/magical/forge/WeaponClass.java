package com.efkrdnz.magical.forge;

/**
 * What shape of weapon a strike is coming off, and the three ways that changes the swing.
 *
 * <p>These are deltas onto the form's own numbers, not replacements: a slash reaches 3.5 blocks and
 * recovers in 8 ticks whatever it is swung with, and the archetype pushes that up or down.
 * {@code ForgeStrikeMath.recovery} floors the result, so a deeply negative recovery delta makes a
 * weapon fast rather than instantaneous.
 *
 * <p>The archetype is also the coarse half of the glyph vocabulary - see {@code ForgeVocabulary} -
 * so adding a constant here opens a pool that weapons of that shape may draw from and others may
 * not.
 */
public enum WeaponClass {
    /** The baseline every other archetype is a deviation from. */
    SWORD(0, 1.0f, 0.0f),
    AXE(3, 1.25f, -0.3f),
    /** Short and quick: least reach and knockback of anything but claws, fastest recovery. */
    DAGGER(-3, 0.55f, -1.0f),
    /** Slow, long and heavy - the only archetype whose knockback outruns an axe. */
    GREATSWORD(7, 1.75f, 0.8f),
    /** Reach is the whole identity, and knockback sits below a sword's to pay for it. */
    SPEAR(1, 0.90f, 1.8f),
    /** Long and wide, swung rather than thrust, and slower than it looks. */
    SCYTHE(4, 1.20f, 1.0f),
    /** Faster than a dagger and shorter still; knockback barely exists. */
    CLAWS(-4, 0.40f, -1.3f);

    private final int recoveryDelta;
    private final float knockbackScale;
    private final float reachDelta;

    WeaponClass(int recoveryDelta, float knockbackScale, float reachDelta) {
        this.recoveryDelta = recoveryDelta;
        this.knockbackScale = knockbackScale;
        this.reachDelta = reachDelta;
    }

    public int recoveryDelta() {
        return recoveryDelta;
    }

    public float knockbackScale() {
        return knockbackScale;
    }

    public float reachDelta() {
        return reachDelta;
    }

    /** Translation key of the archetype's display name, for the weapon tooltip. */
    public String nameKey() {
        return "forge.magical.archetype." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
