package com.efkrdnz.magical.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the training dummy knows about the hits it has taken, and the lines it prints about them.
 *
 * <p>Pure, so every number it reports is pinned by a test rather than trusted to a screenshot. A hit
 * carries both halves of the question the dummy exists to answer: what was dealt (the amount handed
 * to {@code hurt}, before anything took a share) and what was taken (what came off the health bar),
 * with the four reductions NeoForge's damage container names. Whatever is left between them after
 * those four is "other" - this mod's own incoming modifiers, and it can be negative, which is a hit
 * something amplified.
 */
public final class DummyMeter {
    /** How long the rolling window is, and how long a silence has to be before a new run starts. */
    public static final int WINDOW_TICKS = 100, IDLE_RESET_TICKS = 100;
    /** Under this a difference is rounding, not a reduction worth a word. */
    private static final float NOTICEABLE = 0.05F;
    private static final int SOURCES_SHOWN = 4;

    /** One landed hit: when, what was dealt, what was taken, and what each reduction removed. */
    public record Hit(int tick, float raw, float taken, float armor, float enchant, float effects,
            float absorbed, String source) {
        /** What the named reductions do not explain: the mod's own modifiers, negative if amplified. */
        public float other() {
            return raw - taken - armor - enchant - effects - absorbed;
        }
    }

    private final Deque<Hit> window = new ArrayDeque<>();
    private final Map<String, Float> bySource = new LinkedHashMap<>();
    private Hit last;
    private float raw, taken, peak, armor, enchant, effects, absorbed, other;
    private int hits, firstHit = -1, lastHit = -1;

    public void record(Hit hit) {
        // A long silence ends the run. Resetting when the hits stop would wipe the number just as
        // you look at it, so the reset happens when the next one lands instead.
        if (lastHit >= 0 && hit.tick() - lastHit > IDLE_RESET_TICKS) {
            reset();
        }
        if (firstHit < 0) {
            firstHit = hit.tick();
        }
        lastHit = hit.tick();
        last = hit;
        hits++;
        raw += hit.raw();
        taken += hit.taken();
        peak = Math.max(peak, hit.taken());
        armor += hit.armor();
        enchant += hit.enchant();
        effects += hit.effects();
        absorbed += hit.absorbed();
        other += hit.other();
        window.addLast(hit);
        bySource.merge(hit.source(), hit.taken(), Float::sum);
    }

    public void reset() {
        window.clear();
        bySource.clear();
        last = null;
        raw = taken = peak = armor = enchant = effects = absorbed = other = 0F;
        hits = 0;
        firstHit = lastHit = -1;
    }

    public boolean empty() {
        return hits == 0;
    }

    /** Ticks from the first landed hit to the last; zero until a second hit lands. */
    private int runSpan() {
        return firstHit < 0 ? 0 : lastHit - firstHit;
    }

    /**
     * The whole run, taken: first hit to last, which is the number people actually compare.
     *
     * <p>Undefined until the run has length. One hit is a number, not a rate, and reporting it as
     * {@code damage x 20} was the old meter's worst lie - a single 500 hit read as "DPS 10000".
     */
    public float runDps() {
        int span = runSpan();
        return span <= 0 ? 0F : taken * 20F / span;
    }

    /** The same run counted in what was dealt, before any reduction. */
    public float rawRunDps() {
        int span = runSpan();
        return span <= 0 ? 0F : raw * 20F / span;
    }

    /**
     * The last {@link #WINDOW_TICKS} of damage taken, per second.
     *
     * <p>Divided by the time elapsed since the run began, capped at the window, not by the gap
     * between the hits still inside it: that gap makes two quick hits read as an enormous rate.
     */
    public float windowDps(int now) {
        while (!window.isEmpty() && now - window.peekFirst().tick() > WINDOW_TICKS) {
            window.removeFirst();
        }
        int span = Math.min(WINDOW_TICKS, now - firstHit);
        if (window.isEmpty() || firstHit < 0 || span <= 0) {
            return 0F;
        }
        float sum = 0F;
        for (Hit hit : window) {
            sum += hit.taken();
        }
        return sum * 20F / span;
    }

    /** The readout, one string a line, nothing when nothing has landed. */
    public List<String> lines(int now) {
        List<String> out = new ArrayList<>();
        if (empty()) {
            return out;
        }
        out.add("Last  " + change(last.raw(), last.taken()) + "  " + last.source());
        out.add(runSpan() <= 0 ? "DPS  -" : "DPS " + number(runDps())
                + (reduced(raw, taken) ? "   raw " + number(rawRunDps()) : "")
                + "   last 5s " + number(windowDps(now)));
        out.add((reduced(raw, taken)
                ? "Dealt " + number(raw) + " → taken " + number(taken) + "  (" + percent(raw, taken) + ")"
                : "Dealt " + number(taken))
                + String.format(Locale.ROOT, "  over %.1fs", runSpan() / 20F));
        out.add(String.format(Locale.ROOT, "Hits %d   avg %s   peak %s", hits, number(taken / hits), number(peak)));
        String cuts = cut("armor", armor) + cut("ench", enchant) + cut("effects", effects)
                + cut("absorb", absorbed) + cut("other", other);
        if (!cuts.isEmpty()) {
            out.add("Cut" + cuts);
        }
        bySource.entrySet().stream()
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                .limit(SOURCES_SHOWN)
                .forEach(e -> out.add(String.format(Locale.ROOT, "  %s  %s (%.0f%%)",
                        e.getKey(), number(e.getValue()), e.getValue() * 100F / Math.max(1e-4F, taken))));
        return out;
    }

    private static boolean reduced(float dealt, float took) {
        return Math.abs(dealt - took) >= NOTICEABLE;
    }

    /** {@code 12.0 → 8.4  (-30%)}, or just {@code 6.0} when nothing came between. */
    private static String change(float dealt, float took) {
        return reduced(dealt, took)
                ? number(dealt) + " → " + number(took) + "  (" + percent(dealt, took) + ")"
                : number(took);
    }

    private static String percent(float dealt, float took) {
        if (dealt <= 0F) {
            return "+∞%";
        }
        return String.format(Locale.ROOT, "%+d%%", Math.round((took - dealt) * 100F / dealt));
    }

    private static String cut(String name, float amount) {
        return Math.abs(amount) < NOTICEABLE ? "" : "  " + name + " " + number(amount);
    }

    static String number(float value) {
        return Math.abs(value) >= 10_000F
                ? String.format(Locale.ROOT, "%.1fk", value / 1000F)
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
