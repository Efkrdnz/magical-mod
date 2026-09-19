package com.efkrdnz.magical.magic.incantation;

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
 * A Refrain of N looks at the card {@code iteration} deep, runs it once quiet and N-1 times with
 * draw enabled at the next iteration, and only the outermost pays: it puts beat and rest back and
 * discards as many top cards as the chain went deep. Every Refrain stamps its penalty after its
 * copies, so it lands on later copies and on everything cast afterwards. The count collapses to one
 * past the iteration limit. One use of the target pays for all the copies.
 */
class RefrainTest {

    @Test
    void refrainCopiesTheNextVerse() {
        ReciteSession session = session("refrain_2", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle", "needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "the penalty comes after the copies");
        assertEquals(-1.0D, plan.root().state().damageAdd(), 1e-9);
        assertEquals(5.0D, plan.root().state().patternDegrees(), 1e-9);
        assertEquals(7, plan.beatTicks());
        assertEquals(10, plan.manaSpent());
        assertEquals(1, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("refrain_2", "needle"), unread(session), "and the rest rebuilt it in order");
    }

    @Test
    void nestedRefrainsMultiplyAndPenaliseLaterCopies() {
        ReciteSession session = session("refrain_2", "refrain_2", "needle");
        RecitePlan plan = press(session);
        assertEquals(4, plan.bodies().size());
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(0.0D, plan.bodies().get(1).stamped().damageAdd(), 1e-9);
        assertEquals(-1.0D, plan.bodies().get(2).stamped().damageAdd(), 1e-9);
        assertEquals(-1.0D, plan.bodies().get(3).stamped().damageAdd(), 1e-9);
        assertEquals(-3.0D, plan.root().state().damageAdd(), 1e-9);
        assertEquals(7, plan.beatTicks(), "only the outermost pays beat");
        assertEquals(10, plan.manaSpent(), "the inner Refrain was never drawn");
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests());
    }

    @Test
    void theCountCollapsesPastTheIterationLimit() {
        RecitePlan plan = press(session("refrain_2", "refrain_2", "refrain_2", "refrain_2", "refrain_2", "needle"));
        assertEquals(16, plan.bodies().size(), "2 x 2 x 2 x 2 x 1: the fifth Refrain is at iteration 5");
        assertEquals(5, count(plan, ReciteEvent.Kind.DISCARDED));
    }

    @Test
    void aRefrainOnAModifierDrawsForEachDrawingCopy() {
        ReciteSession session = session("refrain_2", "weight", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(7.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "quiet copy, drawing copy, then the drawn Weight itself");
        assertEquals(17, plan.manaSpent());
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("refrain_2", "weight", "needle", "needle"), unread(session), "and the rest rebuilt it in order");
    }

    @Test
    void oneUsePaysForAllTheCopies() {
        Incantation incantation = tape(1, "refrain_2", "ember");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(2, plan.bodies().size());
        session.writeBack(incantation);
        assertEquals(14, incantation.entries().get(1).usesRemaining());
    }

    @Test
    void refrainOfTenIsLimited() {
        Incantation incantation = tape(1, "refrain_10", "needle");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(10, plan.bodies().size());
        assertEquals(27, plan.beatTicks());
        session.writeBack(incantation);
        assertEquals(4, incantation.entries().get(0).usesRemaining());
    }
}
