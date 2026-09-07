package com.efkrdnz.magical.entity.ascendant;

import java.util.Optional;

/**
 * The five authored opponent tiers above the clone, 6 through 10.
 *
 * <p>Difficulties 0-5 are a copy of the player who spawned them: their stats come from three linear
 * formulas and their spells come from whatever that player happens to know. That works as a sparring
 * partner and stops working as a boss, because every difficulty branch in the opponent is a
 * {@code >=} threshold that saturates at 5 - raising the cap alone would give levels 6-10 nothing
 * but more health and a four-tick cast interval.
 *
 * <p>So these tiers are a table rather than a curve, and an Ascendant is the same at tier 8 for
 * every player who meets one. Minecraft-free on purpose: the numbers are the design, and a plain
 * enum is the only part of an entity that can be pinned by a unit test without a running world.
 *
 * @param tier               the difficulty number the command and NBT store
 * @param path               id path, for lang keys
 * @param health             max health; tier 6 is a wall after level 5's 74, not a step
 * @param armour             scaled for the first time here - the clone's is flat 4.0 at every level
 * @param attack             melee attack damage before the weapon's own contribution
 * @param speed              movement speed; a zombie is 0.23, so these stay deliberately modest
 * @param knockbackResistance also flat on the clone, at 0.18
 * @param maxMana            the pool the roster and flight both spend from
 * @param manaPerSecond      regeneration, applied once a second like the clone's
 * @param burstSpells        spells cast back to back before the boss must recover
 * @param burstGapTicks      spacing inside a burst; meaningless when {@code burstSpells} is 1
 * @param recoveryTicks      the opening: no casting, and the only safe time to close
 * @param counterWindowTicks how long the player has to answer this tier's telegraph
 * @param failDamageFraction share of the player's <em>max</em> health a missed counter costs, so
 *                           "survivable at full health" holds at every gear level
 * @param cooldownRate       ticks of skill cooldown burned per game tick
 * @param stance             where this tier wants to stand, which its blade should agree with
 */
public enum AscendantTier {

    ECHO(6, "echo", 180.0D, 8.0D, 14.0D, 0.330D, 0.35D, 400, 10, 1, 0, 40, 20, 0.35F, 4,
            AscendantStance.SKIRMISHER),
    SIN_EATER(7, "sin_eater", 240.0D, 10.0D, 17.0D, 0.340D, 0.45D, 500, 13, 2, 6, 36, 18, 0.42F, 5,
            AscendantStance.DUELIST),
    FALLEN(8, "fallen", 320.0D, 12.0D, 20.0D, 0.350D, 0.55D, 650, 16, 3, 6, 32, 16, 0.50F, 6,
            AscendantStance.ARTILLERY),
    BLACK_FLAME(9, "black_flame", 420.0D, 14.0D, 24.0D, 0.360D, 0.65D, 800, 20, 4, 5, 28, 13, 0.58F, 7,
            AscendantStance.EXECUTIONER),
    AUTHORITY(10, "authority", 560.0D, 16.0D, 29.0D, 0.370D, 0.75D, 1000, 25, 4, 4, 24, 10, 0.65F, 8,
            AscendantStance.AUTHORITY);

    /** Lowest difficulty that is an Ascendant rather than a clone. */
    public static final int MIN_TIER = 6;

    /** Highest difficulty the command and the tower will accept. */
    public static final int MAX_TIER = 10;

    private final int tier;
    private final String path;
    private final double health;
    private final double armour;
    private final double attack;
    private final double speed;
    private final double knockbackResistance;
    private final int maxMana;
    private final int manaPerSecond;
    private final int burstSpells;
    private final int burstGapTicks;
    private final int recoveryTicks;
    private final int counterWindowTicks;
    private final float failDamageFraction;
    private final int cooldownRate;
    private final AscendantStance stance;

    AscendantTier(int tier, String path, double health, double armour, double attack, double speed,
            double knockbackResistance, int maxMana, int manaPerSecond, int burstSpells,
            int burstGapTicks, int recoveryTicks, int counterWindowTicks, float failDamageFraction,
            int cooldownRate, AscendantStance stance) {
        this.tier = tier;
        this.path = path;
        this.health = health;
        this.armour = armour;
        this.attack = attack;
        this.speed = speed;
        this.knockbackResistance = knockbackResistance;
        this.maxMana = maxMana;
        this.manaPerSecond = manaPerSecond;
        this.burstSpells = burstSpells;
        this.burstGapTicks = burstGapTicks;
        this.recoveryTicks = recoveryTicks;
        this.counterWindowTicks = counterWindowTicks;
        this.failDamageFraction = failDamageFraction;
        this.cooldownRate = cooldownRate;
        this.stance = stance;
    }

    /** Whether {@code difficulty} names an Ascendant rather than a clone of the player. */
    public static boolean isAscendant(int difficulty) {
        return difficulty >= MIN_TIER && difficulty <= MAX_TIER;
    }

    /** The tier a difficulty names, or empty when it is a clone difficulty or out of range. */
    public static Optional<AscendantTier> byTier(int difficulty) {
        for (AscendantTier value : values()) {
            if (value.tier == difficulty) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public int tier() {
        return tier;
    }

    public String path() {
        return path;
    }

    /** Translation key of the name shown above the entity. */
    public String nameKey() {
        return "entity.magical.ascendant." + path;
    }

    public double health() {
        return health;
    }

    public double armour() {
        return armour;
    }

    public double attack() {
        return attack;
    }

    public double speed() {
        return speed;
    }

    public double knockbackResistance() {
        return knockbackResistance;
    }

    public int maxMana() {
        return maxMana;
    }

    public int manaPerSecond() {
        return manaPerSecond;
    }

    public int burstSpells() {
        return burstSpells;
    }

    public int burstGapTicks() {
        return burstGapTicks;
    }

    public int recoveryTicks() {
        return recoveryTicks;
    }

    public int counterWindowTicks() {
        return counterWindowTicks;
    }

    public float failDamageFraction() {
        return failDamageFraction;
    }

    /**
     * Ticks of skill cooldown an Ascendant burns per game tick.
     *
     * <p>The tier-4 roster it draws from was priced for a player who casts one of these a minute:
     * Crucible alone is 1200 ticks. A boss on player cooldowns would open with its whole hand and
     * then stand there unarmed for the rest of the fight, which is not a fight. Burning cooldown
     * several times faster is what lets the burst-and-recover rhythm in {@link #recoveryTicks}
     * actually repeat, and it scales with the tier so the top of the table presses hardest.
     */
    public int cooldownRate() {
        return cooldownRate;
    }

    /** Where this tier wants to stand, and therefore when it leaves the ground. */
    public AscendantStance stance() {
        return stance;
    }

    /**
     * What a missed telegraph costs a player with {@code maxHealth} hearts.
     *
     * <p>Read off max rather than current health so the promise holds at every tier: a player at
     * full health always lives through one miss, whatever their gear. Scaling off current health
     * instead would make the first miss nearly free and the last one unavoidable.
     */
    public float failDamage(float maxHealth) {
        return Math.max(1.0F, maxHealth * failDamageFraction);
    }
}
