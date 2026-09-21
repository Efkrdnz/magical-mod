package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * A passive is free, unlimited, draws one and names no body; read, it is transparent, so a tape
 * with one in it casts what the tape without it casts; written anywhere it is on, once, and only
 * a passive counts. The half halo's sector and the closing test are functions of their vectors.
 */
class PassiveVersesTest {

    @Test
    void everyPassiveIsFreeUnlimitedAndDrawsOne() {
        List<Verse> passives = VerseContent.CATALOGUE.ofType(VerseType.PASSIVE);
        assertEquals(5, passives.size());
        for (Verse verse : passives) {
            assertEquals(0, verse.mana(), verse.id() + " is free");
            assertTrue(verse.unlimited(), verse.id() + " has no uses to spend");
            assertEquals(Verse.Declared.of(1, 0, 0), verse.declared(), verse.id() + " draws one and adds nothing");
            assertFalse(verse.hasPrototype(), verse.id() + " names no body");
            assertFalse(verse.recursive(), verse.id() + " copies nothing");
            assertTrue(verse.type().imposeScans(), verse.id() + " is what an Impose walks over");
            assertFalse(verse.type().spawnsBodies(), verse.id() + " produces no body");
        }
    }

    @Test
    void aPassiveIsTransparentToComposition() {
        RecitePlan with = press(ReciteSession.of(tape(1, "taper", "needle"), VerseContent.CATALOGUE), 1, PLENTY, new FixedWorld());
        RecitePlan without = press(ReciteSession.of(tape(1, "needle"), VerseContent.CATALOGUE), 1, PLENTY, new FixedWorld());
        assertEquals(1, with.root().bodies().size(), "the needle after the taper");
        assertEquals(VersePrototypes.NEEDLE, with.root().bodies().get(0).prototype());
        assertEquals(without.beatTicks(), with.beatTicks(), "the same beat");
        assertEquals(without.manaSpent(), with.manaSpent(), "the same bill");
        assertEquals(Landing.of(without.root()), Landing.of(with.root()), "the same landing");
    }

    @Test
    void writtenReadsEverySlotOnceAndOnlyPassives() {
        Grimoire grimoire = new Grimoire();
        assertTrue(PassiveVerses.written(grimoire, VerseContent.CATALOGUE).isEmpty(), "an empty book carries nothing");
        assertTrue(grimoire.incantation(0).write(List.of(PassiveVerses.TAPER, ProjectileVerses.NEEDLE, PassiveVerses.HALO), 1, VerseContent.CATALOGUE));
        assertTrue(grimoire.incantation(3).write(List.of(PassiveVerses.HALO, PassiveVerses.FAMILIAR, ProjectileVerses.NEEDLE), 2, VerseContent.CATALOGUE));
        List<ResourceLocation> written = List.copyOf(PassiveVerses.written(grimoire, VerseContent.CATALOGUE));
        assertEquals(List.of(PassiveVerses.TAPER, PassiveVerses.HALO, PassiveVerses.FAMILIAR), written, "each once, in the order first met, the needles left out");
        grimoire.incantation(0).clear();
        assertEquals(List.of(PassiveVerses.HALO, PassiveVerses.FAMILIAR), List.copyOf(PassiveVerses.written(grimoire, VerseContent.CATALOGUE)), "struck from one slot, still written in another");
        grimoire.clear();
        assertTrue(PassiveVerses.written(grimoire, VerseContent.CATALOGUE).isEmpty(), "the Authority leaving clears them all");
    }

    @Test
    void theSectorIsAFunctionOfTheLookAndTheOffset() {
        Vec3 look = new Vec3(1.0D, 0.0D, 0.0D);
        assertTrue(VersePassives.inSector(look, new Vec3(3.0D, 0.0D, 0.0D), VersePassives.HALF_HALO_DEGREES), "dead ahead");
        assertFalse(VersePassives.inSector(look, new Vec3(-3.0D, 0.0D, 0.0D), VersePassives.HALF_HALO_DEGREES), "behind");
        double just = Math.toRadians(VersePassives.HALF_HALO_DEGREES - 1.0D);
        double past = Math.toRadians(VersePassives.HALF_HALO_DEGREES + 1.0D);
        assertTrue(VersePassives.inSector(look, new Vec3(Math.cos(just), 0.0D, Math.sin(just)), VersePassives.HALF_HALO_DEGREES), "a degree inside the edge");
        assertFalse(VersePassives.inSector(look, new Vec3(Math.cos(past), 0.0D, Math.sin(past)), VersePassives.HALF_HALO_DEGREES), "a degree outside it");
        assertTrue(VersePassives.inSector(look, new Vec3(-3.0D, 0.0D, 0.0D), 180.0D), "a whole halo is everywhere");
        assertTrue(VersePassives.inSector(look, Vec3.ZERO, VersePassives.HALF_HALO_DEGREES), "on the wielder is inside");
    }

    @Test
    void closingMeansMovingAgainstTheOffset() {
        Vec3 offset = new Vec3(2.0D, 0.0D, 0.0D);
        assertTrue(VersePassives.closing(new Vec3(-1.5D, 0.0D, 0.0D), offset), "flying in");
        assertFalse(VersePassives.closing(new Vec3(1.5D, 0.0D, 0.0D), offset), "flying away");
        assertFalse(VersePassives.closing(new Vec3(0.0D, 0.0D, 1.0D), offset), "flying past");
    }
}
