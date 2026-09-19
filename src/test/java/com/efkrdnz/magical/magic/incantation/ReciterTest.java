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
 * The draw. A modifier reaches every body drawn after it in the same shot and none before, the
 * breath is the root budget and the verses decide the rest, a verse you cannot afford is skipped
 * rather than failing the press, uses follow the body, and what a body does on its own account is
 * its prototype's and never the shot's.
 */
class ReciterTest {

    @Test
    void aModifierReachesEveryBodyAfterIt() {
        ReciteSession session = session("weight", "couplet", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle", "needle"), bodies(plan.root()));
        for (ProjectilePlan body : plan.bodies()) {
            assertEquals(2.5D, body.stamped().damageAdd(), 1e-9);
        }
    }

    @Test
    void aModifierAfterABodyDoesNotReachBack() {
        ReciteSession session = session("needle", "weight", "needle");
        RecitePlan plan = press(session, 3, PLENTY, new FixedWorld());
        assertEquals(List.of("needle", "needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(2.5D, plan.bodies().get(1).stamped().damageAdd(), 1e-9);
        assertTrue(plan.rests(), "the root draw ran dry, so the press rests");
    }

    @Test
    void aBodysOwnHitEffectIsItsPrototypesNotTheShots() {
        ReciteSession session = session("couplet", "ember", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("ember", "needle"), bodies(plan.root()));
        assertEquals(HitEffect.BURN, VersePrototypes.EMBER.hit(), "the Ember body is on fire of itself");
        assertEquals(HitEffect.SHOCK, VersePrototypes.ARC.hit());
        assertTrue(plan.bodies().get(0).stamped().hitEffects().isEmpty(), "and does not write the burn into the shot");
        assertTrue(plan.bodies().get(1).stamped().hitEffects().isEmpty(), "so the needle behind it is not set alight");
    }

    @Test
    void drawCountsByType() {
        assertEquals(1, press(session("needle", "needle")).bodies().size());
        assertEquals(1, press(session("weight", "needle", "needle")).bodies().size());
        assertEquals(2, press(session("couplet", "needle", "needle", "needle")).bodies().size());
    }

    @Test
    void aTapeOfThreePresses() {
        ReciteSession session = session("weight", "couplet", "needle", "needle", "haste", "needle", "detonation");

        RecitePlan first = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("needle", "needle"), bodies(first.root()));
        assertEquals(2.5D, first.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(5.0D, first.bodies().get(0).stamped().critChance(), 1e-9);
        assertEquals(10.0D, first.bodies().get(1).stamped().critChance(), 1e-9);
        assertEquals(4, first.beatTicks());
        assertEquals(11, first.manaSpent());
        assertFalse(first.rests());
        assertEquals(List.of("haste", "needle", "detonation"), unread(session));

        RecitePlan second = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("needle"), bodies(second.root()));
        assertEquals(2.5D, second.bodies().get(0).stamped().speedMultiplier(), 1e-9);
        assertEquals(0.0D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(1, second.beatTicks());
        assertEquals(6, second.manaSpent());
        assertEquals(List.of("detonation"), unread(session));

        RecitePlan third = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("detonation"), bodies(third.root()));
        assertEquals(VersePrototypes.BURST, third.bodies().get(0).prototype());
        assertEquals(20, third.manaSpent());
        assertTrue(third.rests());
        assertEquals(0, third.restTicks());
        assertEquals(1, count(third, ReciteEvent.Kind.REST));
        assertEquals(List.of("weight", "couplet", "needle", "needle", "haste", "needle", "detonation"), unread(session));

        RecitePlan fourth = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("needle", "needle"), bodies(fourth.root()));
    }

    @Test
    void aVerseYouCannotAffordIsSkipped() {
        ReciteSession session = session("detonation", "needle");
        RecitePlan plan = press(session, 1, 10, new FixedWorld());
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(4, plan.manaSpent());
        assertEquals(6, plan.manaLeft());
        assertEquals(1, count(plan, ReciteEvent.Kind.FALTER_MANA));
        assertEquals(List.of("detonation", "needle"), unread(session), "both went to the read pile and the rest brought them back in order");
    }

    @Test
    void aSpentVerseIsSkipped() {
        Incantation incantation = tape(1, "ember", "needle");
        incantation.setUses(0, 0);
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(1, count(plan, ReciteEvent.Kind.FALTER_SPENT));
        assertEquals(4, plan.manaSpent(), "a spent verse is not billed");
    }

    @Test
    void chargesFollowTheBody() {
        Incantation alone = tape(1, "undying");
        ReciteSession session = ReciteSession.of(alone, VerseContent.CATALOGUE);
        press(session);
        session.writeBack(alone);
        assertEquals(3, alone.entries().get(0).usesRemaining(), "a modifier with nothing after it keeps its use");

        Incantation withBody = tape(1, "undying", "needle");
        session = ReciteSession.of(withBody, VerseContent.CATALOGUE);
        press(session);
        session.writeBack(withBody);
        assertEquals(2, withBody.entries().get(0).usesRemaining());

        Incantation limitedBody = tape(1, "ember");
        session = ReciteSession.of(limitedBody, VerseContent.CATALOGUE);
        press(session);
        session.writeBack(limitedBody);
        assertEquals(14, limitedBody.entries().get(0).usesRemaining());
    }

    @Test
    void costScaleMovesThePrice() {
        ReciteSession session = session("needle");
        RecitePlan plan = Reciter.recite(session, 1, PLENTY, 1.5D, new FixedWorld());
        assertEquals(6, plan.manaSpent());
    }
}
