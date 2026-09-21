package com.efkrdnz.magical.magic.causality;

import java.util.Locale;

/**
 * How far out of joint the wielder has put things, and what it costs them.
 *
 * <p>Paradox is charged for <b>breaking conservation and for nothing else</b>. Moving a consequence
 * somewhere it can still land is free of it; erasing one, doubling one, or turning harm into
 * healing is a claim about the world that the world did not agree to, and the gauge is the world
 * disagreeing. So the gauge is not a cooldown wearing a costume - it is the one number that tells
 * the wielder which of two builds is the clever one.
 *
 * <p>The rungs bite in the order a designer would want them to. {@link Rung#STRAINED} makes the
 * Weave expensive; {@link Rung#FRAYING} makes it <em>short</em>, cutting off everything past
 * {@link #FRAYING_DEPTH} pins, which takes the elaborate machinery away first and leaves the simple
 * chains working. At {@link #MAX} the board collapses: every chain stops for
 * {@link #COLLAPSE_TICKS}, and the ledger comes due on the wielder, because consequence held is
 * consequence owed and there is nothing left holding it.
 */
public final class Paradox {

    public static final int MAX = 100;

    /** Where the rungs sit. */
    public static final int STRAINED_AT = 40;
    public static final int FRAYING_AT = 70;

    /**
     * Pins a chain may hold while fraying. Past this a signal simply stops.
     *
     * <p>Three is a cause, one question and a consequence - the canonical simple rule - so the
     * rung takes the elaborate machinery away first and leaves the plain rules working, which
     * is the shape of degradation a wielder can plan around.
     */
    public static final int FRAYING_DEPTH = 3;

    /** What everything is multiplied by while fraying. */
    public static final float FRAYING_SCALE = 0.5F;

    /** What a fired action costs in mana while strained or worse. */
    public static final float STRAINED_MANA_SCALE = 2.0F;

    /** How long the board is shut after a collapse, and what the gauge falls back to. */
    public static final int COLLAPSE_TICKS = 400;
    public static final int COLLAPSE_RESET = 50;

    /** One point cools every this many ticks, once nothing has fired for {@link #CALM_TICKS}. */
    public static final int COOL_INTERVAL = 20;
    public static final int CALM_TICKS = 60;

    /** Which rung the gauge is on, and what that means for a walk. */
    public enum Rung {
        SETTLED,
        STRAINED,
        FRAYING;

        public String translationKey() {
            return "paradox.magical." + name().toLowerCase(Locale.ROOT);
        }
    }

    private float value;
    private long lastFired = Long.MIN_VALUE;
    private long shutUntil = Long.MIN_VALUE;

    public float value() {
        return value;
    }

    public int rounded() {
        return Math.round(value);
    }

    public Rung rung() {
        return rung(value);
    }

    public static Rung rung(float value) {
        if (value >= FRAYING_AT) {
            return Rung.FRAYING;
        }
        return value >= STRAINED_AT ? Rung.STRAINED : Rung.SETTLED;
    }

    /** True while a collapse is still shutting the board. Nothing fires and nothing is charged. */
    public boolean shut(long now) {
        return now < shutUntil;
    }

    public long shutUntil() {
        return shutUntil;
    }

    /**
     * Charges the gauge, and answers true when that tipped it over into a collapse.
     *
     * <p>The caller owes the wielder the rest of a collapse when this says yes: shutting the board
     * and dumping the ledger are the service job, because both of them touch the world.
     */
    public boolean add(float amount, long now) {
        if (amount > 0.0F) {
            lastFired = now;
        }
        value = Math.max(0.0F, value + amount);
        return value >= MAX;
    }

    /** The other half of a collapse: the board shuts and the gauge drops to half. */
    public void collapse(long now) {
        value = COLLAPSE_RESET;
        shutUntil = now + COLLAPSE_TICKS;
        lastFired = now;
    }

    /**
     * The slow cool, once per {@link #COOL_INTERVAL} ticks.
     *
     * <p>Only while calm. A Weave that is still firing is a Weave still arguing, and an argument
     * does not settle while it is going on - which is what makes a wielder stop and let it rest
     * rather than leaving every chain armed all day.
     */
    public void cool(long now) {
        if (now - lastFired < CALM_TICKS) {
            return;
        }
        value = Math.max(0.0F, value - 1.0F);
    }

    public void set(float amount) {
        value = Math.max(0.0F, Math.min(MAX, amount));
    }

    public void clear() {
        value = 0.0F;
        lastFired = Long.MIN_VALUE;
        shutUntil = Long.MIN_VALUE;
    }

    public void copyFrom(Paradox other) {
        value = other.value;
        lastFired = other.lastFired;
        shutUntil = other.shutUntil;
    }

    public long lastFired() {
        return lastFired;
    }

    public void restore(float amount, long lastFiredTick, long shutUntilTick) {
        value = Math.max(0.0F, Math.min(MAX, amount));
        lastFired = lastFiredTick;
        shutUntil = shutUntilTick;
    }
}
