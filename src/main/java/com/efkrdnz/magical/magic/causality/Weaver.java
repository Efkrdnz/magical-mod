package com.efkrdnz.magical.magic.causality;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The engine. Given a board, a thing that happened and a reading of the world, it says what the
 * world should now do - and does none of it.
 *
 * <p>Pure, with no Minecraft in it at all. Every rule of the Authority is in this one file and is
 * pinned on exact values by {@code WeaverTest}: the order two branches of a cause fire in, how much
 * of a hit a Store actually takes, what an Erase costs, when a walk runs out of mana, what fraying
 * does to a long chain. The service on the other side of {@link Resolution} does as it is told and
 * decides nothing.
 *
 * <p>Two things make the walk worth reading:
 *
 * <p><b>The signal is live.</b> A chain reads the consequence as it stands at the moment the signal
 * reaches the pin, not as it stood when the cause fired. So two Stores at half take half and then
 * half of the rest, never half twice, and conservation cannot be broken by drawing a second wire.
 * The same holds within one dispatch for the ledger: a Store feeding a Spend can bank a hit and
 * throw it back in the same breath, which is the design doc worked counterattack built out of two
 * pins rather than handed over as a skill.
 *
 * <p><b>The wielder chooses the order.</b> Branches fire in the order the wires were drawn and
 * causes in the order they were pinned. That is not an implementation detail leaking out - it is
 * the one thing a graph can offer that a list and a rule table cannot, and it is what two wielders
 * of this Authority will argue about.
 */
public final class Weaver {

    /** Mana a fired consequence costs, before anything is scaled. */
    public static final float MANA_PER_ACTION = 2.0F;

    /** On top of that, for a consequence aimed at something wearing the mark. */
    public static final float MANA_ANCHORED_EXTRA = 3.0F;

    /**
     * The magnitude paradox is charged on when an effect has none of its own.
     *
     * <p>A Bind or a Step moves no consequence around, so a per-point rate would charge nothing at
     * all and the rung would never be reached by a board made entirely of them. Eight points is
     * about a heart and a half: enough that Sever is felt and Kindle is not.
     */
    public static final float PARADOX_FLOOR = 8.0F;

    /** Consequences one dispatch may produce. A ceiling on the explosion, never on the design. */
    public static final int MAX_ACTIONS = 12;

    private Weaver() {}

    /**
     * Walks the board for one event.
     *
     * <p>The caller has already checked that the Weave is not shut by a collapse; this does not
     * know what a collapse is. It never returns null, and on a board with nothing matching it
     * returns the magnitude it was handed, untouched.
     */
    public static Resolution resolve(Weave weave, CausalEvent event, CausalWorld world) {
        if (weave == null || weave.empty() || event == null || world == null) {
            return Resolution.nothing(event == null ? 0.0F : event.magnitude());
        }
        Walk walk = new Walk(weave, event, world);
        for (CausalNode cause : weave.causes()) {
            if (cause.cause() != event.cause() || !walk.causeFires(cause)) {
                continue;
            }
            walk.step(cause, 1);
        }
        return walk.done();
    }

    /** One dispatch, and everything it accumulated. Not shared and not reused. */
    private static final class Walk {
        private final Weave weave;
        private final CausalEvent event;
        private final CausalWorld world;
        private final Paradox.Rung rung;
        private final List<CausalAction> actions = new ArrayList<>();
        private final Set<Integer> gates = new LinkedHashSet<>();

        private float live;
        private float ledger;
        private float paradox;
        private float mana;
        private boolean starved;
        private boolean clipped;

        Walk(Weave weave, CausalEvent event, CausalWorld world) {
            this.weave = weave;
            this.event = event;
            this.world = world;
            this.rung = Paradox.rung(world.paradox());
            this.live = event.cause().carriesMagnitude() ? Math.max(0.0F, event.magnitude()) : 0.0F;
            this.ledger = Math.max(0.0F, world.ledger());
        }

        Resolution done() {
            float finalMagnitude = event.cause().carriesMagnitude() ? live : event.magnitude();
            return new Resolution(finalMagnitude, actions, paradox, mana, List.copyOf(gates), starved, clipped);
        }

        /** Whether this particular cause pin is interested, once its own number is consulted. */
        boolean causeFires(CausalNode node) {
            return switch (node.cause()) {
                case BRIM -> ledger >= node.param();
                // The service beats out a TOLL every MIN_INTERVAL ticks; a pin set to five seconds
                // wants every fifth beat, and asking it this way keeps it exact at any interval.
                case TOLL -> (world.now() / Cause.MIN_INTERVAL) % Math.max(1, node.param() / Cause.MIN_INTERVAL) == 0;
                // NEAR arrives carrying the distance to whatever tripped it.
                case NEAR -> event.magnitude() <= node.param();
                default -> true;
            };
        }

        void step(CausalNode node, int depth) {
            if (depth >= Weave.MAX_DEPTH || (rung == Paradox.Rung.FRAYING && depth >= Paradox.FRAYING_DEPTH)) {
                if (!weave.downstream(node.id()).isEmpty()) {
                    clipped = true;
                }
                return;
            }
            for (CausalNode next : weave.downstream(node.id())) {
                if (actions.size() >= MAX_ACTIONS) {
                    clipped = true;
                    return;
                }
                if (next.kind() == NodeKind.CONDITION) {
                    if (!passes(next)) {
                        continue;
                    }
                    if (next.condition().hasMemory()) {
                        gates.add(next.id());
                    }
                    step(next, depth + 1);
                } else if (next.kind() == NodeKind.EFFECT) {
                    fire(next);
                }
            }
        }

        /** A question, answered against the world and against the signal as it now stands. */
        boolean passes(CausalNode node) {
            int param = node.param();
            return switch (node.condition()) {
                case FROM_MARKED -> event.is(CausalEvent.FROM_MARKED);
                case IS_MELEE -> event.is(CausalEvent.MELEE);
                case IS_PROJECTILE -> event.is(CausalEvent.PROJECTILE);
                case IS_MAGIC -> event.is(CausalEvent.MAGIC);
                case IS_FIRE -> event.is(CausalEvent.FIRE);
                case ABOVE -> live >= param;
                case BELOW -> live < param;
                case HURT_UNDER -> world.selfHealth() * 100.0F < param;
                case HALE_OVER -> world.selfHealth() * 100.0F > param;
                case LEDGER_OVER -> ledger >= param;
                case WITHIN -> event.hasOther() && world.distanceTo(event.otherId()) <= param;
                case CROUCHED -> world.crouching();
                case SETTLED -> world.paradox() < param;
                case MARKED_LIVES -> world.markAlive();
                case ONCE_PER -> {
                    long last = world.lastPassed(node.id());
                    yield last == Long.MIN_VALUE || world.now() - last >= param;
                }
            };
        }

        /**
         * A consequence, billed and recorded.
         *
         * <p>A {@link Modifier#TWICE} is two of everything - two bills, two draws on the ledger, two
         * actions - because the second copy is a second consequence and pretending otherwise would
         * be the one place conservation quietly broke.
         */
        void fire(CausalNode node) {
            Effect effect = node.effect();
            // An effect that reaches back into the event needs an event still worth reaching into.
            if (effect.rewrites() && !event.rewritable()) {
                return;
            }
            // Fraying reality will not let you claim a thing never happened. Everything else still works.
            if (effect == Effect.ERASE && rung == Paradox.Rung.FRAYING) {
                clipped = true;
                return;
            }
            int copies = node.wears(Modifier.TWICE) ? 2 : 1;
            for (int copy = 0; copy < copies; copy++) {
                if (actions.size() >= MAX_ACTIONS) {
                    clipped = true;
                    return;
                }
                float cost = MANA_PER_ACTION + (node.scope().needsAnchor() ? MANA_ANCHORED_EXTRA : 0.0F);
                if (rung != Paradox.Rung.SETTLED) {
                    cost *= Paradox.STRAINED_MANA_SCALE;
                }
                if (mana + cost > world.mana()) {
                    starved = true;
                    return;
                }
                float amount = commit(node, effect);
                if (amount < 0.0F) {
                    // The effect wanted something there was none of - an empty ledger, a hit already
                    // stored to nothing. Nothing is billed for a consequence that did not happen.
                    return;
                }
                mana += cost;
                paradox += rate(node, effect) * Math.max(amount, PARADOX_FLOOR);
                int delay = (node.wears(Modifier.AFTER) ? Modifier.DELAY_TICKS : 0)
                        + (copy == 1 ? Modifier.TWICE_GAP : 0);
                actions.add(CausalAction.of(node, amount, delay));
            }
        }

        /**
         * Moves the numbers this effect moves, and answers with what it carries away.
         *
         * <p>Negative means there was nothing to do and nothing should be billed. This is the only
         * place {@link #live} and {@link #ledger} change, which is what makes the conservation rule
         * checkable by reading one method.
         */
        private float commit(CausalNode node, Effect effect) {
            float scale = node.modifierScale() * (rung == Paradox.Rung.FRAYING ? Paradox.FRAYING_SCALE : 1.0F);
            switch (effect) {
                case STORE -> {
                    float want = live * (node.param() / 100.0F) * scale;
                    float taken = Math.min(Math.min(want, live), Math.max(0.0F, Ledger.MAX - ledger));
                    if (taken <= 0.0F) {
                        return -1.0F;
                    }
                    live -= taken;
                    ledger += taken;
                    return taken;
                }
                case ERASE, RETURN, PASS -> {
                    if (live <= 0.0F) {
                        return -1.0F;
                    }
                    float moved = live * scale;
                    live = 0.0F;
                    // Erase carries what it unmade even though nothing will be done with it,
                    // because that number is what reality is charged for. Returning zero here
                    // billed every Erase at the floor instead, which made erasing a big hit
                    // cheaper per point than erasing a small one - the rule exactly inverted.
                    return moved;
                }
                case ECHO -> {
                    // The one effect that leaves the consequence where it is and makes another.
                    return live <= 0.0F ? -1.0F : live * scale;
                }
                case SPEND, MEND, WARD -> {
                    float want = node.param() * scale;
                    float drawn = Math.min(want, ledger);
                    if (drawn <= 0.0F) {
                        return -1.0F;
                    }
                    ledger -= drawn;
                    return drawn;
                }
                default -> {
                    // Bind, Kindle, Sever, Step, Haul, Brand, Sigh: the number is on the pin and no
                    // consequence changes hands, so there is nothing to conserve and nothing to carry.
                    return 0.0F;
                }
            }
        }

        /** Paradox per point, the effect own rate plus whatever its tools add to it. */
        private float rate(CausalNode node, Effect effect) {
            float total = effect.paradoxPerPoint();
            for (Modifier modifier : node.modifiers()) {
                total += modifier.paradoxPerPoint();
            }
            return total;
        }
    }
}
