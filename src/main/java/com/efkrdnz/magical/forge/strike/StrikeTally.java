package com.efkrdnz.magical.forge.strike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The bookkeeping a single strike carries across every target it touches and every extra pass it
 * makes - a flurry's pulses, a heavy slam's second ring, a wave threading three bodies. One instance
 * per strike entity, mutable by design, and deliberately free of Minecraft types so the once-only
 * rules it enforces can be pinned by a plain unit test.
 */
public final class StrikeTally {

    private final Map<UUID, Integer> touches = new HashMap<>();
    private float leechHealed;
    private int impacts;
    private int passImpacts;
    private boolean primaryCorrectionSpent;

    /** Counts one landed hit and returns the running total for this strike. */
    public int noteImpact() {
        passImpacts++;
        return ++impacts;
    }

    public int impacts() {
        return impacts;
    }

    /**
     * Opens a fresh pass over the bodies in reach: the next flurry pulse, a heavy slam's second
     * ring. The press-wide {@link #impacts()} keeps running; only the per-pass count restarts.
     *
     * <p>The two counts answer different questions. An Art that may happen once per <em>press</em> -
     * a lingering zone, a chain, a line sweep - reads {@code impacts() == 1}. An Art that happens
     * once per <em>pulse</em> - GALE's step, which dashes the wielder half a block - reads
     * {@code passImpacts() == 1}, so three pulses into three bodies dash three times and not
     * nine.</p>
     */
    public void beginPass() {
        passImpacts = 0;
    }

    /** How many bodies the current pass has touched, this one included. */
    public int passImpacts() {
        return passImpacts;
    }

    /**
     * Counts one touch on {@code target} and returns how many this strike has now made against that
     * one body, starting at 1.
     *
     * <p>Two rules read it. An element rider fires only on {@code 1}, so it lands once per target
     * per press however many passes the form makes. An Art fires on every touch and reads the count
     * itself: a flurry's stacking Arts want the pulse number, and the third pulse is where a
     * discharge goes off.</p>
     */
    public int noteTargetHit(UUID target) {
        return touches.merge(target, 1, Integer::sum);
    }

    /** How many times this body has been touched so far, without counting another. */
    public int touches(UUID target) {
        return touches.getOrDefault(target, 0);
    }

    public float leechHealed() {
        return leechHealed;
    }

    public void addLeechHealed(float amount) {
        leechHealed += amount;
    }

    /**
     * True at most once per strike, and only for the one target vanilla's own {@code Player#attack}
     * already hit on this press.
     *
     * <p>The correction exists to cancel that single vanilla hit, so it has to be <em>consumed</em>.
     * Multi-hit forms run the whole damage pipeline against the same body more than once - a flurry
     * re-pulses three to five times, a heavy slam rings twice - and there is still only ever one
     * vanilla hit to cancel. Subtracting the weapon's damage on every pass would quietly cost the
     * primary target a third of a heavy chain.</p>
     */
    public boolean consumePrimaryCorrection(int targetId, int primaryTargetId) {
        if (primaryCorrectionSpent || primaryTargetId < 0 || targetId != primaryTargetId) {
            return false;
        }
        primaryCorrectionSpent = true;
        return true;
    }
}
