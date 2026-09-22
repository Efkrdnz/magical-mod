package com.efkrdnz.magical.magic.sword.stance;

/**
 * What the swords do while nobody is pressing anything.
 *
 * <p>This is the half the class was asked for by name - <em>swords doing things by themselves</em>
 * - and it is what makes a stance a decision rather than a skin. One per stance, and the whole of
 * the difference between them is the four numbers on the enum constant plus the one branch in
 * {@code StanceWatchService} that reads it.
 *
 * <p><b>Three of the six spend a sword and three do not</b>, and that is the balance, stated once:
 * a behaviour that sends steel away leaves the wielder with fewer swords for Loose, One Blade and
 * the Keel, so it is allowed to be worth more. {@link #SHEAR} and {@link #SHRED} are the formation
 * itself cutting and cost nothing, which is why their numbers are small; {@link #GLIDE} spends
 * nothing because it is a posture rather than an attack.
 */
public enum Watch {

    /**
     * Guard. A closing projectile or an incoming blow inside the guarded arc is met by a sword,
     * which goes away for its trouble. Ward of the Array, moved off the lattice onto the formation.
     */
    INTERCEPT(10, 8.0D, 2.0F, true),

    /** Vanguard. A hostile held in the crosshair darts a sword at itself, which returns. */
    STAB(30, 18.0D, 4.0F, true),

    /** Crown. Everything hostile inside the ring is cut on the interval. The ring is the attack. */
    SHEAR(20, 2.6D, 2.5F, false),

    /** Wings. The descent is slowed and the fall cancelled; a melee strike is followed home. */
    GLIDE(0, 0.0D, 3.0F, false),

    /** Coil. Anything that touches the wielder is cut and thrown off. */
    SHRED(12, 1.8D, 2.0F, false),

    /** Rain. A body the wielder wounds has a sword come down on it. */
    DROP(25, 20.0D, 5.0F, true);

    private final int interval;
    private final double range;
    private final float bite;
    private final boolean spendsASword;

    Watch(int interval, double range, float bite, boolean spendsASword) {
        this.interval = interval;
        this.range = range;
        this.bite = bite;
        this.spendsASword = spendsASword;
    }

    /**
     * Ticks between two of these, before the apex rung halves it. Zero means it is not on a clock
     * at all - {@link #GLIDE} is a state of the wielder and answers every tick or not at all.
     */
    public int interval() {
        return interval;
    }

    /** Blocks. What it reaches, or zero where the behaviour is about the wielder's own body. */
    public double range() {
        return range;
    }

    /** Damage, before the skill's own scaling. */
    public float bite() {
        return bite;
    }

    /** Whether one firing costs a present sword. Three do and three do not - see the class note. */
    public boolean spendsASword() {
        return spendsASword;
    }

    /** Sword God halves it, with a floor of one tick - and a zero interval stays zero. */
    public int intervalAt(boolean relentless) {
        if (interval <= 0) {
            return 0;
        }
        return relentless ? Math.max(1, interval / 2) : interval;
    }
}
