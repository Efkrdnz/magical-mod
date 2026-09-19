package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Noita runs its draw on one player's frame; this runs on a server tick for every wielder. Past a
 * cap the recite frays, deterministically: drawing stops, what was planned still comes back, and
 * the same tape, mana, breath and world always give the same plan and the same events.
 */
class CapsTest {

    @Test
    void theBodyCapFraysAndKeepsWhatWasPlanned() {
        RecitePlan plan = press(session("refrain_10", "refrain_10", "needle"));
        assertTrue(plan.frayed());
        assertEquals(ReciteCaps.MAX_BODIES, plan.bodies().size());
        assertEquals(1, count(plan, ReciteEvent.Kind.FRAYED));
    }

    @Test
    void theStepCapFrays() {
        // A verse that calls itself without being flagged recursive: the recursion limit never sees
        // it, so the step cap is the only thing that stops it. Wild Verse reaches it through the world.
        Verse[] loop = new Verse[1];
        loop[0] = Verse.of("loop", VerseType.CONTROL, 0, Verse.UNLIMITED, null, 1, Verse.Declared.NONE,
                (r, rec, it) -> r.call(loop[0], rec, 1));
        FixedWorld world = new FixedWorld();
        world.all = List.of(loop[0]);
        RecitePlan plan = press(session("wild_verse"), 1, PLENTY, world);
        assertTrue(plan.frayed());
        assertTrue(plan.bodies().isEmpty());
        assertEquals(1, count(plan, ReciteEvent.Kind.FRAYED));
    }

    @Test
    void aRunawayChainFrays() {
        // 800 copies of one card, the wiki's ceiling: the body cap trips first and the plan keeps 64.
        RecitePlan plan = press(session("refrain_10", "refrain_10", "refrain_4", "refrain_2", "needle"));
        assertTrue(plan.frayed());
        assertEquals(ReciteCaps.MAX_BODIES, plan.bodies().size());
    }

    @Test
    void anOrdinaryTapeNeverFrays() {
        RecitePlan plan = press(session("octave", "needle", "needle", "needle", "needle", "needle", "needle", "needle", "needle"));
        assertFalse(plan.frayed());
        assertEquals(8, plan.bodies().size());
    }

    @Test
    void theSamePressGivesTheSamePlan() {
        String[] tape = {"refrain_2", "impose_latch", "weight", "needle_latch", "couplet", "needle", "wild_verse", "clause_wounded", "needle", "otherwise", "ember"};
        RecitePlan first = press(session(tape), 2, 60, new FixedWorld().roll(3, 1, 4, 1, 5));
        RecitePlan second = press(session(tape), 2, 60, new FixedWorld().roll(3, 1, 4, 1, 5));
        assertEquals(first.events(), second.events());
        assertEquals(bodies(first.root()), bodies(second.root()));
        assertEquals(first.manaSpent(), second.manaSpent());
        assertEquals(first.beatTicks(), second.beatTicks());
        assertEquals(first.root().countAll(), second.root().countAll());
        for (int i = 0; i < first.bodies().size(); i++) {
            assertEquals(first.bodies().get(i).stamped().damageAdd(), second.bodies().get(i).stamped().damageAdd(), 1e-9);
            assertEquals(first.bodies().get(i).payloadKind(), second.bodies().get(i).payloadKind());
        }
    }

    @Test
    void breathIsTheRootBudget() {
        assertEquals(1, press(session("needle", "needle", "needle"), 1, PLENTY, new FixedWorld()).bodies().size());
        assertEquals(3, press(session("needle", "needle", "needle"), 3, PLENTY, new FixedWorld()).bodies().size());
        RecitePlan more = press(session("needle", "needle"), ReciteCaps.MAX_BREATH, PLENTY, new FixedWorld());
        assertEquals(2, more.bodies().size());
        assertTrue(more.rests());
    }
}
