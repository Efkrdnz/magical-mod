package com.efkrdnz.magical.magic.incantation;

import net.minecraft.network.chat.Component;

/** The one sentence a {@link Landing} makes, for the Grimoire's reading and the preview command alike. */
public final class LandingText {

    private LandingText() {}

    /** "9 damage and 4 healing on one target every body meets"; empty when nothing lands. */
    public static Component describe(Landing landing) {
        if (landing.isNothing()) {
            return Component.empty();
        }
        Component damage = Component.translatable("screen.magical.grimoire.damage_amount", Landing.amount(landing.damage()));
        Component healing = Component.translatable("screen.magical.grimoire.healing_amount", Landing.amount(landing.healing()));
        Component amounts = landing.damage() > 0.0D && landing.healing() > 0.0D
                ? Component.translatable("screen.magical.grimoire.both", damage, healing)
                : landing.damage() > 0.0D ? damage : healing;
        return Component.translatable("screen.magical.grimoire.lands", amounts);
    }
}
