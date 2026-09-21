package com.efkrdnz.magical.magic.sword;

/**
 * Every damage number in the kit, in one file, so the Sword God volley can be added up by reading
 * rather than by playing.
 *
 * <p>Twelve stations at 3 Edge with 60 strain is {@code 12 * (2.0 + 6.0 + 15.0)} = 276 half-hearts
 * if every blade lands, and it will not: the forward projection is at most half of a ring, the
 * fuse caps at 48 and the blast at 72, the strain term caps at +24, and the wielder is bleeding
 * the whole time. The point of gathering the arithmetic here is that the ceiling is a thing that
 * can be signed off rather than discovered.
 *
 * <p>Inputs are clamped at zero. A negative Edge or a negative strain is a bug upstream, and
 * healing somebody with a sword is a worse symptom than a number that reads a little low.
 */
public final class SwordMath {

    /** What a blade is worth before any metal is on it. */
    public static final double BLADE_BASE = 2.0D;

    public static final double BLADE_PER_EDGE = 2.0D;

    /** Sword God: every point of strain sharpens every blade, and this is the rate. */
    public static final double STRAIN_PER_POINT = 0.25D;

    /** ...and this is the ceiling on it, which is what keeps an apex from being an exponent. */
    public static final double STRAIN_CAP = 24.0D;

    /** A shed blade cuts the line home, and a long line is a worse place to be standing. */
    public static final double SHED_BASE = 3.0D;

    public static final double SHED_PER_BLOCK = 0.35D;

    public static final double SHED_CAP = 24.0D;

    /** Ward: what one interception takes off a blow, before the barrier is asked for anything. */
    public static final double WARD_BASE = 2.0D;

    public static final double WARD_PER_EDGE = 1.5D;

    public static final double SLASH_BASE = 6.0D;

    public static final double SLASH_PER_EDGE = 1.5D;

    public static final double SLASH_CAP = 48.0D;

    /** The blast is the slash spent all at once, and it ends the fusion. */
    public static final double BLAST_FACTOR = 1.5D;

    public static final double BLAST_CAP = 72.0D;

    /** The greatsword's length in blocks: 8 Edge is 3.94, the full 36 is 8.98. */
    public static final double ONE_BLADE_BASE_REACH = 2.5D;

    public static final double ONE_BLADE_REACH_PER_EDGE = 0.18D;

    public static final double ONE_BLADE_BASE_ARC = 60.0D;

    public static final double ONE_BLADE_ARC_PER_EDGE = 3.0D;

    public static final double ONE_BLADE_ARC_CAP = 160.0D;

    /** Sword Heart: your pool is your blades. */
    public static final int MANA_PER_STATION = 3;

    /** Twelve manned stations, and {@code 3 * 12} is exactly this. */
    public static final int SWORD_HEART_CAP = 36;

    private SwordMath() {
    }

    /**
     * One blade landing: {@code 2 + 2 * edge + 0.25 * strain}, the strain term capped at +24.
     *
     * <p>The cap is on the term and not on the total, so metal always beats strain: a Sword God
     * who has stopped planting is a Sword God whose damage has stopped growing.
     */
    public static double bladeDamage(int edge, int strain) {
        return BLADE_BASE + BLADE_PER_EDGE * Math.max(0, edge) + strainBonus(strain);
    }

    /** A reflection carries half the metal, and never nothing at all. */
    public static double mirrorDamage(int edge, int strain) {
        return Math.max(1.0D, bladeDamage(Math.max(0, edge) / 2, strain));
    }

    /** A blade cutting its line home, by how long that line was. */
    public static double shedDamage(double lineLength) {
        return Math.min(SHED_CAP, SHED_BASE + SHED_PER_BLOCK * Math.max(0.0D, lineLength));
    }

    /** What one station turns aside, spending one Edge to do it. */
    public static double wardAbsorb(int edge) {
        return WARD_BASE + WARD_PER_EDGE * Math.max(0, edge);
    }

    /** The greatsword's horizontal cut, off the summed Edge that went into it. */
    public static double oneBladeSlash(int totalEdge, int strain) {
        return Math.min(SLASH_CAP,
                SLASH_BASE + SLASH_PER_EDGE * Math.max(0, totalEdge) + STRAIN_PER_POINT * Math.max(0, strain));
    }

    /** The release: everything at once, and the Array is empty afterwards. */
    public static double oneBladeBlast(int totalEdge, int strain) {
        return Math.min(BLAST_CAP, oneBladeSlash(totalEdge, strain) * BLAST_FACTOR);
    }

    /** Blocks. The greatsword is made of the blades that collapsed, so its length is their Edge. */
    public static double oneBladeReach(int totalEdge) {
        return ONE_BLADE_BASE_REACH + ONE_BLADE_REACH_PER_EDGE * Math.max(0, totalEdge);
    }

    /** Degrees of arc, for the same reason and off the same number. */
    public static double oneBladeArc(int totalEdge) {
        return Math.min(ONE_BLADE_ARC_CAP, ONE_BLADE_BASE_ARC + ONE_BLADE_ARC_PER_EDGE * Math.max(0, totalEdge));
    }

    /**
     * Sword Heart: {@code min(36, 3 * manned)}.
     *
     * <p>Every skill that spends steel lowers the wielder's own ceiling, and re-manning refills
     * the mage. An opponent counting blades is reading the mana bar.
     */
    public static int bonusMaxMana(int mannedStations) {
        return Math.min(SWORD_HEART_CAP, MANA_PER_STATION * Math.max(0, mannedStations));
    }

    private static double strainBonus(int strain) {
        return Math.min(STRAIN_CAP, STRAIN_PER_POINT * Math.max(0, strain));
    }
}
