package com.efkrdnz.magical.entity.ascendant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The tier table is the design, so it is pinned here rather than left to be read off a spawned
 * entity. These are the first tests in the mod to touch the opponent at all.
 */
class AscendantTierTest {

    /** What the clone reaches at its cap, from {@code 34.0 + 5 * 8.0}. */
    private static final double LEVEL_FIVE_CLONE_HEALTH = 74.0D;

    @Test
    void tiersCoverSixThroughTenAndNothingElse() {
        for (int difficulty = 0; difficulty <= 5; difficulty++) {
            assertFalse(AscendantTier.isAscendant(difficulty),
                    "difficulty " + difficulty + " is a clone and must stay one");
            assertTrue(AscendantTier.byTier(difficulty).isEmpty());
        }
        for (int difficulty = 6; difficulty <= 10; difficulty++) {
            assertTrue(AscendantTier.isAscendant(difficulty));
            assertEquals(difficulty, AscendantTier.byTier(difficulty).orElseThrow().tier());
        }
        assertFalse(AscendantTier.isAscendant(11));
        assertTrue(AscendantTier.byTier(11).isEmpty());
        assertTrue(AscendantTier.byTier(-1).isEmpty());
    }

    @Test
    void tierSixIsAWallRatherThanAStepUpFromTheCloneCap() {
        assertTrue(AscendantTier.ECHO.health() > LEVEL_FIVE_CLONE_HEALTH * 2.0D,
                "a tier 6 that merely continues the clone curve is the outcome this table exists to avoid");
    }

    @Test
    void everyStatRisesWithTheTier() {
        AscendantTier[] tiers = AscendantTier.values();
        for (int i = 1; i < tiers.length; i++) {
            AscendantTier previous = tiers[i - 1];
            AscendantTier current = tiers[i];
            assertEquals(previous.tier() + 1, current.tier(), "tiers must be declared in order");
            assertTrue(current.health() > previous.health(), current + " health");
            assertTrue(current.armour() > previous.armour(), current + " armour");
            assertTrue(current.attack() > previous.attack(), current + " attack");
            assertTrue(current.speed() > previous.speed(), current + " speed");
            assertTrue(current.knockbackResistance() > previous.knockbackResistance(), current + " knockback");
            assertTrue(current.maxMana() > previous.maxMana(), current + " mana pool");
            assertTrue(current.manaPerSecond() > previous.manaPerSecond(), current + " mana regen");
        }
    }

    @Test
    void theFightGetsHarderRatherThanOnlyFaster() {
        AscendantTier[] tiers = AscendantTier.values();
        for (int i = 1; i < tiers.length; i++) {
            AscendantTier previous = tiers[i - 1];
            AscendantTier current = tiers[i];
            assertTrue(current.burstSpells() >= previous.burstSpells(), current + " burst size");
            assertTrue(current.recoveryTicks() < previous.recoveryTicks(),
                    current + " must leave a shorter opening than " + previous);
            assertTrue(current.counterWindowTicks() < previous.counterWindowTicks(),
                    current + " must give less time to answer its telegraph");
            assertTrue(current.failDamageFraction() > previous.failDamageFraction(),
                    current + " must punish a missed telegraph harder");
            assertTrue(current.cooldownRate() > previous.cooldownRate(),
                    current + " must come back off cooldown sooner");
        }
    }

    @Test
    void everyTierLeavesAnOpeningAndAnAnswerableWindow() {
        for (AscendantTier tier : AscendantTier.values()) {
            assertTrue(tier.recoveryTicks() >= 20,
                    tier + " recovery is the only safe time to close - it cannot vanish");
            assertTrue(tier.counterWindowTicks() >= 10,
                    tier + " window is under half a second, which stops being a reaction test");
            assertTrue(tier.burstSpells() >= 1 && tier.burstSpells() <= 4, tier + " burst size");
            // Crucible is 1200 ticks. On a rate of 1 an Ascendant would empty its hand once and
            // then be unarmed for the rest of the fight.
            assertTrue(tier.cooldownRate() >= 2, tier + " would spend the fight on cooldown");
        }
    }

    @Test
    void aMissedTelegraphIsSurvivableAtFullHealth() {
        for (AscendantTier tier : AscendantTier.values()) {
            // Twenty hearts, no absorption: one miss must leave the player alive.
            assertTrue(tier.failDamage(20.0F) < 20.0F,
                    tier + " one-shots a player at full health");
            // Three in a row must not. The early tiers forgive a second miss on purpose - that is
            // where the mechanic is learned - but no tier lets it be ignored indefinitely.
            assertTrue(tier.failDamage(20.0F) * 3.0F >= 20.0F,
                    tier + " lets a player ignore the mechanic outright");
        }
    }

    @Test
    void failDamageScalesWithThePlayersOwnMaxHealth() {
        assertEquals(7.0F, AscendantTier.ECHO.failDamage(20.0F), 1.0E-4F);
        assertEquals(14.0F, AscendantTier.ECHO.failDamage(40.0F), 1.0E-4F);
        // Never free, however little health the target has.
        assertTrue(AscendantTier.ECHO.failDamage(0.0F) >= 1.0F);
    }

    @Test
    void nameKeysAreDistinctAndNamespaced() {
        long distinct = java.util.Arrays.stream(AscendantTier.values())
                .map(AscendantTier::nameKey)
                .distinct()
                .count();
        assertEquals(AscendantTier.values().length, distinct);
        assertEquals("entity.magical.ascendant.authority", AscendantTier.AUTHORITY.nameKey());
    }
}
