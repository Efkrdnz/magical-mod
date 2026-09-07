package com.efkrdnz.magical.forge.chain;

import java.util.Locale;
import java.util.Optional;

public enum ForgeGrade {
    CRUDE(1, 0, 15, 6, 2.0f, 0.50f, ForgeMaterial.WOOD, 200),
    FINE(2, 0, 28, 12, 3.5f, 0.56f, ForgeMaterial.WOOD, 400),
    HIGH(2, 1, 42, 20, 5.0f, 0.62f, ForgeMaterial.IRON, 900),
    MASTER(3, 1, 58, 30, 6.0f, 0.68f, ForgeMaterial.DIAMOND, 1800),
    MYTHIC(3, 2, 75, 42, 8.5f, 0.74f, ForgeMaterial.NETHERITE, 3600),
    DIVINE(4, 3, 100, 55, 11.0f, 0.80f, ForgeMaterial.IRON, 6000);

    private final int formSlots;
    private final int modifierSlots;
    private final int manaPercent;
    private final int classXp;
    private final float baseDamage;
    private final float sigilAcceptScore;
    private final ForgeMaterial materialFloor;
    private final int reforgeCooldownTicks;

    ForgeGrade(int formSlots, int modifierSlots, int manaPercent, int classXp, float baseDamage,
            float sigilAcceptScore, ForgeMaterial materialFloor, int reforgeCooldownTicks) {
        this.formSlots = formSlots;
        this.modifierSlots = modifierSlots;
        this.manaPercent = manaPercent;
        this.classXp = classXp;
        this.baseDamage = baseDamage;
        this.sigilAcceptScore = sigilAcceptScore;
        this.materialFloor = materialFloor;
        this.reforgeCooldownTicks = reforgeCooldownTicks;
    }

    public int formSlots() {
        return formSlots;
    }

    /**
     * How many element runes a chain of this grade may hold.
     *
     * <p>Two means the smith may fuse a pair. It sits at MYTHIC and above on top of the class and
     * skill gates, which finally gives MYTHIC a reason to exist rather than being a cheaper DIVINE.
     */
    public int elementSlots() {
        return ordinal() >= MYTHIC.ordinal() ? 2 : 1;
    }

    public int modifierSlots() {
        return modifierSlots;
    }

    public int manaPercent() {
        return manaPercent;
    }

    public int classXp() {
        return classXp;
    }

    public float baseDamage() {
        return baseDamage;
    }

    public float sigilAcceptScore() {
        return sigilAcceptScore;
    }

    public ForgeMaterial materialFloor() {
        return materialFloor;
    }

    public int reforgeCooldownTicks() {
        return reforgeCooldownTicks;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ForgeGrade> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (ForgeGrade grade : values()) {
            if (grade.name().equalsIgnoreCase(name)) {
                return Optional.of(grade);
            }
        }
        return Optional.empty();
    }
}
