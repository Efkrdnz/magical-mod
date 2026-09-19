package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The Grimoire is the one field the Authority of Mana puts on the player: four incantations, the
 * verses the wielder knows, and the Every Other toggle all four share. It round-trips NBT, copies
 * wholesale (the attachment is copyOnDeath) and clears to nothing when the Authority leaves.
 */
class GrimoireTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    @Test
    void roundTripsNbt() {
        Grimoire grimoire = new Grimoire();
        grimoire.learn(VerseIds.of("needle"));
        grimoire.incantation(2).write(List.of(VerseIds.of("needle"), VerseIds.of("needle")), 3, catalogue());
        assertFalse(grimoire.everyOtherSkipAndFlip());
        CompoundTag tag = grimoire.save();
        Grimoire loaded = new Grimoire();
        loaded.load(tag);
        assertTrue(loaded.knows(VerseIds.of("needle")));
        assertEquals(2, loaded.incantation(2).size());
        assertEquals(3, loaded.incantation(2).breath());
        assertTrue(loaded.incantation(0).isEmpty());
        assertTrue(loaded.everyOtherSkipAndFlip(), "the toggle had been flipped once before saving");
    }

    @Test
    void copyFromReplacesEverything() {
        Grimoire source = new Grimoire();
        source.learn(VerseIds.of("needle"));
        source.incantation(0).write(List.of(VerseIds.of("needle")), 1, catalogue());
        Grimoire target = new Grimoire();
        target.incantation(3).write(List.of(VerseIds.of("needle")), 1, catalogue());
        target.copyFrom(source);
        assertEquals(1, target.incantation(0).size());
        assertTrue(target.incantation(3).isEmpty());
        assertTrue(target.knows(VerseIds.of("needle")));
    }

    @Test
    void clearForgetsTheBook() {
        Grimoire grimoire = new Grimoire();
        grimoire.learn(VerseIds.of("needle"));
        grimoire.incantation(1).write(List.of(VerseIds.of("needle")), 1, catalogue());
        grimoire.clear();
        assertTrue(grimoire.known().isEmpty());
        for (int slot = 0; slot < Grimoire.SLOTS; slot++) {
            assertTrue(grimoire.incantation(slot).isEmpty());
        }
    }

    @Test
    void aSlotOutOfRangeIsClamped() {
        Grimoire grimoire = new Grimoire();
        assertEquals(grimoire.incantation(0), grimoire.incantation(-5));
        assertEquals(grimoire.incantation(Grimoire.SLOTS - 1), grimoire.incantation(99));
    }
}
