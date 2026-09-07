package com.efkrdnz.magical.forge.chain;

import java.util.Optional;

/**
 * The pure arithmetic of the forge: payload limits, mana, quality, material caps, cooldown and
 * class experience. Every method is total and side-effect free, so client and server agree.
 *
 * <p>Per-modifier mana and stability numbers live in the content registries, so callers pass in
 * their already summed deltas.
 */
public final class ForgeRules {

    /**
     * Most glyphs a single chain may contain.
     *
     * <p>Sixteen quantized glyphs come to about 24.5 KB on the wire against a 32,767-byte cap, so
     * the packet is not what binds. The recognizer is: one submit is roughly 4.7 million distance
     * calls on the server thread, held in check only by the submit cooldown. Eighteen would still
     * fit the packet and is not worth the tick.
     */
    public static final int MAX_GLYPHS = 16;

    /** Most strokes one glyph may be drawn with. */
    public static final int MAX_STROKES_PER_GLYPH = 6;

    /** Most points one stroke is stored with; longer strokes are resampled down. */
    public static final int MAX_POINTS_PER_STROKE = 64;

    /** Resolution of the quantization grid canvas coordinates are snapped to. */
    public static final int CANVAS_UNITS = 1024;

    /** Quality below which a forge misfires. */
    public static final int MISFIRE_QUALITY = 25;


    private static final int MIN_QUALITY = 0;

    private static final int MAX_QUALITY = 100;

    private ForgeRules() {
    }

    /**
     * Mana a forge costs: the grade's share of the pool plus the modifier deltas, capped at the
     * whole pool and never free while the player has any pool at all.
     */
    public static int manaCost(ForgeGrade grade, int modifierManaDeltaSum, int maxMana) {
        if (maxMana <= 0) {
            return 0;
        }
        int percent = grade.manaPercent() + modifierManaDeltaSum;
        int cost = (int) Math.ceil(maxMana * percent / 100.0);
        return Math.max(1, Math.min(maxMana, cost));
    }

    /** Final weapon quality: the mean glyph quality shifted by stability, clamped to 0..100. */
    public static int quality(int meanGlyphQuality, int stabilityDeltaSum) {
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, meanGlyphQuality + stabilityDeltaSum));
    }

    /**
     * What {@code copies} of one modifier rune cost in stability: the first at full price, every
     * repeat at half.
     *
     * <p>Repeats have to cost something, because stacking a rune is what makes a chain strong and
     * the misfire wall at {@link #MISFIRE_QUALITY} is the whole risk of reaching for it. Full price
     * per copy is too steep to ever be worth taking - three PIERCE would spend 24 quality on their
     * own and misfire all but an immaculate drawing. Half price puts a triple stack at 16 and
     * leaves it a real choice.
     */
    public static int stackedStability(int stabilityDelta, int copies) {
        if (copies <= 0) {
            return 0;
        }
        return stabilityDelta + stabilityDelta * (copies - 1) / 2;
    }

    /**
     * Whether {@code material} and the weapon's existing grade allow forging at {@code grade}.
     *
     * @return the blocking error, or empty when the grade is allowed
     */
    public static Optional<ForgeError> checkGrade(
            ForgeMaterial material, ForgeGrade grade, Optional<ForgeGrade> existing) {
        if (grade == ForgeGrade.DIVINE) {
            if (material.maxGrade().ordinal() < ForgeGrade.HIGH.ordinal()) {
                return Optional.of(ForgeError.MATERIAL_CAP);
            }
            boolean alreadyHighForged = existing
                    .filter(previous -> previous.ordinal() >= ForgeGrade.HIGH.ordinal())
                    .isPresent();
            return alreadyHighForged ? Optional.empty() : Optional.of(ForgeError.DIVINE_REQUIRES_FORGED);
        }
        return grade.ordinal() > material.maxGrade().ordinal()
                ? Optional.of(ForgeError.MATERIAL_CAP)
                : Optional.empty();
    }

    /** Ticks left on the reforge cooldown, 0 when the weapon was never forged or is ready. */
    public static long remainingCooldown(long forgedAtGameTime, ForgeGrade previousGrade, long now) {
        if (forgedAtGameTime <= 0) {
            return 0L;
        }
        return Math.max(0L, forgedAtGameTime + previousGrade.reforgeCooldownTicks() - now);
    }

    /** Class experience for a forge; reforging to a lower grade only pays a quarter. */
    public static int classXp(ForgeGrade newGrade, Optional<ForgeGrade> previous) {
        boolean fullAward = previous
                .map(before -> newGrade.ordinal() >= before.ordinal())
                .orElse(true);
        if (fullAward) {
            return newGrade.classXp();
        }
        return Math.max(1, Math.round(newGrade.classXp() * 0.25f));
    }

    public static boolean isMisfire(int quality) {
        return quality < MISFIRE_QUALITY;
    }
}
