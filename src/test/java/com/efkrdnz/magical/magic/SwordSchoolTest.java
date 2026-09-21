package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The -3 layer as a whole: six verbs, four passives, and the three places a new school has to be
 * wired in by hand.
 *
 * <p>Two of those three fail silently, which is the reason this file exists at all.
 * {@link MagicSchool#isForbidden()} is an {@code ==} chain, so a school left out of it compiles
 * clean and is then treated as an ordinary positive school by every caller; {@link
 * SchoolMaterial#of} falls back to ARCANE with no warning, so a missing material row ships the
 * whole kit rendering arcane-blue on a fully green build. Only {@link MagicAttribute}'s two
 * exhaustive switches would have refused to compile.
 */
class SwordSchoolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<MagicSkillDefinition> swordSkills() {
        return MagicContent.orderedSkillIds().stream()
                .map(MagicContent::get)
                .filter(skill -> skill.school() == MagicSchool.SWORD)
                .toList();
    }

    @Test
    void theLayerHoldsExactlySixVerbsAndFourPassives() {
        assertEquals(List.of(MagicContent.CALL_THE_BLADE, MagicContent.THE_BEARING,
                        MagicContent.THE_KEEL, MagicContent.LOOSE,
                        MagicContent.BELOW, MagicContent.ONE_BLADE),
                swordSkills(), "the school is budgeted at six actives, and these are the six");
        // A codex row below the line draws one list, so a layer shared by two schools makes one of
        // them unlocked, castable and invisible. Layer -3 was measurably empty before this.
        assertEquals(swordSkills(), MagicContent.skillsForTier(-3),
                "the sword school must be the whole of layer -3");
        for (MagicPassiveDefinition passive : List.of(MagicPassiveContent.SWORD_HEART,
                MagicPassiveContent.WARD_OF_THE_ARRAY, MagicPassiveContent.RETURNING,
                MagicPassiveContent.MIRROR_OF_THE_ARRAY)) {
            // forbiddenPassive and never classPassive: a class passive granted only by a hidden
            // chain reads as ungranted to ClassTreeTest's global count, and the codex Passives tab
            // would print the class name as a group header for a class nobody has heard of.
            assertTrue(MagicPassiveContent.isForbiddenPassive(passive.id()), passive.id() + " is one of the four");
            assertFalse(MagicPassiveContent.classPassives().contains(passive.id()),
                    passive.id() + " must not be registered as a class passive");
        }
    }

    @Test
    void everyVerbSitsOnTheEmptyLayerWithItsOwnAttribute() {
        for (MagicSkillDefinition skill : swordSkills()) {
            assertEquals(-3, skill.tier(), skill.id() + " belongs on the sword layer");
            assertEquals(MagicAttribute.SWORD, skill.attribute(), skill.id() + " attribute");
        }
    }

    @Test
    void everyVerbIsAClassRewardAndIsInNoOtherSet() {
        for (MagicSkillDefinition skill : swordSkills()) {
            assertTrue(MagicContent.isClassRewardSkill(skill.id()),
                    skill.id() + " is not a class reward, so no node of the hidden chain can grant it");
            // SUB_SKILLS would take it out of the codex and out of reach of the unlock command;
            // AUTHORITY_SKILLS would stop unlockall granting it, which every capture depends on.
            assertFalse(MagicContent.isSubSkill(skill.id()), skill.id() + " must not be a sub-skill");
            assertFalse(MagicContent.isCreatedSkill(skill.id()), skill.id() + " must not be a created skill");
            assertFalse(MagicContent.isAuthoritySkill(skill.id()), skill.id() + " must not be an authority skill");
        }
    }

    /**
     * TODO: assert that every one of the six has a cast handler in {@code SkillCastRegistry}, as
     * section 8 item 7 of the design requires. It cannot pass before {@code MagicCastContentSword}
     * and {@code magic/skill/sword/} land, so the assertion arrives with the handlers.
     */
    @Test
    void everyVerbIsCommandableAndTranslated() {
        List<String> commandable = MagicContent.commandIds();
        for (MagicSkillDefinition skill : swordSkills()) {
            assertTrue(commandable.contains(skill.id().getPath()), skill.id() + " cannot be unlocked by command");
            assertTrue(skill.nameKey().startsWith("skill.magical."), skill.id() + " name key");
            assertEquals(skill.nameKey() + ".desc", skill.descriptionKey(), skill.id() + " desc key");
        }
    }

    @Test
    void theSchoolIsForbiddenAndCarriesAMaterialRowOfItsOwn() {
        assertTrue(MagicSchool.SWORD.isForbidden(),
                "isForbidden is an == chain, so a school left out of it is silently an ordinary positive school");
        assertNotEquals(SchoolMaterial.ARCANE, SchoolMaterial.of(MagicSchool.SWORD),
                "SchoolMaterial.of falls back to ARCANE without a warning, and the whole kit would render arcane-blue");
        assertEquals(MagicSchool.SWORD, SchoolMaterial.of(MagicSchool.SWORD).school(), "the row must be the sword row");
    }
}
