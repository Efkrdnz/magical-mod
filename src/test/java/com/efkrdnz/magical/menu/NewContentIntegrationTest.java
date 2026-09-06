package com.efkrdnz.magical.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillTuningView;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Registering content is not the same as wiring it into the screens that show it. These assert the
 * hookups the codex actually reads, so a skill or passive cannot exist in the registry yet be
 * invisible or inert in the GUI.
 */
class NewContentIntegrationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<MagicSkillDefinition> newSkills() {
        return List.of(MagicContent.ANVIL_FALL, MagicContent.SIGIL_FORGE, MagicContent.WAR_HORN,
                MagicContent.SIGHT_LINE, MagicContent.MANA_BLOOM, MagicContent.VIAL_BREAK);
    }

    @Test
    void newSkillsAppearInTheCodexSkillList() {
        List<ResourceLocation> ordered = MagicContent.orderedSkillIds();
        for (MagicSkillDefinition skill : newSkills()) {
            assertTrue(ordered.contains(skill.id()), skill.id() + " is not in orderedSkillIds, so the codex cannot list it");
            assertNotNull(MagicContent.get(skill.id()), skill.id() + " does not resolve");
            assertTrue(skill.tier() >= 0, skill.id() + " has a hidden tier and will be filtered out of the codex");
        }
    }

    @Test
    void newSkillsAreTunableInTheCodex() {
        for (MagicSkillDefinition skill : newSkills()) {
            List<com.efkrdnz.magical.magic.MagicTuningStat> stats = MagicSkillTuningView.statsFor(skill);
            assertFalse(stats.isEmpty(), skill.id() + " exposes no tuning rows, so its options cannot be edited");
        }
    }

    @Test
    void newSkillsAreClassRewardsSoTheTreeCanGrantThem() {
        for (MagicSkillDefinition skill : newSkills()) {
            assertTrue(MagicContent.CLASS_REWARD_SKILLS.contains(skill.id()),
                    skill.id() + " is not a class reward, so no node can grant it");
        }
    }

    /** The passives tab lists normalPassives and toggles by index into that same list. */
    @Test
    void everyClassPassiveIsListedAsANormalTogglablePassive() {
        List<MagicPassiveDefinition> normal = MagicPassiveContent.normalPassives();
        for (ResourceLocation id : MagicPassiveContent.classPassives()) {
            MagicPassiveDefinition definition = MagicPassiveContent.get(id);
            assertNotNull(definition, id + " does not resolve");
            assertFalse(definition.curse(), id + " is a curse, so it would show in the curse column with no checkbox");
            assertTrue(normal.contains(definition),
                    id + " is missing from normalPassives, so the passives tab cannot show or toggle it");
        }
    }
}
