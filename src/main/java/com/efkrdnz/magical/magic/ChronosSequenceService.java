package com.efkrdnz.magical.magic;

import java.util.ArrayList;
import java.util.List;

/**
 * A scripted "boss scenery" for Chronos End: one command lights the whole timeline, layering the
 * individual /chronosfx effects on top of each other over ~25 seconds to build one escalating scene
 * - the world freezes, meteors fall, a maw churns at the zenith, the sky tears open, reality trades
 * themes, and it settles into a churning climax that holds until stopped.
 *
 * The timeline is a list of (tick, action) steps advanced once per server tick from
 * {@link MagicGameplayEvents#onServerTick}. Effects still broadcast to everyone; only players inside
 * Chronos End render them.
 */
public final class ChronosSequenceService {
    private static final List<Step> TIMELINE = buildTimeline();
    private static final int FINALE_TICK = TIMELINE.isEmpty() ? 0 : TIMELINE.get(TIMELINE.size() - 1).tick();

    private static boolean active;
    private static int tick;

    private record Step(int tick, Runnable action) {}

    private ChronosSequenceService() {}

    /** Light the timeline from the top. Safe to call again to restart. */
    public static void start() {
        active = true;
        tick = 0;
    }

    /** Cancel the sequence and ease every effect back to calm. */
    public static void stop() {
        active = false;
        tick = 0;
        ChronosEnvironmentService.skyCut(false, 40);
        ChronosEnvironmentService.themeShift(false, 60);
        ChronosEnvironmentService.pulseStorm(false, 40, 1.0F);
        ChronosEnvironmentService.skyVortex(false, 40);
        ChronosEnvironmentService.timeFreeze(false, 40);
        ChronosEnvironmentService.starRain(false, 40, 1.0F);
        ChronosEnvironmentService.clocksOnly(false, 40);
        ChronosEnvironmentService.colorPalette(ChronosEnvironmentService.PALETTE_DEFAULT, 40);
    }

    public static boolean isActive() {
        return active;
    }

    /** Advanced once per server tick; fires any steps due this tick and holds at the finale. */
    public static void tick() {
        if (!active) {
            return;
        }
        for (Step step : TIMELINE) {
            if (step.tick() == tick) {
                step.action().run();
            }
        }
        if (tick <= FINALE_TICK) {
            tick++;
        }
        // Past the finale the scene simply holds (all its effects stay active) until stop().
    }

    private static List<Step> buildTimeline() {
        Builder b = new Builder();
        // --- The stillness: time grinds to a halt as a faint throb gathers ---
        b.at(0.0, () -> ChronosEnvironmentService.timeFreeze(true, 60));
        b.at(0.0, () -> ChronosEnvironmentService.pulseStorm(true, 40, 0.6F));
        // --- The meteors and the maw ---
        b.at(4.5, () -> ChronosEnvironmentService.starRain(true, 40, 1.3F));
        b.at(6.5, () -> ChronosEnvironmentService.skyVortex(true, 80));
        b.at(8.5, () -> ChronosEnvironmentService.timeFreeze(false, 30)); // time lurches back, the maw churns
        // --- The tear: the throb peaks and the sky splits (monuments dissolve) ---
        b.at(10.5, () -> ChronosEnvironmentService.pulseStorm(true, 20, 1.8F));
        b.at(11.5, () -> ChronosEnvironmentService.skyCut(true, 90));
        // --- The reversal: realities trade themes, the monuments return recoloured ---
        b.at(16.5, () -> ChronosEnvironmentService.themeShift(true, 110));
        b.at(21.5, () -> ChronosEnvironmentService.skyCut(false, 60)); // the wound seals
        // --- The climax holds: maw + meteors + swapped theme + steady throb ---
        b.at(23.5, () -> ChronosEnvironmentService.pulseStorm(true, 40, 1.15F));
        return b.steps;
    }

    private static final class Builder {
        private final List<Step> steps = new ArrayList<>();

        private void at(double seconds, Runnable action) {
            steps.add(new Step((int) Math.round(seconds * 20.0D), action));
        }
    }
}
