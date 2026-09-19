package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Clause branches by discarding. Failing: from the top through the first Otherwise, else through
 * the End Clause, else just the next verse. Passing with an Otherwise: from the Otherwise through the
 * End Clause; with no End Clause only the Otherwise itself; with another Clause before any End,
 * from the Otherwise to the end of the pile. The scan stops at the next Clause. Then draw one.
 *
 * <p>The two counting Clauses ask the world about a radius, not about the whole level, so a crowd
 * standing further out than the sixteen blocks they name does not answer them.
 */
class ClauseTest {

    private static FixedWorld atHealth(double fraction) {
        FixedWorld world = new FixedWorld();
        world.health = fraction;
        return world;
    }

    @Test
    void aFailingClauseDiscardsThroughTheOtherwise() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle", "end_clause");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertEquals(List.of("end_clause"), unread(session));
    }

    @Test
    void aPassingClauseDiscardsFromTheOtherwiseThroughTheEnd() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle", "end_clause");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(0.2D));
        assertEquals(List.of("balm_dart"), bodies(plan.root()));
        assertEquals(3, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests());
    }

    @Test
    void aPassingClauseWithoutAnEndDiscardsOnlyTheOtherwise() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(0.2D));
        assertEquals(List.of("balm_dart"), bodies(plan.root()));
        assertEquals(1, count(plan, ReciteEvent.Kind.DISCARDED));
        assertEquals(List.of("needle"), unread(session));
    }

    @Test
    void aFailingClauseWithNoMarkSkipsOne() {
        ReciteSession session = session("clause_wounded", "balm_dart", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(1, count(plan, ReciteEvent.Kind.DISCARDED));
    }

    @Test
    void aFailingClauseWithAnEndButNoOtherwiseDiscardsThroughTheEnd() {
        ReciteSession session = session("clause_wounded", "balm_dart", "end_clause", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
    }

    @Test
    void theScanStopsAtTheNextClause() {
        ReciteSession session = session("clause_wounded", "balm_dart", "clause_outnumbered", "otherwise", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED), "the first Clause skipped one, the second skipped through its Otherwise");
        assertEquals(3, count(plan, ReciteEvent.Kind.PLAYED));
    }

    @Test
    void aPassingClauseWithALaterClauseDiscardsToTheEndOfThePile() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle", "clause_outnumbered", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(0.2D));
        assertEquals(List.of("balm_dart"), bodies(plan.root()));
        assertEquals(4, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests());
    }

    @Test
    void everyOtherAlternatesOnTheSharedToggle() {
        FixedWorld world = new FixedWorld();
        ReciteSession session = session("clause_every_other", "needle", "ember");
        assertEquals(List.of("needle"), bodies(press(session, 1, PLENTY, world).root()));
        assertEquals(List.of("ember"), bodies(press(session, 1, PLENTY, world).root()));
        assertEquals(List.of("ember"), bodies(press(session, 1, PLENTY, world).root()), "the second reading skips");
        assertTrue(world.everyOtherSkip == false, "flipped twice");
    }

    @Test
    void outnumberedAndCrowdedReadTheWorld() {
        FixedWorld crowd = new FixedWorld();
        crowd.enemies = 6;
        assertEquals(List.of("needle"), bodies(press(session("clause_outnumbered", "needle", "otherwise", "ember"), 1, PLENTY, crowd).root()));
        crowd.enemies = 5;
        assertEquals(List.of("ember"), bodies(press(session("clause_outnumbered", "needle", "otherwise", "ember"), 1, PLENTY, crowd).root()));
        FixedWorld sky = new FixedWorld();
        sky.projectiles = 12;
        assertEquals(List.of("needle"), bodies(press(session("clause_crowded", "needle", "otherwise", "ember"), 1, PLENTY, sky).root()));
        sky.projectiles = 11;
        assertEquals(List.of("ember"), bodies(press(session("clause_crowded", "needle", "otherwise", "ember"), 1, PLENTY, sky).root()));
    }

    @Test
    void outnumberedAsksAboutSixteenBlocksAndNoFurther() {
        FixedWorld far = new FixedWorld();
        far.enemies = 6;
        far.enemyRange = 17.0D;
        assertEquals(List.of("ember"), bodies(press(session("clause_outnumbered", "needle", "otherwise", "ember"), 1, PLENTY, far).root()),
                "six hostiles standing seventeen blocks out are not six within sixteen");
        FixedWorld near = new FixedWorld();
        near.enemies = 6;
        near.enemyRange = 16.0D;
        assertEquals(List.of("needle"), bodies(press(session("clause_outnumbered", "needle", "otherwise", "ember"), 1, PLENTY, near).root()),
                "the same six at sixteen are inside the radius the Clause names");
    }

    @Test
    void crowdedSkyAsksAboutSixteenBlocksAndNoFurther() {
        FixedWorld far = new FixedWorld();
        far.projectiles = 12;
        far.projectileRange = 17.0D;
        assertEquals(List.of("ember"), bodies(press(session("clause_crowded", "needle", "otherwise", "ember"), 1, PLENTY, far).root()),
                "twelve bodies seventeen blocks out are not twelve within sixteen");
        FixedWorld near = new FixedWorld();
        near.projectiles = 12;
        near.projectileRange = 16.0D;
        assertEquals(List.of("needle"), bodies(press(session("clause_crowded", "needle", "otherwise", "ember"), 1, PLENTY, near).root()),
                "the same twelve at sixteen are inside the radius the Clause names");
    }
}
