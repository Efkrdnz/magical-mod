package com.efkrdnz.magical.magic.causality;

import java.util.List;
import java.util.Locale;

/**
 * Three boards somebody already built, for a wielder who has not yet worked out what to build.
 *
 * <p>An Authority whose whole content is a blank graph has a first hour that is a blank graph, and
 * the design doc asks for tools rather than answers - but a tool nobody can pick up is not a tool.
 * These are three worked examples that each teach one rule and then get out of the way: they are
 * loaded onto the board, where every pin can be moved, retuned or thrown away.
 *
 * <p>{@link #COUNTER} is the design doc own example, built out of the pieces rather than handed
 * over as a skill. {@link #THORNS} is the cheapest useful thing on the board and teaches that
 * moving a consequence is nearly free. {@link #BANK} teaches the ledger: store half of everything,
 * and turn the bank into health on a heartbeat when it is worth doing.
 *
 * <p>Pure, and every one of them is asserted to fit the budget and to be sound by
 * {@code WeavePresetsTest} - which is the point of having them in code rather than in a wiki page.
 */
public final class WeavePresets {

    /** The design doc worked counterattack: bank everything that lands on you, then hand it back. */
    public static final String COUNTER = "counter";
    /** A melee blow goes back to whoever swung it. Two pins, and nearly no paradox. */
    public static final String THORNS = "thorns";
    /** Store half of everything; on a slow beat, turn a full enough bank into health. */
    public static final String BANK = "bank";

    public static final List<String> NAMES = List.of(COUNTER, THORNS, BANK);

    private WeavePresets() {}

    /** Builds one by name, or null when there is no such board. Never returns a shared instance. */
    public static Weave of(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case COUNTER -> counter();
            case THORNS -> thorns();
            case BANK -> bank();
            default -> null;
        };
    }

    /**
     * {@code Take Damage -> Store all of it}, and
     * {@code Ledger reaches 20 -> Spend 25 on whatever is nearest}.
     *
     * <p>Two chains rather than one, joined through the ledger rather than through a wire, because
     * the second half has to happen on a different event from the first. That is the lesson: the
     * ledger is how a board talks to itself across time, and {@link Cause#BRIM} is the ear.
     *
     * <p>The bank is unconditional. A cause may feed an effect directly ({@code NodeKind.mayFeed}
     * asks only that the rank does not go backwards), and a Store breaks no conservation, so this
     * half costs nothing in paradox and asks nothing of the wielder beforehand - it fills on the
     * first hit of any fight rather than only against a body already wearing the Mark. The payout
     * asks for no Mark either, so the whole board works the day the Authority is taken.
     *
     * <p>Not {@link Scope#OTHER}, which would read better and would do nothing at all: a BRIM is
     * built by {@code CausalEvent.of(Cause.BRIM)} with an {@code otherId} of -1, because crossing a
     * threshold in the ledger has no second party the way a blow does. That is the price of the
     * ledger being how a board talks to itself across time - the participants do not make the trip.
     * {@code OTHER} would resolve to an empty target list and the payout would silently never land,
     * so the nearest body is the honest aim, and a tick after a hit that is the thing that threw it.
     */
    private static Weave counter() {
        Weave weave = new Weave();
        CausalNode hurt = weave.add(CausalNode.of(0, Cause.HURT, 30, 30));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 210, 30).withParam(100));
        weave.connect(hurt.id(), store.id());

        CausalNode brim = weave.add(CausalNode.of(0, Cause.BRIM, 30, 110).withParam(20));
        CausalNode spend = weave.add(CausalNode.of(0, Effect.SPEND, 130, 110)
                .withParam(25).withScope(Scope.NEAREST));
        weave.connect(brim.id(), spend.id());
        return weave;
    }

    /** {@code Take Damage -> it was melee -> it happens to whoever swung instead}. */
    private static Weave thorns() {
        Weave weave = new Weave();
        CausalNode hurt = weave.add(CausalNode.of(0, Cause.HURT, 40, 70));
        CausalNode melee = weave.add(CausalNode.of(0, Condition.IS_MELEE, 140, 70));
        CausalNode back = weave.add(CausalNode.of(0, Effect.RETURN, 240, 70));
        weave.connect(hurt.id(), melee.id());
        weave.connect(melee.id(), back.id());
        return weave;
    }

    /** {@code Take Damage -> Store half}, and {@code every 5s -> if the bank is over 30 -> Mend 10}. */
    private static Weave bank() {
        Weave weave = new Weave();
        CausalNode hurt = weave.add(CausalNode.of(0, Cause.HURT, 30, 34));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 150, 34).withParam(50));
        weave.connect(hurt.id(), store.id());

        CausalNode toll = weave.add(CausalNode.of(0, Cause.TOLL, 30, 120).withParam(100));
        CausalNode over = weave.add(CausalNode.of(0, Condition.LEDGER_OVER, 130, 120).withParam(30));
        CausalNode mend = weave.add(CausalNode.of(0, Effect.MEND, 230, 120)
                .withParam(10).withScope(Scope.SELF));
        weave.connect(toll.id(), over.id());
        weave.connect(over.id(), mend.id());
        return weave;
    }
}
