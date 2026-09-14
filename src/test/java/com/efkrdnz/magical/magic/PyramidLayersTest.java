package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The rows below the line in the codex are keyed by the skill's tier, and the row for a tier draws
 * one list. Two schools sharing a tier means one of them is invisible: unlocked, castable, and
 * nowhere in the pyramid. These pin one school to a layer, the Authority row below all of them,
 * and a name on every occupied layer.
 */
class PyramidLayersTest {

    private static final int DEEPEST_LAYER = 8;

    @Test
    void eachLayerBelowTheLineHoldsOneSchool() {
        for (int tier = -1; tier >= -DEEPEST_LAYER; tier--) {
            List<MagicSkillDefinition> layer = MagicContent.skillsForTier(tier);
            if (layer.isEmpty()) {
                continue;
            }
            Set<MagicSchool> schools = layer.stream().map(MagicSkillDefinition::school).collect(Collectors.toCollection(LinkedHashSet::new));
            boolean authority = layer.stream().allMatch(skill -> MagicContent.isAuthoritySkill(skill.id()));
            assertTrue(authority || schools.size() == 1,
                    "layer " + tier + " mixes " + schools + ", so the codex row can only show one of them");
        }
    }

    @Test
    void theEldritchLayerIsTheEldritchSix() {
        Set<ResourceLocation> onTheLayer = MagicContent.skillsForTier(-5).stream()
                .map(MagicSkillDefinition::id).collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(Set.of(MagicContent.GRASP_OF_THE_DEEP.id(), MagicContent.UNBLINKING_EYE.id(),
                MagicContent.HUNGERING_MAW.id(), MagicContent.TENDRIL_LASH.id(),
                MagicContent.SKIN_OF_THE_DEEP.id(), MagicContent.CALL_OF_THE_DEEP.id()), onTheLayer,
                "the eldritch layer must hold the six calls and nothing else");
    }

    @Test
    void theAuthorityRowSitsBelowEverySchoolLayer() {
        Set<Integer> authorityTiers = MagicContent.authoritySkills().stream()
                .map(MagicSkillDefinition::tier).collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(1, authorityTiers.size(), "the authority skills share one row, got " + authorityTiers);
        int authorityTier = authorityTiers.iterator().next();
        assertTrue(authorityTier < MagicContent.minTier(),
                "the authority row must sit below the deepest school layer: authority " + authorityTier
                        + ", deepest school " + MagicContent.minTier());
    }

    @Test
    void everyOccupiedLayerIsNamed() throws IOException {
        String lang = lang();
        for (int tier = -1; tier >= -DEEPEST_LAYER; tier--) {
            List<MagicSkillDefinition> layer = MagicContent.skillsForTier(tier);
            if (layer.isEmpty() || layer.stream().allMatch(skill -> MagicContent.isAuthoritySkill(skill.id()))) {
                continue;
            }
            String key = "\"tier.magical.below." + -tier + "\"";
            assertTrue(lang.contains(key), "layer " + tier + " would render a raw number: " + key + " is missing");
        }
    }

    private static String lang() throws IOException {
        try (InputStream in = PyramidLayersTest.class.getResourceAsStream("/assets/magical/lang/en_us.json")) {
            assertNotNull(in, "en_us.json is not shipped");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
