package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;

/**
 * The stacking ladders. Each rune has to be worth clearly more at two copies than at one, and
 * clearly less again at three, or stacking is either mandatory or pointless.
 */
class ModifierLadderTest {

    private static final float DELTA = 1.0E-4f;

    private static ModifierStack stack(ForgeModifierKind kind, int copies) {
        ModifierStack mods = ModifierStack.EMPTY;
        for (int i = 0; i < copies; i++) {
            mods = mods.plus(kind);
        }
        return mods;
    }

    @Test
    void noCopiesIsAlwaysWorthNothing() {
        assertEquals(0f, ForgeStrikeMath.reachBonus(ModifierStack.EMPTY), DELTA);
        assertEquals(0f, ForgeStrikeMath.pierceFraction(ModifierStack.EMPTY), DELTA);
        assertEquals(0f, ForgeStrikeMath.leechFraction(ModifierStack.EMPTY), DELTA);
        assertEquals(0, ForgeStrikeMath.brandMaxStacks(ModifierStack.EMPTY));
        assertEquals(1f, ForgeStrikeMath.shatterBonus(ModifierStack.EMPTY), DELTA,
                "no shatter is a multiplier of one, not of zero");
    }

    @Test
    void everyLadderRisesAndFlattens() {
        assertRung(ForgeModifierKind.REACH, ForgeStrikeMath::reachBonus, 1.0f, 1.6f, 2.0f);
        assertRung(ForgeModifierKind.PIERCE, ForgeStrikeMath::pierceFraction, 0.20f, 0.32f, 0.40f);
        assertRung(ForgeModifierKind.LEECH, ForgeStrikeMath::leechFraction, 0.08f, 0.13f, 0.16f);
        assertRung(ForgeModifierKind.LEECH, ForgeStrikeMath::leechCap, 2.0f, 3.0f, 3.5f);
        assertRung(ForgeModifierKind.SHATTER, ForgeStrikeMath::shatterBonus, 1.30f, 1.45f, 1.55f);
    }

    private static void assertRung(ForgeModifierKind kind,
            java.util.function.Function<ModifierStack, Float> ladder, float one, float two, float three) {
        assertEquals(one, ladder.apply(stack(kind, 1)), DELTA, kind + " at one copy");
        assertEquals(two, ladder.apply(stack(kind, 2)), DELTA, kind + " at two copies");
        assertEquals(three, ladder.apply(stack(kind, 3)), DELTA, kind + " at three copies");
        float firstStep = two - one;
        float secondStep = three - two;
        assertTrue(secondStep < firstStep, kind + " must give diminishing returns");
        assertTrue(firstStep > 0f, kind + " must be worth stacking at all");
    }

    @Test
    void brandRaisesTheStackCeilingRatherThanTheDamagePerStack() {
        assertEquals(3, ForgeStrikeMath.brandMaxStacks(stack(ForgeModifierKind.BRAND, 1)));
        assertEquals(4, ForgeStrikeMath.brandMaxStacks(stack(ForgeModifierKind.BRAND, 2)));
        assertEquals(5, ForgeStrikeMath.brandMaxStacks(stack(ForgeModifierKind.BRAND, 3)));
    }

    @Test
    void seekingTurnsHarderPerCopy() {
        double one = ForgeStrikeMath.seekingTurnRadians(stack(ForgeModifierKind.SEEKING, 1));
        double three = ForgeStrikeMath.seekingTurnRadians(stack(ForgeModifierKind.SEEKING, 3));
        assertTrue(three > one);
        assertEquals(0.12, one, 1.0E-6);
    }

    @Test
    void theFinisherBonusStaysCappedHoweverDeepBothRunesAreStacked() {
        ModifierStack both = ModifierStack.of(List.of(
                ForgeModifierKind.SHATTER, ForgeModifierKind.SHATTER, ForgeModifierKind.SHATTER,
                ForgeModifierKind.BRAND, ForgeModifierKind.BRAND, ForgeModifierKind.BRAND));

        float bonus = ForgeStrikeMath.finisherBonus(true, 99, both);

        assertEquals(ForgeStrikeMath.FINISHER_BONUS_CAP, bonus, DELTA);
    }

    @Test
    void leechPoursIntoABiggerPoolPerCopy() {
        ModifierStack three = stack(ForgeModifierKind.LEECH, 3);
        // Already healed 2.0, which would have exhausted a single-rune pool entirely.
        assertTrue(ForgeStrikeMath.leechHeal(100f, 2.0f, three) > 0f);
        assertEquals(0f, ForgeStrikeMath.leechHeal(100f, 2.0f, stack(ForgeModifierKind.LEECH, 1)), DELTA);
    }
}
