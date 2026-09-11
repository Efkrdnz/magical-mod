package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.cast.MagicCastContent;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.skill.light.PurificationSkill;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The one way out of Corruption.
 *
 * <p>Dark magic is only playable because this exists, so what is pinned here is the exit itself:
 * that it is reachable, that it clears a countable amount, and that it reaches all the way down.
 */
class PurificationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    @Test
    void purificationIsALightSkillOnTheFourthDisplayedTier() {
        MagicSkillDefinition skill = MagicContent.PURIFICATION;
        assertEquals(MagicSchool.LIGHT, skill.school());
        assertEquals(MagicAttribute.DIVINE, skill.attribute());
        assertEquals(3, skill.tier(), "code tier 3 is what the codex draws as Tier 4");
        assertNotNull(SkillCastRegistry.get(skill.id()), "no handler means a button that does nothing");
    }

    @Test
    void itCostsEnoughToBeWorthPlanningAround() {
        // A cheap Purification would make Corruption a formality. The numbers are the balance, so
        // they are pinned rather than left to drift.
        assertTrue(MagicContent.PURIFICATION.baseManaCost() >= 60, "the rite has to actually cost");
        assertTrue(MagicContent.PURIFICATION.baseCooldownTicks() >= 1200,
                "and it must not be castable again inside the same fight");
    }

    @Test
    void oneCastClearsExactlyOneRungOfTheLadder() {
        assertEquals(DarkService.THRESHOLD_STEP, PurificationSkill.CLEARED,
                "a clear that is not one whole rung is a number nobody can count in their head");

        PlayerMagicState state = new PlayerMagicState();
        state.setCorruption(DarkService.THRESHOLD_STEP * 3);
        assertEquals(3, DarkService.threshold(state));

        DarkService.cleanse(null, state, PurificationSkill.CLEARED);
        assertEquals(2, DarkService.threshold(state), "one cast, one rung");
    }

    @Test
    void fourCastsTakeAFullyCorruptedMageAllTheWayBack() {
        // The exit has to actually reach the bottom. A ladder you could only climb most of the way
        // down would leave a permanent floor of debt nobody asked for.
        PlayerMagicState state = new PlayerMagicState();
        state.setCorruption(PlayerMagicState.MAX_CORRUPTION);
        int baseMana = new PlayerMagicState().maxMana();

        for (int rite = 0; rite < 4; rite++) {
            DarkService.cleanse(null, state, PurificationSkill.CLEARED);
        }

        assertEquals(0, state.corruption(), "four rites is the whole ladder");
        assertEquals(baseMana, state.maxMana(), "and the pools come all the way back");
        assertFalse(state.hasCurse(MagicPassiveContent.CORRUPTION_CURSE.id()), "curse included");
    }

    @Test
    void itIsDeepEnoughToAnswerDarkMagicItself() {
        // The forbidden-depth rule: a counter answers its own depth and everything shallower. At
        // code tier 3 this reaches Chaos at -3, which is what put it on this tier in the first place.
        assertTrue(MagicCounterService.matchesForbiddenDepth(MagicContent.PURIFICATION, -2),
                "the skill that cleans up after Dark should be able to answer it too");
        assertTrue(MagicCounterService.matchesForbiddenDepth(MagicContent.PURIFICATION, -3),
                "and the layer below it");
        assertFalse(MagicCounterService.matchesForbiddenDepth(MagicContent.PURIFICATION, -4),
                "but not the floor - that belongs to the tier above this one");
    }

    @Test
    void cleansingIsTheOnlyDirectionCorruptionEverMovesBackwards() {
        // DarkService.cleanse is the single funnel, and the curse comes off inside it. corrupt()
        // refusing to run backwards is what keeps a second, undocumented exit from appearing.
        PlayerMagicState state = new PlayerMagicState();
        state.setCorruption(50);

        assertEquals(0, DarkService.corrupt(null, state, -10),
                "corrupt() must refuse to run backwards - cleansing is not its job");
        assertEquals(50, state.corruption());
    }
}
