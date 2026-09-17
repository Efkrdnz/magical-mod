package com.efkrdnz.magical.magic.chaos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A sandpile, and the whole of the Authority of Chaos out in the world.
 *
 * <p>Sites hold stress. A site over its capacity gives way, pushing what it held into its
 * neighbours by the {@link Fault} for its generation; a neighbour that was already full gives way
 * in turn. That is an avalanche, and its size is bounded by nothing the wielder spent.
 *
 * <p><b>There is no random number in this class.</b> Self-organized criticality is the canonical
 * model of a system that is completely deterministic and completely unpredictable, which is the
 * actual definition of chaos - so the unpredictability here is a property of the arithmetic rather
 * than of a dice roll, and every rule is pinned on an exact value by {@code PileTest}.
 *
 * <p><b>There is no way to take stress off a site.</b> A law has RESTORE; entropy does not. Stress
 * leaves by exactly two routes - the site gives way, or the site is forgotten - and neither is a
 * verb the wielder can point at something.
 */
public final class Pile {

    /** How long a site that has given way refuses further burdening. You cannot dig one twice. */
    public static final int SLACK_TICKS = 600;

    /** Give-ways per tick. The remainder is carried, so a big avalanche visibly rolls. */
    public static final int DEFAULT_BUDGET = 48;

    /** A ceiling so a runaway cannot eat the server. New sites are refused past it. */
    public static final int MAX_SITES = 4096;

    private record Step(PileSite site, int generation, PileSite from) {}

    private final PileWorld world;
    private final Map<PileSite, Integer> stress = new LinkedHashMap<>();
    private final Map<PileSite, Long> slackUntil = new HashMap<>();
    private final Map<PileSite, Integer> reinforced = new HashMap<>();
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    private final Set<PileSite> queued = new HashSet<>();

    private int groundDrop;
    private long groundUntil;
    private long lastTick;

    public Pile(PileWorld world) {
        this.world = world;
    }

    public int stressAt(PileSite site) {
        return stress.getOrDefault(site, 0);
    }

    /** What this site holds before it gives, after Criticality and after anything that ROOTed. */
    public int capacityAt(PileSite site, long now) {
        int base = world.capacity(site) + reinforced.getOrDefault(site, 0);
        if (now < groundUntil) {
            base -= groundDrop;
        }
        return Math.max(0, base);
    }

    public boolean unstable(PileSite site) {
        return stressAt(site) > capacityAt(site, lastTick);
    }

    public boolean slack(PileSite site, long now) {
        return now < slackUntil.getOrDefault(site, Long.MIN_VALUE);
    }

    public boolean settling() {
        return !queue.isEmpty();
    }

    public Set<PileSite> sites() {
        return Set.copyOf(stress.keySet());
    }

    public int loadedSites() {
        return stress.size();
    }

    /**
     * Burdens a site, and arms it if that put it over.
     *
     * <p>False when the burden was refused: a negative amount (there is no way to take stress back),
     * a slack site, or a Pile already at its ceiling.
     */
    public boolean add(PileSite site, int amount, long now) {
        lastTick = now;
        if (amount <= 0 || slack(site, now)) {
            return false;
        }
        if (!stress.containsKey(site) && stress.size() >= MAX_SITES) {
            return false;
        }
        pour(site, amount);
        arm(site, 1, null, now);
        return true;
    }

    /** Criticality: every capacity drops, and everything that was merely standing is now armed. */
    public void lowerGround(int amount, long untilTick, long now) {
        lastTick = now;
        groundDrop = Math.max(0, amount);
        groundUntil = untilTick;
        for (PileSite site : List.copyOf(stress.keySet())) {
            arm(site, 1, null, now);
        }
    }

    /**
     * Runs the avalanche, at most {@code budget} give-ways, and returns how many it performed.
     *
     * <p>What is left stays queued for the next call, which is both the performance guard and the
     * reason a long cascade is watchable: it rolls across the ground over a second or two instead of
     * resolving inside one frame.
     */
    public int settle(Fracture fracture, int budget, long now) {
        lastTick = now;
        int done = 0;
        while (done < budget && !queue.isEmpty()) {
            Step step = queue.poll();
            queued.remove(step.site());
            if (!world.present(step.site())) {
                stress.remove(step.site());
                continue;
            }
            if (slack(step.site(), now) || !overCapacity(step.site(), now)) {
                continue;
            }
            giveWay(step, fracture.at(step.generation()), now);
            done++;
        }
        return done;
    }

    /** Forgets a site entirely - a body that died, a block whose chunk went away. Not a verb. */
    public void forget(PileSite site) {
        stress.remove(site);
        reinforced.remove(site);
    }

    // ---- the collapse itself ---------------------------------------------------------------

    private void giveWay(Step step, Fault fault, long now) {
        PileSite site = step.site();
        int amount = stressAt(site);
        if (amount <= 0) {
            return;
        }

        // ROOT is the one fault that does not let go: it swallows what it was given and thickens,
        // so it neither spills nor goes slack, and the cascade simply stops at it.
        if (fault == Fault.ROOT) {
            int over = amount - capacityAt(site, now);
            if (over > 0) {
                reinforced.merge(site, over, Integer::sum);
            }
            return;
        }

        stress.remove(site);
        slackUntil.put(site, now + SLACK_TICKS);

        List<PileSite> targets = recipients(site, fault, step.from());
        if (targets.isEmpty()) {
            // Nowhere to send it does not mean it evaporates. Stress is conserved: it is spent.
            world.shed(site, amount);
            return;
        }

        int each = amount / targets.size();
        int remainder = amount % targets.size();
        for (int i = 0; i < targets.size(); i++) {
            int give = each + (i < remainder ? 1 : 0);
            if (give <= 0) {
                continue;
            }
            PileSite target = targets.get(i);
            pour(target, give);
            arm(target, step.generation() + 1, site, now);
        }
    }

    /** Empty means there is nothing to give to, and the caller spends it where it stands. */
    private List<PileSite> recipients(PileSite site, Fault fault, PileSite from) {
        if (fault == Fault.SHED) {
            return List.of();
        }
        if (fault == Fault.RECOIL) {
            return from == null ? List.of() : List.of(from);
        }
        List<PileSite> neighbours = world.neighbours(site);
        if (neighbours.isEmpty()) {
            return List.of();
        }
        switch (fault) {
            case SLUMP -> {
                PileSite lowest = null;
                double best = Double.MAX_VALUE;
                for (PileSite neighbour : neighbours) {
                    double y = world.height(neighbour);
                    if (y < best) {
                        best = y;
                        lowest = neighbour;
                    }
                }
                return lowest == null ? List.of() : List.of(lowest);
            }
            case HEAP -> {
                PileSite fullest = null;
                int best = -1;
                for (PileSite neighbour : neighbours) {
                    int held = stressAt(neighbour);
                    if (held > best) {
                        best = held;
                        fullest = neighbour;
                    }
                }
                return fullest == null ? List.of() : List.of(fullest);
            }
            case HUNT -> {
                List<PileSite> alive = new ArrayList<>();
                for (PileSite neighbour : neighbours) {
                    if (world.living(neighbour)) {
                        alive.add(neighbour);
                    }
                }
                return alive;
            }
            default -> {
                return neighbours;
            }
        }
    }

    private void pour(PileSite site, int amount) {
        stress.merge(site, amount, Integer::sum);
    }

    /**
     * Queues a site to give way, if it is over and not spent.
     *
     * <p>A slack site may still <em>receive</em> - that is how RECOIL sends a cascade back into the
     * ground it just came out of - but it may not give way again, which is also what stops that
     * from being an infinite loop.
     */
    private void arm(PileSite site, int generation, PileSite from, long now) {
        if (slack(site, now) || !overCapacity(site, now)) {
            return;
        }
        if (queued.add(site)) {
            queue.add(new Step(site, generation, from));
        }
    }

    private boolean overCapacity(PileSite site, long now) {
        return stressAt(site) > capacityAt(site, now);
    }
}
