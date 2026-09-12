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
 * The proof that the Art table is whole: every row a distinct combination with a trigger, a name and
 * a description in the shipped language file, and no two rows able to tie for the same strike.
 *
 * <p>The tie check is the one that matters most now the table keys on four columns instead of two.
 * A tie would make which Art fires depend on the order constants happen to sit in the enum - the
 * kind of bug that shows up as "sometimes it does the other thing" and is almost unfindable in a
 * running game.
 *
 * <p>Minecraft-free, so it runs in the ordinary unit-test suite rather than needing a game launch.
 * {@code ForgeSpecials} carries the other half of the proof: its class initialiser refuses to load
 * unless every constant here has a behaviour bound to it.</p>
 */
class ForgeArtTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";
    /** The eight drawable elements carry four Arts each; the rest key on the weapon instead. */
    private static final int ELEMENT_ARTS = 32;
    private static final int ARTS_PER_ELEMENT = 4;

    private static final List<String> ELEMENTS =
            List.of("fire", "frost", "storm", "void", "radiant", "venom", "terra", "gale");

    @Test
    void theEightElementsStillCarryTheirThirtyTwoArts() {
        int elementArts = 0;
        for (ForgeArt art : ForgeArt.values()) {
            // Null-guarded: ELEMENTS is a List.of, and List.of(...).contains(null) throws rather
            // than answering false - which is exactly what the weapon-keyed Arts hand it.
            if (art.element() != null && ELEMENTS.contains(art.element())
                    && art.archetype() == null && art.temper() == null) {
                elementArts++;
            }
        }
        assertEquals(ELEMENT_ARTS, elementArts,
                "an element/form Art was added or lost; widening the key must not disturb them");
    }

    @Test
    void everyCombinationIsClaimedByAtMostOneArt() {
        Set<String> seen = new HashSet<>();
        for (ForgeArt art : ForgeArt.values()) {
            assertTrue(seen.add(art.key()), "duplicate combination: " + art.key());
        }
        assertEquals(ForgeArt.values().length, seen.size());
    }

    @Test
    void allEightElementsCarryFourArtsEach() {
        for (String element : ELEMENTS) {
            assertEquals(ARTS_PER_ELEMENT, ForgeArt.forElement(element).size(),
                    element + " should carry four Arts");
        }
    }

    @Test
    void everyArtHasATriggerAndResolvesBackFromItsOwnColumns() {
        for (ForgeArt art : ForgeArt.values()) {
            assertNotNull(art.trigger(), art + " has no trigger");
            assertNotNull(art.form(), art + " names no form, so nothing could ever fire it");
            assertEquals(art,
                    ForgeArt.bestMatch(art.element(), art.form(), art.archetype(), art.temper()).orElse(null),
                    art + " does not find itself from the combination it claims");
        }
    }

    /**
     * No two Arts can tie on any combination a real weapon could present.
     *
     * <p>Swept over the table itself rather than over a hand-written list of pairs: the combinations
     * that can collide are exactly the ones some Art already names, and a sweep keeps being right as
     * rows are added.
     */
    @Test
    void noTwoArtsCanTieForTheSameStrike() {
        for (ForgeArt outer : ForgeArt.values()) {
            for (ForgeArt inner : ForgeArt.values()) {
                if (outer == inner || outer.specificity() != inner.specificity()) {
                    continue;
                }
                assertFalse(sameClaim(outer, inner),
                        outer + " and " + inner + " both claim " + outer.key()
                                + " at the same specificity, so which one fires depends on enum order");
            }
        }
    }

    /** Whether two Arts of equal specificity would both match one single real strike. */
    private static boolean sameClaim(ForgeArt a, ForgeArt b) {
        return b.matches(a.element(), a.form(), a.archetype(), a.temper())
                && a.matches(b.element(), b.form(), b.archetype(), b.temper());
    }

    /**
     * A more specific Art wins over a broader one that also matches.
     *
     * <p>A lookup that answered the broader one would make every weapon-keyed Art in the catalogue
     * unreachable, which is the failure this whole widening exists to avoid.
     */
    @Test
    void theMoreSpecificArtWins() {
        assertEquals(ForgeArt.HEARTSEEKER,
                ForgeArt.bestMatch("blood", "lunge", "DAGGER", "rush").orElse(null));
        assertEquals(ForgeArt.IMPALE,
                ForgeArt.bestMatch("blood", "lunge", "SPEAR", "coil").orElse(null),
                "two Arts share the lunge; the weapon is what tells them apart");
        assertEquals(ForgeArt.THOUSAND_CUTS,
                ForgeArt.bestMatch("fire", "slash", "DAGGER", "rush").orElse(null),
                "the dagger Art should fire whatever the blade is made of");
        assertTrue(ForgeArt.bestMatch("fire", "slash", "DAGGER", "keen").isEmpty(),
                "a dagger tempered some other way still got the rush Art");
    }

    @Test
    void anUnclaimedPairAndAnUnknownIdResolveToNothing() {
        assertTrue(ForgeArt.of("fire", "slash").isEmpty());
        assertTrue(ForgeArt.bestMatch("fire", "slash", "SWORD", "keen").isEmpty());
        assertTrue(ForgeArt.bestMatch(null, null, null, null).isEmpty());
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
