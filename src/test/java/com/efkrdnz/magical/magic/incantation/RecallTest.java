package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Copies are free: a Recall runs a card's function without drawing it, so no mana, no use, and it
 * works on a card at zero uses. The recursion limit is two. Reprise re-runs the hand, then draws.
 */
class RecallTest {

    @Test
    void recallFirstCopiesTheFirstReadCard() {
        ReciteSession session = session("needle", "weight", "recall_first", "needle");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle"), bodies(second.root()));
        assertEquals(2.5D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(15, second.manaSpent(), "weight and the recall; the copied needle is free");
        assertEquals(1, count(second, ReciteEvent.Kind.COPIED));
        assertEquals(List.of("needle"), unread(session));
    }

    @Test
    void recursionStopsAtTwo() {
        // Nothing read yet, so Recall First finds itself at the top of the hand: it runs three times
        // in all (played, copied at level 1, copied at level 2) and the fourth is refused.
        RecitePlan plan = press(session("recall_first", "needle"));
        assertEquals(2, count(plan, ReciteEvent.Kind.COPIED));
        assertEquals(15, plan.beatTicks());
        assertTrue(plan.bodies().isEmpty());
    }

    @Test
    void recallLastCopiesTheLastUnreadCard() {
        ReciteSession session = session("recall_last", "weight", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(List.of("weight", "needle"), unread(session));
        assertEquals(12, plan.manaSpent());
    }

    @Test
    void recallPairMemorisesBothBeforeRunningEither() {
        RecitePlan plan = press(session("recall_pair", "couplet", "needle", "needle"));
        // The copied Couplet draws both needles; the memorised second card, a needle, still runs after.
        assertEquals(3, plan.bodies().size());
        assertEquals(28, plan.manaSpent());
    }

    @Test
    void recallAllSweepsEverythingWithDrawDisabled() {
        ReciteSession session = session("needle", "recall_all", "weight", "needle");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle", "needle"), bodies(second.root()));
        assertEquals(0.0D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(2.5D, second.bodies().get(1).stamped().damageAdd(), 1e-9);
        assertEquals(1, count(second, ReciteEvent.Kind.PLAYED));
        assertEquals(List.of("weight", "needle"), unread(session), "nothing was drawn");
        assertEquals(60, second.manaSpent());
    }

    @Test
    void recallAllNeverTurnsThePage() {
        ReciteSession session = session("recall_all", "fresh_page");
        press(session);
        assertEquals(List.of("fresh_page"), unread(session));
    }

    @Test
    void recallModifiersRestoresThenDrawsOne() {
        RecitePlan plan = press(session("recall_modifiers", "weight", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(5.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "the copy and the draw both weighed in");
        assertEquals(20, plan.beatTicks(), "the copy's beat was put back; the drawn Weight's and the needle's were not");
        assertEquals(37, plan.manaSpent());
    }

    @Test
    void recallProjectilesSweepsBodiesAndDoesNotDraw() {
        ReciteSession session = session("recall_projectiles", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(2, plan.bodies().size());
        assertEquals(List.of("needle", "needle"), unread(session));
        assertEquals(17, plan.beatTicks());
    }

    @Test
    void recallStaticsSweepsThenDrawsOne() {
        RecitePlan plan = press(session("recall_statics", "detonation"));
        assertEquals(List.of("detonation", "detonation"), bodies(plan.root()));
        assertEquals(50, plan.manaSpent());
    }

    @Test
    void reprise() {
        RecitePlan plan = press(session("weight", "reprise", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(5.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "Weight ran twice, the copy with draw enabled");
        assertEquals(1, count(plan, ReciteEvent.Kind.COPIED));
    }

    @Test
    void aCopyWorksAtZeroUses() {
        Incantation incantation = tape(1, "ember", "recall_first");
        incantation.setUses(0, 0);
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 1, PLENTY, new FixedWorld());
        assertEquals(List.of("ember"), bodies(plan.root()));
        assertEquals(12, plan.manaSpent());
    }
}
