package com.efkrdnz.magical.forge.chain;

import java.util.Locale;

public enum ForgeMaterial {
    WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE, UNKNOWN;

    /** Highest grade this material accepts (DIVINE is handled separately by rules). */
    public ForgeGrade maxGrade() {
        return switch (this) {
            case WOOD, STONE -> ForgeGrade.FINE;
            case IRON, GOLD, UNKNOWN -> ForgeGrade.HIGH;
            case DIAMOND -> ForgeGrade.MASTER;
            case NETHERITE -> ForgeGrade.MYTHIC;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
