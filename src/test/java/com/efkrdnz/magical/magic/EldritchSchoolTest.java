package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.cast.MagicCastContent;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The -5 layer as a whole: six calls, two passives, one price list, one door in. */
class EldritchSchoolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    private static List<MagicSkillDefinition> eldritchSkills() {
        return MagicContent.orderedSkillIds().stream()
                .map(MagicContent::get)
                .filter(skill -> skill.school() == MagicSchool.ELDRITCH)
                .toList();
    }

    @Test
    void theLayerHoldsExactlySixCallsAndTwoPassives() {
        assertEquals(List.of(MagicContent.GRASP_OF_THE_DEEP, MagicContent.UNBLINKING_EYE,
                        MagicContent.HUNGERING_MAW, MagicContent.TENDRIL_LASH,
                        MagicContent.SKIN_OF_THE_DEEP, MagicContent.CALL_OF_THE_DEEP),
                eldritchSkills(), "the school is budgeted at six actives, and these are the six");
        for (MagicPassiveDefinition passive : List.of(MagicPassiveContent.LIDLESS, MagicPassiveContent.DEEP_BARGAIN)) {
            assertTrue(MagicPassiveContent.isForbiddenPassive(passive.id()), passive.id() + " is one of the two");
        }
    }

    @Test
    void everyCallSitsOnItsOwnLayerWithItsOwnAttributeAndPaysMana() {
        for (MagicSkillDefinition skill : eldritchSkills()) {
            assertEquals(-5, skill.tier(), skill.id() + " belongs on the eldritch layer");
            assertEquals(MagicAttribute.ELDRITCH, skill.attribute(), skill.id() + " attribute");
            // The one forbidden school that pays both: mana in the pipeline, Notice in the handler.
            assertTrue(skill.baseManaCost() > 0, skill.id() + " is paid in mana as well as Notice");
            assertTrue(EldritchPrices.base(skill.id()) > 0, skill.id() + " has no notice price");
        }
    }

    @Test
    void everyCallIsWiredCommandableAndTranslated() {
        List<String> commandable = MagicContent.commandIds();
        for (MagicSkillDefinition skill : eldritchSkills()) {
            assertTrue(SkillCastRegistry.all().containsKey(skill.id()), skill.id() + " has no cast handler");
            assertTrue(commandable.contains(skill.id().getPath()), skill.id() + " cannot be unlocked by command");
            assertTrue(skill.nameKey().startsWith("skill.magical."), skill.id() + " name key");
            assertEquals(skill.nameKey() + ".desc", skill.descriptionKey(), skill.id() + " desc key");
        }
    }
}
