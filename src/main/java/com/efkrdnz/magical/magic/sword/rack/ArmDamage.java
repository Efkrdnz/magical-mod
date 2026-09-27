package com.efkrdnz.magical.magic.sword.rack;

import com.efkrdnz.magical.magic.sword.SwordMath;

/**
 * What a racked weapon does to its sword's hit, and it is the whole of the hit.
 *
 * <p><b>The kit's numbers are written in units of one sword landing.</b> {@code SwordMath} counts
 * in {@link SwordMath#BLADE_BASE}: a volley is that once a sword, Below bills it once a sunk sword,
 * and One Blade and the stances' own cuts are multiples of it. A racked weapon replaces the unit
 * with its own hit - {@link #scale} is that hit over the unit - so one sword of a 230 weapon lands
 * 230, and every move in the kit keeps the proportions it was balanced at. It used to land the unit
 * and a flavour of at most fifteen percent, which is how a 230 sword came to hit for six.
 *
 * <p>Pure, like {@code SwordMath}: {@code SwordArms} reads the item and the body, and hands the
 * numbers here.
 */
public final class ArmDamage {

    /** The attack damage attribute's own ceiling, which vanilla clamps every read of it to. */
    public static final double ATTACK_CEILING = 2048.0D;

    private ArmDamage() {
    }

    /**
     * A weapon's attack as its tooltip reads it: the wielder's base, plus the item's additions,
     * then its base multipliers on that sum, then its total multipliers on everything.
     *
     * <p>The order {@code AttributeInstance.calculateValue} runs and the clamp
     * {@code RangedAttribute.sanitizeValue} applies, so a NaN is the attribute's floor rather than
     * a number that poisons every hit after it.
     *
     * @param multiplyTotal the product of {@code 1 + amount} over every total multiplier; 1 for none
     */
    public static double attack(double base, double add, double multiplyBase, double multiplyTotal) {
        double sum = base + add;
        double value = (sum + sum * multiplyBase) * multiplyTotal;
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(ATTACK_CEILING, value));
    }

    /**
     * What one sword multiplies the kit's number by: its weapon's hit, over the kit's unit.
     *
     * <p>A hit that is not a number reads as the unit, so a broken modifier from another mod costs
     * that sword its weapon rather than giving it an infinite one; a negative hit is nothing.
     */
    public static double scale(double weaponHit) {
        if (!Double.isFinite(weaponHit)) {
            return 1.0D;
        }
        return Math.max(0.0D, weaponHit) / SwordMath.BLADE_BASE;
    }

    /**
     * The scale of a wound made of {@code swords} swords, {@code scales.length} of which carry a
     * weapon: the mean, with a sword that carries nothing counted at the unit.
     *
     * <p>The mean and not the sum, because the kit's number for a wound of many swords is already
     * the unit times how many there are, so the mean over those swords is exactly what turns that
     * bill into the sum of their weapons.
     */
    public static double mean(double[] scales, int swords) {
        int count = Math.max(swords, scales.length);
        if (count <= 0) {
            return 1.0D;
        }
        double sum = count - scales.length;
        for (double scale : scales) {
            sum += Math.max(0.0D, scale);
        }
        return sum / count;
    }
}
