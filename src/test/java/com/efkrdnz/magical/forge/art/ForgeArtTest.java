package com.efkrdnz.magical.forge.art;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The proof that the Art matrix is whole. Thirty-two rows, one per element/form pair, each with a
 * distinct pair, a trigger, and both a name and a description in the shipped language file.
 *
 * <p>Minecraft-free, so it runs in the ordinary unit-test suite rather than needing a game launch.
 * {@code ForgeSpecials} carries the other half of the proof: its class initialiser refuses to load
 * unless every constant here has a behaviour bound to it.</p>
 */
class ForgeArtTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";
    private static final int EXPECTED_ARTS = 32;
    private static final int ARTS_PER_ELEMENT = 4;

    private static final List<String> ELEMENTS =
            List.of("fire", "frost", "storm", "void", "radiant", "venom", "terra", "gale");

    @Test
    void theTableHasExactlyThirtyTwoRows() {
        assertEquals(EXPECTED_ARTS, ForgeArt.values().length);
    }

    @Test
    void everyElementFormPairIsClaimedByAtMostOneArt() {
        Set<String> seen = new HashSet<>();
        for (ForgeArt art : ForgeArt.values()) {
            assertTrue(seen.add(art.key()), "duplicate element/form pair: " + art.key());
        }
        assertEquals(EXPECTED_ARTS, seen.size());
    }

    @Test
    void allEightElementsCarryFourArtsEach() {
        for (String element : ELEMENTS) {
            assertEquals(ARTS_PER_ELEMENT, ForgeArt.forElement(element).size(),
                    element + " should carry four Arts");
        }
    }

    @Test
    void everyArtHasATriggerAndResolvesBackFromItsPair() {
        for (ForgeArt art : ForgeArt.values()) {
            assertNotNull(art.trigger(), art + " has no trigger");
            assertEquals(art, ForgeArt.of(art.element(), art.form()).orElse(null));
        }
    }

    @Test
    void anUnclaimedPairAndAnUnknownIdResolveToNothing() {
        assertTrue(ForgeArt.of("fire", "slash").isEmpty());
        assertTrue(ForgeArt.of("aether", "spin").isEmpty());
        assertTrue(ForgeArt.of(null, "spin").isEmpty());
        assertTrue(ForgeArt.forForm("nonsense").isEmpty());
    }

    @Test
    void triggersMatchThePressTheyName() {
        assertTrue(ArtTrigger.LIGHT.matches(false, false));
        assertFalse(ArtTrigger.LIGHT.matches(true, false));
        assertTrue(ArtTrigger.HEAVY.matches(true, false));
        assertFalse(ArtTrigger.HEAVY.matches(false, true));
        assertTrue(ArtTrigger.ANY.matches(false, false));
        assertTrue(ArtTrigger.ANY.matches(true, true));
        assertTrue(ArtTrigger.FINISHER.matches(false, true));
        assertFalse(ArtTrigger.FINISHER.matches(true, false));
        assertTrue(ArtTrigger.FINISHER_OR_HEAVY.matches(true, false));
        assertTrue(ArtTrigger.FINISHER_OR_HEAVY.matches(false, true));
        assertFalse(ArtTrigger.FINISHER_OR_HEAVY.matches(false, false));
    }

    /** Every Art is named and explained in en_us, so none of them can show up as a raw key in game. */
    @Test
    void everyArtIsNamedAndDescribedInTheLanguageFile() throws IOException {
        String lang = readLang();
        List<String> missing = new ArrayList<>();
        for (ForgeArt art : ForgeArt.values()) {
            if (!lang.contains('"' + art.langKey() + '"')) {
                missing.add(art.langKey());
            }
            if (!lang.contains('"' + art.descriptionKey() + '"')) {
                missing.add(art.descriptionKey());
            }
        }
        assertTrue(missing.isEmpty(), "language file is missing: " + missing);
    }

    private static String readLang() throws IOException {
        try (InputStream stream = ForgeArtTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(stream, "could not find " + LANG_PATH + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
