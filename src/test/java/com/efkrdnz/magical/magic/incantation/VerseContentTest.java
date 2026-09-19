package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is data, and its declared numbers are what a tooltip will show, so they are held to
 * what the machine does. Every verse in the catalogue is run at the top of a tape of needles, and
 * the cards it drew, the beat it left at the root and the rest it added must be exactly what it
 * declared. The controls are in the sweep too, each on the shortest tape that makes it do its work:
 * a Refrain wants one card to copy, an Impose wants a target and one verse left over to be worth a
 * payload, a Clause and its two markers want a card to skip and a card to draw, and the world every
 * Clause is asked is one where every Clause fails.
 *
 * <p>{@link #EXEMPT} is the whole of what the formula cannot reach, with the reason written beside
 * each one: a verse that copies another verse enacts that verse's numbers, not its own, and a verse
 * that rolls for what to run enacts whatever the roll landed on. Nothing else is excused, and no
 * verse's {@code Declared} may be moved to make this pass.
 */
class VerseContentTest {

    private static final String COPIES = "a copy enacts the copied verse's beat, which the tooltip cannot count";
    private static final String ROLLS = "the world picks what runs, so the numbers enacted are the pick's";

    private static final Map<ResourceLocation, String> EXEMPT = Map.ofEntries(
            Map.entry(VerseIds.of("recall_first"), COPIES + ": the first card of read, hand or unread"),
            Map.entry(VerseIds.of("recall_last"), COPIES + ": the last card of unread or hand"),
            Map.entry(VerseIds.of("recall_pair"), COPIES + ": two cards, both of them"),
            Map.entry(VerseIds.of("recall_all"), COPIES + ": every card in every pile"),
            Map.entry(VerseIds.of("recall_modifiers"), COPIES + ", and the sweep puts beat, rest and mana back afterwards"),
            Map.entry(VerseIds.of("recall_projectiles"), COPIES + ", and the sweep puts beat, rest and mana back afterwards"),
            Map.entry(VerseIds.of("recall_statics"), COPIES + ", and the sweep puts beat, rest and mana back afterwards"),
            Map.entry(VerseIds.of("reprise"), COPIES + ": every card in hand but itself"),
            Map.entry(VerseIds.of("wild_verse"), ROLLS + ", from every known verse"),
            Map.entry(VerseIds.of("wild_bolt"), ROLLS + ", from the known bodies"),
            Map.entry(VerseIds.of("wild_mark"), ROLLS + ", from the known modifiers"),
            Map.entry(VerseIds.of("wild_recall"), ROLLS + ", from the other three incantations"),
            Map.entry(VerseIds.of("blind_draw"), ROLLS + ", from unread laid against read"),
            Map.entry(VerseIds.of("blind_trio"), ROLLS + ", three times over"));

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
        int swept = 0;
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            if (EXEMPT.containsKey(verse.id()) || verse.type() == VerseType.MATERIAL || verse.type() == VerseType.PASSIVE) {
                continue;
            }
            swept++;
            int draw = verse.declared().draw() == Verse.Declared.ALL ? 4 : verse.declared().draw();
            Incantation incantation = tape(1, tapeFor(verse, draw).toArray(new String[0]));
            ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
            RecitePlan plan = press(session, 1, PLENTY, everyClauseFails());
            List<ReciteEvent> played = plan.events().stream().filter(e -> e.kind() == ReciteEvent.Kind.PLAYED).toList();
            assertEquals(draw, played.size() - 1, verse.id() + " drew");
            long rootNeedles = played.subList(1, played.size()).stream().filter(e -> e.depth() == 0).count();
            assertEquals(verse.declared().beat() + rootNeedles, plan.beatTicks(), verse.id() + " beat");
            int rest = plan.rests() ? plan.restTicks() : session.restCarry();
            assertEquals(verse.declared().rest(), rest, verse.id() + " rest");
        }
        assertEquals(VerseContent.CATALOGUE.size() - EXEMPT.size(), swept, "every verse the exemptions do not name was measured");
        assertEquals(13, VerseContent.CATALOGUE.ofType(VerseType.CONTROL).stream().filter(v -> !EXEMPT.containsKey(v.id())).count(),
                "the four Refrains, the three Imposes, the four Clauses and the two markers");
    }

    @Test
    void everyControlIsSweptOrNamed() {
        for (Verse verse : VerseContent.CATALOGUE.ofType(VerseType.CONTROL)) {
            assertTrue(EXEMPT.containsKey(verse.id()) || tapeFor(verse, verse.declared().draw()).size() > 1,
                    verse.id() + " is neither swept on a tape of its own nor named as an exemption");
        }
        for (ResourceLocation id : EXEMPT.keySet()) {
            assertNotNull(VerseContent.get(id), id + " is exempted and does not exist");
            assertFalse(EXEMPT.get(id).isBlank(), id + " is exempted with no reason");
        }
    }

    /**
     * The default is the verse and as many needles as it says it draws. Three families need more:
     * a Refrain needs a card under it to copy, an Impose needs a target <em>and</em> a verse left in
     * the pile afterwards or it runs the target quietly instead of making a carrier, and a Clause or
     * one of its markers needs a card to skip and a card to draw.
     */
    private static List<String> tapeFor(Verse verse, int draw) {
        List<String> paths = new ArrayList<>();
        paths.add(verse.path());
        int needles = draw;
        if (verse.path().startsWith("refrain_")) {
            needles = 1;
        } else if (ControlVerses.isImpose(verse)) {
            needles = 2;
        } else if (ControlVerses.isClause(verse) || verse.id().equals(ControlVerses.OTHERWISE)
                || verse.id().equals(ControlVerses.END_CLAUSE)) {
            needles = 2;
        }
        for (int i = 0; i < needles; i++) {
            paths.add("needle");
        }
        return paths;
    }

    /** No crowd, no barrage, whole health, and the shared toggle standing on skip. */
    private static FixedWorld everyClauseFails() {
        FixedWorld world = new FixedWorld();
        world.everyOtherSkip = true;
        return world;
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
