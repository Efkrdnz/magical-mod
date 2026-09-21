package com.efkrdnz.magical.client.screen.causality;

import com.efkrdnz.magical.magic.causality.Cause;
import com.efkrdnz.magical.magic.causality.CausalNode;
import com.efkrdnz.magical.magic.causality.Condition;
import com.efkrdnz.magical.magic.causality.Effect;
import com.efkrdnz.magical.magic.causality.Modifier;
import com.efkrdnz.magical.magic.causality.NodeKind;
import com.efkrdnz.magical.magic.causality.Scope;
import java.util.EnumMap;
import java.util.Map;

/**
 * One 9x9 silhouette per word on the board, so a chain reads before a name does.
 *
 * <p>Forty-three of them, and they are the whole of what makes a graph legible at a glance: a wire
 * between two glyphs says more than a wire between two identical dots with labels, and a board with
 * six chains on it has no room to draw six labels at once.
 *
 * <p>The three vocabularies are drawn to be told apart at a distance before any one of them is read.
 * <b>Causes</b> point inward or carry a strike; <b>conditions</b> are built around a gap or a
 * comparison, because a question is a thing a signal has to get past; <b>effects</b> are solid and
 * point outward. Colour finishes the job - amber, pale blue, crimson - so the shape and the hue
 * agree and neither is load-bearing on its own.
 *
 * <p>Pure: rows of bits, the three enums and nothing else. {@code CausalGlyphsTest} holds every one
 * of them present and distinct, which is the only way a table this size stays honest through edits.
 */
public final class CausalGlyphs {

    public static final int SIZE = 9;

    /** A 9x9 silhouette, one int per row, bit {@code SIZE - 1 - col} lit. */
    public record Glyph(String name, int[] rows) {
        public boolean lit(int col, int row) {
            return (rows[row] >> (SIZE - 1 - col) & 1) != 0;
        }

        public int litCount() {
            int count = 0;
            for (int row : rows) {
                count += Integer.bitCount(row);
            }
            return count;
        }

        /** What the uniqueness test compares: the bits themselves, not the name. */
        public String key() {
            StringBuilder key = new StringBuilder();
            for (int row : rows) {
                key.append(Integer.toHexString(row)).append(':');
            }
            return key.toString();
        }
    }

    private static final Map<Cause, Glyph> CAUSES = new EnumMap<>(Cause.class);
    private static final Map<Condition, Glyph> CONDITIONS = new EnumMap<>(Condition.class);
    private static final Map<Effect, Glyph> EFFECTS = new EnumMap<>(Effect.class);
    private static final Map<Modifier, Glyph> MODIFIERS = new EnumMap<>(Modifier.class);
    private static final Map<Scope, Glyph> SCOPES = new EnumMap<>(Scope.class);

    private CausalGlyphs() {}

    private static Glyph glyph(String name, String... rows) {
        int[] bits = new int[SIZE];
        for (int row = 0; row < SIZE; row++) {
            int value = 0;
            String line = row < rows.length ? rows[row] : "";
            for (int col = 0; col < SIZE; col++) {
                if (col < line.length() && line.charAt(col) == '#') {
                    value |= 1 << (SIZE - 1 - col);
                }
            }
            bits[row] = value;
        }
        return new Glyph(name, bits);
    }

    private static void cause(Cause key, String... rows) {
        CAUSES.put(key, glyph(key.name(), rows));
    }

    private static void condition(Condition key, String... rows) {
        CONDITIONS.put(key, glyph(key.name(), rows));
    }

    private static void effect(Effect key, String... rows) {
        EFFECTS.put(key, glyph(key.name(), rows));
    }

    private static void modifier(Modifier key, String... rows) {
        MODIFIERS.put(key, glyph(key.name(), rows));
    }

    private static void scope(Scope key, String... rows) {
        SCOPES.put(key, glyph(key.name(), rows));
    }

    public static Glyph of(CausalNode node) {
        return switch (node.kind()) {
            case CAUSE -> of(node.cause());
            case CONDITION -> of(node.condition());
            case EFFECT -> of(node.effect());
        };
    }

    public static Glyph of(Cause cause) {
        return CAUSES.get(cause);
    }

    public static Glyph of(Condition condition) {
        return CONDITIONS.get(condition);
    }

    public static Glyph of(Effect effect) {
        return EFFECTS.get(effect);
    }

    public static Glyph of(Modifier modifier) {
        return MODIFIERS.get(modifier);
    }

    public static Glyph of(Scope scope) {
        return SCOPES.get(scope);
    }

    /** Every glyph in the table, for the test that holds them apart. */
    public static Map<String, Glyph> all() {
        Map<String, Glyph> everything = new java.util.LinkedHashMap<>();
        CAUSES.forEach((key, value) -> everything.put("cause/" + key.name(), value));
        CONDITIONS.forEach((key, value) -> everything.put("condition/" + key.name(), value));
        EFFECTS.forEach((key, value) -> everything.put("effect/" + key.name(), value));
        MODIFIERS.forEach((key, value) -> everything.put("modifier/" + key.name(), value));
        SCOPES.forEach((key, value) -> everything.put("scope/" + key.name(), value));
        return everything;
    }

    /** The colour family a pin is drawn in: what happened, what is asked, what follows. */
    public static int tint(NodeKind kind) {
        return switch (kind) {
            case CAUSE -> 0xFFC85C;
            case CONDITION -> 0x7FC8E8;
            case EFFECT -> 0xE8615C;
        };
    }

    // ---- causes: something arriving, or a strike going out ----------------------------------------

    static {
        cause(Cause.HURT,
                "....#....",
                "...###...",
                "..#.#.#..",
                "....#....",
                ".........",
                "#########",
                "#.......#",
                "#.......#",
                "#########");
        cause(Cause.STRIKE,
                "#########",
                "#.......#",
                "#.......#",
                "#########",
                ".........",
                "....#....",
                "...###...",
                "..#.#.#..",
                "....#....");
        cause(Cause.SLAY,
                "..#####..",
                ".#######.",
                "#.##.##.#",
                "#.##.##.#",
                "#########",
                "#.#####.#",
                ".#.#.#.#.",
                "..#.#.#..",
                "...###...");
        cause(Cause.MENDED,
                "...###...",
                "..#####..",
                ".##.#.##.",
                "###.#.###",
                "##.###.##",
                "###.#.###",
                ".##.#.##.",
                "..#####..",
                "...###...");
        cause(Cause.INVOKE,
                "....#....",
                "....#....",
                "..#.#.#..",
                "...###...",
                "##..#..##",
                "...###...",
                "..#.#.#..",
                "....#....",
                "....#....");
        cause(Cause.BREAK,
                "#.#####.#",
                "#.#...#.#",
                "#.#.#.#.#",
                "#..###..#",
                "#..#.#..#",
                ".#.#.#.#.",
                ".#.#.#.#.",
                "..##.##..",
                "...#.#...");
        cause(Cause.DECREE,
                "....#....",
                "...###...",
                "...###...",
                "...###...",
                "...###...",
                "...###...",
                ".........",
                "...###...",
                "...###...");
        cause(Cause.BRIM,
                "##.....##",
                "##.....##",
                "##.....##",
                "##.....##",
                "#########",
                "#########",
                "#########",
                "#########",
                ".#######.");
        cause(Cause.TOLL,
                "....#....",
                "...###...",
                "..#####..",
                "..#.#.#..",
                ".##.#.##.",
                ".##.#.##.",
                "#########",
                ".........",
                "....#....");
        cause(Cause.NEAR,
                "....#....",
                "...#.#...",
                "..#...#..",
                ".#..#..#.",
                "#..###..#",
                ".#..#..#.",
                "..#...#..",
                "...#.#...",
                "....#....");
        cause(Cause.MARKED_HURT,
                "..#####..",
                ".#.....#.",
                "#..###..#",
                "#.##.##.#",
                "#..###..#",
                ".#.....#.",
                "..#####..",
                "....#....",
                "...###...");
        cause(Cause.MARKED_STRIKES,
                "...###...",
                "....#....",
                "..#####..",
                ".#.....#.",
                "#..###..#",
                "#.##.##.#",
                "#..###..#",
                ".#.....#.",
                "..#####..");
        cause(Cause.MARKED_FALLS,
                "..#####..",
                ".#.....#.",
                "#..###..#",
                "#.##.##.#",
                "#..###..#",
                ".#.....#.",
                "..#####..",
                ".........",
                "#########");
    }

    // ---- conditions: a gap a signal has to get past ----------------------------------------------

    static {
        condition(Condition.FROM_MARKED,
                "....#....",
                "...#.#...",
                "..#...#..",
                ".#.###.#.",
                "#.##.##.#",
                ".#.###.#.",
                "..#...#..",
                "...#.#...",
                "....#....");
        condition(Condition.IS_MELEE,
                ".......##",
                "......##.",
                ".....##..",
                "....##...",
                "...##....",
                "..##.....",
                ".##..#...",
                "##..##...",
                "#..###...");
        condition(Condition.IS_PROJECTILE,
                ".......##",
                "......###",
                ".....##..",
                "....##...",
                "...##....",
                "..##.....",
                ".##......",
                "##.......",
                "#........");
        condition(Condition.IS_MAGIC,
                "....#....",
                "..#.#.#..",
                "...#.#...",
                "##..#..##",
                ".........",
                "##..#..##",
                "...#.#...",
                "..#.#.#..",
                "....#....");
        condition(Condition.IS_FIRE,
                "....#....",
                "...##....",
                "..##.#...",
                ".##..##..",
                ".#..#.#..",
                "##.##..#.",
                "#.###..#.",
                "#.....##.",
                ".#####...");
        condition(Condition.ABOVE,
                "##.......",
                "..##.....",
                "....##...",
                "......##.",
                "....##...",
                "..##.....",
                "##.......",
                ".........",
                "#########");
        condition(Condition.BELOW,
                ".......##",
                ".....##..",
                "...##....",
                ".##......",
                "...##....",
                ".....##..",
                ".......##",
                ".........",
                "#########");
        condition(Condition.HURT_UNDER,
                ".##...##.",
                "#..#.#..#",
                "#...#...#",
                ".#.....#.",
                "..#...#..",
                "...#.#...",
                "....#....",
                "..#####..",
                "...###...");
        condition(Condition.HALE_OVER,
                "...###...",
                "..#####..",
                "....#....",
                ".##...##.",
                "#..#.#..#",
                "#...#...#",
                ".#.....#.",
                "..#...#..",
                "...#.#...");
        condition(Condition.LEDGER_OVER,
                "##.....##",
                "##..#..##",
                "##.###.##",
                "#####.###",
                "###.#####",
                "##.###.##",
                "##..#..##",
                "#########",
                ".#######.");
        condition(Condition.WITHIN,
                "..#####..",
                ".#.....#.",
                "#.......#",
                "#...#...#",
                "#..###..#",
                "#...#...#",
                "#.......#",
                ".#.....#.",
                "..#####..");
        condition(Condition.CROUCHED,
                "...###...",
                "...###...",
                ".........",
                "..#####..",
                ".##...##.",
                "##.....##",
                "#.......#",
                "##.....##",
                "##.....##");
        condition(Condition.SETTLED,
                "....#....",
                "....#....",
                "#########",
                ".#.....#.",
                "..#...#..",
                "...#.#...",
                "....#....",
                ".........",
                "#########");
        condition(Condition.MARKED_LIVES,
                "....#....",
                "...#.#...",
                "..#...#..",
                ".#.###.#.",
                "#.##.##.#",
                ".#.###.#.",
                "..#...#..",
                ".........",
                "##.##.###");
        condition(Condition.ONCE_PER,
                "#########",
                ".#.....#.",
                "..#...#..",
                "...#.#...",
                "....#....",
                "...#.#...",
                "..#.#.#..",
                ".#.###.#.",
                "#########");
    }

    // ---- effects: solid, and pointing outward ------------------------------------------------------

    static {
        effect(Effect.STORE,
                "....#....",
                "...###...",
                "..#####..",
                "....#....",
                "##.....##",
                "##.....##",
                "#########",
                "#########",
                ".#######.");
        effect(Effect.SPEND,
                ".#######.",
                "#########",
                "#########",
                "##.....##",
                "##.....##",
                "....#....",
                "..#####..",
                "...###...",
                "....#....");
        effect(Effect.MEND,
                "....#....",
                "..#####..",
                "....#....",
                ".........",
                "##.....##",
                "##.....##",
                "#########",
                "#########",
                ".#######.");
        effect(Effect.WARD,
                "#########",
                "#########",
                "##.....##",
                "##.....##",
                "##.....##",
                ".#.....#.",
                ".##...##.",
                "..##.##..",
                "...###...");
        effect(Effect.ERASE,
                "##.....##",
                "###...###",
                ".###.###.",
                "..#####..",
                "...###...",
                "..#####..",
                ".###.###.",
                "###...###",
                "##.....##");
        effect(Effect.RETURN,
                "..#......",
                ".##......",
                "#########",
                ".##.....#",
                "..#.....#",
                "........#",
                "........#",
                "......###",
                ".......#.");
        effect(Effect.PASS,
                ".........",
                "......#..",
                "......##.",
                "#########",
                "#########",
                "......##.",
                "......#..",
                ".........",
                ".........");
        effect(Effect.ECHO,
                "..#...#..",
                ".##...##.",
                "###...###",
                "###...###",
                ".##...##.",
                "###...###",
                "###...###",
                ".##...##.",
                "..#...#..");
        effect(Effect.BRAND,
                "..#####..",
                ".#######.",
                "###...###",
                "##.###.##",
                "##.###.##",
                "##.###.##",
                "###...###",
                ".#######.",
                "..#####..");
        effect(Effect.STEP,
                "##.......",
                "##...##..",
                "##..####.",
                "##.######",
                "##..####.",
                "##...##..",
                "##.......",
                ".........",
                "#########");
        effect(Effect.HAUL,
                ".......##",
                "..##...##",
                ".####..##",
                "######.##",
                ".####..##",
                "..##...##",
                ".......##",
                ".........",
                "#########");
        effect(Effect.BIND,
                "..###....",
                ".#...#...",
                "#.....#..",
                "#..##..#.",
                ".#...#..#",
                "..###..#.",
                "......#..",
                ".....#...",
                "..###....");
        effect(Effect.KINDLE,
                "...##....",
                "..####...",
                ".######..",
                "########.",
                "###..###.",
                "##....##.",
                "##....##.",
                ".##..##..",
                "..####...");
        effect(Effect.SEVER,
                "##.....##",
                ".##...##.",
                "..##.##..",
                ".........",
                "....#....",
                ".........",
                "..##.##..",
                ".##...##.",
                "##.....##");
        effect(Effect.SIGH,
                "....#....",
                "...###...",
                "..#####..",
                ".#######.",
                "#########",
                ".#######.",
                "..#####..",
                "...###...",
                "..#...#..");
    }

    // ---- the tools and where they land -------------------------------------------------------------

    static {
        modifier(Modifier.AFTER,
                "..#####..",
                ".#.....#.",
                "#...#...#",
                "#...#...#",
                "#...###.#",
                "#.......#",
                "#.......#",
                ".#.....#.",
                "..#####..");
        modifier(Modifier.TWICE,
                "###...###",
                "###...###",
                "###...###",
                ".........",
                ".........",
                ".........",
                "###...###",
                "###...###",
                "###...###");
        modifier(Modifier.GREATER,
                "....#....",
                "...###...",
                "..#####..",
                ".#######.",
                "....#....",
                "....#....",
                "....#....",
                "....#....",
                "....#....");
        modifier(Modifier.LESSER,
                "....#....",
                "....#....",
                "....#....",
                "....#....",
                "....#....",
                ".#######.",
                "..#####..",
                "...###...",
                "....#....");
        modifier(Modifier.TURNED,
                "...###...",
                "..#...#..",
                ".#.....#.",
                "#........",
                "#.......#",
                "........#",
                ".#.....#.",
                "..#...#..",
                "...###...");
        modifier(Modifier.SHARED,
                "....#....",
                "...###...",
                "....#....",
                "..#.#.#..",
                ".#..#..#.",
                "#...#...#",
                "#...#...#",
                "##..#..##",
                "##.###.##");
        scope(Scope.SELF,
                "...###...",
                "..#####..",
                "...###...",
                ".........",
                "..#####..",
                ".#######.",
                "#.#####.#",
                "..#####..",
                "..#...#..");
        scope(Scope.OTHER,
                "..##.##..",
                ".#######.",
                "..##.##..",
                ".........",
                "..#####..",
                ".##...##.",
                "##.....##",
                ".##...##.",
                "..#####..");
        scope(Scope.NEAREST,
                "....#....",
                "...###...",
                "..#####..",
                "....#....",
                "....#....",
                "..#####..",
                ".#######.",
                "..#####..",
                "...###...");
        scope(Scope.MARKED,
                "..#####..",
                ".#.....#.",
                "#..###..#",
                "#.##.##.#",
                "#..###..#",
                ".#.....#.",
                "..#####..",
                ".........",
                "..#####..");
        scope(Scope.FIELD,
                "##.....##",
                "#.......#",
                ".........",
                "....#....",
                "...###...",
                "....#....",
                ".........",
                "#.......#",
                "##.....##");
    }
}
