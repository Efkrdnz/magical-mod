package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A preview assumes its world and touches nothing real: not the Grimoire's toggle, not a random, not a heart. */
class PreviewReciteWorldTest {

    private static final ResourceLocation NEEDLE = VerseIds.of("needle");
    private static final ResourceLocation EMBER = VerseIds.of("ember");
    private static final ResourceLocation WEIGHT = VerseIds.of("weight");

    private static Grimoire grimoire() {
        Grimoire grimoire = new Grimoire();
        grimoire.learnAll(List.of(NEEDLE, EMBER, WEIGHT));
        assertTrue(grimoire.incantation(0).write(List.of(NEEDLE, NEEDLE), 1, VerseContent.CATALOGUE), "slot one takes two needles");
        assertTrue(grimoire.incantation(2).write(List.of(EMBER, WEIGHT), 2, VerseContent.CATALOGUE), "slot three takes a weighted ember");
        return grimoire;
    }

    @Test
    void theToggleIsPrivateToThePreview() {
        Grimoire grimoire = grimoire();
        PreviewReciteWorld world = new PreviewReciteWorld(grimoire, 1, VerseContent.CATALOGUE);
        assertFalse(world.everyOtherSkipAndFlip(), "the first ask casts");
        assertTrue(world.everyOtherSkipAndFlip(), "the second skips");
        assertFalse(grimoire.everyOtherSkipAndFlip(), "the Grimoire toggle never moved");
    }

    @Test
    void theWorldIsAssumedAndTheRandomIsFixed() {
        PreviewReciteWorld world = new PreviewReciteWorld(grimoire(), 1, VerseContent.CATALOGUE);
        assertEquals(0, world.enemiesWithin(16.0D));
        assertEquals(0, world.projectilesWithin(16.0D));
        assertEquals(1.0D, world.healthFraction());
        for (int i = 0; i < 5; i++) {
            assertEquals(0, world.random(7), "fixed, so two previews of one incantation agree");
        }
        // Nobody to hurt: the call must be harmless, and there is nothing to observe.
        world.payHealth(4.0D);
        assertTrue(world.isKnown(NEEDLE));
        assertFalse(world.isKnown(VerseIds.of("couplet")));
        assertEquals(VerseContent.CATALOGUE.size(), world.allVerses().size());
    }

    @Test
    void theOtherIncantationsAreReadInSlotOrderWithoutTheOwn() {
        List<Verse> fromSlotOne = new PreviewReciteWorld(grimoire(), 0, VerseContent.CATALOGUE).otherIncantationVerses();
        assertEquals(List.of(EMBER, WEIGHT), fromSlotOne.stream().map(Verse::id).toList());
        List<Verse> fromSlotThree = new PreviewReciteWorld(grimoire(), 2, VerseContent.CATALOGUE).otherIncantationVerses();
        assertEquals(List.of(NEEDLE, NEEDLE), fromSlotThree.stream().map(Verse::id).toList());
    }
}
