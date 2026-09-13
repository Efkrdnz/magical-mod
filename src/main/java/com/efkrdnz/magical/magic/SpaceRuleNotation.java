package com.efkrdnz.magical.magic;

import java.util.EnumMap;
import java.util.Map;

/**
 * The physics notation behind the space rules: one textbook formula per category with the one
 * symbol a rule bends, and the kind of bend each operation is.
 *
 * <p>Formulas are written with the changed symbol in square brackets ({@code "F = m·[g]"}) and
 * split once. Every glyph has to exist in the default font's bitmap sheets - the flash draws
 * them at twice the size, where a unifont fallback with its half shadow would stand out - which
 * is why there is no nabla and no prime; {@code SpaceRuleNotationTest} holds that line.
 */
public final class SpaceRuleNotation {

    /** A formula in three pieces: what comes before the changed symbol, the symbol, what follows. */
    public record Formula(String before, String symbol, String after) {
        public String text() {
            return before + symbol + after;
        }
    }

    private static final Map<SpaceRuleCategory, Formula> FORMULAS = new EnumMap<>(SpaceRuleCategory.class);

    static {
        put(SpaceRuleCategory.GRAVITY, "F = m·[g]");
        put(SpaceRuleCategory.VELOCITY, "[v] = Δx/Δt");
        put(SpaceRuleCategory.ACCELERATION, "[a] = Δv/Δt");
        put(SpaceRuleCategory.AIR_RESISTANCE, "F = ½[ρ]v²");
        put(SpaceRuleCategory.PRESSURE, "[P] = F/A");
        put(SpaceRuleCategory.MASS, "p = [m]·v");
        put(SpaceRuleCategory.TIME_FLOW, "dτ/dt = [γ]");
        put(SpaceRuleCategory.VECTOR_FIELD, "F = q·[E]");
        put(SpaceRuleCategory.ENTROPY, "[S] = k·ln W");
        put(SpaceRuleCategory.FRICTION, "F = [μ]·N");
        put(SpaceRuleCategory.BOUNDARY, "r ≤ [R]");
        put(SpaceRuleCategory.COLLISION, "[J] = Δp");
    }

    private SpaceRuleNotation() {}

    private static void put(SpaceRuleCategory category, String notation) {
        int open = notation.indexOf('[');
        int close = notation.indexOf(']');
        if (open < 0 || close < open || notation.indexOf('[', open + 1) >= 0 || notation.indexOf(']', close + 1) >= 0) {
            throw new IllegalStateException("a formula marks exactly one symbol: " + notation);
        }
        FORMULAS.put(category, new Formula(notation.substring(0, open), notation.substring(open + 1, close), notation.substring(close + 1)));
    }

    public static Formula formula(SpaceRuleCategory category) {
        return FORMULAS.get(category);
    }

    /** The kind of bend; exhaustive, so a new operation cannot compile without one. */
    public static SpaceRuleChange change(SpaceRuleOperation operation) {
        return switch (operation) {
            case INCREASE_GRAVITY, ACCELERATE, DENSE_AIR, CRUSH_PRESSURE, WEIGH_DOWN, HASTEN_TIME,
                    DESTABILIZE_ENTROPY, STICKY, REPEL_BOUNDARY, INTENSIFY_COLLISION -> SpaceRuleChange.RAISE;
            case DECREASE_GRAVITY, DECELERATE, THIN_AIR, EXPAND_PRESSURE, LIGHTEN_MASS, SLOW_TIME,
                    STABILIZE_ENTROPY, SLIPPERY, ATTRACT_BOUNDARY -> SpaceRuleChange.LOWER;
            case REMOVE_GRAVITY, STOP, REMOVE_ACCELERATION, VACUUM, STASIS_TIME, DISABLE_COLLISION -> SpaceRuleChange.ZERO;
            case REVERSE_GRAVITY, REVERSE_ACCELERATION, BURST_PRESSURE, WRAP_BOUNDARY, RICOCHET_COLLISION -> SpaceRuleChange.FLIP;
            case CONTROL_GRAVITY, UNIFORM_MOTION, DRAG_LOCK, NORMALIZE_MASS, NORMALIZE_TIME, NORMALIZE_FRICTION,
                    SEAL_BOUNDARY, SELECTIVE_COLLISION -> SpaceRuleChange.LOCK;
            case IMPLODE_PRESSURE, ANCHOR_MASS, CHAOTIC_MOTION -> SpaceRuleChange.SURGE;
            case PULL_NORTH, PULL_SOUTH, ORBIT, CONVERGE -> SpaceRuleChange.AIM;
            case CLEAR_GRAVITY, CLEAR_VELOCITY, CLEAR_ACCELERATION, CLEAR_AIR_RESISTANCE, CLEAR_PRESSURE, CLEAR_MASS,
                    CLEAR_TIME_FLOW, CLEAR_VECTOR_FIELD, CLEAR_ENTROPY, CLEAR_FRICTION, CLEAR_BOUNDARY,
                    CLEAR_COLLISION -> SpaceRuleChange.RESTORE;
        };
    }

    /** Which way an {@link SpaceRuleChange#AIM} points: north, south, round, inward. Zero for every other kind. */
    public static int variant(SpaceRuleOperation operation) {
        return switch (operation) {
            case PULL_NORTH -> 0;
            case PULL_SOUTH -> 1;
            case ORBIT -> 2;
            case CONVERGE -> 3;
            default -> 0;
        };
    }
}
