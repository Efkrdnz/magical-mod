package com.efkrdnz.magical.forge.visual;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What each element throws off a forged blade as it swings: which matter, how much of the shower
 * each is, how fast and which way it leaves the lip, and what colours it runs through.
 *
 * <p>The smear atlas says what an element's blade looks like; this says what comes off it. Fire
 * throws coals and bouncing sparks and a little smoke, frost throws flakes and chips, venom drops
 * that splat where they land, void hollows that fall back into the cut. None of it is drawn on
 * the blade any more: it is real particles, lit by the world once it has stopped burning, so it has
 * weight and lands on things.
 *
 * <p>Pure, and exhaustive over {@link ForgeElementKind}: a new element fails to compile here until
 * it is given matter.
 */
public final class ForgeMatter {

    /** Which way a piece of matter leaves the lip, in the blade's own frame. */
    public enum Launch {
        /** On along the swing, flung off the way the blade was going. */
        ALONG,
        /** Straight out from the arc, away from the wielder. */
        OUT,
        /** Born just outside the lip and pulled back into it. */
        IN,
        /** Up, whatever the blade is doing: smoke, light. */
        UP
    }

    /** A colour a stop resolves to, out of the strike's palette or a fixed one. */
    public enum Ink { WHITE, EDGE, PRIMARY, SECONDARY, SOOT }

    /** The three colours a particle runs through: its hot one, its body, its last. */
    public record Stops(Ink hot, Ink body, Ink cool) {}

    /**
     * One share of a shower.
     *
     * @param weight how much of the shower this is, against the other shares of the same element
     * @param speed  blocks a tick at the lip, before each particle's own spread about it
     * @param cone   how far off its launch direction it may leave: 0 a line, 1 a hemisphere
     */
    public record Emission(MatterKind kind, int weight, float speed, float cone, Launch launch, Stops stops) {}

    /** The shower at the bottom of the grade ladder and at the top of it. */
    public static final int FEWEST = 3;
    public static final int MOST = 18;
    /** A heavy blow throws more, an echo less. */
    public static final float HEAVY = 1.5f;
    public static final float ECHO = 0.5f;
    /** The most one swing may throw, whatever the weapon. */
    public static final int SWING_CAP = 30;

    private static final Stops HOT = new Stops(Ink.WHITE, Ink.EDGE, Ink.PRIMARY);
    private static final Stops COAL = new Stops(Ink.EDGE, Ink.PRIMARY, Ink.SOOT);
    private static final Stops BODY = new Stops(Ink.EDGE, Ink.PRIMARY, Ink.SECONDARY);
    private static final Stops EARTH = new Stops(Ink.PRIMARY, Ink.PRIMARY, Ink.SECONDARY);
    private static final Stops PALE = new Stops(Ink.WHITE, Ink.EDGE, Ink.EDGE);
    private static final Stops DARK = new Stops(Ink.SECONDARY, Ink.SECONDARY, Ink.SOOT);

    private static final Map<ForgeElementKind, List<Emission>> SWING = new EnumMap<>(ForgeElementKind.class);

    static {
        for (ForgeElementKind kind : ForgeElementKind.values()) {
            SWING.put(kind, swingOf(kind));
        }
    }

    private ForgeMatter() {}

    /** What a swing of this element throws. Never empty. */
    public static List<Emission> swing(ForgeElementKind kind) {
        return SWING.get(kind);
    }

    /**
     * How much a weapon of this grade throws, before heavy and echo. An ordinal, because it arrives
     * over the wire and an ordinal this build does not know still has to throw something.
     */
    public static int shower(int gradeOrdinal) {
        int grades = ForgeGrade.values().length;
        float t = gradeOrdinal < 0 || gradeOrdinal >= grades ? 0.5f : gradeOrdinal / (float) (grades - 1);
        return Math.round(FEWEST + (MOST - FEWEST) * t);
    }

    /** How much one swing throws in all. */
    public static int swingCount(int gradeOrdinal, boolean heavy, boolean echo) {
        float count = shower(gradeOrdinal) * (heavy ? HEAVY : 1.0f) * (echo ? ECHO : 1.0f);
        return Math.min(SWING_CAP, Math.max(1, Math.round(count)));
    }

    /**
     * Which emission each particle of a shower of {@code total} belongs to, as indices into
     * {@code emissions}: the shares by weight, laid out interleaved so that a shower thinned by the
     * particle setting still has some of every kind in it.
     */
    public static int[] deal(List<Emission> emissions, int total) {
        if (emissions.isEmpty() || total <= 0) {
            return new int[0];
        }
        int[] counts = split(emissions, total);
        int[] dealt = new int[Math.max(0, total)];
        int at = 0;
        for (int round = 0; at < dealt.length; round++) {
            for (int i = 0; i < counts.length && at < dealt.length; i++) {
                if (round < counts[i]) {
                    dealt[at++] = i;
                }
            }
        }
        return dealt;
    }

    /**
     * Which particles of a dealt shower a thinned particle setting still throws: every
     * {@code stride}th of each share, counted within the share, so each kind keeps its first piece.
     * Counting by position in the shower instead lost a whole kind whenever the stride divided the
     * number of shares - two shares dealt alternately, halved, were one.
     */
    public static boolean[] keep(int[] dealt, int shares, int stride) {
        boolean[] kept = new boolean[dealt.length];
        int[] seen = new int[Math.max(0, shares)];
        int step = Math.max(1, stride);
        for (int i = 0; i < dealt.length; i++) {
            int share = dealt[i];
            kept[i] = seen[share] % step == 0;
            seen[share]++;
        }
        return kept;
    }

    /** The share of {@code total} each emission gets, by weight, summing exactly to the total. */
    public static int[] split(List<Emission> emissions, int total) {
        int[] counts = new int[emissions.size()];
        if (total <= 0 || emissions.isEmpty()) {
            return counts;
        }
        int weight = 0;
        for (Emission e : emissions) {
            weight += e.weight();
        }
        float[] remainder = new float[counts.length];
        int given = 0;
        for (int i = 0; i < counts.length; i++) {
            float exact = total * emissions.get(i).weight() / (float) weight;
            counts[i] = (int) exact;
            remainder[i] = exact - counts[i];
            given += counts[i];
        }
        while (given < total) {
            int best = 0;
            for (int i = 1; i < counts.length; i++) {
                if (remainder[i] > remainder[best]) {
                    best = i;
                }
            }
            counts[best]++;
            remainder[best] = -1.0f;
            given++;
        }
        return counts;
    }

    private static List<Emission> swingOf(ForgeElementKind kind) {
        return switch (kind) {
            case FIRE -> List.of(
                    e(MatterKind.EMBER, 3, 0.22f, 0.5f, Launch.ALONG, COAL),
                    e(MatterKind.SPARK, 2, 0.38f, 0.35f, Launch.ALONG, HOT),
                    e(MatterKind.SMOKE, 1, 0.04f, 0.6f, Launch.UP, DARK));
            case FROST -> List.of(
                    e(MatterKind.FLAKE, 3, 0.12f, 0.7f, Launch.ALONG, PALE),
                    e(MatterKind.CHIP, 2, 0.25f, 0.4f, Launch.OUT, BODY),
                    e(MatterKind.GLINT, 1, 0.08f, 0.8f, Launch.ALONG, HOT));
            case STORM -> List.of(
                    e(MatterKind.SPARK, 4, 0.45f, 0.6f, Launch.ALONG, HOT),
                    e(MatterKind.GLINT, 1, 0.1f, 0.8f, Launch.OUT, HOT));
            case VOID -> List.of(
                    e(MatterKind.HOLLOW, 3, 0.12f, 0.5f, Launch.IN, BODY),
                    e(MatterKind.SMOKE, 1, 0.03f, 0.6f, Launch.UP, DARK));
            case RADIANT -> List.of(
                    e(MatterKind.GLINT, 3, 0.1f, 0.8f, Launch.UP, HOT),
                    e(MatterKind.SPARK, 1, 0.3f, 0.4f, Launch.ALONG, HOT));
            case VENOM -> List.of(
                    e(MatterKind.DROP, 3, 0.2f, 0.4f, Launch.ALONG, BODY),
                    e(MatterKind.SMOKE, 1, 0.03f, 0.5f, Launch.UP, BODY));
            case TERRA -> List.of(
                    e(MatterKind.GRIT, 4, 0.28f, 0.4f, Launch.OUT, EARTH),
                    e(MatterKind.CHIP, 1, 0.22f, 0.5f, Launch.OUT, EARTH));
            case GALE -> List.of(
                    e(MatterKind.GUST, 3, 0.35f, 0.2f, Launch.ALONG, PALE),
                    e(MatterKind.ASH, 1, 0.15f, 0.7f, Launch.ALONG, PALE));
            case DARK -> List.of(
                    e(MatterKind.ASH, 2, 0.1f, 0.7f, Launch.ALONG, DARK),
                    e(MatterKind.SMOKE, 2, 0.05f, 0.6f, Launch.UP, DARK),
                    e(MatterKind.HOLLOW, 1, 0.1f, 0.5f, Launch.IN, BODY));
            case BLOOD -> List.of(
                    e(MatterKind.DROP, 4, 0.22f, 0.4f, Launch.ALONG, BODY));
            case BLACK_FLAME -> List.of(
                    e(MatterKind.EMBER, 3, 0.2f, 0.5f, Launch.ALONG, COAL),
                    e(MatterKind.ASH, 2, 0.1f, 0.7f, Launch.UP, DARK),
                    e(MatterKind.SMOKE, 1, 0.04f, 0.6f, Launch.UP, DARK));
            case EXPLOSION -> List.of(
                    e(MatterKind.SPARK, 3, 0.5f, 0.8f, Launch.OUT, HOT),
                    e(MatterKind.EMBER, 2, 0.25f, 0.6f, Launch.OUT, COAL),
                    e(MatterKind.SMOKE, 1, 0.05f, 0.7f, Launch.UP, DARK));
            case RIME_GALE -> List.of(
                    e(MatterKind.FLAKE, 2, 0.2f, 0.5f, Launch.ALONG, PALE),
                    e(MatterKind.GUST, 2, 0.35f, 0.2f, Launch.ALONG, PALE));
            case PLASMA -> List.of(
                    e(MatterKind.SPARK, 3, 0.45f, 0.6f, Launch.ALONG, HOT),
                    e(MatterKind.EMBER, 1, 0.2f, 0.5f, Launch.ALONG, COAL),
                    e(MatterKind.GLINT, 1, 0.1f, 0.8f, Launch.OUT, HOT));
            case MAGMA -> List.of(
                    e(MatterKind.EMBER, 3, 0.2f, 0.5f, Launch.ALONG, COAL),
                    e(MatterKind.GRIT, 2, 0.25f, 0.4f, Launch.OUT, COAL));
            case HAILSTORM -> List.of(
                    e(MatterKind.CHIP, 3, 0.3f, 0.5f, Launch.OUT, BODY),
                    e(MatterKind.SPARK, 1, 0.4f, 0.6f, Launch.ALONG, HOT),
                    e(MatterKind.FLAKE, 1, 0.12f, 0.7f, Launch.ALONG, PALE));
            case ECLIPSE -> List.of(
                    e(MatterKind.HOLLOW, 2, 0.12f, 0.5f, Launch.IN, BODY),
                    e(MatterKind.GLINT, 2, 0.1f, 0.8f, Launch.UP, HOT));
            case BLIGHT -> List.of(
                    e(MatterKind.DROP, 2, 0.18f, 0.4f, Launch.ALONG, BODY),
                    e(MatterKind.SMOKE, 2, 0.04f, 0.6f, Launch.UP, BODY),
                    e(MatterKind.ASH, 1, 0.1f, 0.7f, Launch.ALONG, DARK));
            case VERDIGRIS -> List.of(
                    e(MatterKind.GRIT, 2, 0.25f, 0.4f, Launch.OUT, EARTH),
                    e(MatterKind.DROP, 2, 0.18f, 0.4f, Launch.ALONG, BODY));
            case CORRUPTION -> List.of(
                    e(MatterKind.HOLLOW, 2, 0.12f, 0.5f, Launch.IN, BODY),
                    e(MatterKind.DROP, 2, 0.2f, 0.4f, Launch.ALONG, BODY),
                    e(MatterKind.SMOKE, 1, 0.04f, 0.6f, Launch.UP, DARK));
            case MARTYR -> List.of(
                    e(MatterKind.GLINT, 2, 0.1f, 0.8f, Launch.UP, HOT),
                    e(MatterKind.DROP, 2, 0.2f, 0.4f, Launch.ALONG, BODY));
            case CLOT -> List.of(
                    e(MatterKind.DROP, 3, 0.2f, 0.4f, Launch.ALONG, BODY),
                    e(MatterKind.CHIP, 1, 0.22f, 0.5f, Launch.OUT, BODY));
        };
    }

    private static Emission e(MatterKind kind, int weight, float speed, float cone, Launch launch, Stops stops) {
        return new Emission(kind, weight, speed, cone, launch, stops);
    }
}
