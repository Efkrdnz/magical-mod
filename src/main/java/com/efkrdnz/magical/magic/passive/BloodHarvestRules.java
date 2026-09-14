package com.efkrdnz.magical.magic.passive;

import net.minecraft.util.Mth;

/**
 * The numbers the harvest runs on: what a corpse is worth, how long its blood waits, how far it
 * will come, and how long it takes to get there.
 *
 * <p>Pure, so the server that pays and the renderer that draws read the same table. Nothing in here
 * knows about entities or players; {@code BloodHarvestEntity} is where these become behaviour.
 */
public final class BloodHarvestRules {

    /**
     * Ticks a pool waits at the corpse before it dries. This was the mote's lifetime before there
     * were pools, and Vein Walk's players have learned it, so it is inherited rather than chosen.
     */
    public static final int POOL_LIFETIME = 300;

    /**
     * The window before {@link #POOL_LIFETIME} in which a pool is drying and may no longer lift.
     * The renderer's dissolve has to fit inside it; a test holds the two together.
     */
    public static final int DRYING_TICKS = 10;

    /** Pools one player may have waiting at once. Past this, the oldest is spent. */
    public static final int MAX_POOLS = 32;

    /** How near the owner has to be for a pool to lift, in blocks. */
    public static final double PULL_RANGE = 12.0D;

    /** Bloodscent's reveal range, which is also how far it pulls: what it can see, it can take. */
    public static final double BLOODSCENT_PULL_RANGE = 20.0D;

    /** Vessel one drop of blood is worth. A doubled kill leaves two. */
    public static final int VESSEL_PER_DROP = 12;

    public static final int MIN_FLIGHT_TICKS = 12;
    public static final int MAX_FLIGHT_TICKS = 40;
    private static final float FLIGHT_BASE_TICKS = 10.0F;
    private static final float FLIGHT_TICKS_PER_BLOCK = 1.2F;

    /** How long the overflow burst plays at the chest once the stream has landed. */
    public static final int OVERFLOW_TICKS = 10;

    /** Cubes drawn per point of Vessel, so a richer pool is visibly a bigger one. */
    public static final int CUBES_PER_POINT = 4;

    private BloodHarvestRules() {
    }

    /**
     * Ticks a stream takes from lift to landing, fixed at the moment it lifts.
     *
     * <p>Grows with distance so far blood is seen to travel, and is capped so a lance kill across
     * a room does not leave the player waiting on their own payout.
     */
    public static int flightTicks(double distance) {
        int flight = Math.round(FLIGHT_BASE_TICKS + FLIGHT_TICKS_PER_BLOCK * (float) Math.max(0.0D, distance));
        return Mth.clamp(flight, MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS);
    }

    public static double pullRange(boolean bloodscent) {
        return bloodscent ? BLOODSCENT_PULL_RANGE : PULL_RANGE;
    }

    public static int yield(int drops) {
        return Math.max(0, drops) * VESSEL_PER_DROP;
    }

    public static int cubes(int yield) {
        return Math.max(1, yield * CUBES_PER_POINT);
    }

    /** Whether a pool of this age may still lift, or has begun to dry and must be left to it. */
    public static boolean canLift(int poolAge) {
        return poolAge < POOL_LIFETIME - DRYING_TICKS;
    }
}
