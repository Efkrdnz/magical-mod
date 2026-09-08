package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The tuning budget: points are divided between a skill's stats, not stacked on each of them.
 *
 * <p>Before this, every stat carried its own cap, so a proficient player put the maximum into all
 * five and every build was the same build. What is pinned here is that the number is now a total,
 * that weakening a stat is free rather than a source of points, and that each sub-skill of a family
 * still gets a budget of its own.
 */
class MagicSkillTuningTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void spendingIsTheSumOfWhatWasAdded() {
        assertEquals(0, MagicSkillTuning.DEFAULT.spent());
        assertEquals(6, new MagicSkillTuning(3, 2, 1, 0, 0).spent());
        assertEquals(11, new MagicSkillTuning(11, 0, 0, 0, 0).spent(),
                "one stat may hold the whole budget - the rule is that you cannot have everything");
    }

    @Test
    void weakeningAStatCostsNothingAndPaysNothing() {
        // Deliberate: taking a stat down says "this one does not matter to me". If it refunded, the
        // budget would stop being a ceiling and a player could hold far more than eleven points of
        // upside by dumping the stats they never use.
        assertEquals(0, new MagicSkillTuning(0, -11, -11, -11, -11).spent());
        assertEquals(3, new MagicSkillTuning(3, -11, -11, -11, -11).spent());
    }

    @Test
    void anAllocationFitsUpToTheBudgetAndNotOnePastIt() {
        MagicSkillTuning exact = new MagicSkillTuning(2, 1, 0, 0, 0);
        assertTrue(exact.fitsIn(3));
        assertFalse(exact.fitsIn(2));
        assertTrue(MagicSkillTuning.DEFAULT.fitsIn(0), "spending nothing fits any budget");
    }

    @Test
    void theBudgetGrowsWithProficiencyAndStopsAtEleven() {
        PlayerMagicState state = new PlayerMagicState();
        int[] expected = {3, 5, 7, 9, 11, 11};
        int[] xp = {0, 70, 170, 300, 480, 720};
        for (int level = 0; level < xp.length; level++) {
            state.setProficiencyXp(xp[level]);
            assertEquals(expected[level], state.tuningLimit(), "proficiency level " + level);
        }
    }

    @Test
    void pointsRunOutAndComeBackWhenAStatIsLowered() {
        PlayerMagicState state = new PlayerMagicState();
        state.setProficiencyXp(0);
        ResourceLocation skill = MagicContent.CRUCIBLE.id();
        state.unlock(skill);
        assertEquals(3, state.tuningLimit());

        for (int i = 0; i < 3; i++) {
            state.adjustTuning(skill, MagicTuningStat.DAMAGE, 1);
        }
        assertEquals(3, state.tuningFor(skill).damage());

        state.adjustTuning(skill, MagicTuningStat.DAMAGE, 1);
        assertEquals(3, state.tuningFor(skill).damage(), "the fourth point does not exist");
        state.adjustTuning(skill, MagicTuningStat.SIZE, 1);
        assertEquals(0, state.tuningFor(skill).size(), "and it cannot be spent elsewhere either");

        state.adjustTuning(skill, MagicTuningStat.DAMAGE, -1);
        state.adjustTuning(skill, MagicTuningStat.SIZE, 1);
        assertEquals(1, state.tuningFor(skill).size(), "taking one back must free it up again");
        assertEquals(3, state.tuningFor(skill).spent());
    }

    @Test
    void goingNegativeDoesNotBuyAnExtraPoint() {
        PlayerMagicState state = new PlayerMagicState();
        state.setProficiencyXp(0);
        ResourceLocation skill = MagicContent.CRUCIBLE.id();
        state.unlock(skill);

        state.adjustTuning(skill, MagicTuningStat.SPEED, -1);
        for (int i = 0; i < 4; i++) {
            state.adjustTuning(skill, MagicTuningStat.DAMAGE, 1);
        }
        assertEquals(-1, state.tuningFor(skill).speed());
        assertEquals(3, state.tuningFor(skill).damage(),
                "dumping speed weakens the skill; it does not hand back a point to spend on damage");
    }

    @Test
    void everySubSkillOfAFamilyHoldsItsOwnBudget() {
        // The whole reason "eleven per sub-skill" needed no special-casing: tuning is keyed by skill
        // id and the unlock cascade gives each command its own entry.
        PlayerMagicState state = new PlayerMagicState();
        state.setProficiencyXp(720);
        state.unlock(MagicContent.GABRIEL.id());

        ResourceLocation judgement = MagicContent.GABRIEL_JUDGEMENT.id();
        ResourceLocation holyField = MagicContent.GABRIEL_HOLY_FIELD.id();
        for (int i = 0; i < 11; i++) {
            state.adjustTuning(judgement, MagicTuningStat.DAMAGE, 1);
        }
        assertEquals(11, state.tuningFor(judgement).damage());
        assertEquals(11, state.tuningFor(judgement).spent(), "one command's budget is fully spent");

        state.adjustTuning(holyField, MagicTuningStat.DAMAGE, 1);
        assertEquals(1, state.tuningFor(holyField).damage(),
                "its sibling is untouched - the budgets are separate, not shared across the family");
    }

    @Test
    void sovereignAegisGrantsAndBudgetsItsCommandsLikeEveryOtherFamily() {
        // These three were unreachable: no unlock cascade meant hasUnlocked was always false, so
        // adjustTuning refused them and the codex never showed them.
        PlayerMagicState state = new PlayerMagicState();
        state.setProficiencyXp(720);
        state.unlock(MagicContent.SOVEREIGN_AEGIS.id());

        for (MagicSkillDefinition subSkill : MagicContent.sovereignAegisSubSkills()) {
            assertTrue(state.hasUnlocked(subSkill.id()), subSkill.id() + " was not granted");
            assertFalse(MagicSkillTuningView.statsFor(subSkill).isEmpty(),
                    subSkill.id() + " has nothing to spend points on");
            state.adjustTuning(subSkill.id(), MagicTuningStat.SIZE, 1);
            assertEquals(1, state.tuningFor(subSkill.id()).size(), subSkill.id() + " is not tunable");
        }
        assertTrue(MagicSkillTuningView.statsFor(MagicContent.SOVEREIGN_AEGIS).isEmpty(),
                "the parent is a hold-wheel, not a spell; its points belong to the commands");
    }

    @Test
    void dumpingStatsCannotBuyAFreeSpell() {
        // The mana and cooldown multipliers are sums of signed terms, so before the floor they went
        // negative and the cost fell onto its absolute minimum: Crucible read 4 mana on an
        // eight-tick cooldown instead of 96 and twenty seconds. Weakening a skill was the cheapest
        // way to get a fast one, which is the opposite of what a nerf should do.
        MagicSkillDefinition crucible = MagicContent.CRUCIBLE;
        MagicSkillResolvedStats base = crucible.resolve(MagicSkillTuning.DEFAULT);
        MagicSkillResolvedStats dumped = crucible.resolve(new MagicSkillTuning(-11, 0, -11, 0, 0));

        assertEquals(Math.round(base.manaCost() * 0.25F), dumped.manaCost(),
                "a nerf may discount mana by three quarters and no more");
        assertEquals(Math.round(base.cooldownTicks() * 0.25F), dumped.cooldownTicks(),
                "and the same for cooldown");
        assertTrue(dumped.manaCost() > 4, "it must not reach the absolute minimum");
        assertTrue(dumped.cooldownTicks() > 8, "nor the eight-tick floor");
    }

    @Test
    void theFloorHoldsWhicheverWayTheSignsFall() {
        // Applied to the product rather than to each factor: the stat sum and the efficiency
        // multiplier can each be negative, and two negatives would otherwise multiply into a
        // positive - or two separate floors would compound into a 94% discount.
        MagicSkillDefinition crucible = MagicContent.CRUCIBLE;
        int base = crucible.resolve(MagicSkillTuning.DEFAULT).manaCost();
        int floor = Math.round(base * 0.25F);

        assertEquals(floor, crucible.resolve(new MagicSkillTuning(-11, 0, -11, 0, 11)).manaCost(),
                "stats dumped and efficiency maxed: both factors negative");
        assertEquals(floor, crucible.resolve(new MagicSkillTuning(0, 0, 0, 0, 11)).manaCost(),
                "max efficiency alone is a three-quarter discount, not a free spell");
        assertTrue(crucible.resolve(new MagicSkillTuning(11, 0, 0, 0, 0)).manaCost() > base,
                "investing still costs more, which the floor must not interfere with");
    }

    @Test
    void aSaveFromTheOldRulesIsRefundedRatherThanLeftIllegal() {
        // Under the per-stat cap this was a legal allocation worth 55 points. It has to come back as
        // zeros, not as something the player can neither keep nor edit.
        MagicSkillTuning maxedUnderOldRules = new MagicSkillTuning(11, 11, 11, 11, 11);
        assertEquals(55, maxedUnderOldRules.spent());
        assertFalse(maxedUnderOldRules.fitsIn(MagicSkillTuning.ABSOLUTE_MAX));
        assertTrue(MagicSkillTuning.DEFAULT.fitsIn(MagicSkillTuning.MAX));
    }
}
