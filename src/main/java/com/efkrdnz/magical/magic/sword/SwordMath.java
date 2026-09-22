package com.efkrdnz.magical.magic.sword;

/**
 * Every damage number in the kit, in one file, so a Sword God's volley can be added up by reading
 * rather than by playing.
 *
 * <p><b>The only input is a count of swords.</b> There used to be two - an Edge per station and a
 * strain over the whole Array - and the ceiling took a paragraph to state. Twelve swords at
 * {@link #BLADE_BASE} is 60 half-hearts if every one of them lands, the fuse caps at
 * {@link #SLASH_CAP} and the blast at {@link #BLAST_CAP}, and that is the whole of it. The point
 * of gathering the arithmetic here is that the ceiling is a thing which can be signed off rather
 * than discovered.
 *
 * <p>Inputs are clamped at zero. A negative count is a bug upstream, and healing somebody with a
 * sword is a worse symptom than a number that reads a little low.
 */
public final class SwordMath {

    /** What one sword landing is worth. The kit's progression is how many of them you have. */
    public static final double BLADE_BASE = 5.0D;

    /** A sword cutting the line home, and a long line is a worse place to be standing. */
    public static final double SHED_BASE = 3.0D;

    public static final double SHED_PER_BLOCK = 0.35D;

    public static final double SHED_CAP = 24.0D;

    /** What one interception takes off a blow, before the barrier is asked for anything. */
    public static final double WARD_ABSORB = 4.0D;

    public static final double SLASH_BASE = 6.0D;

    public static final double SLASH_PER_SWORD = 3.5D;

    public static final double SLASH_CAP = 48.0D;

    /** The blast is the slash spent all at once, and it ends the fusion. */
    public static final double BLAST_FACTOR = 1.5D;

    public static final double BLAST_CAP = 72.0D;

    /** The greatsword's length in blocks: four swords is 4.3, the full twelve is 7.9. */
    public static final double ONE_BLADE_BASE_REACH = 2.5D;

    public static final double ONE_BLADE_REACH_PER_SWORD = 0.45D;

    public static final double ONE_BLADE_BASE_ARC = 60.0D;

    public static final double ONE_BLADE_ARC_PER_SWORD = 7.0D;

    public static final double ONE_BLADE_ARC_CAP = 160.0D;

    /** Sword Heart: your pool is your steel. */
    public static final int MANA_PER_SWORD = 3;

    /** Twelve swords present, and {@code 3 * 12} is exactly this. */
    public static final int SWORD_HEART_CAP = 36;

    private SwordMath() {
    }

    /** One sword landing. Flat, because the count is the only variable the kit has left. */
    public static double bladeDamage() {
        return BLADE_BASE;
    }

    /** A sword cutting its line home, by how long that line was. */
    public static double shedDamage(double lineLength) {
        return Math.min(SHED_CAP, SHED_BASE + SHED_PER_BLOCK * Math.max(0.0D, lineLength));
    }

    /** The greatsword's horizontal cut, off how many swords went into it. */
    public static double oneBladeSlash(int swords) {
        return Math.min(SLASH_CAP, SLASH_BASE + SLASH_PER_SWORD * Math.max(0, swords));
    }

    /** The release: everything at once, and the formation is empty afterwards. */
    public static double oneBladeBlast(int swords) {
        return Math.min(BLAST_CAP, oneBladeSlash(swords) * BLAST_FACTOR);
    }

    /** Blocks. The greatsword is made of the swords that collapsed, so its length is their count. */
    public static double oneBladeReach(int swords) {
        return ONE_BLADE_BASE_REACH + ONE_BLADE_REACH_PER_SWORD * Math.max(0, swords);
    }

    /** Degrees of arc, for the same reason and off the same number. */
    public static double oneBladeArc(int swords) {
        return Math.min(ONE_BLADE_ARC_CAP,
                ONE_BLADE_BASE_ARC + ONE_BLADE_ARC_PER_SWORD * Math.max(0, swords));
    }

    /**
     * Sword Heart: {@code min(36, 3 * present)}.
     *
     * <p>Every skill that sends steel away lowers the wielder's own ceiling, and every sword that
     * comes home refills the mage. An opponent counting the swords round a Summoner's head is
     * reading their mana bar.
     */
    public static int bonusMaxMana(int presentSwords) {
        return Math.min(SWORD_HEART_CAP, MANA_PER_SWORD * Math.max(0, presentSwords));
    }
}
