package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A payload is a new shot with a fresh state, drawn from the same pile at that moment: nothing set
 * before the carrier reaches it and nothing it sets leaks back out. Payloads nest, can overrun, and
 * past the depth cap a carrier is added bare and the recite frays.
 */
class PayloadTest {

    @Test
    void aPayloadHasItsOwnState() {
        ReciteSession session = session("needle_latch", "weight", "detonation", "haste", "needle");
        RecitePlan first = press(session);
        assertEquals(List.of("needle_latch"), bodies(first.root()));
        ProjectilePlan carrier = first.bodies().get(0);
        assertEquals(PayloadKind.LATCH, carrier.payloadKind());
        assertEquals(0.0D, carrier.stamped().damageAdd(), 1e-9);
        assertEquals(5.0D, carrier.stamped().critChance(), 1e-9);
        assertEquals(List.of("detonation"), bodies(carrier.payload()));
        assertEquals(2.5D, carrier.payload().bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(3, carrier.payload().state().beatTicks());
        assertEquals(1, first.beatTicks());
        assertEquals(29, first.manaSpent());
        assertEquals(List.of("haste", "needle"), unread(session));

        RecitePlan second = press(session);
        assertEquals(2.5D, second.bodies().get(0).stamped().speedMultiplier(), 1e-9);
        assertEquals(0.0D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
    }

    @Test
    void aPayloadInsideAPayload() {
        ReciteSession session = session("needle_latch", "needle_fuse", "weight", "needle");
        RecitePlan plan = press(session);
        ProjectilePlan outer = plan.bodies().get(0);
        ProjectilePlan inner = outer.payload().bodies().get(0);
        assertEquals("needle_fuse", inner.verse().getPath());
        assertEquals(PayloadKind.FUSE, inner.payloadKind());
        assertEquals(4, inner.fuseTicks());
        ProjectilePlan leaf = inner.payload().bodies().get(0);
        assertEquals("needle", leaf.verse().getPath());
        assertEquals(2.5D, leaf.stamped().damageAdd(), 1e-9);
        assertFalse(leaf.hasPayload());
        assertEquals(3, plan.root().countAll());
        assertTrue(plan.events().stream().anyMatch(e -> e.kind() == ReciteEvent.Kind.PLAYED && e.depth() == 2));
    }

    @Test
    void aPayloadOverrunsIntoTheReadPile() {
        ReciteSession session = session("needle", "needle_latch");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle"), bodies(second.bodies().get(0).payload()));
        assertEquals(1, count(second, ReciteEvent.Kind.OVERRUN));
        assertTrue(second.rests());
    }

    @Test
    void aTwinLatchDrawsTwo() {
        RecitePlan plan = press(session("needle_twin_latch", "needle", "needle", "needle"));
        assertEquals(List.of("needle", "needle"), bodies(plan.bodies().get(0).payload()));
    }

    @Test
    void anEpitaphCarrierIsStaticAndDrawsThree() {
        RecitePlan plan = press(session("held_word", "needle", "needle", "needle"));
        ProjectilePlan word = plan.bodies().get(0);
        assertEquals(PayloadKind.EPITAPH, word.payloadKind());
        assertTrue(word.prototype().isStatic());
        assertEquals(3, word.payload().bodies().size());
        assertEquals(3, plan.beatTicks());
    }

    @Test
    void farWordIsAUtilityThatCarries() {
        RecitePlan plan = press(session("far_word", "needle"));
        ProjectilePlan word = plan.bodies().get(0);
        assertEquals(VersePrototypes.WORD_FAR, word.prototype());
        assertEquals(PayloadKind.EPITAPH, word.payloadKind());
        assertEquals(List.of("needle"), bodies(word.payload()));
        assertEquals(-2, plan.beatTicks());
    }

    @Test
    void depthIsCapped() {
        RecitePlan plan = press(session("needle_latch", "needle_latch", "needle_latch", "needle_latch", "needle_latch", "needle_latch"));
        assertTrue(plan.frayed());
        assertEquals(5, count(plan, ReciteEvent.Kind.PLAYED));
        ProjectilePlan body = plan.bodies().get(0);
        int depth = 0;
        while (body.hasPayload()) {
            body = body.payload().bodies().get(0);
            depth++;
        }
        assertEquals(ReciteCaps.MAX_DEPTH, depth);
        assertEquals(PayloadKind.NONE, body.payloadKind(), "the carrier past the cap is added bare");
    }
}
