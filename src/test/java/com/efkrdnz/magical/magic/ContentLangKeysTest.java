package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A skill with no lang key is not a crash. It is a codex row reading
 * {@code skill.magical.claim_weave}, a cast card with a raw id on it and a tooltip that says
 * nothing - all of which read as placeholder art rather than as a bug, so they survive review.
 *
 * <p>Nothing has ever checked these. {@code MagicalTooltipAssetsTest} deliberately skips keys built
 * by concatenation, and {@link MagicSkillDefinition#nameKey()} is exactly that; {@code
 * MagicalWeaponsTest} and {@code ForgeArtTest} cover their own catalogues; {@code BloodSchoolTest}
 * checks the shape of a key without ever opening the language file. Three authorities are about to
 * add skills in bulk, so the net goes up before they do.
 */
class ContentLangKeysTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everySkillHasANameAndADescription() throws IOException {
        String lang = readLang();
        List<String> missing = new ArrayList<>();
        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            MagicSkillDefinition skill = MagicContent.get(id);
            assertNotNull(skill, id + " is ordered but not registered");
            if (!lang.contains('"' + skill.nameKey() + '"')) {
                missing.add(skill.nameKey());
            }
            if (!lang.contains('"' + skill.descriptionKey() + '"')) {
                missing.add(skill.descriptionKey());
            }
        }
        assertTrue(missing.isEmpty(), "the language file is missing " + missing.size() + " skill keys: " + missing);
    }

    @Test
    void everyAuthorityIsNamed() throws IOException {
        String lang = readLang();
        List<String> missing = new ArrayList<>();
        for (AuthorityDefinition authority : AuthorityContent.all()) {
            if (!lang.contains('"' + authority.nameKey() + '"')) {
                missing.add(authority.nameKey());
            }
            if (!lang.contains('"' + authority.descriptionKey() + '"')) {
                missing.add(authority.descriptionKey());
            }
        }
        assertTrue(missing.isEmpty(), "the language file is missing " + missing.size() + " authority keys: " + missing);
    }

    /**
     * An authority hands its whole kit over in one call, so a skill it names but nobody registered
     * is a silent hole in that kit rather than anything a player or a log would report.
     */
    @Test
    void everySkillAnAuthorityNamesIsRegisteredAndIsAnAuthoritySkill() {
        List<String> wrong = new ArrayList<>();
        for (AuthorityDefinition authority : AuthorityContent.all()) {
            for (ResourceLocation skillId : authority.skillIds()) {
                if (MagicContent.get(skillId) == null) {
                    wrong.add(authority.id() + " grants unregistered " + skillId);
                } else if (!MagicContent.isAuthoritySkill(skillId)) {
                    wrong.add(authority.id() + " grants " + skillId + ", which is not in AUTHORITY_SKILLS");
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }

    private static String readLang() throws IOException {
        try (InputStream stream = ContentLangKeysTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(stream, "could not find " + LANG_PATH + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
