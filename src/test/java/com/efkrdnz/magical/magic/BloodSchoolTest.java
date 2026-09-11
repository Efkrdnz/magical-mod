package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.cast.MagicCastContent;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The -1 layer as a whole: six actives, three passives, one price, and no way in but a command. */
class BloodSchoolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    private static List<MagicSkillDefinition> bloodSkills() {
        return MagicContent.orderedSkillIds().stream()
                .map(MagicContent::get)
                .filter(skill -> skill.school() == MagicSchool.BLOOD)
                .toList();
    }

    @Test
    void theLayerHoldsExactlySevenActivesAndThreePassives() {
        // Named as well as counted, because a bare number says nothing about which one went missing
        // the day somebody deletes a registration.
        assertEquals(List.of(MagicContent.CRIMSON_TITHE, MagicContent.HEMORRHAGE,
                        MagicContent.SCARLET_LANCE, MagicContent.SECOND_HEART,
                        MagicContent.VEIN_WALK, MagicContent.EXSANGUINATE,
                        MagicContent.BLOOD_MANIPULATION),
                bloodSkills(), "the school is budgeted at seven actives, and these are the seven");
        // Named rather than counted: forbiddenPassives() holds every school's, so a bare size
        // assertion would break every time a new layer is built rather than when Blood changes.
        for (MagicPassiveDefinition passive : List.of(MagicPassiveContent.BLOODSCENT,
                MagicPassiveContent.CLOTTING, MagicPassiveContent.VESSEL_OVERFLOWS)) {
            assertTrue(MagicPassiveContent.isForbiddenPassive(passive.id()),
                    passive.id() + " is one of Blood's three passives");
        }
    }

    @Test
    void everyBloodSkillSitsOnItsOwnLayerWithItsOwnAttribute() {
        // One tier, one school, one price. A blood skill anywhere else breaks the rule the whole
        // underside is organised around.
        for (MagicSkillDefinition skill : bloodSkills()) {
            assertEquals(-1, skill.tier(), skill.id() + " belongs on the blood layer");
            assertEquals(MagicAttribute.BLOOD, skill.attribute(), skill.id() + " attribute");
        }
    }

    @Test
    void noBloodSkillChargesManaAsWellAsBlood() {
        // The cast pipeline spends manaCost before the handler runs, and the handler then charges
        // blood. A non-zero mana cost here would quietly bill the player twice for one cast.
        for (MagicSkillDefinition skill : bloodSkills()) {
            assertEquals(0, skill.baseManaCost(), skill.id() + " would be paid for twice");
        }
    }

    @Test
    void everyBloodSkillIsActuallyWiredUpRatherThanJustRegistered() {
        // A definition with no handler is a skill that shows up in the codex, can be equipped, and
        // does nothing at all when pressed.
        for (MagicSkillDefinition skill : bloodSkills()) {
            assertNotNull(SkillCastRegistry.get(skill.id()), skill.id() + " has no cast handler");
        }
    }

    @Test
    void bloodMagicIsReachableByCommand() {
        // Acquisition is commands only for now, by decision - so this is the only door in, and it
        // has to actually be open. The proficiency roll never reaches it: that walks tier >= 0.
        List<String> commandable = MagicContent.commandIds();
        for (MagicSkillDefinition skill : bloodSkills()) {
            assertTrue(commandable.contains(skill.id().getPath()),
                    skill.id() + " cannot be unlocked by command, so nothing can reach it");
        }
    }

    @Test
    void aPlayerWhoHasNeverTouchedBloodIsNotABloodMage() {
        // This gates the whole economy and the HUD bar. If it were ever true by default, every
        // player in the game would start accruing a resource they can neither see nor spend.
        assertFalse(BloodService.isBloodMage(new PlayerMagicState()));

        PlayerMagicState state = new PlayerMagicState();
        state.unlock(MagicContent.HEMORRHAGE.id());
        assertTrue(BloodService.isBloodMage(state), "one blood skill is enough to make a blood mage");
    }

    @Test
    void theVesselCoversACastAndTheBodyIsOnlyAskedForTheRest() {
        // The school's central rule, in the arithmetic the payment path actually runs on.
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(PlayerMagicState.MAX_BLOOD_VESSEL);

        int cost = 40;
        assertEquals(0, state.drawFromVessel(cost), "a full Vessel pays outright");
        assertEquals(PlayerMagicState.MAX_BLOOD_VESSEL - cost, state.bloodVessel());
    }

    @Test
    void everyBloodSkillNamesATranslationKeyRatherThanShowingItsId() {
        for (MagicSkillDefinition skill : bloodSkills()) {
            assertTrue(skill.nameKey().startsWith("skill.magical."), skill.id() + " name key");
            assertEquals(skill.nameKey() + ".desc", skill.descriptionKey(), skill.id() + " desc key");
        }
    }
}
