package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rules that make the underside of the pyramid a system rather than a junk drawer: one layer
 * one school, a counter that has to reach the depth it answers, and a floor nothing reaches.
 */
class ForbiddenMagicTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aForbiddenSkillIsAnsweredFromItsOwnDepthOrBelow() {
        // The depth rule is a floor, not an exact match: tier X answers -X and everything shallower.
        // Exact matching left tier -1 with a single legal answer in the entire registry.
        MagicSkillDefinition tierTwo = MagicContent.CLEANSING_RAY;
        assertEquals(2, tierTwo.tier(), "this test is anchored to cleansing_ray sitting at code tier 2");

        assertTrue(MagicCounterService.matchesForbiddenDepth(tierTwo, -2), "tier 2 must answer -2");
        assertTrue(MagicCounterService.matchesForbiddenDepth(tierTwo, -1), "and anything shallower");
        assertFalse(MagicCounterService.matchesForbiddenDepth(tierTwo, -3), "but never deeper");
    }

    @Test
    void bloodIsNotLeftWithASingleAnswerInTheWholeGame() {
        // What the floor bought. Blood is the shallowest forbidden layer and the first one a player
        // meets; under exact matching, revelation was the only skill in the game that could answer
        // it, so anyone who had not unlocked that one skill had no counterplay at all.
        long answers = everySkillIncludingSubSkills().stream()
                .filter(skill -> skill.attribute().counters(MagicAttribute.BLOOD))
                .filter(skill -> MagicCounterService.matchesForbiddenDepth(skill, -1))
                .count();

        assertTrue(answers > 1, "blood has only " + answers + " answer(s) in the whole registry");
    }

    @Test
    void anOrdinaryThreatIsNotSubjectToTheDepthRuleAtAll() {
        // The rule is for forbidden magic only. Every positive-tier clash keeps working the way it
        // did before the rule existed, decided purely by attribute opposition.
        for (MagicSkillDefinition skill : everySkillIncludingSubSkills()) {
            if (skill.tier() < 0) {
                continue;
            }
            assertTrue(MagicCounterService.matchesForbiddenDepth(skill, 0), skill.id() + " vs tier 0");
            assertTrue(MagicCounterService.matchesForbiddenDepth(skill, 4), skill.id() + " vs tier 4");
        }
    }

    @Test
    void nothingInTheGameCanAnswerTheAuthorityFloor() {
        // The point of the rule: -5 needs a tier 5 counter, the ladder stops at 4, so Authority is
        // uncounterable without a line of code saying so. If a tier 5 skill is ever registered this
        // fails - which is the warning that the floor just became reachable.
        for (MagicSkillDefinition skill : everySkillIncludingSubSkills()) {
            assertFalse(MagicCounterService.matchesForbiddenDepth(skill, -5),
                    skill.id() + " can answer an authority skill");
        }
        assertEquals(4, TierFive.APEX_TIER, "the ladder's ceiling is what makes -5 unreachable");
    }

    @Test
    void everyForbiddenLayerHasSomethingThatCanActuallyAnswerIt() {
        // A depth with no legal counter is a skill nobody can play against. This walks the real
        // registry rather than trusting a table, so it fails if a layer is left unanswerable.
        for (int layer = 1; layer <= 4; layer++) {
            final int tier = -layer;
            final int answeringTier = layer;
            List<MagicSkillDefinition> answers = everySkillIncludingSubSkills().stream()
                    .filter(skill -> skill.tier() >= answeringTier)
                    .filter(skill -> skill.attribute().counters(MagicAttribute.BLOOD)
                            || skill.attribute().counters(MagicAttribute.DARK))
                    .toList();
            assertFalse(answers.isEmpty(), "nothing can answer tier " + tier);
            for (MagicSkillDefinition answer : answers) {
                assertTrue(MagicCounterService.matchesForbiddenDepth(answer, tier),
                        answer.id() + " should answer tier " + tier);
            }
        }
    }

    @Test
    void lightIsTheOneAnswerThatReachesEveryForbiddenSchool() {
        for (MagicAttribute forbidden : List.of(MagicAttribute.BLOOD, MagicAttribute.DARK,
                MagicAttribute.CHAOS, MagicAttribute.PRIMORDIAL, MagicAttribute.ELDRITCH)) {
            assertTrue(MagicAttribute.DIVINE.counters(forbidden), "divine must answer " + forbidden);
        }
        // And nothing answers Primordial in kind - it is the one school with no opposite.
        for (MagicAttribute attribute : MagicAttribute.values()) {
            assertFalse(MagicAttribute.PRIMORDIAL.counters(attribute),
                    "primordial must counter nothing, but claims " + attribute);
        }
    }

    @Test
    void theVoidSchoolStillBehavesExactlyAsItDidBeforeDarkExisted() {
        // Skills are still registered to school VOID. Re-pointing VOID at anything but DARK would
        // change every one of their clashes at once, which is the one thing this split must not do.
        assertEquals(MagicAttribute.DARK, MagicAttribute.fromSchool(MagicSchool.VOID));
        assertEquals(MagicAttribute.DARK, MagicAttribute.fromSchool(MagicSchool.DARK));
    }

    @Test
    void blackFlamesAndAbyssalDischargeShareTheDarkLayer() {
        for (MagicSkillDefinition skill : List.of(MagicContent.BLACK_FLAMES, MagicContent.BLACK_FLAMES_CAST,
                MagicContent.BLACK_FLAMES_IMBUE, MagicContent.BLACK_FLAMES_BRAND, MagicContent.ABYSSAL_DISCHARGE)) {
            assertEquals(-2, skill.tier(), skill.id() + " belongs on the dark layer");
            assertEquals(MagicSchool.DARK, skill.school(), skill.id() + " is dark magic, not void");
            assertEquals(MagicAttribute.DARK, skill.attribute(), skill.id() + " attribute");
        }
        // Greed's payout is not forbidden study, and stays where it was.
        assertEquals(-4, MagicContent.VAULT_OF_AVARICE.tier());
        assertEquals(MagicSchool.VOID, MagicContent.VAULT_OF_AVARICE.school());
    }

    @Test
    void everyForbiddenSchoolHasItsOwnPaletteRatherThanTheArcaneFallback() {
        // SchoolMaterial.of falls back to ARCANE for anything it does not know, so a missing row is
        // not a crash - it is a forbidden skill quietly rendering as blue arcane magic.
        for (MagicSchool school : MagicSchool.values()) {
            if (!school.isForbidden()) {
                continue;
            }
            SchoolMaterial material = SchoolMaterial.of(school);
            assertEquals(school, material.school(), school + " fell through to " + material);
        }
    }

    @Test
    void everyForbiddenSchoolCarriesAnAttributeOfItsOwn() {
        assertEquals(MagicAttribute.BLOOD, MagicAttribute.fromSchool(MagicSchool.BLOOD));
        assertEquals(MagicAttribute.CHAOS, MagicAttribute.fromSchool(MagicSchool.CHAOS));
        assertEquals(MagicAttribute.PRIMORDIAL, MagicAttribute.fromSchool(MagicSchool.PRIMORDIAL));
        assertEquals(MagicAttribute.ELDRITCH, MagicAttribute.fromSchool(MagicSchool.ELDRITCH));
    }

    @Test
    void aMissingPlayerIsNeverHandedAFreeCast() {
        // waived() is consulted before every price in the forbidden schools. A null-safe "true"
        // here would make all of those costs free in any code path that lost its player.
        assertFalse(MagicPrice.waived(null));
    }

    /** Sub-skills count: a counter is looked up straight out of the player's unlocked set. */
    private static List<MagicSkillDefinition> everySkillIncludingSubSkills() {
        return MagicContent.orderedSkillIds().stream().map(MagicContent::get).toList();
    }
}
