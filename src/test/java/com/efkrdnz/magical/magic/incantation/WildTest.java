package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Wild verses pick with the world's random: from the known catalogue, from the known verses
 * of a type, from the unread and read piles (spending a use), or from the other incantations.
 * With a fixed world every pick is exact.
 */
class WildTest {

    private static final Verse NEEDLE = VerseContent.get(ProjectileVerses.NEEDLE);
    private static final Verse WEIGHT = VerseContent.get(ModifierVerses.WEIGHT);

    @Test
    void wildVersePicksAKnownVerse() {
        FixedWorld world = new FixedWorld().roll(0, 1);
        world.all = List.of(NEEDLE, WEIGHT);
        world.known = Set.of(WEIGHT.id());
        RecitePlan plan = press(session("wild_verse", "needle"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "the first roll was unknown, the second was Weight");
        assertEquals(7, plan.manaSpent());
    }

    @Test
    void wildBoltPicksOnlyABody() {
        FixedWorld world = new FixedWorld().roll(1, 0);
        world.all = List.of(NEEDLE, WEIGHT);
        RecitePlan plan = press(session("wild_bolt"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
    }

    @Test
    void wildMarkPicksOnlyAModifier() {
        FixedWorld world = new FixedWorld().roll(0, 1);
        world.all = List.of(NEEDLE, WEIGHT);
        RecitePlan plan = press(session("wild_mark", "needle"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
    }

    @Test
    void blindDrawSpendsAUse() {
        Incantation incantation = tape(1, "blind_draw", "ember");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 1, PLENTY, new FixedWorld().roll(0));
        assertEquals(List.of("ember"), bodies(plan.root()));
        session.writeBack(incantation);
        assertEquals(14, incantation.entries().get(1).usesRemaining());
        assertEquals(6, plan.manaSpent());
    }

    @Test
    void blindTrioDrawsThreeSeparatePicks() {
        RecitePlan plan = press(session("blind_trio", "needle", "ember", "shard"), 1, PLENTY, new FixedWorld().roll(0, 1, 2));
        assertEquals(List.of("needle", "ember", "shard"), bodies(plan.root()),
                "three independent rolls over the same pile, each landing on the card it named");
    }

    @Test
    void blindDrawWalksPastASpentCard() {
        Incantation incantation = tape(1, "blind_draw", "ember", "shard");
        incantation.setUses(1, 0);
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 1, PLENTY, new FixedWorld().roll(0));
        assertEquals(List.of("shard"), bodies(plan.root()), "the roll landed on a spent Ember, so the walk went on to the next card");
        assertEquals(0, incantation.entries().get(1).usesRemaining(), "and the spent card was not billed again");
    }

    @Test
    void wildRecallReadsTheOtherIncantations() {
        FixedWorld world = new FixedWorld().roll(0);
        world.others = List.of(WEIGHT);
        RecitePlan plan = press(session("wild_recall", "needle"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
    }

    @Test
    void wildRecallWithNothingElseJustDraws() {
        RecitePlan plan = press(session("wild_recall", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
    }
}
