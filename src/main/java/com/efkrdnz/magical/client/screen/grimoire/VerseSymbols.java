package com.efkrdnz.magical.client.screen.grimoire;

import com.efkrdnz.magical.magic.incantation.Verse;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VerseType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One symbol per verse, so a page reads before a name does.
 *
 * <p>A symbol is a 9x9 <b>glyph</b> - the silhouette of what the verse is: a body's look, or its
 * own for a modifier, a multicast, a control - a 3x3 <b>badge</b> in the bottom-right corner saying
 * which of its family it is (the latch, the fuse, the epitaph; the frost, the storm, the flame), and
 * <b>marks</b> along the top row that count (Refrain's repeats, Blind's draws; ten is a bar). Every
 * verse in the catalogue has a row of its own in the table, and {@code VerseSymbolsTest} holds all
 * of them distinct, none of them a fallback, and the top row clear on any glyph that carries marks.
 * A verse that ever arrives without a row wears its look's glyph, and failing that its type's, so
 * nothing is ever drawn blank.
 *
 * <p>Pure: rows of bits, no Minecraft in it but the {@link Verse} it reads.
 */
public final class VerseSymbols {

    public static final int SIZE = 9;
    public static final int BADGE_SIZE = 3;
    /** This many marks are drawn as one bar along the top rather than counted. */
    public static final int MARKS_BAR = 10;

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
    }

    /** A 3x3 mark for the corner, one int per row, bit {@code BADGE_SIZE - 1 - col} lit. */
    public record Badge(String name, int[] rows) {
        public boolean lit(int col, int row) {
            return (rows[row] >> (BADGE_SIZE - 1 - col) & 1) != 0;
        }
    }

    /** What a verse wears: its glyph, a badge or none, and how many marks along the top (0 for none). */
    public record Symbol(Glyph glyph, Badge badge, int marks) {
        public boolean hasBadge() {
            return badge != null;
        }

        public boolean hasMarks() {
            return marks > 0;
        }

        /** The identity the uniqueness test compares. */
        public String key() {
            return glyph.name() + "/" + (badge == null ? "-" : badge.name()) + "/" + marks;
        }
    }

    // ---- the glyphs of the twelve looks ---------------------------------------------------------

    private static final Glyph NEEDLE = glyph("NEEDLE",
            "........#",
            ".......##",
            "......#..",
            ".....#...",
            "....#....",
            "...#.....",
            "..#......",
            "##.......",
            "#........");
    private static final Glyph ORB = glyph("ORB",
            "...###...",
            "..#...#..",
            ".#.....#.",
            "#.......#",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#...#..",
            "...###...");
    private static final Glyph SHARD = glyph("SHARD",
            "....#....",
            "...#.#...",
            "...#.#...",
            "..#...#..",
            "..#...#..",
            ".#.....#.",
            ".#.....#.",
            "..#...#..",
            "...###...");
    private static final Glyph FLAME = glyph("FLAME",
            "....#....",
            "....#....",
            "...##....",
            "...###...",
            "..##.#...",
            "..#..##..",
            ".#....#..",
            ".#....#..",
            "..####...");
    private static final Glyph ARC = glyph("ARC",
            ".....###.",
            "....##...",
            "...##....",
            "..#####..",
            "....##...",
            "...##....",
            "..##.....",
            ".##......",
            "##.......");
    private static final Glyph DART = glyph("DART",
            ".........",
            ".....#...",
            "......#..",
            ".......#.",
            "#########",
            ".......#.",
            "......#..",
            ".....#...",
            ".........");
    private static final Glyph WHISPER = glyph("WHISPER",
            ".........",
            ".##...##.",
            "#..#.#..#",
            "....#....",
            ".........",
            ".##...##.",
            "#..#.#..#",
            "....#....",
            ".........");
    private static final Glyph PORTAL = glyph("PORTAL",
            "...###...",
            "..#...#..",
            ".#.....#.",
            ".#..#..#.",
            ".#.###.#.",
            ".#..#..#.",
            ".#.....#.",
            "..#...#..",
            "...###...");
    private static final Glyph RING = glyph("RING",
            "..#####..",
            ".#.....#.",
            "#..###..#",
            "#.#...#.#",
            "#.#...#.#",
            "#.#...#.#",
            "#..###..#",
            ".#.....#.",
            "..#####..");
    private static final Glyph BURST = glyph("BURST",
            "#...#...#",
            ".#..#..#.",
            "..#.#.#..",
            "...###...",
            "#########",
            "...###...",
            "..#.#.#..",
            ".#..#..#.",
            "#...#...#");
    private static final Glyph PIT = glyph("PIT",
            "#########",
            "#.......#",
            ".#.....#.",
            ".#.....#.",
            "..#...#..",
            "..#...#..",
            "...#.#...",
            "...#.#...",
            "....#....");
    private static final Glyph WORD = glyph("WORD",
            ".#######.",
            "#.......#",
            "#.#####.#",
            "#.......#",
            "#.#####.#",
            "#.......#",
            "#.###...#",
            "#.......#",
            ".#######.");

    // ---- the glyphs of the types, for the rail and the fallback ----------------------------------

    private static final Glyph PLUS = glyph("PLUS",
            ".........",
            "....#....",
            "....#....",
            "....#....",
            ".#######.",
            "....#....",
            "....#....",
            "....#....",
            ".........");
    private static final Glyph FAN3 = glyph("FAN3",
            "#...#...#",
            "#...#...#",
            ".#..#..#.",
            ".#..#..#.",
            "..#.#.#..",
            "..#.#.#..",
            "...###...",
            "....#....",
            "....#....");
    private static final Glyph FAN2 = glyph("FAN2",
            "#.......#",
            "#.......#",
            ".#.....#.",
            ".#.....#.",
            "..#...#..",
            "..#...#..",
            "...#.#...",
            "....#....",
            "....#....");
    private static final Glyph BLOCK = glyph("BLOCK",
            "#########",
            "#.......#",
            "#.#####.#",
            "#.#####.#",
            "#.#####.#",
            "#.#####.#",
            "#.#####.#",
            "#.......#",
            "#########");
    private static final Glyph CLAUSE = glyph("CLAUSE",
            "....##...",
            "...#.....",
            "...#.....",
            "...#.....",
            ".##......",
            "...#.....",
            "...#.....",
            "...#.....",
            "....##...");
    private static final Glyph CLAUSE_END = glyph("CLAUSE_END",
            "...##....",
            ".....#...",
            ".....#...",
            ".....#...",
            "......##.",
            ".....#...",
            ".....#...",
            ".....#...",
            "...##....");
    private static final Glyph PAGE = glyph("PAGE",
            ".#####...",
            ".#....#..",
            ".#....##.",
            ".#......#",
            ".#......#",
            ".#......#",
            ".#......#",
            ".#......#",
            ".########");
    private static final Glyph INFINITY = glyph("INFINITY",
            ".........",
            ".........",
            ".##...##.",
            "#..#.#..#",
            "#...#...#",
            "#..#.#..#",
            ".##...##.",
            ".........",
            ".........");
    /** The category row's first entry: every verse there is, as a grid of them. */
    private static final Glyph EVERY = glyph("EVERY",
            "##.##.##.",
            "##.##.##.",
            ".........",
            "##.##.##.",
            "##.##.##.",
            ".........",
            "##.##.##.",
            "##.##.##.",
            ".........");

    // ---- the glyphs verses own outright ----------------------------------------------------------

    private static final Glyph ANCHOR = glyph("ANCHOR",
            "...###...",
            "...#.#...",
            "...###...",
            "....#....",
            ".#######.",
            "....#....",
            "#...#...#",
            ".#..#..#.",
            "..#####..");
    private static final Glyph DOWN = glyph("DOWN",
            "....#....",
            "....#....",
            "....#....",
            "....#....",
            ".#..#..#.",
            "..#.#.#..",
            "...###...",
            "....#....",
            ".........");
    private static final Glyph UP = glyph("UP",
            ".........",
            "....#....",
            "...###...",
            "..#.#.#..",
            ".#..#..#.",
            "....#....",
            "....#....",
            "....#....",
            "....#....");
    private static final Glyph CHEVRONS = glyph("CHEVRONS",
            ".........",
            "#...#....",
            ".#...#...",
            "..#...#..",
            "...#...#.",
            "..#...#..",
            ".#...#...",
            "#...#....",
            ".........");
    private static final Glyph HOURGLASS = glyph("HOURGLASS",
            "#########",
            ".#.....#.",
            "..#...#..",
            "...#.#...",
            "....#....",
            "...#.#...",
            "..#...#..",
            ".#.....#.",
            "#########");
    private static final Glyph HEART = glyph("HEART",
            ".........",
            ".##...##.",
            "#..#.#..#",
            "#...#...#",
            "#.......#",
            ".#.....#.",
            "..#...#..",
            "...#.#...",
            "....#....");
    private static final Glyph BLADE = glyph("BLADE",
            "........#",
            ".......#.",
            "......#..",
            ".....#...",
            "....#....",
            "#..#.....",
            ".##......",
            ".##......",
            "#..#.....");
    private static final Glyph PUNCTURE = glyph("PUNCTURE",
            ".......#.",
            "......#..",
            "..###.#..",
            ".#...#..#",
            "#...#...#",
            "#..#....#",
            ".#.#...#.",
            "..#.#....",
            ".#.......");
    private static final Glyph CROSSHAIR = glyph("CROSSHAIR",
            "....#....",
            "..#####..",
            ".#..#..#.",
            "#...#...#",
            "#########",
            "#...#...#",
            ".#..#..#.",
            "..#####..",
            "....#....");
    private static final Glyph TARGET = glyph("TARGET",
            "..#####..",
            ".#.....#.",
            "#.......#",
            "#...#...#",
            "#..###..#",
            "#...#...#",
            "#.......#",
            ".#.....#.",
            "..#####..");
    private static final Glyph EYE = glyph("EYE",
            ".........",
            ".........",
            "..#####..",
            ".#.....#.",
            "#..###..#",
            "#..###..#",
            ".#.....#.",
            "..#####..",
            ".........");
    private static final Glyph ZIGZAG = glyph("ZIGZAG",
            "###......",
            "...#.....",
            "....#....",
            "...#.....",
            "..#......",
            "...#.....",
            "....#....",
            ".....#...",
            "......###");
    private static final Glyph SERPENT = glyph("SERPENT",
            "..#####..",
            ".#.....#.",
            "#........",
            ".#.......",
            "..#####..",
            ".......#.",
            "........#",
            ".#.....#.",
            "..#####..");
    private static final Glyph SPIRAL = glyph("SPIRAL",
            "..######.",
            ".#......#",
            "#..####.#",
            "#.#...#.#",
            "#.#.#.#.#",
            "#.#.##..#",
            "#.#.....#",
            "#..#####.",
            ".#.......");
    private static final Glyph BOUNCE = glyph("BOUNCE",
            "#.......#",
            ".#.....#.",
            "..#...#..",
            "...#.#...",
            "....#....",
            ".........",
            "#########",
            ".........",
            ".........");
    private static final Glyph TWIN = glyph("TWIN",
            ".#.....#.",
            "###...###",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.");
    private static final Glyph RELAY = glyph("RELAY",
            ".........",
            ".........",
            "###...###",
            "#.#...#.#",
            "#.#####.#",
            "#.#...#.#",
            "###...###",
            ".........",
            ".........");
    private static final Glyph NAUGHT = glyph("NAUGHT",
            "..#####.#",
            ".#.....#.",
            "#.....#.#",
            "#....#..#",
            "#...#...#",
            "#..#....#",
            "#.#.....#",
            ".#.....#.",
            "#.#####..");
    private static final Glyph LANTERN = glyph("LANTERN",
            "....#....",
            "...###...",
            "..#####..",
            "..#.#.#..",
            "..#.#.#..",
            "..#####..",
            "...###...",
            "....#....",
            ".........");
    private static final Glyph DISPLACE = glyph("DISPLACE",
            ".........",
            ".........",
            ".....#...",
            "......#..",
            "#.#.#.###",
            "......#..",
            ".....#...",
            ".........",
            ".........");
    private static final Glyph SPARK = glyph("SPARK",
            ".........",
            "....#....",
            "....#....",
            "...###...",
            ".#######.",
            "...###...",
            "....#....",
            "....#....",
            ".........");
    private static final Glyph DROP = glyph("DROP",
            "....#....",
            "....#....",
            "...#.#...",
            "...#.#...",
            "..#...#..",
            ".#.....#.",
            "#.......#",
            ".#.....#.",
            "..#####..");
    private static final Glyph WAKE = glyph("WAKE",
            ".........",
            ".........",
            ".........",
            "......##.",
            "#.#.#####",
            "......##.",
            ".........",
            ".........",
            ".........");
    private static final Glyph WREATH = glyph("WREATH",
            "..#.#.#..",
            ".#.....#.",
            "#.......#",
            ".........",
            "#.......#",
            ".........",
            "#.......#",
            ".#.....#.",
            "..#.#.#..");
    private static final Glyph QUESTION = glyph("QUESTION",
            "..#####..",
            ".#.....#.",
            "#.......#",
            ".......#.",
            "......#..",
            ".....#...",
            "....#....",
            ".........",
            "....#....");
    private static final Glyph TRIDENT = glyph("TRIDENT",
            "#...#...#",
            "#...#...#",
            "#...#...#",
            ".#..#..#.",
            "..#####..",
            "....#....",
            "....#....",
            "....#....",
            "....#....");
    private static final Glyph QUATRAIN = glyph("QUATRAIN",
            "#..#.#..#",
            "#..#.#..#",
            ".#.#.#.#.",
            ".#.#.#.#.",
            "..#.#.#..",
            "...###...",
            "....#....",
            "....#....",
            "....#....");
    private static final Glyph PENTACLE = glyph("PENTACLE",
            "....#....",
            "...#.#...",
            "#########",
            ".#.....#.",
            "..#...#..",
            "..#...#..",
            ".#..#..#.",
            ".#.#.#.#.",
            "##.....##");
    private static final Glyph HEXAD = glyph("HEXAD",
            "....#....",
            "..##.##..",
            "##.....##",
            "#.......#",
            "#.......#",
            "#.......#",
            "##.....##",
            "..##.##..",
            "....#....");
    private static final Glyph OCTAVE = glyph("OCTAVE",
            "..#####..",
            ".#.....#.",
            ".#.....#.",
            "..#####..",
            ".#.....#.",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#####..");
    private static final Glyph COLUMN = glyph("COLUMN",
            ".#######.",
            ".........",
            ".#######.",
            ".........",
            ".#######.",
            ".........",
            ".#######.",
            ".........",
            ".#######.");
    private static final Glyph CLEFT = glyph("CLEFT",
            "....#....",
            "....#....",
            "...#.#...",
            "...#.#...",
            "..#...#..",
            "..#...#..",
            ".#.....#.",
            ".#.....#.",
            "#.......#");
    private static final Glyph MIRROR = glyph("MIRROR",
            ".........",
            "#...#...#",
            "##..#..##",
            "###.#.###",
            "####.####",
            "###.#.###",
            "##..#..##",
            "#...#...#",
            ".........");
    private static final Glyph EPIC = glyph("EPIC",
            "#...#...#",
            "#...#...#",
            "#.#.#.#.#",
            "#.#.#.#.#",
            "#########",
            ".#.....#.",
            ".#.....#.",
            ".#######.",
            ".........");
    private static final Glyph REPRISE = glyph("REPRISE",
            "..#####..",
            ".#.....#.",
            "#.......#",
            "#.....###",
            "#......##",
            "#.....#.#",
            ".#.....#.",
            "..#####..",
            ".........");
    /** Carries marks: the top row stays clear for them. */
    private static final Glyph REFRAIN = glyph("REFRAIN",
            ".........",
            "....#.##.",
            "....#.##.",
            ".#..#.##.",
            "....#.##.",
            "....#.##.",
            ".#..#.##.",
            "....#.##.",
            "....#.##.");
    private static final Glyph RECALL = glyph("RECALL",
            ".........",
            "....#....",
            "...#.....",
            "..#......",
            ".#######.",
            "..#.....#",
            "...#....#",
            "....#...#",
            ".....###.");
    /** Carries marks: the top row stays clear for them. */
    private static final Glyph BLIND = glyph("BLIND",
            ".........",
            ".........",
            ".........",
            "#.......#",
            ".#.....#.",
            "..#####..",
            "..#.#.#..",
            ".#..#..#.",
            ".........");
    private static final Glyph FORK = glyph("FORK",
            ".........",
            ".......##",
            "......#..",
            ".....#...",
            "#####....",
            ".....#...",
            "......#..",
            ".......##",
            ".........");
    private static final Glyph IMPOSE = glyph("IMPOSE",
            "..#####..",
            "..#...#..",
            "..#####..",
            "....#....",
            "....#....",
            "..#####..",
            ".#.....#.",
            "#########",
            ".........");
    private static final Glyph DICE = glyph("DICE",
            ".........",
            "#########",
            "#.#...#.#",
            "#.......#",
            "#...#...#",
            "#.......#",
            "#.#...#.#",
            "#########",
            ".........");

    // ---- the badges ------------------------------------------------------------------------------

    private static final Badge LATCH = badge("LATCH", "#..", "#..", "###");
    private static final Badge FUSE = badge("FUSE", "..#", ".#.", "#..");
    private static final Badge EPITAPH = badge("EPITAPH", "###", ".#.", ".#.");
    private static final Badge TWIN_LATCH = badge("TWIN_LATCH", "#.#", "#.#", "###");
    private static final Badge PLUS_MARK = badge("PLUS_MARK", ".#.", "###", ".#.");
    private static final Badge SNOW = badge("SNOW", ".#.", "#.#", ".#.");
    private static final Badge BOLT = badge("BOLT", "..#", "###", "#..");
    private static final Badge UP_MARK = badge("UP_MARK", ".#.", "###", "...");
    private static final Badge DARK = badge("DARK", ".##", "###", "##.");
    private static final Badge FLAME_MARK = badge("FLAME_MARK", ".#.", "##.", "###");
    private static final Badge WAVE = badge("WAVE", "...", "#.#", ".#.");
    private static final Badge DICE_MARK = badge("DICE_MARK", "#.#", "...", "#.#");
    private static final Badge ARROW = badge("ARROW", "#..", ".#.", "#..");
    private static final Badge DOT = badge("DOT", "...", ".#.", "...");
    private static final Badge BAR = badge("BAR", "...", "###", "...");
    private static final Badge STAIRS = badge("STAIRS", "#..", "##.", "###");
    private static final Badge CROSS = badge("CROSS", "#.#", ".#.", "#.#");
    private static final Badge RECALL_MARK = badge("RECALL_MARK", "..#", ".##", "###");
    private static final Badge FIRST = badge("FIRST", "#..", "...", "...");
    private static final Badge LAST = badge("LAST", "...", "...", "..#");
    private static final Badge PAIR = badge("PAIR", "#.#", "...", "...");
    private static final Badge ALL = badge("ALL", "###", "###", "###");
    private static final Badge BODIES = badge("BODIES", "##.", "#..", "...");
    private static final Badge STATICS = badge("STATICS", "###", "#.#", "###");
    private static final Badge MODIFIERS = badge("MODIFIERS", ".#.", ".#.", ".#.");
    private static final Badge SPREAD = badge("SPREAD", "#..", ".#.", "..#");
    private static final Badge CROWD = badge("CROWD", "#.#", "#.#", "#.#");
    private static final Badge BOOM = badge("BOOM", "###", ".#.", "###");

    // ---- the tables ------------------------------------------------------------------------------

    private static final Map<VersePrototype.Look, Glyph> LOOKS = new EnumMap<>(VersePrototype.Look.class);
    private static final Map<VerseType, Glyph> TYPES = new EnumMap<>(VerseType.class);
    private static final Map<String, Symbol> TABLE = new LinkedHashMap<>();

    static {
        LOOKS.put(VersePrototype.Look.NEEDLE, NEEDLE);
        LOOKS.put(VersePrototype.Look.ORB, ORB);
        LOOKS.put(VersePrototype.Look.SHARD, SHARD);
        LOOKS.put(VersePrototype.Look.EMBER, FLAME);
        LOOKS.put(VersePrototype.Look.ARC, ARC);
        LOOKS.put(VersePrototype.Look.DART, DART);
        LOOKS.put(VersePrototype.Look.WHISPER, WHISPER);
        LOOKS.put(VersePrototype.Look.BLINK, PORTAL);
        LOOKS.put(VersePrototype.Look.RING, RING);
        LOOKS.put(VersePrototype.Look.BURST, BURST);
        LOOKS.put(VersePrototype.Look.PIT, PIT);
        LOOKS.put(VersePrototype.Look.WORD, WORD);

        TYPES.put(VerseType.PROJECTILE, DART);
        TYPES.put(VerseType.STATIC, RING);
        TYPES.put(VerseType.MODIFIER, PLUS);
        TYPES.put(VerseType.MULTICAST, FAN3);
        TYPES.put(VerseType.MATERIAL, BLOCK);
        TYPES.put(VerseType.CONTROL, CLAUSE);
        TYPES.put(VerseType.UTILITY, PAGE);
        TYPES.put(VerseType.PASSIVE, INFINITY);

        // projectiles
        put("needle", NEEDLE);
        put("needle_latch", NEEDLE, LATCH);
        put("needle_fuse", NEEDLE, FUSE);
        put("needle_twin_latch", NEEDLE, TWIN_LATCH);
        put("wild_bolt", NEEDLE, DICE_MARK);
        put("orb", ORB);
        put("orb_latch", ORB, LATCH);
        put("orb_fuse", ORB, FUSE);
        put("orb_epitaph", ORB, EPITAPH);
        put("shard", SHARD);
        put("ember", FLAME);
        put("arc_bolt", ARC);
        put("balm_dart", DART, PLUS_MARK);
        put("blink_dart", PORTAL);
        put("whisper", WHISPER);
        // statics
        put("balm_ring", RING, PLUS_MARK);
        put("rime_ring", RING, SNOW);
        put("storm_ring", RING, BOLT);
        put("uplift_ring", RING, UP_MARK);
        put("detonation", BURST);
        put("held_word", WORD, BAR);
        put("void_pit", PIT);
        // modifiers
        put("ballast", ANCHOR);
        put("weight", DOWN);
        put("loft", UP);
        put("sink", DOWN, WAVE);
        put("uplift", UP, BAR);
        put("haste", CHEVRONS);
        put("endurance", HOURGLASS);
        put("undying", INFINITY);
        put("second_wind", HEART);
        put("keen_edge", BLADE);
        put("blunt", BLOCK);
        put("puncture", PUNCTURE);
        put("seeker", CROSSHAIR);
        put("true_aim", TARGET);
        put("sightline", EYE);
        put("errant", ZIGZAG);
        put("serpentine", SERPENT);
        put("gyre", SPIRAL);
        put("ricochet", BOUNCE);
        put("bursting_ricochet", BOUNCE, BOOM);
        put("twin_path", TWIN);
        put("relay", RELAY);
        put("naught", NAUGHT);
        put("lantern", LANTERN);
        put("displace", DISPLACE);
        put("volatile", SPARK);
        put("wellspring", DROP);
        put("fire_wake", WAKE, FLAME_MARK);
        put("frost_wake", WAKE, SNOW);
        put("water_wake", WAKE, WAVE);
        put("flame_wreath", WREATH, FLAME_MARK);
        put("rime_wreath", WREATH, SNOW);
        put("storm_wreath", WREATH, BOLT);
        put("umbral_wreath", WREATH, DARK);
        put("wild_mark", QUESTION);
        // multicasts
        put("couplet", FAN2);
        put("loose_couplet", FAN2, SPREAD);
        put("tercet", FAN3);
        put("loose_tercet", FAN3, SPREAD);
        put("trident", TRIDENT);
        put("quatrain", QUATRAIN);
        put("pentacle", PENTACLE);
        put("hexad", HEXAD);
        put("octave", OCTAVE);
        put("column", COLUMN);
        put("cleft", CLEFT);
        put("mirror", MIRROR);
        put("epic", EPIC);
        // controls
        put("reprise", REPRISE);
        put("refrain_2", REFRAIN, 2);
        put("refrain_3", REFRAIN, 3);
        put("refrain_4", REFRAIN, 4);
        put("refrain_10", REFRAIN, MARKS_BAR);
        put("recall_first", RECALL, FIRST);
        put("recall_last", RECALL, LAST);
        put("recall_pair", RECALL, PAIR);
        put("recall_all", RECALL, ALL);
        put("recall_projectiles", RECALL, BODIES);
        put("recall_statics", RECALL, STATICS);
        put("recall_modifiers", RECALL, MODIFIERS);
        put("blind_draw", BLIND, 1);
        put("blind_trio", BLIND, 3);
        put("clause_crowded", CLAUSE, CROWD);
        put("clause_outnumbered", CLAUSE, ALL);
        put("clause_wounded", CLAUSE, CROSS);
        put("clause_every_other", CLAUSE, PAIR);
        put("end_clause", CLAUSE_END);
        put("otherwise", FORK);
        put("impose_latch", IMPOSE, LATCH);
        put("impose_fuse", IMPOSE, FUSE);
        put("impose_epitaph", IMPOSE, EPITAPH);
        put("wild_verse", DICE);
        put("wild_recall", DICE, RECALL_MARK);
        // utilities
        put("fresh_page", PAGE);
        put("far_word", WORD, ARROW);
        put("near_word", WORD, DOT);
        put("step_word", WORD, STAIRS);
        put("blood_toll", DROP, CROSS);
    }

    private VerseSymbols() {
    }

    // ---- reading ---------------------------------------------------------------------------------

    /** The symbol a verse wears: its own row, else its look's glyph, else its type's. */
    public static Symbol of(Verse verse) {
        VersePrototype.Look look = verse.hasPrototype() ? verse.prototype().look() : null;
        return symbolFor(verse.path(), look, verse.type());
    }

    static Symbol symbolFor(String path, VersePrototype.Look look, VerseType type) {
        Symbol own = TABLE.get(path);
        if (own != null) {
            return own;
        }
        if (look != null) {
            return new Symbol(LOOKS.get(look), null, 0);
        }
        return new Symbol(TYPES.get(type), null, 0);
    }

    /** The glyph the category row shows for a type, and the last fallback for a verse of it. */
    public static Glyph forType(VerseType type) {
        return TYPES.get(type);
    }

    /** The glyph of the category that is no filter at all. */
    public static Glyph all() {
        return EVERY;
    }

    /** The glyph a body of this look wears when its verse has no row of its own. */
    public static Glyph lookGlyph(VersePrototype.Look look) {
        return LOOKS.get(look);
    }

    /**
     * The top row for a count: one to four spaced across it, five to nine filled from the left,
     * {@link #MARKS_BAR} or more a full bar.
     */
    public static int marksRow(int marks) {
        if (marks <= 0) {
            return 0;
        }
        if (marks >= MARKS_BAR) {
            return 0b111111111;
        }
        return switch (marks) {
            case 1 -> 0b000010000;
            case 2 -> 0b001000100;
            case 3 -> 0b010010010;
            case 4 -> 0b101000101;
            default -> ((1 << marks) - 1) << (SIZE - marks);
        };
    }

    /** Every verse with a row of its own, by path, in table order. */
    public static Map<String, Symbol> table() {
        return Collections.unmodifiableMap(TABLE);
    }

    // ---- building --------------------------------------------------------------------------------

    private static void put(String path, Glyph glyph) {
        put(path, new Symbol(glyph, null, 0));
    }

    private static void put(String path, Glyph glyph, Badge badge) {
        put(path, new Symbol(glyph, badge, 0));
    }

    private static void put(String path, Glyph glyph, int marks) {
        put(path, new Symbol(glyph, null, marks));
    }

    private static void put(String path, Symbol symbol) {
        if (TABLE.put(path, symbol) != null) {
            throw new IllegalStateException(path + " is in the table twice");
        }
    }

    private static Glyph glyph(String name, String... art) {
        return new Glyph(name, parse(name, SIZE, art));
    }

    private static Badge badge(String name, String... art) {
        return new Badge(name, parse(name, BADGE_SIZE, art));
    }

    /** Rows of {@code #} and {@code .}, {@code size} of them each {@code size} wide, into bits with the leftmost pixel highest. */
    private static int[] parse(String name, int size, String[] art) {
        if (art.length != size) {
            throw new IllegalArgumentException(name + " has " + art.length + " rows, not " + size);
        }
        int[] rows = new int[size];
        for (int r = 0; r < size; r++) {
            String line = art[r];
            if (line.length() != size) {
                throw new IllegalArgumentException(name + " row " + r + " is " + line.length() + " wide, not " + size);
            }
            int bits = 0;
            for (int c = 0; c < size; c++) {
                char pixel = line.charAt(c);
                if (pixel == '#') {
                    bits |= 1 << (size - 1 - c);
                } else if (pixel != '.') {
                    throw new IllegalArgumentException(name + " row " + r + " holds '" + pixel + "'");
                }
            }
            rows[r] = bits;
        }
        return rows;
    }
}
