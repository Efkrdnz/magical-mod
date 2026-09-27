package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.Accent;
import com.efkrdnz.magical.magic.visual.MagicVisualContent;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Who gets the matter layer and who keeps the look they were designed with.
 *
 * <p>The request that made the layer drew two lines round it - every skill except the
 * Authorities, and not the ones that are mostly shader by nature - and both lines are properties
 * of data nobody sees in game until the wrong skill starts throwing flame. So they are held here.
 */
class AccentResolutionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicVisualContent.init();
    }

    @Test
    void noAuthorityThrowsMatter() {
        List<String> wrong = new ArrayList<>();
        for (MagicSkillDefinition skill : MagicContent.authoritySkills()) {
            if (VisualProfiles.of(skill).accent() != Accent.NONE) {
                wrong.add(skill.id().toString());
            }
        }
        assertTrue(wrong.isEmpty(), "Authorities keep their own look: " + wrong);
    }

    @Test
    void noAuthorityThrowsMatterEvenWhenItsProfileNamesSome() {
        MagicSkillDefinition authority = MagicContent.authoritySkills().get(0);
        assertEquals(Accent.NONE, VisualProfile.resolveAccent(authority, SchoolMaterial.FIRE, Accent.EMBER, true));
    }

    @Test
    void theSwordSchoolKeepsItsSteel() {
        assertEquals(Accent.NONE, Accent.of(SchoolMaterial.SWORD));
        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            VisualProfile profile = VisualProfiles.of(id);
            if (profile.material() == SchoolMaterial.SWORD) {
                assertEquals(Accent.NONE, profile.accent(), id.toString());
            }
        }
    }

    @Test
    void aSkillStillOnADefaultProfileKeepsTheOldLook() {
        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            if (!VisualProfiles.hasExplicit(id)) {
                assertEquals(Accent.NONE, VisualProfiles.of(id).accent(), id.toString());
            }
        }
    }

    @Test
    void everySchoolThatThrowsMatterHasAnAccentOfItsOwn() {
        for (SchoolMaterial material : SchoolMaterial.values()) {
            if (material != SchoolMaterial.SWORD) {
                assertTrue(Accent.of(material).active(), material.name());
            }
        }
    }

    @Test
    void mostOfTheRosterIsPolished() {
        int accented = 0;
        int eligible = 0;
        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            if (MagicContent.isAuthoritySkill(id) || !VisualProfiles.hasExplicit(id)
                    || VisualProfiles.of(id).material() == SchoolMaterial.SWORD) {
                continue;
            }
            eligible++;
            if (VisualProfiles.of(id).accent().active()) {
                accented++;
            }
        }
        // the beams opt out by name; everything else a school owns takes its accent
        assertTrue(accented >= eligible - 12, accented + " of " + eligible);
    }
}
