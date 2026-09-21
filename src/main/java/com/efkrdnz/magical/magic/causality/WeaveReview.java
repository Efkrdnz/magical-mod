package com.efkrdnz.magical.magic.causality;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a board actually says, and everywhere it says nothing.
 *
 * <p>A graph is the most expressive shape in the mod and the easiest one to get quietly wrong. Every
 * mistake here is silent at runtime - a condition that can never be true simply never fires, an
 * effect that reaches back into an event that has already happened is dropped, a pin with no string
 * on it is a pin nobody will ever look at again - and a wielder who cannot see the difference
 * between a Weave that is waiting and a Weave that is broken will stop trusting the Authority.
 *
 * <p>So the board is reviewed continuously and in full while it is open, and the reading sits under
 * it. Pure, so {@code WeaveReviewTest} holds every rule on exact boards.
 */
public final class WeaveReview {

    /** One root-to-consequence path: the cause, whatever it was asked on the way, the effect. */
    public record Chain(List<CausalNode> pins) {

        public Chain {
            pins = List.copyOf(pins);
        }

        public CausalNode cause() {
            return pins.get(0);
        }

        public CausalNode last() {
            return pins.get(pins.size() - 1);
        }

        /** True when the path ends in something that happens rather than trailing off. */
        public boolean complete() {
            return last().kind() == NodeKind.EFFECT;
        }

        public int length() {
            return pins.size();
        }

        /** True when the walk would be cut short by the depth ceiling before reaching the end. */
        public boolean overlong() {
            return pins.size() > Weave.MAX_DEPTH;
        }
    }

    /** Something the board should be told about, and the pin it is about. */
    public record Issue(int nodeId, Kind kind) {}

    /** Every way a board can be drawn and still say nothing. Each is one line under the board. */
    public enum Kind {
        /** A condition or an effect with nothing feeding it. It will never see a signal. */
        ORPHANED,
        /** A cause or a condition with nothing hanging off it. The chain trails away. */
        DANGLING,
        /**
         * An effect that rewrites the event it was fired by, on a cause whose event is already over.
         * Storing part of a death is not a thing, and the engine drops it rather than pretend.
         */
        NOTHING_TO_REWRITE,
        /**
         * A question about the event, on a cause that carries no event to ask about. Asking whether
         * a heartbeat was melee is not false, it is meaningless, and it will never let anything past.
         */
        NEVER_TRUE,
        /** A pin that reaches outside the wielder, with no mark out there to reach. */
        WANTS_MARK,
        /** A chain longer than a signal may travel. Everything past the ceiling is drawn and dead. */
        TOO_DEEP;

        public String translationKey() {
            return "message.magical.weave_issue_" + name().toLowerCase(Locale.ROOT);
        }
    }

    private WeaveReview() {}

    /**
     * Every path from a cause to wherever it ends, depth-first, in the order the wires were drawn.
     *
     * <p>Which is the order they fire in, so the list reads top to bottom as the machine runs. The
     * walk is bounded by {@link Weave#MAX_DEPTH} plus one, so an over-long chain appears once and is
     * flagged rather than being followed forever - the board forbids loops, but the ceiling is what
     * the engine honours and the reading must agree with the engine.
     */
    public static List<Chain> chains(Weave weave) {
        List<Chain> found = new ArrayList<>();
        if (weave == null) {
            return found;
        }
        for (CausalNode cause : weave.causes()) {
            walk(weave, cause, new ArrayList<>(), found);
        }
        return found;
    }

    private static void walk(Weave weave, CausalNode node, List<CausalNode> path, List<Chain> out) {
        path.add(node);
        List<CausalNode> next = weave.downstream(node.id());
        if (next.isEmpty() || path.size() > Weave.MAX_DEPTH) {
            out.add(new Chain(path));
        } else {
            for (CausalNode child : next) {
                walk(weave, child, new ArrayList<>(path), out);
            }
        }
    }

    /** Every chain that ends in something happening. What the wielder actually built. */
    public static List<Chain> live(Weave weave) {
        List<Chain> live = new ArrayList<>();
        for (Chain chain : chains(weave)) {
            if (chain.complete() && !chain.overlong()) {
                live.add(chain);
            }
        }
        return live;
    }

    /**
     * Everything wrong with the board, in the order the pins were put up.
     *
     * <p>{@code hasMark} is whether a mark is live right now. A board that wants one is not broken -
     * it is idle - so that issue is worded as a state rather than as a mistake, and it comes and
     * goes as the wielder anchors and loses things.
     */
    public static List<Issue> issues(Weave weave, boolean hasMark) {
        List<Issue> issues = new ArrayList<>();
        if (weave == null) {
            return issues;
        }
        for (CausalNode node : weave.nodes()) {
            if (node.kind() != NodeKind.CAUSE && weave.orphan(node.id())) {
                issues.add(new Issue(node.id(), Kind.ORPHANED));
            }
            if (node.kind() != NodeKind.EFFECT && weave.downstream(node.id()).isEmpty()) {
                issues.add(new Issue(node.id(), Kind.DANGLING));
            }
            if (!hasMark && node.needsAnchor()) {
                issues.add(new Issue(node.id(), Kind.WANTS_MARK));
            }
        }
        for (Chain chain : chains(weave)) {
            if (chain.overlong()) {
                issues.add(new Issue(chain.last().id(), Kind.TOO_DEEP));
                continue;
            }
            Cause cause = chain.cause().cause();
            for (CausalNode pin : chain.pins()) {
                if (pin.kind() == NodeKind.CONDITION && pin.condition().asksAboutTheEvent()
                        && !cause.carriesMagnitude() && cause != Cause.NEAR) {
                    issues.add(new Issue(pin.id(), Kind.NEVER_TRUE));
                }
                if (pin.kind() == NodeKind.EFFECT && pin.effect().rewrites() && !cause.rewritable()) {
                    issues.add(new Issue(pin.id(), Kind.NOTHING_TO_REWRITE));
                }
            }
        }
        return dedupe(issues);
    }

    /** A pin on two chains earns one line, not two. */
    private static List<Issue> dedupe(List<Issue> issues) {
        List<Issue> unique = new ArrayList<>();
        for (Issue issue : issues) {
            if (!unique.contains(issue)) {
                unique.add(issue);
            }
        }
        return unique;
    }

    /** True when nothing on the board is wrong and at least one chain would do something. */
    public static boolean sound(Weave weave, boolean hasMark) {
        return issues(weave, hasMark).isEmpty() && !live(weave).isEmpty();
    }
}
