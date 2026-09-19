package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * An incantation is the authored thing: an order of verses with their uses, and a breath. Writing it
 * re-seeds uses from the catalogue, refuses anything the catalogue does not hold, and survives NBT.
 */
class IncantationTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        catalogue.register(Verse.of("ember", VerseType.PROJECTILE, 14, 15, VersePrototypes.EMBER, 1,
                Verse.Declared.of(0, 12, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    @Test
    void writingSeedsUsesFromTheCatalogue() {
        Incantation incantation = new Incantation();
        assertTrue(incantation.write(List.of(VerseIds.of("ember"), VerseIds.of("needle")), 2, catalogue()));
        assertEquals(2, incantation.breath());
        assertEquals(15, incantation.entries().get(0).usesRemaining());
        assertEquals(Verse.UNLIMITED, incantation.entries().get(1).usesRemaining());
    }

    @Test
    void anUnknownVerseRefusesTheWholeWrite() {
        Incantation incantation = new Incantation();
        assertTrue(incantation.write(List.of(VerseIds.of("needle")), 1, catalogue()));
        assertFalse(incantation.write(List.of(VerseIds.of("needle"), VerseIds.of("nothing")), 1, catalogue()));
        assertEquals(1, incantation.size());
    }

    @Test
    void tooLongOrBreathlessIsRefused() {
        Incantation incantation = new Incantation();
        List<ResourceLocation> tooMany = Collections.nCopies(ReciteCaps.MAX_VERSES + 1, VerseIds.of("needle"));
        assertFalse(incantation.write(tooMany, 1, catalogue()));
        assertFalse(incantation.write(List.of(VerseIds.of("needle")), 0, catalogue()));
        assertFalse(incantation.write(List.of(VerseIds.of("needle")), ReciteCaps.MAX_BREATH + 1, catalogue()));
        assertTrue(incantation.isEmpty());
    }

    @Test
    void usesSpentInPlayAreKept() {
        Incantation incantation = new Incantation();
        incantation.write(List.of(VerseIds.of("ember")), 1, catalogue());
        incantation.setUses(0, 3);
        assertEquals(3, incantation.entries().get(0).usesRemaining());
        CompoundTag tag = incantation.save();
        Incantation loaded = new Incantation();
        loaded.load(tag);
        assertEquals(3, loaded.entries().get(0).usesRemaining());
        assertEquals(VerseIds.of("ember"), loaded.entries().get(0).id());
        assertEquals(1, loaded.breath());
    }

    @Test
    void loadingGarbageKeepsWhatItCan() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("breath", 40);
        ListTag verses = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putString("id", "not a valid id!");
        bad.putInt("uses", -9);
        verses.add(bad);
        CompoundTag good = new CompoundTag();
        good.putString("id", "magical:needle");
        good.putInt("uses", -9);
        verses.add(good);
        tag.put("verses", verses);
        Incantation loaded = new Incantation();
        loaded.load(tag);
        assertEquals(ReciteCaps.MAX_BREATH, loaded.breath());
        assertEquals(1, loaded.size());
        assertEquals(Verse.UNLIMITED, loaded.entries().get(0).usesRemaining());
    }
}
