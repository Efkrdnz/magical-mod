package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A session is the three piles between presses. It is built from the incantation in order, it
 * writes spent uses back into the incantation (the only runtime fact that is saved), and where the
 * Lua reassigns a pile the session replaces the list object, so a verse holding the old list keeps
 * iterating the old contents exactly as {@code ipairs} would.
 */
class ReciteSessionTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        catalogue.register(Verse.of("ember", VerseType.PROJECTILE, 14, 15, VersePrototypes.EMBER, 1,
                Verse.Declared.of(0, 12, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    private static Incantation tape() {
        Incantation incantation = new Incantation();
        incantation.write(List.of(VerseIds.of("ember"), VerseIds.of("needle"), VerseIds.of("ember")), 1, catalogue());
        return incantation;
    }

    @Test
    void cardsKeepTheTapeOrderAndTheirIndex() {
        ReciteSession session = ReciteSession.of(tape(), catalogue());
        assertEquals(3, session.deck().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i, session.deck().get(i).deckIndex());
        }
        assertEquals(15, session.deck().get(0).usesRemaining());
        assertEquals(Verse.UNLIMITED, session.deck().get(1).usesRemaining());
        assertTrue(session.firstShot());
        assertEquals(VerseIds.of("ember"), session.nextUnread());
        assertEquals(3, session.unreadCount());
    }

    @Test
    void spentUsesAreWrittenBackEvenForACardThatLeftThePiles() {
        Incantation incantation = tape();
        ReciteSession session = ReciteSession.of(incantation, catalogue());
        VerseCard first = session.deck().remove(0);
        for (int i = 0; i < 15; i++) {
            first.consumeUse();
        }
        assertTrue(first.spent());
        first.consumeUse();
        assertEquals(0, first.usesRemaining(), "a spent card stays at zero");
        session.writeBack(incantation);
        assertEquals(0, incantation.entries().get(0).usesRemaining());
        assertEquals(15, incantation.entries().get(2).usesRemaining());
    }

    @Test
    void replacingAPileLeavesTheOldListToWhoeverHoldsIt() {
        ReciteSession session = ReciteSession.of(tape(), catalogue());
        List<VerseCard> deckBefore = session.deck();
        session.replaceDeck();
        assertNotSame(deckBefore, session.deck());
        assertEquals(3, deckBefore.size());
        assertTrue(session.deck().isEmpty());
        List<VerseCard> discardBefore = session.discard();
        discardBefore.addAll(deckBefore);
        session.moveDiscardToDeck();
        assertEquals(3, session.deck().size());
        assertNotSame(discardBefore, session.discard());
        assertTrue(session.discard().isEmpty());
    }

    @Test
    void orderDeckSortsByIndexNeverShuffles() {
        ReciteSession session = ReciteSession.of(tape(), catalogue());
        VerseCard a = session.deck().remove(0);
        session.deck().add(a);
        session.orderDeck();
        assertSame(a, session.deck().get(0));
        assertEquals(List.of(0, 1, 2), session.deck().stream().map(VerseCard::deckIndex).toList());
    }

    @Test
    void anEntryTheCatalogueLostIsSkippedButKeepsItsIndex() {
        Incantation incantation = tape();
        VerseCatalogue smaller = new VerseCatalogue();
        smaller.register(catalogue().get(VerseIds.of("needle")));
        ReciteSession session = ReciteSession.of(incantation, smaller);
        assertEquals(1, session.deck().size());
        assertEquals(1, session.deck().get(0).deckIndex());
        assertFalse(session.deck().isEmpty());
        session.deck().clear();
        assertNull(session.nextUnread());
    }

    @Test
    void thePlanRecordsAreSelfDescribing() {
        ShotState stamped = new ShotState();
        ProjectilePlan leaf = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle"), stamped, PayloadKind.NONE, 0, null);
        ShotPlan payload = new ShotPlan(List.of(leaf, leaf), new ShotState());
        ProjectilePlan carrier = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle_latch"), stamped, PayloadKind.LATCH, 0, payload);
        ShotPlan root = new ShotPlan(List.of(carrier), new ShotState());
        assertTrue(carrier.hasPayload());
        assertFalse(leaf.hasPayload());
        assertEquals(3, root.countAll());
        RecitePlan plan = new RecitePlan(root, -3, 10, true, 6, 94, false, List.of());
        assertEquals(10, plan.cooldownTicks());
        assertEquals(0, new RecitePlan(root, -3, 10, false, 6, 94, false, List.of()).cooldownTicks());
        assertEquals(1, plan.bodies().size());
    }
}
