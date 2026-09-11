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

/** The -2 layer as a whole: one tier, one school, and the two residents that were already on it. */
class DarkSchoolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    /** The four the school was designed around. Deliberately not "everything school DARK". */
    private static List<MagicSkillDefinition> newDarkSkills() {
        return List.of(MagicContent.EFFIGY, MagicContent.UMBRAL_TENANCY,
                MagicContent.LONG_DEBT, MagicContent.SEVER_THE_THREAD);
    }

    private static List<MagicSkillDefinition> darkSkills() {
        return MagicContent.orderedSkillIds().stream()
                .map(MagicContent::get)
                .filter(skill -> skill.school() == MagicSchool.DARK)
                .toList();
    }

    @Test
    void theLayerHoldsFourNewActivesAndTwoPassives() {
        assertEquals(4, newDarkSkills().size(), "the school was budgeted at four actives");
        for (MagicPassiveDefinition passive : List.of(MagicPassiveContent.LEDGER, MagicPassiveContent.WILLING)) {
            assertTrue(MagicPassiveContent.isForbiddenPassive(passive.id()),
                    passive.id() + " is one of Dark's two passives");
        }
    }

    @Test
    void everyDarkSkillSitsOnItsOwnLayerWithItsOwnAttribute() {
        // One tier, one school, one price. This is the rule the whole underside is organised
        // around, and the black_flames family and abyssal_discharge answer to it now as well.
        for (MagicSkillDefinition skill : darkSkills()) {
            assertEquals(-2, skill.tier(), skill.id() + " belongs on the dark layer");
            assertEquals(MagicAttribute.DARK, skill.attribute(), skill.id() + " attribute");
        }
    }

    @Test
    void theOlderResidentsOfTheLayerCameAcrossWithIt() {
        // The explicit ask: abyssal discharge and the black flames family are dark magic, not void.
        for (MagicSkillDefinition skill : List.of(MagicContent.ABYSSAL_DISCHARGE, MagicContent.BLACK_FLAMES,
                MagicContent.BLACK_FLAMES_CAST, MagicContent.BLACK_FLAMES_IMBUE, MagicContent.BLACK_FLAMES_BRAND)) {
            assertEquals(MagicSchool.DARK, skill.school(), skill.id() + " should no longer be void");
            assertEquals(-2, skill.tier(), skill.id() + " shares the layer with the new four");
        }
    }

    @Test
    void noNewDarkSkillChargesManaAsWellAsCorruption() {
        // The cast pipeline spends manaCost before the handler runs, and the handler then signs for
        // Corruption. A non-zero mana cost here would bill the player in two currencies for one
        // cast, and the school's whole premise is that casting itself is free.
        for (MagicSkillDefinition skill : newDarkSkills()) {
            assertEquals(0, skill.baseManaCost(), skill.id() + " would be paid for twice");
        }
    }

    @Test
    void theOlderResidentsKeepTheirManaCostsOnPurpose() {
        // They predate the school and were balanced against a mana bar, so they were moved by
        // school and tier only. If they are ever converted to Corruption, this test is the thing
        // to change first and deliberately, rather than the surprise found afterwards.
        for (MagicSkillDefinition skill : List.of(MagicContent.ABYSSAL_DISCHARGE, MagicContent.BLACK_FLAMES)) {
            assertTrue(skill.baseManaCost() > 0,
                    skill.id() + " still bills in mana; converting it is a balance decision, not a tidy-up");
        }
    }

    @Test
    void everyNewDarkSkillIsActuallyWiredUpRatherThanJustRegistered() {
        // A definition with no handler is a skill that shows up in the codex, can be equipped, and
        // does nothing at all when pressed.
        for (MagicSkillDefinition skill : newDarkSkills()) {
            assertNotNull(SkillCastRegistry.get(skill.id()), skill.id() + " has no cast handler");
        }
    }

    @Test
    void darkMagicIsReachableByCommand() {
        // Acquisition is commands only for now, by decision - so this is the only door in, and it
        // has to actually be open. The proficiency roll never reaches it: that walks tier >= 0.
        List<String> commandable = MagicContent.commandIds();
        for (MagicSkillDefinition skill : newDarkSkills()) {
            assertTrue(commandable.contains(skill.id().getPath()),
                    skill.id() + " cannot be unlocked by command, so nothing can reach it");
        }
    }

    @Test
    void aPlayerWhoHasNeverTouchedDarkIsNotADarkMage() {
        // This gates the HUD bar and the slow tick. If it were ever true by default, every player
        // in the game would carry a Corruption bar for a debt they cannot accrue.
        assertFalse(DarkService.isDarkMage(new PlayerMagicState()));

        PlayerMagicState state = new PlayerMagicState();
        state.unlock(MagicContent.EFFIGY.id());
        assertTrue(DarkService.isDarkMage(state), "one dark skill is enough");

        PlayerMagicState legacy = new PlayerMagicState();
        legacy.unlock(MagicContent.BLACK_FLAMES.id());
        assertTrue(DarkService.isDarkMage(legacy), "and so is one of the layer's older residents");
    }

    @Test
    void everyNewDarkSkillNamesATranslationKeyRatherThanShowingItsId() {
        for (MagicSkillDefinition skill : newDarkSkills()) {
            assertTrue(skill.nameKey().startsWith("skill.magical."), skill.id() + " name key");
            assertEquals(skill.nameKey() + ".desc", skill.descriptionKey(), skill.id() + " desc key");
        }
    }
}
