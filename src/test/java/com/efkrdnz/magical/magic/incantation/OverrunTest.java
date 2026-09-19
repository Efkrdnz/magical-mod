package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A draw that runs past the end of the unread pile reads the top of the incantation again (an
 * overrun) and the press ends with a rest; rest accumulates across presses until a rest happens;
 * Fresh Page rebuilds the unread pile from everything, forbids overrun for the rest of the press,
 * spares uses, and a second one in the same press forces a rest.
 */
class OverrunTest {

    @Test
    void theLastModifierOverruns() {
        ReciteSession session = session("needle", "weight");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle"), bodies(second.root()));
        assertEquals(2.5D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(1, count(second, ReciteEvent.Kind.OVERRUN));
        assertTrue(second.rests());
        assertEquals(List.of("needle", "weight"), unread(session));
    }

    @Test
    void restCarriesUntilTheRest() {
        ReciteSession session = session("second_wind", "needle", "needle");
        RecitePlan first = press(session);
        assertFalse(first.rests());
        assertEquals(-7, session.restCarry());
        RecitePlan second = press(session);
        assertTrue(second.rests());
        assertEquals(-7, second.restTicks());
        assertEquals(0, session.restCarry());
        assertEquals(1, second.cooldownTicks(), "the needle's beat; a negative rest never becomes a negative cooldown");
    }

    @Test
    void freshPageRebuildsTheUnreadPile() {
        ReciteSession session = session("weight", "fresh_page", "needle");
        RecitePlan plan = press(session);
        assertTrue(plan.bodies().isEmpty());
        assertFalse(plan.rests());
        assertEquals(List.of("weight", "fresh_page", "needle"), unread(session));
        assertEquals(-8, session.restCarry());
        assertEquals(2, count(plan, ReciteEvent.Kind.PLAYED));
    }

    @Test
    void freshPageForbidsOverrunForTheRestOfThePress() {
        ReciteSession session = session("couplet", "fresh_page", "weight");
        RecitePlan plan = press(session);
        assertEquals(0, count(plan, ReciteEvent.Kind.OVERRUN), "an empty pile after a Fresh Page does not wrap");
        assertTrue(plan.rests(), "a second Fresh Page in the same press forces a rest");
        assertEquals(-16, plan.restTicks());
    }

    @Test
    void freshPageSparesUses() {
        Incantation withPage = tape(2, "ember", "couplet", "needle", "fresh_page");
        ReciteSession session = ReciteSession.of(withPage, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 2, PLENTY, new FixedWorld());
        assertEquals(List.of("ember", "needle"), bodies(plan.root()));
        session.writeBack(withPage);
        assertEquals(15, withPage.entries().get(0).usesRemaining());

        Incantation without = tape(2, "ember", "couplet", "needle", "weight");
        session = ReciteSession.of(without, VerseContent.CATALOGUE);
        press(session, 2, PLENTY, new FixedWorld());
        session.writeBack(without);
        assertEquals(14, without.entries().get(0).usesRemaining());
    }
}
