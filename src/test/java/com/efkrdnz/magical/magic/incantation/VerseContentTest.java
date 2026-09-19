package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is data, and its declared numbers are what a tooltip will show, so they are held
 * to what the machine does: every non-recursive projectile, static, modifier, multicast and utility
 * verse is run at the top of a tape of needles, and the cards it drew, the beat it added at the
 * root and the rest it added must be exactly what it declared. Control verses are exempt because
 * their numbers depend on the piles; the rule tests pin those.
 */
class VerseContentTest {

    @Test
    void theCatalogueIsFull() {
        VerseCatalogue c = VerseContent.CATALOGUE;
        assertEquals(15, c.ofType(VerseType.PROJECTILE).size());
        assertEquals(7, c.ofType(VerseType.STATIC).size());
        assertEquals(35, c.ofType(VerseType.MODIFIER).size());
        assertEquals(13, c.ofType(VerseType.MULTICAST).size());
        assertEquals(5, c.ofType(VerseType.UTILITY).size());
        assertEquals(25, c.ofType(VerseType.CONTROL).size());
        assertEquals(0, c.ofType(VerseType.MATERIAL).size());
        assertEquals(0, c.ofType(VerseType.PASSIVE).size());
        assertEquals(100, c.size());
    }

    @Test
    void everyPrototypeIsInTheTable() {
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            if (verse.hasPrototype()) {
                assertSame(verse.prototype(), VersePrototypes.byId(verse.prototype().id()), verse.id() + " names a body the table does not hold");
            }
        }
    }

    @Test
    void declaredNumbersAreEnacted() {
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            if (verse.recursive() || verse.type() == VerseType.CONTROL || verse.type() == VerseType.MATERIAL || verse.type() == VerseType.PASSIVE) {
                continue;
            }
            int draw = verse.declared().draw() == Verse.Declared.ALL ? 4 : verse.declared().draw();
            List<String> paths = new ArrayList<>();
            paths.add(verse.path());
            for (int i = 0; i < draw; i++) {
                paths.add("needle");
            }
            Incantation incantation = tape(1, paths.toArray(new String[0]));
            ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
            RecitePlan plan = press(session, 1, PLENTY, new FixedWorld());
            List<ReciteEvent> played = plan.events().stream().filter(e -> e.kind() == ReciteEvent.Kind.PLAYED).toList();
            assertEquals(draw, played.size() - 1, verse.id() + " drew");
            long rootNeedles = played.subList(1, played.size()).stream().filter(e -> e.depth() == 0).count();
            assertEquals(verse.declared().beat() + rootNeedles, plan.beatTicks(), verse.id() + " beat");
            int rest = plan.rests() ? plan.restTicks() : session.restCarry();
            assertEquals(verse.declared().rest(), rest, verse.id() + " rest");
        }
    }

    @Test
    void theLimitedVersesAreTheOnesTheDesignLimits() {
        assertEquals(15, VerseContent.get(VerseIds.of("ember")).maxUses());
        assertEquals(20, VerseContent.get(VerseIds.of("balm_dart")).maxUses());
        assertEquals(3, VerseContent.get(VerseIds.of("void_pit")).maxUses());
        assertEquals(15, VerseContent.get(VerseIds.of("rime_ring")).maxUses());
        assertEquals(15, VerseContent.get(VerseIds.of("storm_ring")).maxUses());
        assertEquals(6, VerseContent.get(VerseIds.of("balm_ring")).maxUses());
        assertEquals(3, VerseContent.get(VerseIds.of("undying")).maxUses());
        assertEquals(10, VerseContent.get(VerseIds.of("epic")).maxUses());
        assertEquals(5, VerseContent.get(VerseIds.of("refrain_10")).maxUses());
        long limited = VerseContent.CATALOGUE.all().stream().filter(v -> !v.unlimited()).count();
        assertEquals(9, limited);
    }

    @Test
    void bloodTollPaysInHealthAndRefundsMana() {
        FixedWorld world = new FixedWorld();
        RecitePlan plan = press(ReciteSession.of(tape(1, "blood_toll", "needle"), VerseContent.CATALOGUE), 1, 50, world);
        assertEquals(List.of(4.0D), world.healthPaid);
        assertEquals(-26, plan.manaSpent(), "a 30 refund less the needle");
        assertEquals(76, plan.manaLeft());
    }
}
