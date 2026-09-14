package com.efkrdnz.magical.magic.passive;

import net.minecraft.util.Mth;

/**
 * The numbers pooled blood runs on: what a corpse is worth, how long a pool waits, how far it will
 * come, how long it takes to get there, and which kinds of pool come at all.
 *
 * <p>Pure, so the server that pays and the renderer that draws read the same table. Nothing in here
 * knows about entities or players; {@code BloodHarvestEntity} is where these become behaviour.
 */
public final class BloodHarvestRules {

    /** What a kill or a bleed leaves: it lifts into the Vessel on its own when the owner is near. */
    public static final byte KIND_HARVEST = 0;

    /** Blood set down on purpose - the Rite, a Spear impact: it waits to be spent. */
    public static final byte KIND_BATTERY = 1;

    /** Where a Vein Walk began: worth nothing, and only ever somewhere to step back to. */
    public static final byte KIND_TRACE = 2;

    /**
     * Ticks a pool waits before it dries. This was the mote's lifetime before there were pools,
     * and Vein Walk's players have learned it, so it is inherited rather than chosen.
     */
    public static final int POOL_LIFETIME = 300;

    /**
     * The window before a pool's life runs out in which it is drying and may no longer lift. The
     * renderer's dissolve has to fit inside it; a test holds the two together.
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

    /**
     * Ticks the stream of a Vein Walk takes from where it began to the walker. The shortest the
     * harvest timeline allows, so the last cube is in the body before the stream is gone.
     */
    public static final int VEIN_FLIGHT_TICKS = MIN_FLIGHT_TICKS;

    /** Cubes a Vein Walk streams. A walk has no worth to draw a count from, so it has its own. */
    public static final int VEIN_CUBES = 48;

    /** Cubes a trace is drawn with, for the same reason. */
    public static final int TRACE_CUBES = 12;

    /** Ticks after its last drop before a fed pool may lift, so a bleed does not race its own harvest. */
    public static final int FEED_GRACE_TICKS = 10;

    /** How long the overflow burst plays at the chest once the stream has landed. */
    public static final int OVERFLOW_TICKS = 10;

    /** Cubes drawn per point of Vessel, so a richer pool is visibly a bigger one. */
    public static final int CUBES_PER_POINT = 4;

    /** The most cubes a pool is ever drawn with: VoxelStyle.HARVEST's cap, which the server cannot read. */
    public static final int MAX_CUBES = 96;

    private BloodHarvestRules() {
    }

    /**
     * Ticks a stream takes from lift to landing, fixed at the moment it lifts.
     *
     * <p>Grows with distance so far blood is seen to travel, and is capped so a kill across a room
     * does not leave the player waiting on their own payout.
     */
    public static int flightTicks(double distance) {
        int flight = Math.round(FLIGHT_BASE_TICKS + FLIGHT_TICKS_PER_BLOCK * (float) Math.max(0.0D, distance));
        return Mth.clamp(flight, MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS);
    }

    public static double pullRange(boolean bloodscent) {
        return bloodscent ? BLOODSCENT_PULL_RANGE : PULL_RANGE;
    }

    /** How long a fresh pool of this owner's waits: Clotting makes spilled blood dry half as fast. */
    public static int lifetime(boolean clotting) {
        return clotting ? POOL_LIFETIME * 2 : POOL_LIFETIME;
    }

    public static int yield(int drops) {
        return Math.max(0, drops) * VESSEL_PER_DROP;
    }

    public static int cubes(int yield) {
        return Math.max(1, yield * CUBES_PER_POINT);
    }

    /** Cubes a pool of this kind and worth is drawn with. A trace has no worth, so it has a size of its own. */
    public static int cubes(byte kind, int yield) {
        return kind == KIND_TRACE ? TRACE_CUBES : cubes(yield);
    }

    /** Whether a pool of this age, in a pool of this life, may still lift - or has begun to dry. */
    public static boolean canLift(int poolAge, int life) {
        return poolAge < life - DRYING_TICKS;
    }

    /** Only a harvest comes to you on its own. A battery waits to be spent; a trace waits to be stepped on. */
    public static boolean liftsOnItsOwn(byte kind) {
        return kind == KIND_HARVEST;
    }
}
