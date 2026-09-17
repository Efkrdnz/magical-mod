package com.efkrdnz.magical.magic.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSchool;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The book the Authority of Mana keeps.
 *
 * <p>These are the rules that make the Ledger a ledger rather than a region: you may only legislate
 * what you have witnessed, a writ is keyed by what it binds and what it binds about, and the hand
 * that wrote a writ is not automatically exempt from it.
 */
class ManaLedgerTest {

    private static final UUID WIELDER = UUID.nameUUIDFromBytes("wielder".getBytes());
    private static final UUID STRANGER = UUID.nameUUIDFromBytes("stranger".getBytes());

    private static Writ writ(ResourceLocation skill, WritAspect aspect, WritOperation operation) {
        return new Writ(skill, null, aspect, operation, WritSubject.ALL);
    }

    @Test
    void aSpellIsEnteredOnceHoweverOftenItIsWitnessed() {
        ManaLedger book = new ManaLedger();
        assertTrue(book.witness(MagicContent.WILDFIRE.id()), "the first sighting is new");
        assertFalse(book.witness(MagicContent.WILDFIRE.id()), "the second sighting is not");
        assertEquals(1, book.witnessed().size());
    }

    @Test
    void theBookForgetsItsOldestSightingRatherThanRefusingANewOne() {
        ManaLedger book = new ManaLedger();
        for (int i = 0; i < ManaLedger.MAX_WITNESSED + 4; i++) {
            book.witness(ResourceLocation.fromNamespaceAndPath("magical", "spell_" + i));
        }
        assertEquals(ManaLedger.MAX_WITNESSED, book.witnessed().size());
        assertFalse(book.witnessed().contains(ResourceLocation.fromNamespaceAndPath("magical", "spell_0")),
                "the oldest sighting fell out of the book");
        assertTrue(book.witnessed().contains(ResourceLocation.fromNamespaceAndPath("magical", "spell_" + (ManaLedger.MAX_WITNESSED + 3))),
                "the newest sighting is in it");
    }

    @Test
    void aSecondWritOnTheSameSpellAndAspectReplacesTheFirst() {
        ManaLedger book = new ManaLedger();
        assertSame(ManaLedger.Outcome.WRITTEN, book.write(writ(MagicContent.WILDFIRE.id(), WritAspect.COST, WritOperation.RAISE)));
        assertSame(ManaLedger.Outcome.WRITTEN, book.write(writ(MagicContent.WILDFIRE.id(), WritAspect.COST, WritOperation.LOWER)));
        assertEquals(1, book.writs().size(), "a price has one ruling, not a stack of them");
        assertSame(WritOperation.LOWER, book.writs().get(0).operation());
    }

    @Test
    void restoreStrikesAWritOutInsteadOfDeclaringOne() {
        ManaLedger book = new ManaLedger();
        book.write(writ(MagicContent.WILDFIRE.id(), WritAspect.COST, WritOperation.RAISE));
        assertSame(ManaLedger.Outcome.STRUCK, book.write(writ(MagicContent.WILDFIRE.id(), WritAspect.COST, WritOperation.RESTORE)));
        assertTrue(book.writs().isEmpty());
        assertSame(ManaLedger.Outcome.NOTHING_TO_STRIKE,
                book.write(writ(MagicContent.WILDFIRE.id(), WritAspect.COST, WritOperation.RESTORE)));
    }

    @Test
    void aFullBookRefusesANewWritButStillAllowsRewritingAndStriking() {
        ManaLedger book = new ManaLedger();
        WritAspect[] aspects = WritAspect.values();
        for (int i = 0; i < ManaLedger.MAX_WRITS; i++) {
            assertSame(ManaLedger.Outcome.WRITTEN,
                    book.write(writ(MagicContent.WILDFIRE.id(), aspects[i], WritOperation.RAISE)));
        }
        assertSame(ManaLedger.Outcome.FULL,
                book.write(writ(MagicContent.SMOKESTACK.id(), WritAspect.COST, WritOperation.RAISE)),
                "a full book has no room for a new ruling");
        assertSame(ManaLedger.Outcome.WRITTEN,
                book.write(writ(MagicContent.WILDFIRE.id(), aspects[0], WritOperation.LOWER)),
                "but rewriting one it already holds takes no new room");
        assertSame(ManaLedger.Outcome.STRUCK,
                book.write(writ(MagicContent.WILDFIRE.id(), aspects[0], WritOperation.RESTORE)));
    }

    @Test
    void aSchoolWritCoversEverySpellOfThatSchoolAndASpellWritCoversOnlyItsOwn() {
        Writ school = new Writ(null, MagicSchool.FIRE, WritAspect.MANIFESTATION, WritOperation.ZERO, WritSubject.THEIRS);
        assertTrue(school.covers(MagicContent.WILDFIRE), "wildfire is fire");
        assertTrue(school.covers(MagicContent.SMOKESTACK), "so is smokestack");
        assertFalse(school.covers(MagicContent.MANA_FORM), "mana form is not");

        Writ single = writ(MagicContent.WILDFIRE.id(), WritAspect.COST, WritOperation.RAISE);
        assertTrue(single.covers(MagicContent.WILDFIRE));
        assertFalse(single.covers(MagicContent.SMOKESTACK), "one spell means one spell");
    }

    @Test
    void theSubjectDecidesWhetherAWritBindsTheHandThatWroteIt() {
        Writ all = new Writ(MagicContent.WILDFIRE.id(), null, WritAspect.COST, WritOperation.RAISE, WritSubject.ALL);
        assertTrue(all.binds(WIELDER, WIELDER), "legislating everyone includes yourself");
        assertTrue(all.binds(WIELDER, STRANGER));

        Writ mine = new Writ(MagicContent.WILDFIRE.id(), null, WritAspect.COST, WritOperation.LOWER, WritSubject.MINE);
        assertTrue(mine.binds(WIELDER, WIELDER));
        assertFalse(mine.binds(WIELDER, STRANGER));

        Writ theirs = new Writ(MagicContent.WILDFIRE.id(), null, WritAspect.COST, WritOperation.RAISE, WritSubject.THEIRS);
        assertFalse(theirs.binds(WIELDER, WIELDER));
        assertTrue(theirs.binds(WIELDER, STRANGER));
    }

    @Test
    void aBookSurvivesBeingWrittenOutAndReadBack() {
        ManaLedger book = new ManaLedger();
        book.open();
        book.witness(MagicContent.WILDFIRE.id());
        book.witness(MagicContent.SMOKESTACK.id());
        book.write(writ(MagicContent.WILDFIRE.id(), WritAspect.POTENCY, WritOperation.INVERT));
        book.write(new Writ(null, MagicSchool.FIRE, WritAspect.COOLDOWN, WritOperation.RAISE, WritSubject.THEIRS));

        ManaLedger read = new ManaLedger();
        read.load(book.save());

        assertTrue(read.isOpen());
        assertEquals(book.witnessed(), read.witnessed());
        assertEquals(2, read.writs().size());
        assertSame(WritOperation.INVERT, read.writs().get(0).operation());
        assertNull(read.writs().get(0).school());
        assertSame(MagicSchool.FIRE, read.writs().get(1).school());
        assertNull(read.writs().get(1).skill());
    }
}
