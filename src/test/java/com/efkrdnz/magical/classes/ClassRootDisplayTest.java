package com.efkrdnz.magical.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSchool;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The class tree paints a node and prints an XP pool by asking a list of roots, and for a long
 * time that list was {@link MagicalClasses#startingRoots()}.
 *
 * <p>That list deliberately holds only the five trees a new player may be offered. A root which is
 * not one of them - the Spell Creator, and now the hidden Sword chain - was therefore in neither
 * loop: {@code ClassTreeScreen.accentFor} fell through to arcane blue, so the Sword line was drawn
 * the same colour as the Mage line, and {@code drawFooter} skipped the owner's own XP pool and
 * showed them the "choose a class" hint instead. Both are silent - the screen renders perfectly,
 * it just renders the wrong thing - so they are pinned here rather than left to a screenshot.
 */
class ClassRootDisplayTest {

    @Test
    void theSwordLineTakesItsOwnSchoolAndNotArcane() {
        MagicSchool school = MagicalClasses.schoolOf(MagicalClasses.SWORD_SUMMONER);
        assertNotNull(school, "the Sword chain grants only Sword skills, so it speaks with one voice");
        assertEquals(MagicSchool.SWORD, school,
                "the Sword line must be drawn pewter; arcane blue is the Mage line's colour");
    }

    /**
     * Every rung of the chain answers for the root, because {@code accentFor} resolves a node to
     * its base first - a Sword God node drawn in a different colour from a Sword Summoner node
     * would break the tree's one reading that a branch is one colour.
     */
    @Test
    void everyRungOfTheChainAnswersWithTheRootSchool() {
        for (ResourceLocation id : List.of(MagicalClasses.SWORD_SUMMONER, MagicalClasses.SWORD_RIDER,
                MagicalClasses.SWORD_SAINT, MagicalClasses.SWORD_GOD)) {
            assertEquals(MagicalClasses.SWORD_SUMMONER, MagicalClasses.baseOf(id),
                    id + " must resolve to the Sword Summoner root");
        }
    }

    /**
     * A root whose tree grants more than one school has no single colour to take, and must say so
     * rather than pick the first one it met - that would repaint a tree that has been the same
     * colour since the screen was written.
     */
    @Test
    void aRootOfManyVoicesTakesNone() {
        long mixed = MagicalClasses.roots().stream()
                .filter(root -> MagicalClasses.schoolOf(root.id()) == null)
                .count();
        assertTrue(mixed > 0, "at least one root grants skills of several schools; if this ever "
                + "becomes false the fallback below is dead code and should be removed");
        assertNull(MagicalClasses.schoolOf(ResourceLocation.fromNamespaceAndPath("magical", "no_such_class")),
                "an unknown id is not a root and has no school");
    }

    /**
     * The starting roots come first and in their own order. {@code accentFor} indexes a palette by
     * a root's position, so a reordering here silently repaints every class in the game.
     */
    @Test
    void theStartingRootsKeepTheirPlacesAtTheFront() {
        List<MagicalClassDefinition> display = MagicalClasses.displayRoots();
        List<MagicalClassDefinition> starting = MagicalClasses.startingRoots();
        assertTrue(display.size() >= starting.size(), "display roots cannot be the smaller list");
        assertEquals(starting, display.subList(0, starting.size()),
                "the five starting roots must stay at the front, in order, or every accent moves");
    }

    @Test
    void displayRootsHoldsEveryRootExactlyOnce() {
        List<MagicalClassDefinition> display = MagicalClasses.displayRoots();
        Set<ResourceLocation> seen = new HashSet<>();
        for (MagicalClassDefinition root : display) {
            assertTrue(seen.add(root.id()), root.id() + " appears twice in displayRoots");
        }
        assertEquals(MagicalClasses.roots().stream().map(MagicalClassDefinition::id)
                        .collect(Collectors.toSet()), seen,
                "every root must have a place, or its owner is shown the hint in place of their pool");
    }

    /**
     * The hidden chain stays hidden where it matters. {@code displayRoots} is read by the footer,
     * which guards every line on {@code hasClass}, and by the accent, which is only ever asked
     * about a node already on screen - so widening that list must not widen the chooser.
     */
    @Test
    void wideningTheDisplayListDoesNotOfferTheSecretRoot() {
        assertFalse(MagicalClasses.startingRoots().stream()
                        .anyMatch(root -> root.id().equals(MagicalClasses.SWORD_SUMMONER)),
                "the Sword Summoner must never be offered to a new player");
        assertTrue(MagicalClasses.displayRoots().stream()
                        .anyMatch(root -> root.id().equals(MagicalClasses.SWORD_SUMMONER)),
                "but it must have a colour and a pool line once its owner can see it");
    }
}
