package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The verses that touch the piles or call other verses: the Recall family (the Greek letters),
 * Reprise, the Wild and Blind verses, and, from their own tasks, the Refrains, the Imposes and the
 * Clauses. Each is a line-for-line port; the Lua card is named in the comment above it.
 */
public final class ControlVerses {

    public static final ResourceLocation REPRISE = VerseIds.of("reprise");

    private ControlVerses() {
    }

    static Verse control(String path, int mana, int uses, Declared declared, VerseAction action) {
        return Verse.of(path, VerseType.CONTROL, mana, uses, null, 1, declared, action);
    }

    static void register(VerseCatalogue c) {
        registerRecalls(c);
        registerWild(c);
    }

    // ---- the Recall family ---------------------------------------------------------------------

    private static void registerRecalls(VerseCatalogue c) {
        // ALPHA: the first card of the read pile, else of the hand, else of the unread pile.
        c.register(control("recall_first", 12, Verse.UNLIMITED, Declared.of(0, 5, 0), (r, recursion, it) -> {
            r.state().addBeat(5);
            VerseCard data = null;
            if (!r.discard().isEmpty()) {
                data = r.discard().get(0);
            } else if (!r.hand().isEmpty()) {
                data = r.hand().get(0);
            } else if (!r.deck().isEmpty()) {
                data = r.deck().get(0);
            }
            copy(r, data, recursion);
            return VerseAction.NONE;
        }).asRecursive());
        // GAMMA: the last card of the unread pile, else of the hand.
        c.register(control("recall_last", 12, Verse.UNLIMITED, Declared.of(0, 5, 0), (r, recursion, it) -> {
            r.state().addBeat(5);
            VerseCard data = null;
            if (!r.deck().isEmpty()) {
                data = r.deck().get(r.deck().size() - 1);
            } else if (!r.hand().isEmpty()) {
                data = r.hand().get(r.hand().size() - 1);
            }
            copy(r, data, recursion);
            return VerseAction.NONE;
        }).asRecursive());
        // TAU: deck[1] and deck[2], both looked up before either runs.
        c.register(control("recall_pair", 20, Verse.UNLIMITED, Declared.of(0, 12, 0), (r, recursion, it) -> {
            r.state().addBeat(12);
            List<VerseCard> deck = r.deck();
            VerseCard data1 = deck.isEmpty() ? null : deck.get(0);
            VerseCard data2 = deck.size() > 1 ? deck.get(1) : null;
            int rec1 = data1 == null ? recursion : r.checkRecursion(data1.verse(), recursion);
            int rec2 = data2 == null ? recursion : r.checkRecursion(data2.verse(), recursion);
            if (data1 != null && rec1 > -1) {
                r.call(data1.verse(), rec1, 1);
            }
            if (data2 != null && rec2 > -1) {
                r.call(data2.verse(), rec2, 1);
            }
            return VerseAction.NONE;
        }).asRecursive());
        // OMEGA: every card of read, the non-recursive of hand, every card of unread; quiet; never RESET.
        c.register(control("recall_all", 60, Verse.UNLIMITED, Declared.of(0, 17, 0), (r, recursion, it) -> {
            r.state().addBeat(17);
            List<VerseCard> discard = r.discard();
            for (int i = 0; i < discard.size(); i++) {
                VerseCard data = discard.get(i);
                int rec = r.checkRecursion(data.verse(), recursion);
                if (rec > -1 && !data.id().equals(UtilityVerses.FRESH_PAGE)) {
                    copyQuiet(r, data.verse(), rec);
                }
            }
            List<VerseCard> hand = r.hand();
            for (int i = 0; i < hand.size(); i++) {
                VerseCard data = hand.get(i);
                int rec = r.checkRecursion(data.verse(), recursion);
                if (!data.verse().recursive()) {
                    copyQuiet(r, data.verse(), rec);
                }
            }
            List<VerseCard> deck = r.deck();
            for (int i = 0; i < deck.size(); i++) {
                VerseCard data = deck.get(i);
                int rec = r.checkRecursion(data.verse(), recursion);
                if (rec > -1 && !data.id().equals(UtilityVerses.FRESH_PAGE)) {
                    copyQuiet(r, data.verse(), rec);
                }
            }
            return VerseAction.NONE;
        }).asRecursive());
        c.register(sweep("recall_modifiers", 30, 17, VerseType.MODIFIER, true));
        c.register(sweep("recall_projectiles", 30, 17, VerseType.PROJECTILE, false));
        c.register(sweep("recall_statics", 30, 10, VerseType.STATIC, true));
    }

    /** {@code data.action(rec)} behind {@code check_recursion}; nothing when there is no card. */
    private static void copy(Recital r, VerseCard data, int recursion) {
        if (data == null) {
            return;
        }
        int rec = r.checkRecursion(data.verse(), recursion);
        if (rec > -1) {
            r.call(data.verse(), rec, 1);
        }
    }

    /** A copy with {@code dont_draw_actions} set around it. */
    static void copyQuiet(Recital r, Verse verse, int rec) {
        r.setDrawDisabled(true);
        r.call(verse, rec, 1);
        r.setDrawDisabled(false);
    }

    /** MU / PHI / SIGMA: every card of one type in read, hand, unread, quiet; then beat, rest and mana put back. */
    private static Verse sweep(String path, int mana, int beat, VerseType type, boolean drawAfter) {
        return control(path, mana, Verse.UNLIMITED, Declared.of(drawAfter ? 1 : 0, beat, 0), (r, recursion, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            int beatBefore = s.beatTicks();
            int restBefore = r.rest();
            int manaBefore = r.mana();
            sweepPile(r, r.discard(), type, recursion);
            sweepPile(r, r.hand(), type, recursion);
            sweepPile(r, r.deck(), type, recursion);
            s.setBeat(beatBefore);
            r.setRest(restBefore);
            r.setMana(manaBefore);
            if (drawAfter) {
                r.drawActions(1);
            }
            return VerseAction.NONE;
        }).asRecursive();
    }

    private static void sweepPile(Recital r, List<VerseCard> pile, VerseType type, int recursion) {
        for (int i = 0; i < pile.size(); i++) {
            VerseCard data = pile.get(i);
            if (data.type() != type) {
                continue;
            }
            int rec = r.checkRecursion(data.verse(), recursion);
            if (rec > -1) {
                copyQuiet(r, data.verse(), rec);
            }
        }
    }

    // ---- the Wild and Blind verses, and Reprise ----------------------------------------------

    private static void registerWild(VerseCatalogue c) {
        // RANDOM_SPELL: any known verse, run with draw enabled.
        c.register(control("wild_verse", 3, Verse.UNLIMITED, Declared.NONE, (r, recursion, it) -> {
            wild(r, recursion, null);
            return VerseAction.NONE;
        }).asRecursive());
        // ZETA: a verse from another incantation, quiet, then draw one.
        c.register(control("wild_recall", 4, Verse.UNLIMITED, Declared.of(1, 0, 0), (r, recursion, it) -> {
            List<Verse> options = r.world().otherIncantationVerses();
            if (!options.isEmpty()) {
                Verse data = options.get(r.world().random(options.size()));
                int rec = r.checkRecursion(data, recursion);
                if (rec > -1) {
                    copyQuiet(r, data, rec);
                }
            }
            r.drawActions(1);
            return VerseAction.NONE;
        }).asRecursive());
        // DRAW_RANDOM.
        c.register(control("blind_draw", 6, Verse.UNLIMITED, Declared.NONE, (r, recursion, it) -> {
            blindDraw(r, recursion, r.deck().size() + r.discard().size());
            return VerseAction.NONE;
        }).asRecursive());
        // DRAW_3_RANDOM: three independent picks over the sizes as they were at the start.
        c.register(control("blind_trio", 12, Verse.UNLIMITED, Declared.NONE, (r, recursion, it) -> {
            int datasize = r.deck().size() + r.discard().size();
            for (int i = 0; i < 3; i++) {
                blindDraw(r, recursion, datasize);
            }
            return VerseAction.NONE;
        }).asRecursive());
        // DUPLICATE: every card in hand but itself, draw enabled, bounded by the hand as it was; then draw one.
        c.register(control("reprise", 45, Verse.UNLIMITED, Declared.of(1, 7, 7), (r, recursion, it) -> {
            List<VerseCard> hand = r.hand();
            int handCount = hand.size();
            for (int i = 0; i < handCount && i < hand.size(); i++) {
                VerseCard v = hand.get(i);
                int rec = r.checkRecursion(v.verse(), recursion);
                if (!v.id().equals(REPRISE) && rec > -1) {
                    r.call(v.verse(), rec, 1);
                }
            }
            r.state().addBeat(7);
            r.addRest(7);
            r.drawActions(1);
            return VerseAction.NONE;
        }).asRecursive());
    }

    /**
     * RANDOM_SPELL / RANDOM_PROJECTILE / RANDOM_MODIFIER: up to a hundred rolls for a known verse of
     * the type that the recursion limit allows, then the last roll runs regardless, as the Lua does.
     */
    static void wild(Recital r, int recursion, VerseType type) {
        List<Verse> all = r.world().allVerses();
        if (all.isEmpty()) {
            return;
        }
        Verse data = all.get(r.world().random(all.size()));
        int rec = r.checkRecursion(data, recursion);
        boolean usable = r.world().isKnown(data.id());
        int safety = 0;
        while (safety < 100 && ((type != null && data.type() != type) || rec == -1 || !usable)) {
            data = all.get(r.world().random(all.size()));
            rec = r.checkRecursion(data, recursion);
            usable = r.world().isKnown(data.id());
            safety++;
        }
        r.call(data, rec, 1);
    }

    /**
     * DRAW_RANDOM's body: one card from the unread and read piles laid end to end, walking on past
     * refused or spent cards; the copy runs with draw enabled and the card loses a use.
     */
    static void blindDraw(Recital r, int recursion, int datasize) {
        if (datasize <= 0) {
            return;
        }
        int rnd = r.world().random(datasize);
        VerseCard data = pick(r, rnd);
        if (data == null) {
            return;
        }
        int checks = 0;
        int rec = r.checkRecursion(data.verse(), recursion);
        while (data != null && (rec == -1 || data.spent()) && checks < datasize) {
            rnd = (rnd + 1) % datasize;
            checks++;
            data = pick(r, rnd);
            rec = data == null ? -1 : r.checkRecursion(data.verse(), recursion);
        }
        if (data != null && rec > -1 && !data.spent()) {
            r.call(data.verse(), rec, 1);
            data.consumeUse();
        }
    }

    private static VerseCard pick(Recital r, int index) {
        List<VerseCard> deck = r.deck();
        if (index < deck.size()) {
            return deck.get(index);
        }
        List<VerseCard> discard = r.discard();
        int rest = index - deck.size();
        return rest < discard.size() ? discard.get(rest) : null;
    }
}
