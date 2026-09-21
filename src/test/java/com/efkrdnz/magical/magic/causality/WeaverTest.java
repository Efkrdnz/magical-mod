package com.efkrdnz.magical.magic.causality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The engine, on exact numbers.
 *
 * <p>The conservation rule is the whole balance of this Authority, and it is arithmetic, so it is
 * checkable: what a Store takes off a blow is exactly what it banks, what a Pass moves is exactly
 * what was there, and what an Erase costs is exactly nine tenths of what it unmade. Every one of
 * those is a number here rather than a thing somebody watched happen once in a dev client.
 */
class WeaverTest {

    /** A world that answers whatever the test needs it to, and remembers nothing on its own. */
    private static final class Fake implements CausalWorld {
        long now = 1000L;
        float ledger;
        float paradox;
        float mana = 100.0F;
        boolean mark = true;
        float health = 1.0F;
        double distance = 3.0D;
        boolean crouching;
        boolean crowded = true;
        final Map<Integer, Long> gates = new HashMap<>();

        @Override
        public long now() {
            return now;
        }

        @Override
        public float ledger() {
            return ledger;
        }

        @Override
        public float paradox() {
            return paradox;
        }

        @Override
        public float mana() {
            return mana;
        }

        @Override
        public boolean markAlive() {
            return mark;
        }

        @Override
        public float selfHealth() {
            return health;
        }

        @Override
        public double distanceTo(int entityId) {
            return distance;
        }

        @Override
        public boolean crouching() {
            return crouching;
        }

        @Override
        public long lastPassed(int nodeId) {
            return gates.getOrDefault(nodeId, Long.MIN_VALUE);
        }

        @Override
        public boolean crowded() {
            return crowded;
        }
    }

    private static CausalEvent blow(float damage) {
        return new CausalEvent(Cause.HURT, damage, 7, CausalEvent.MELEE);
    }

    /** A cause wired straight to a consequence, which is the smallest board that does anything. */
    private static Weave rule(Cause cause, Effect effect, int param) {
        Weave weave = new Weave();
        CausalNode a = weave.add(CausalNode.of(0, cause, 10, 10));
        CausalNode b = weave.add(CausalNode.of(0, effect, 90, 10).withParam(param));
        weave.connect(a.id(), b.id());
        return weave;
    }

    private static CausalAction only(Resolution resolution) {
        assertEquals(1, resolution.actions().size(), "expected exactly one consequence");
        return resolution.actions().get(0);
    }

    // ---- conservation ------------------------------------------------------------------------

    @Test
    void whatAStoreTakesOffABlowIsExactlyWhatItBanks() {
        Resolution answer = Weaver.resolve(rule(Cause.HURT, Effect.STORE, 50), blow(20.0F), new Fake());
        assertEquals(10.0F, answer.magnitude(), 1.0E-4F, "half the blow lands");
        assertEquals(10.0F, only(answer).magnitude(), 1.0E-4F, "and the other half is banked");
        assertEquals(0.0F, answer.paradox(), 1.0E-4F, "moving a consequence somewhere legal costs no paradox");
    }

    @Test
    void theSignalIsLiveSoTwoStoresTakeHalfAndThenHalfOfTheRest() {
        // The rule that makes a second wire an honest addition rather than a way to double-spend one
        // blow. Half of twenty is ten; half of what is left is five; three quarters is banked.
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode first = weave.add(CausalNode.of(0, Effect.STORE, 90, 10).withParam(50));
        CausalNode second = weave.add(CausalNode.of(0, Effect.STORE, 90, 50).withParam(50));
        weave.connect(cause.id(), first.id());
        weave.connect(cause.id(), second.id());

        Resolution answer = Weaver.resolve(weave, blow(20.0F), new Fake());
        assertEquals(2, answer.actions().size());
        assertEquals(10.0F, answer.actions().get(0).magnitude(), 1.0E-4F);
        assertEquals(5.0F, answer.actions().get(1).magnitude(), 1.0E-4F);
        assertEquals(5.0F, answer.magnitude(), 1.0E-4F, "and what is left is what lands");
    }

    @Test
    void branchesFireInTheOrderTheStringWasDrawn() {
        // Not an implementation detail leaking out: it is the one thing a graph offers that a list
        // and a rule table cannot, and two wielders will argue about it.
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 90, 10).withParam(50));
        CausalNode erase = weave.add(CausalNode.of(0, Effect.ERASE, 90, 50));
        weave.connect(cause.id(), erase.id());
        weave.connect(cause.id(), store.id());

        Resolution answer = Weaver.resolve(weave, blow(20.0F), new Fake());
        assertSame(Effect.ERASE, answer.actions().get(0).effect(), "the first wire drawn is the first to fire");
        assertEquals(1, answer.actions().size(), "and it left the Store nothing to take");
        assertEquals(0.0F, answer.magnitude(), 1.0E-4F);
    }

    @Test
    void erasingABlowCostsNineTenthsOfItAndPassingItCostsAlmostNothing() {
        Resolution erased = Weaver.resolve(rule(Cause.HURT, Effect.ERASE, 1), blow(20.0F), new Fake());
        assertEquals(0.0F, erased.magnitude(), 1.0E-4F);
        assertEquals(18.0F, erased.paradox(), 1.0E-3F, "nine tenths a point, and twenty points of it");

        Resolution passed = Weaver.resolve(rule(Cause.HURT, Effect.PASS, 1), blow(20.0F), new Fake());
        assertEquals(0.0F, passed.magnitude(), 1.0E-4F, "it still leaves the wielder");
        assertEquals(20.0F, only(passed).magnitude(), 1.0E-4F, "carrying all of it somewhere else");
        assertEquals(1.6F, passed.paradox(), 1.0E-3F, "and reality barely notices");
    }

    @Test
    void anEchoMakesASecondConsequenceWithoutTakingTheFirstAway() {
        Resolution answer = Weaver.resolve(rule(Cause.HURT, Effect.ECHO, 1), blow(12.0F), new Fake());
        assertEquals(12.0F, answer.magnitude(), 1.0E-4F, "the blow still lands");
        assertEquals(12.0F, only(answer).magnitude(), 1.0E-4F, "and so does a copy of it");
        assertEquals(6.0F, answer.paradox(), 1.0E-3F, "which is consequence made from nothing, and dear");
    }

    @Test
    void aBankedPointMayBeSpentInTheSameBreathItWasBanked() {
        // The design doc worked counterattack, in one chain rather than two.
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 90, 10).withParam(100));
        CausalNode spend = weave.add(CausalNode.of(0, Effect.SPEND, 90, 60).withParam(25));
        weave.connect(cause.id(), store.id());
        weave.connect(cause.id(), spend.id());

        Resolution answer = Weaver.resolve(weave, blow(18.0F), new Fake());
        assertEquals(0.0F, answer.magnitude(), 1.0E-4F, "the whole blow was taken");
        assertEquals(18.0F, answer.actions().get(0).magnitude(), 1.0E-4F, "into the ledger");
        assertEquals(18.0F, answer.actions().get(1).magnitude(), 1.0E-4F,
                "and straight back out again, capped by what is actually there");
    }

    @Test
    void aLedgerAtTheCeilingTakesNothingAndTheBlowLandsWhole() {
        Fake world = new Fake();
        world.ledger = Ledger.MAX;
        Resolution answer = Weaver.resolve(rule(Cause.HURT, Effect.STORE, 100), blow(20.0F), world);
        assertEquals(20.0F, answer.magnitude(), 1.0E-4F);
        assertTrue(answer.actions().isEmpty(), "and nothing is billed for a consequence that did not happen");
        assertEquals(0.0F, answer.mana(), 1.0E-4F);
    }

    // ---- the questions -----------------------------------------------------------------------

    private static Weave gated(Condition condition, int param) {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode gate = weave.add(CausalNode.of(0, condition, 60, 10).withParam(param));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.PASS, 110, 10));
        weave.connect(cause.id(), gate.id());
        weave.connect(gate.id(), effect.id());
        return weave;
    }

    @Test
    void aQuestionThatAnswersNoKillsTheBranchAndNothingIsBilled() {
        Weave weave = gated(Condition.IS_PROJECTILE, 0);
        Resolution answer = Weaver.resolve(weave, blow(20.0F), new Fake());
        assertEquals(20.0F, answer.magnitude(), 1.0E-4F, "a melee blow is not a projectile");
        assertTrue(answer.actions().isEmpty());
        assertEquals(0.0F, answer.mana(), 1.0E-4F);
    }

    @Test
    void aQuestionNeverChangesWhatTheChainIsCarrying() {
        Weave weave = gated(Condition.ABOVE, 5);
        Resolution answer = Weaver.resolve(weave, blow(20.0F), new Fake());
        assertEquals(20.0F, only(answer).magnitude(), 1.0E-4F, "twenty went in and twenty came out");
    }

    @Test
    void theGovernorReportsItselfSoTheServiceCanWindItsClock() {
        Weave weave = gated(Condition.ONCE_PER, 40);
        Fake world = new Fake();

        Resolution first = Weaver.resolve(weave, blow(10.0F), world);
        assertEquals(1, first.gatesPassed().size(), "the gate says it let a signal through");
        assertFalse(first.actions().isEmpty());

        // The service would now stamp the clock. Twenty ticks later the gate is still shut.
        world.gates.put(first.gatesPassed().get(0), world.now);
        world.now += 20L;
        assertTrue(Weaver.resolve(weave, blow(10.0F), world).actions().isEmpty(), "too soon");

        world.now += 21L;
        assertFalse(Weaver.resolve(weave, blow(10.0F), world).actions().isEmpty(), "and now it is not");
    }

    @Test
    void anAnchoredQuestionIsSimplyFalseWithNoMarkOutThere() {
        Weave weave = gated(Condition.MARKED_LIVES, 0);
        Fake world = new Fake();
        world.mark = false;
        assertTrue(Weaver.resolve(weave, blow(20.0F), world).actions().isEmpty());
        world.mark = true;
        assertFalse(Weaver.resolve(weave, blow(20.0F), world).actions().isEmpty());
    }

    @Test
    void aHeartbeatOnlyFiresOnItsOwnBeat() {
        // The service beats out a TOLL every MIN_INTERVAL ticks; a pin set to five seconds wants
        // every fifth beat and nothing in between.
        Weave weave = new Weave();
        CausalNode toll = weave.add(CausalNode.of(0, Cause.TOLL, 10, 10).withParam(100));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.SIGH, 90, 10).withParam(5));
        weave.connect(toll.id(), effect.id());

        Fake world = new Fake();
        world.now = 500L;
        assertFalse(Weaver.resolve(weave, CausalEvent.of(Cause.TOLL), world).actions().isEmpty(),
                "500 is a multiple of 100");
        world.now = 520L;
        assertTrue(Weaver.resolve(weave, CausalEvent.of(Cause.TOLL), world).actions().isEmpty(),
                "520 is a beat, but not this pin beat");
    }

    // ---- what only works on a blow -----------------------------------------------------------

    @Test
    void anEffectThatReachesBackIsDroppedOnACauseWithNothingToReachBackInto() {
        Weave weave = new Weave();
        CausalNode slay = weave.add(CausalNode.of(0, Cause.SLAY, 10, 10));
        CausalNode erase = weave.add(CausalNode.of(0, Effect.ERASE, 90, 10));
        weave.connect(slay.id(), erase.id());

        Resolution answer = Weaver.resolve(weave, new CausalEvent(Cause.SLAY, 20.0F, 7, 0), new Fake());
        assertTrue(answer.actions().isEmpty(), "a death cannot be un-dealt");
        assertEquals(20.0F, answer.magnitude(), 1.0E-4F, "and the event is handed back untouched");
        assertEquals(0.0F, answer.paradox(), 1.0E-4F);
    }

    @Test
    void aCauseThatCarriesNothingStartsItsChainAtZero() {
        Weave weave = new Weave();
        CausalNode decree = weave.add(CausalNode.of(0, Cause.DECREE, 10, 10));
        CausalNode spend = weave.add(CausalNode.of(0, Effect.SPEND, 90, 10).withParam(30));
        weave.connect(decree.id(), spend.id());

        Fake world = new Fake();
        world.ledger = 12.0F;
        Resolution answer = Weaver.resolve(weave, CausalEvent.of(Cause.DECREE), world);
        assertEquals(12.0F, only(answer).magnitude(), 1.0E-4F,
                "so a Spend reads the ledger rather than a blow that was never there");
    }

    // ---- the tools ---------------------------------------------------------------------------

    @Test
    void twiceIsTwoOfEverythingRatherThanOneConsequenceCountedTwice() {
        // Two bills, two draws on the ledger, two landings. Pretending otherwise would be the one
        // place conservation quietly broke.
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode spend = weave.add(CausalNode.of(0, Effect.SPEND, 90, 10)
                .withParam(10).toggled(Modifier.TWICE));
        weave.connect(cause.id(), spend.id());

        Fake world = new Fake();
        world.ledger = 25.0F;
        Resolution answer = Weaver.resolve(weave, blow(5.0F), world);
        assertEquals(2, answer.actions().size());
        assertEquals(10.0F, answer.actions().get(0).magnitude(), 1.0E-4F);
        assertEquals(10.0F, answer.actions().get(1).magnitude(), 1.0E-4F);
        assertEquals(Modifier.TWICE_GAP, answer.actions().get(1).delay(), "a beat behind the first");
        assertEquals(2.0F * Weaver.MANA_PER_ACTION, answer.mana(), 1.0E-4F, "and billed twice");
    }

    @Test
    void aSecondDrawSeesTheLedgerTheFirstOneLeftBehind() {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode spend = weave.add(CausalNode.of(0, Effect.SPEND, 90, 10)
                .withParam(20).toggled(Modifier.TWICE));
        weave.connect(cause.id(), spend.id());

        Fake world = new Fake();
        world.ledger = 30.0F;
        Resolution answer = Weaver.resolve(weave, blow(5.0F), world);
        assertEquals(20.0F, answer.actions().get(0).magnitude(), 1.0E-4F);
        assertEquals(10.0F, answer.actions().get(1).magnitude(), 1.0E-4F, "there was only ten left");
    }

    @Test
    void askingForLessIsFreeAndAskingForMoreIsNot() {
        Weave lighter = new Weave();
        CausalNode cause = lighter.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode pass = lighter.add(CausalNode.of(0, Effect.PASS, 90, 10).toggled(Modifier.LESSER));
        lighter.connect(cause.id(), pass.id());
        Resolution less = Weaver.resolve(lighter, blow(20.0F), new Fake());
        assertEquals(12.0F, only(less).magnitude(), 1.0E-3F, "three fifths of it");
        assertEquals(0.96F, less.paradox(), 1.0E-3F,
                "and Lesser adds nothing to the rate, so a smaller move is a smaller bill");

        Weave heavier = new Weave();
        CausalNode c2 = heavier.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode p2 = heavier.add(CausalNode.of(0, Effect.PASS, 90, 10).toggled(Modifier.GREATER));
        heavier.connect(c2.id(), p2.id());
        Resolution more = Weaver.resolve(heavier, blow(20.0F), new Fake());
        assertEquals(30.0F, only(more).magnitude(), 1.0E-3F, "half again as much");
        assertEquals(6.9F, more.paradox(), 1.0E-3F, "made from nothing, so reality is charged for it");
    }

    @Test
    void aDelayedConsequenceIsTheSameConsequenceLater() {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode pass = weave.add(CausalNode.of(0, Effect.PASS, 90, 10).toggled(Modifier.AFTER));
        weave.connect(cause.id(), pass.id());
        Resolution answer = Weaver.resolve(weave, blow(20.0F), new Fake());
        assertEquals(Modifier.DELAY_TICKS, only(answer).delay());
        assertEquals(20.0F, only(answer).magnitude(), 1.0E-4F, "and it carries what it always carried");
    }

    // ---- what paradox does -------------------------------------------------------------------

    @Test
    void strainedRealityChargesTwiceTheManaAndStillWorks() {
        Fake settled = new Fake();
        Fake strained = new Fake();
        strained.paradox = Paradox.STRAINED_AT;
        Weave weave = rule(Cause.HURT, Effect.STORE, 50);
        assertEquals(Weaver.MANA_PER_ACTION, Weaver.resolve(weave, blow(20.0F), settled).mana(), 1.0E-4F);
        assertEquals(Weaver.MANA_PER_ACTION * Paradox.STRAINED_MANA_SCALE,
                Weaver.resolve(weave, blow(20.0F), strained).mana(), 1.0E-4F);
    }

    @Test
    void frayingRealityHalvesEverythingAndWillNotLetYouEraseAtAll() {
        Fake world = new Fake();
        world.paradox = Paradox.FRAYING_AT;
        Resolution stored = Weaver.resolve(rule(Cause.HURT, Effect.STORE, 100), blow(20.0F), world);
        assertEquals(10.0F, only(stored).magnitude(), 1.0E-4F, "half of what it asked for");

        Resolution erased = Weaver.resolve(rule(Cause.HURT, Effect.ERASE, 1), blow(20.0F), world);
        assertTrue(erased.actions().isEmpty(), "you cannot claim a thing never happened while reality frays");
        assertEquals(20.0F, erased.magnitude(), 1.0E-4F);
        assertTrue(erased.clipped(), "and the wielder is told the chain was cut short");
    }

    @Test
    void frayingRealityCutsLongChainsAndLeavesShortOnesAlone() {
        // The rung is meant to take the elaborate machinery away first. A chain of four pins is
        // deeper than fraying allows; a chain of two is not.
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode a = weave.add(CausalNode.of(0, Condition.IS_MELEE, 60, 10));
        CausalNode b = weave.add(CausalNode.of(0, Condition.ABOVE, 110, 10).withParam(1));
        CausalNode deep = weave.add(CausalNode.of(0, Effect.STORE, 160, 10).withParam(50));
        // Four pins: a cause, two questions and a consequence, which is one question more than
        // fraying reality will carry a signal through.
        CausalNode shallow = weave.add(CausalNode.of(0, Effect.SIGH, 60, 60).withParam(4));
        weave.connect(cause.id(), a.id());
        weave.connect(a.id(), b.id());
        weave.connect(b.id(), deep.id());
        weave.connect(cause.id(), shallow.id());

        Fake world = new Fake();
        world.paradox = Paradox.FRAYING_AT;
        Resolution answer = Weaver.resolve(weave, blow(20.0F), world);
        assertEquals(1, answer.actions().size(), "the short chain still fires");
        assertSame(Effect.SIGH, answer.actions().get(0).effect());
        assertTrue(answer.clipped(), "and the long one was cut");
    }

    @Test
    void aWalkThatRunsOutOfManaStopsAndSaysSo() {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode first = weave.add(CausalNode.of(0, Effect.SIGH, 90, 10).withParam(2));
        CausalNode second = weave.add(CausalNode.of(0, Effect.SIGH, 90, 50).withParam(2));
        weave.connect(cause.id(), first.id());
        weave.connect(cause.id(), second.id());

        Fake world = new Fake();
        world.mana = Weaver.MANA_PER_ACTION;
        Resolution answer = Weaver.resolve(weave, blow(20.0F), world);
        assertEquals(1, answer.actions().size(), "one consequence is all the pool would pay for");
        assertTrue(answer.starved());
    }

    @Test
    void aimingAtTheMarkedCostsMoreManaThanAimingAtYourself() {
        Weave near = new Weave();
        CausalNode c1 = near.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode e1 = near.add(CausalNode.of(0, Effect.KINDLE, 90, 10).withScope(Scope.OTHER));
        near.connect(c1.id(), e1.id());

        Weave far = new Weave();
        CausalNode c2 = far.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode e2 = far.add(CausalNode.of(0, Effect.KINDLE, 90, 10).withScope(Scope.MARKED));
        far.connect(c2.id(), e2.id());

        assertEquals(Weaver.MANA_PER_ACTION, Weaver.resolve(near, blow(9.0F), new Fake()).mana(), 1.0E-4F);
        assertEquals(Weaver.MANA_PER_ACTION + Weaver.MANA_ANCHORED_EXTRA,
                Weaver.resolve(far, blow(9.0F), new Fake()).mana(), 1.0E-4F,
                "reaching into somebody else costs substantially more, which is the whole progression");
    }

    @Test
    void anEmptyBoardIsHandedBackTheBlowItWasGiven() {
        Resolution answer = Weaver.resolve(new Weave(), blow(17.0F), new Fake());
        assertEquals(17.0F, answer.magnitude(), 1.0E-4F);
        assertTrue(answer.idle());
    }
}
