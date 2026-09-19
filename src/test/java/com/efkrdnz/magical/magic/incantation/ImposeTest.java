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
 * An Impose scans forward over modifier, passive, control and multicast verses, running each
 * modifier it passes quietly and for free, stops at the first verse with a prototype, discards the
 * scanned verses and the target, and spawns the target as a carrier if any payload-worthy verse is
 * left anywhere in the unread pile; otherwise it runs the target quietly. The target loses a use;
 * the modifiers passed do not. A verse without a prototype ends it with nothing done.
 */
class ImposeTest {

    @Test
    void imposeLatchTurnsTheNextBodyIntoACarrier() {
        ReciteSession session = session("impose_latch", "weight", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(1, plan.bodies().size());
        ProjectilePlan carrier = plan.bodies().get(0);
        assertEquals(VersePrototypes.NEEDLE, carrier.prototype());
        assertEquals(PayloadKind.LATCH, carrier.payloadKind());
        assertEquals(2.5D, carrier.stamped().damageAdd(), 1e-9, "the passed modifier lands on the carrier");
        assertEquals(List.of("needle"), bodies(carrier.payload()));
        assertEquals(0.0D, carrier.payload().bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(8, plan.manaSpent(), "the Impose and the payload needle; the passed Weight and the target are free");
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("impose_latch", "weight", "needle", "needle"), unread(session), "and the rest rebuilt it in order");
    }

    @Test
    void imposeFuseAndEpitaphUseTheirKinds() {
        ProjectilePlan fused = press(session("impose_fuse", "needle", "needle")).bodies().get(0);
        assertEquals(PayloadKind.FUSE, fused.payloadKind());
        assertEquals(7, fused.fuseTicks());
        ProjectilePlan epitaph = press(session("impose_epitaph", "needle", "needle")).bodies().get(0);
        assertEquals(PayloadKind.EPITAPH, epitaph.payloadKind());
    }

    @Test
    void withNoPayloadWorthyVerseLeftTheTargetRunsQuietly() {
        RecitePlan plan = press(session("impose_latch", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(PayloadKind.NONE, plan.bodies().get(0).payloadKind());
        assertEquals(4, plan.manaSpent());
    }

    @Test
    void aVerseWithoutAPrototypeEndsItWithNothingDone() {
        ReciteSession session = session("impose_latch", "fresh_page", "needle");
        RecitePlan plan = press(session);
        assertTrue(plan.bodies().isEmpty());
        assertEquals(1, count(plan, ReciteEvent.Kind.PLAYED));
        assertEquals(List.of("fresh_page", "needle"), unread(session));
    }

    @Test
    void theTargetLosesAUseThePassedModifiersDoNot() {
        Incantation incantation = tape(1, "impose_latch", "undying", "ember", "needle");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(VersePrototypes.EMBER, plan.bodies().get(0).prototype());
        assertTrue(plan.bodies().get(0).stamped().has(Behaviour.UNDYING));
        session.writeBack(incantation);
        assertEquals(3, incantation.entries().get(1).usesRemaining());
        assertEquals(14, incantation.entries().get(2).usesRemaining());
    }

    @Test
    void anImposeIsScannedOverButNeverRun() {
        ReciteSession session = session("impose_latch", "impose_fuse", "needle", "needle");
        RecitePlan plan = press(session);
        ProjectilePlan carrier = plan.bodies().get(0);
        assertEquals(PayloadKind.LATCH, carrier.payloadKind());
        assertEquals(List.of("needle"), bodies(carrier.payload()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("impose_latch", "impose_fuse", "needle", "needle"), unread(session), "and the rest rebuilt it in order");
    }
}
