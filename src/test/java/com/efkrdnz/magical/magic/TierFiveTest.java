package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import com.efkrdnz.magical.entity.ascendant.AscendantTrait;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The apex rule: tier five answers only to tier five.
 *
 * <p>It is stated once in {@link TierFive} and read by the counter service, the player's ward and
 * the boss's. A rule spelled out in four places is a rule that eventually means four things, so
 * what is pinned here is the single definition and the two things that would quietly break it -
 * a skill list with nothing at the apex, and a damage path that loses which skill it came from.
 */
class TierFiveTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theApexIsTierFourBecauseTheRegistryStopsThere() {
        // "Tier 5" is what the lang says; four is what the numbers say. If a genuine tier 5 is ever
        // registered this test is the thing that notices the two have come apart.
        assertEquals(4, TierFive.APEX_TIER);
        assertTrue(TierFive.is(MagicContent.GABRIEL_ULTIMATE_PROTECTION),
                "the player's ward has to be apex or it cannot answer an apex attack");
        assertTrue(TierFive.is(MagicContent.GABRIEL_JUDGEMENT),
                "Judgement is the attack the rule was originally written for");
    }

    @Test
    void nothingBelowTheApexCounts() {
        assertFalse(TierFive.is((MagicSkillDefinition) null));
        assertFalse(TierFive.is((ResourceLocation) null));
        assertFalse(TierFive.is(ResourceLocation.fromNamespaceAndPath("magical", "no_such_skill")));
        for (MagicSkillDefinition skill : MagicContent.allSkills()) {
            assertEquals(skill.tier() >= TierFive.APEX_TIER, TierFive.is(skill), skill.id().toString());
        }
    }

    @Test
    void theAscendantAlwaysHoldsSomethingThatPierces() {
        // The boss's whole answer to a raised Ultimate Protection is an apex spell. Its base roster
        // is granted at tier 6 and never taken away, so if none of these is apex the fight against
        // a warded player is unwinnable for it at every tier.
        assertTrue(TierFive.is(MagicContent.CRUCIBLE));
        assertTrue(TierFive.is(MagicContent.LEVIATHAN_COIL));
        assertTrue(TierFive.is(MagicContent.HEAVENS_GAZE));
        assertTrue(TierFive.is(MagicContent.DIASPORA));
        assertTrue(TierFive.is(MagicContent.FALLEN_FIRMAMENT));
    }

    @Test
    void damageCarriesTheSkillThatDealtIt() {
        // The hole this closes: SkillTargets builds indirectMagic(owner, owner), so both entities on
        // the DamageSource are the caster and the spell's identity is gone. Without the attribution
        // no spell would pierce any ward, which turns every ward in the game into an off switch.
        DamageSource anonymous = new DamageSource(Holder.direct(
                new DamageType("magical", DamageScaling.NEVER, 0.0F)));
        assertNull(TierFive.skillOf(null));
        assertFalse(TierFive.piercesProtection(anonymous),
                "damage nobody has claimed is not apex damage");

        ResourceLocation outer = TierFive.beginAttribution(MagicContent.CRUCIBLE.id());
        try {
            assertEquals(MagicContent.CRUCIBLE.id(), TierFive.skillOf(anonymous));
            assertTrue(TierFive.piercesProtection(anonymous));
        } finally {
            TierFive.endAttribution(outer);
        }
        assertFalse(TierFive.piercesProtection(anonymous),
                "and the claim must not outlive the hurt call that made it");

        MagicSkillDefinition ordinary = MagicContent.allSkills().stream()
                .filter(skill -> skill.tier() >= 0 && skill.tier() < TierFive.APEX_TIER)
                .findFirst()
                .orElseThrow();
        ResourceLocation lesser = TierFive.beginAttribution(ordinary.id());
        try {
            assertFalse(TierFive.piercesProtection(anonymous),
                    ordinary.id() + " is below the apex and must not open a ward");
        } finally {
            TierFive.endAttribution(lesser);
        }
    }

    @Test
    void attributionNestsWithoutLosingTheOuterSkill() {
        // A spell's damage can set off another spell's damage inside the same hurt call. Restoring
        // rather than clearing is what keeps the first one attributed when the second finishes.
        ResourceLocation first = TierFive.beginAttribution(MagicContent.CRUCIBLE.id());
        ResourceLocation second = TierFive.beginAttribution(MagicContent.HEAVENS_GAZE.id());
        TierFive.endAttribution(second);
        assertEquals(MagicContent.CRUCIBLE.id(), second, "the outer skill has to come back out of begin");
        TierFive.endAttribution(first);
        assertNull(first, "and nothing must be left attributed once the outermost call unwinds");
    }

    @Test
    void aWardedBoardFlipsExactlyOnceAndOnlyUpwards() {
        MobCombatSense calm = MobCombatSense.BLIND;
        MobCombatSense warded = new MobCombatSense(true, false, false);
        assertTrue(warded.demandsAnswer(calm), "the player just raised it - the wind-up is now wrong");
        assertFalse(warded.demandsAnswer(warded), "still up is not news; reacting every tick is a stutter");
        assertFalse(calm.demandsAnswer(warded), "it dropped - nothing to abandon a cast for");
        assertTrue(new MobCombatSense(false, true, false).demandsAnswer(calm),
                "an apex spell in the air is the other thing worth answering immediately");
    }

    @Test
    void onlyTheTopTwoTiersCarryAWardOfTheirOwn() {
        // The symmetric half of the rule. Handing it lower would make the mid tiers unkillable for a
        // player who has not reached an apex skill yet, and there is no tier five defence to cast.
        assertFalse(AscendantTier.ECHO.has(AscendantTrait.WARD));
        assertFalse(AscendantTier.SIN_EATER.has(AscendantTrait.WARD));
        assertFalse(AscendantTier.FALLEN.has(AscendantTrait.WARD));
        assertTrue(AscendantTier.BLACK_FLAME.has(AscendantTrait.WARD));
        assertTrue(AscendantTier.AUTHORITY.has(AscendantTrait.WARD));
    }
}
