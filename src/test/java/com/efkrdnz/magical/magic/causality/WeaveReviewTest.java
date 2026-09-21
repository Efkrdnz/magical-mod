package com.efkrdnz.magical.magic.causality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every way a board can be drawn and still say nothing.
 *
 * <p>All of these are silent at runtime - the chain simply never fires - so a wielder who cannot be
 * told the difference between a board that is waiting and a board that is broken will stop trusting
 * the Authority. That makes the review as load-bearing as the engine, and it is worth the same kind
 * of test.
 */
class WeaveReviewTest {

    private static boolean has(List<WeaveReview.Issue> issues, WeaveReview.Kind kind) {
        return issues.stream().anyMatch(issue -> issue.kind() == kind);
    }

    @Test
    void aSoundBoardHasNothingToSayAboutItself() {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode melee = weave.add(CausalNode.of(0, Condition.IS_MELEE, 60, 10));
        CausalNode back = weave.add(CausalNode.of(0, Effect.RETURN, 110, 10));
        weave.connect(cause.id(), melee.id());
        weave.connect(melee.id(), back.id());
        assertTrue(WeaveReview.issues(weave, false).isEmpty(), WeaveReview.issues(weave, false).toString());
        assertTrue(WeaveReview.sound(weave, false));
        assertEquals(1, WeaveReview.live(weave).size());
    }

    @Test
    void aPinWithNothingFeedingItIsCalledOut() {
        Weave weave = new Weave();
        weave.add(CausalNode.of(0, Effect.SPEND, 10, 10));
        assertTrue(has(WeaveReview.issues(weave, true), WeaveReview.Kind.ORPHANED));
    }

    @Test
    void aChainThatTrailsOffIsCalledOut() {
        Weave weave = new Weave();
        weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        List<WeaveReview.Issue> issues = WeaveReview.issues(weave, true);
        assertTrue(has(issues, WeaveReview.Kind.DANGLING));
        assertTrue(WeaveReview.live(weave).isEmpty(), "and nothing on it would fire");
    }

    @Test
    void aQuestionAboutABlowIsCalledOutOnACauseThatCarriesNoBlow() {
        // Asking whether a heartbeat was melee is not false, it is meaningless, and a wielder who is
        // not told will spend an afternoon wondering why a chain they were proud of never runs.
        Weave weave = new Weave();
        CausalNode toll = weave.add(CausalNode.of(0, Cause.TOLL, 10, 10));
        CausalNode melee = weave.add(CausalNode.of(0, Condition.IS_MELEE, 60, 10));
        CausalNode sigh = weave.add(CausalNode.of(0, Effect.SIGH, 110, 10));
        weave.connect(toll.id(), melee.id());
        weave.connect(melee.id(), sigh.id());
        assertTrue(has(WeaveReview.issues(weave, true), WeaveReview.Kind.NEVER_TRUE));
    }

    @Test
    void anEffectThatReachesBackIsCalledOutOnACauseAlreadyOver() {
        Weave weave = new Weave();
        CausalNode slay = weave.add(CausalNode.of(0, Cause.SLAY, 10, 10));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 90, 10));
        weave.connect(slay.id(), store.id());
        assertTrue(has(WeaveReview.issues(weave, true), WeaveReview.Kind.NOTHING_TO_REWRITE));
    }

    @Test
    void aBoardWantingAMarkSaysSoOnlyWhileThereIsNoneOutThere() {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.MARKED_HURT, 10, 10));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 90, 10));
        weave.connect(cause.id(), store.id());
        assertTrue(has(WeaveReview.issues(weave, false), WeaveReview.Kind.WANTS_MARK), "idle");
        assertFalse(has(WeaveReview.issues(weave, true), WeaveReview.Kind.WANTS_MARK), "and not idle");
    }

    @Test
    void aChainDeeperThanASignalCanTravelIsCalledOut() {
        Weave weave = new Weave();
        CausalNode previous = weave.add(CausalNode.of(0, Cause.DECREE, 0, 0));
        // Questions are a point each, so the budget reaches further than the depth ceiling does.
        for (int i = 0; i < Weave.MAX_DEPTH; i++) {
            CausalNode next = weave.add(CausalNode.of(0, Condition.CROUCHED, 10 * i, 20));
            if (next == null) {
                break;
            }
            weave.connect(previous.id(), next.id());
            previous = next;
        }
        assertTrue(has(WeaveReview.issues(weave, true), WeaveReview.Kind.TOO_DEEP),
                "everything past the ceiling is drawn and dead, and the board should say so");
    }

    @Test
    void aPinOnTwoChainsEarnsOneLineAndNotTwo() {
        Weave weave = new Weave();
        CausalNode first = weave.add(CausalNode.of(0, Cause.TOLL, 10, 10));
        CausalNode second = weave.add(CausalNode.of(0, Cause.DECREE, 10, 60));
        CausalNode melee = weave.add(CausalNode.of(0, Condition.IS_MELEE, 80, 30));
        CausalNode sigh = weave.add(CausalNode.of(0, Effect.SIGH, 150, 30));
        weave.connect(first.id(), melee.id());
        weave.connect(second.id(), melee.id());
        weave.connect(melee.id(), sigh.id());
        long said = WeaveReview.issues(weave, true).stream()
                .filter(issue -> issue.kind() == WeaveReview.Kind.NEVER_TRUE).count();
        assertEquals(1, said);
    }

    @Test
    void everyPathFromEveryCauseIsAChain() {
        Weave weave = new Weave();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode one = weave.add(CausalNode.of(0, Effect.STORE, 90, 10));
        CausalNode two = weave.add(CausalNode.of(0, Effect.SIGH, 90, 60));
        weave.connect(cause.id(), one.id());
        weave.connect(cause.id(), two.id());
        List<WeaveReview.Chain> chains = WeaveReview.chains(weave);
        assertEquals(2, chains.size(), "a branch is two chains, not one chain with a fork in it");
        assertEquals(cause.id(), chains.get(0).cause().id());
        assertEquals(one.id(), chains.get(0).last().id(), "and they are in the order the string was drawn");
        assertEquals(two.id(), chains.get(1).last().id());
    }
}
