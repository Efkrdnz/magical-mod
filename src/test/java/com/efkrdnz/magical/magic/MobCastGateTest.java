package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The boss gate lets Ascendants reach past the roster ordinary mobs are held to. What it must never
 * let through is pinned here, because the cost of getting it wrong is a fight nobody can play.
 */
class MobCastGateTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void neitherPerfectSealIsEverCastableByAMob() {
        // There are two of them and they are easy to confuse. Missing either is the whole failure:
        // a sealed player cannot cast at all, and a sealed boss cannot be damaged.
        assertTrue(MagicMobCastingService.isNeverCastableByMobs(MagicContent.GABRIEL_PERFECT_SEAL.id()));
        assertTrue(MagicMobCastingService.isNeverCastableByMobs(MagicContent.AEGIS_PERFECT_SEAL.id()));
        assertNotEquals(MagicContent.GABRIEL_PERFECT_SEAL.id(), MagicContent.AEGIS_PERFECT_SEAL.id(),
                "two distinct ids - a denylist naming only one of them is a denylist that fails open");
    }

    @Test
    void theDenylistIsNotLiftedByAnyDifficulty() {
        for (int difficulty = 0; difficulty <= AscendantTier.MAX_TIER; difficulty++) {
            for (ResourceLocation banned : new ResourceLocation[] {
                    MagicContent.GABRIEL_PERFECT_SEAL.id(),
                    MagicContent.AEGIS_PERFECT_SEAL.id(),
                    MagicContent.VAULT_OF_AVARICE.id()}) {
                assertTrue(MagicMobCastingService.isNeverCastableByMobs(banned),
                        banned + " must stay denied at difficulty " + difficulty);
            }
        }
    }

    @Test
    void theSpellsTheBossIsMeantToGainAreNotOnTheDenylist() {
        // The three created fusions tier 8 unlocks, plus two of the tier-6 roster. If any of these
        // were denied outright the gate change would be silently pointless.
        for (ResourceLocation allowed : new ResourceLocation[] {
                MagicContent.FALLEN_SUN.id(),
                MagicContent.TOTAL_ECLIPSE.id(),
                MagicContent.TECTONIC_VERDICT.id(),
                MagicContent.CRUCIBLE.id(),
                MagicContent.HEAVENS_GAZE.id()}) {
            assertFalse(MagicMobCastingService.isNeverCastableByMobs(allowed), allowed.toString());
        }
    }

    @Test
    void theFusionUltimatesAreOnlyReachableBecauseTheyAreCreatedSkills() {
        // Documents why tier 8 gets them for free: they are blocked by the created-skill check
        // alone, not by a missing handler or an unusable mob profile.
        assertTrue(MagicContent.CREATED_SKILLS.contains(MagicContent.FALLEN_SUN.id()));
        assertTrue(MagicContent.CREATED_SKILLS.contains(MagicContent.TOTAL_ECLIPSE.id()));
        assertTrue(MagicContent.CREATED_SKILLS.contains(MagicContent.TECTONIC_VERDICT.id()));
    }

    @Test
    void bothPerfectSealsAreSubSkillsSoOnlyTheDenylistStopsThem() {
        // An Ascendant is allowed past the sub-skill check. That is exactly why these two need a
        // denylist of their own rather than relying on their classification.
        assertTrue(MagicContent.isSubSkill(MagicContent.GABRIEL_PERFECT_SEAL.id()));
        assertTrue(MagicContent.isSubSkill(MagicContent.AEGIS_PERFECT_SEAL.id()));
    }
}
