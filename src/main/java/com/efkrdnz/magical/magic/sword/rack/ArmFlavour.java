package com.efkrdnz.magical.magic.sword.rack;

/**
 * How much a racked weapon adds to the sword that carries it, as arithmetic.
 *
 * <p><b>Flavour, not power.</b> The kit is priced on one number - how many swords the wielder can
 * see - and {@code SwordMath} is every damage figure it has. A rack that let a netherite blade or a
 * Sharpness V book double that would make the rack the build and the count a formality, so a
 * weapon may lift its sword's hit by at most {@link #CAP}, fifteen percent, and each of the three
 * things a weapon can bring has a smaller ceiling of its own inside that. Its element and its
 * enchantments' on-hit effects - fire, a frost rider, a storm's arc - are the real difference, and
 * they are not numbers here at all.
 *
 * <ul>
 *   <li><b>Its edge</b>: the weapon's own attack bonus past a wooden sword's +3, a fortieth a
 *       point, capped at {@link #ATTACK_CAP}. Iron is +5%, diamond +7.5%, netherite the cap.</li>
 *   <li><b>Its enchantments</b>: whatever they would add to a hit on this body, a hundredth a
 *       point, capped at {@link #ENCHANT_CAP}. Sharpness V is +3%.</li>
 *   <li><b>Its forging</b>: a hundredth a grade past Crude, capped at {@link #GRADE_CAP}.</li>
 * </ul>
 *
 * <p>Pure, and total over its inputs: a negative, an infinity or a NaN - which is what an attribute
 * modifier from somebody else's mod can be - reads as nothing rather than as a multiplier.
 */
public final class ArmFlavour {

    /** A wooden sword's own attack bonus, which earns nothing: a stick is not an upgrade. */
    public static final double ATTACK_FLOOR = 3.0D;

    public static final double PER_ATTACK = 0.025D;
    public static final double ATTACK_CAP = 0.10D;

    public static final double PER_ENCHANT = 0.01D;
    public static final double ENCHANT_CAP = 0.05D;

    public static final double PER_GRADE = 0.01D;
    public static final double GRADE_CAP = 0.04D;

    /** The most any one weapon adds, whatever it is. */
    public static final double CAP = 0.15D;

    private ArmFlavour() {
    }

    /**
     * What one weapon multiplies its sword's hit by.
     *
     * @param attack the weapon's main-hand attack bonus, the {@code +N} on its tooltip
     * @param enchant what its enchantments add to a hit on the body being struck
     * @param grade its forge grade's ordinal, Crude 0, or negative for a weapon never forged
     */
    public static double scale(double attack, double enchant, int grade) {
        double edge = part((finite(attack) - ATTACK_FLOOR) * PER_ATTACK, ATTACK_CAP);
        double runes = part(finite(enchant) * PER_ENCHANT, ENCHANT_CAP);
        double forged = part(Math.max(0, grade) * PER_GRADE, GRADE_CAP);
        return 1.0D + Math.min(CAP, edge + runes + forged);
    }

    /**
     * The mean over {@code swords} swords of which {@code scales} carry a weapon: the rest are plain
     * steel and count as one. Below and One Blade land as one wound made of many swords, and a
     * wound of twelve with one diamond blade in it is one twelfth of a diamond blade better.
     */
    public static double mean(double[] scales, int swords) {
        int count = Math.max(swords, scales.length);
        if (count <= 0) {
            return 1.0D;
        }
        double sum = count - scales.length;
        for (double one : scales) {
            sum += Math.max(1.0D, Math.min(1.0D + CAP, finite(one)));
        }
        return sum / count;
    }

    private static double part(double value, double cap) {
        return Math.max(0.0D, Math.min(cap, value));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
