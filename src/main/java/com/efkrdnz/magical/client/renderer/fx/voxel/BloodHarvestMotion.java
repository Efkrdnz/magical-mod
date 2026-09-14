package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import net.minecraft.util.Mth;

/**
 * Where one cube of harvested blood is at one instant: in the pool at the corpse, on its way to
 * the player, or bursting back out of them.
 *
 * <p>A sibling of {@link VoxelMotion}, not a mode of it. That one flies a synced shape out of the
 * caster; this one flies a pool of blood <em>into</em> them, from a disc on the ground to a chest
 * that moves every frame. The rules it shares with the field are the ones that matter: every term
 * is a continuous function of the ages it is handed, nothing is stored between frames, and the
 * seams are checked - most of all the lift, where the pool's function hands over to the stream's.
 *
 * <p>Two clocks. {@code age} is ticks since the blood was spilled and drives growth and drying,
 * in every phase; {@code streamAge} is ticks since the lift and drives flight. Growth reading the
 * total age is what lets a pool lift on the tick after the kill - the common case - without its
 * cubes snapping to full size: they keep growing in the air, out of the corpse.
 */
public final class BloodHarvestMotion {

    /** Edge of a full-grown cube, in blocks. There is no server pitch here; spilled blood is one size. */
    public static final float PITCH = 0.09F;

    /** Ticks a cube takes to grow to full size once its turn comes. */
    public static final float GROW_TICKS = 2.0F;

    /** How high the middle of the pool sits above its edge, in blocks. */
    public static final float POOL_MOUND = 0.05F;

    /** Radius of the sphere at the chest the cubes converge into, so they enter a body, not a point. */
    public static final float CHEST_SPREAD = 0.12F;

    /** How far an overflow burst throws its cubes from the chest. */
    public static final float BURST_RADIUS = 0.9F;

    private BloodHarvestMotion() {
    }

    private static float h1(int index, int seed) {
        return BloodShapeGeometry.hash01(index * 3 + seed * 7);
    }

    private static float h2(int index, int seed) {
        return BloodShapeGeometry.hash01(index * 5 + seed * 11 + 1);
    }

    private static float h3(int index, int seed) {
        return BloodShapeGeometry.hash01(index * 7 + seed * 13 + 2);
    }

    private static float h4(int index, int seed) {
        return BloodShapeGeometry.hash01(index * 11 + seed * 17 + 3);
    }

    // ---- the pool ---------------------------------------------------------------------------

    /**
     * How far out in the pool this cube lies, 0 at the middle to 1 at the rim.
     *
     * <p>The square root is what makes the disc even: a uniform radius would crowd the middle.
     */
    public static float rank(int index, int seed) {
        return Mth.sqrt(h1(index, seed));
    }

    /** The cube's place in the pool, relative to the corpse, written into {@code out} as x, y, z. */
    public static void poolSpot(VoxelStyle style, int index, int seed, float[] out) {
        float rank = rank(index, seed);
        float radius = rank * style.burstRadius();
        float angle = h2(index, seed) * Mth.TWO_PI;
        out[0] = Mth.cos(angle) * radius;
        out[1] = POOL_MOUND * (1.0F - rank);
        out[2] = Mth.sin(angle) * radius;
    }

    /**
     * Cube size 0..1 as the blood wells up. The middle comes first and the rim last, over the
     * style's {@code materialise}; each cube then takes {@link #GROW_TICKS} to reach full size.
     */
    public static float grow(VoxelStyle.Timeline timing, float rank, float age) {
        float delay = rank * timing.materialise();
        return Mth.clamp((age - delay) / GROW_TICKS, 0.0F, 1.0F);
    }

    /**
     * The idle wobble, added to {@code out} scaled by {@code weight}. Handing the weight in is what
     * lets the caller fade it with growth on the ground and with progress in the air, so it is
     * never switched on or off.
     */
    public static void wobble(VoxelStyle style, int index, int seed, float age, float weight, float[] out) {
        if (weight <= 0.0F) {
            return;
        }
        float amp = style.idleAmp() * weight;
        float phase = age * style.idleHz();
        out[0] += Mth.sin(phase + h1(index, seed) * Mth.TWO_PI) * amp;
        out[1] += Mth.sin(phase * 1.31F + h2(index, seed) * Mth.TWO_PI) * amp;
        out[2] += Mth.sin(phase * 0.83F + h3(index, seed) * Mth.TWO_PI) * amp;
    }

    /**
     * How far gone a pooled cube is as the pool dries, 0 solid to 1 nothing.
     *
     * <p>The rim goes first, so the pool shrinks back toward the corpse rather than thinning all
     * over. The whole of it happens inside the last {@code dissolve + dissolveSpread} ticks of the
     * pool's life, which the rules keep inside the window in which a pool can no longer lift.
     */
    public static float dryOut(VoxelStyle.Timeline timing, float rank, float age, float life) {
        if (timing.dissolve() <= 0.0F) {
            return age >= life ? 1.0F : 0.0F;
        }
        float startsAt = life - timing.dissolve() - timing.dissolveSpread() * rank;
        return Mth.clamp((age - startsAt) / timing.dissolve(), 0.0F, 1.0F);
    }

    // ---- the stream -------------------------------------------------------------------------

    /** Ticks after the lift before this cube leaves the ground: a ragged front, not a marching line. */
    public static float delayFor(VoxelStyle.Timeline timing, int index, int seed) {
        return h4(index, seed) * timing.jitter();
    }

    /**
     * One cube's flight, given the whole stream's. The jitter is taken off the top so the last
     * cube to leave still lands by the time the server pays; the style's {@code launch} is the
     * floor under that.
     */
    public static float cubeFlight(VoxelStyle.Timeline timing, float flightTicks) {
        return Math.max(timing.launch(), flightTicks - timing.jitter());
    }

    /** Flight progress in 0..1: zero before the cube's delay, one once it has entered. */
    public static float progress(float delay, float cubeFlight, float streamAge) {
        if (cubeFlight <= 0.0F) {
            return streamAge >= delay ? 1.0F : 0.0F;
        }
        return Mth.clamp((streamAge - delay) / cubeFlight, 0.0F, 1.0F);
    }

    /**
     * Where the cube is between its pool spot and the chest, written into {@code out}.
     *
     * <p>A quadratic bezier through a lifted control point, so the blood arcs up out of the pool
     * and comes down into the body rather than sliding across the floor. The end is spread over a
     * small sphere at the chest, so a stream enters a torso and not a single point. Progress is
     * eased out: fast off the ground, slowing as it arrives.
     */
    public static void fly(VoxelStyle style, int index, int seed, float progress,
            float fromX, float fromY, float fromZ,
            float chestX, float chestY, float chestZ, float[] out) {
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);

        float angle = h1(index, seed) * Mth.TWO_PI;
        float cosZ = h2(index, seed) * 2.0F - 1.0F;
        float sinZ = Mth.sqrt(Math.max(0.0F, 1.0F - cosZ * cosZ));
        float spread = CHEST_SPREAD * (float) Math.cbrt(h3(index, seed));
        float toX = chestX + Mth.cos(angle) * sinZ * spread;
        float toY = chestY + cosZ * spread;
        float toZ = chestZ + Mth.sin(angle) * sinZ * spread;

        float midX = (fromX + toX) * 0.5F;
        float midY = (fromY + toY) * 0.5F + style.archHeight() * (0.6F + 0.8F * h4(index, seed));
        float midZ = (fromZ + toZ) * 0.5F;

        float inv = 1.0F - eased;
        out[0] = inv * inv * fromX + 2.0F * inv * eased * midX + eased * eased * toX;
        out[1] = inv * inv * fromY + 2.0F * inv * eased * midY + eased * eased * toY;
        out[2] = inv * inv * fromZ + 2.0F * inv * eased * midZ + eased * eased * toZ;
    }

    /**
     * How far gone a flying cube is as it enters, 0 solid to 1 nothing. Shrinks over the last
     * {@code dissolve} ticks of its own flight, so it is seen to go <em>in</em> rather than stop
     * at the skin.
     */
    public static float entering(VoxelStyle.Timeline timing, float progress, float cubeFlight) {
        if (timing.dissolve() <= 0.0F) {
            return progress >= 1.0F ? 1.0F : 0.0F;
        }
        float remaining = (1.0F - progress) * cubeFlight;
        return Mth.clamp(1.0F - remaining / timing.dissolve(), 0.0F, 1.0F);
    }

    // ---- the overflow -----------------------------------------------------------------------

    /** How far gone a bursting cube is: it fades as it flies, and is gone at the end. */
    public static float bursting(float progress) {
        float inv = 1.0F - Mth.clamp(progress, 0.0F, 1.0F);
        return 1.0F - inv * inv;
    }

    /** Where an overflowing cube is, thrown outward from the chest, written into {@code out}. */
    public static void burst(VoxelStyle style, int index, int seed, float progress,
            float chestX, float chestY, float chestZ, float[] out) {
        float eased = bursting(progress);
        float angle = h1(index, seed) * Mth.TWO_PI;
        float cosZ = h2(index, seed) * 2.0F - 1.0F;
        float sinZ = Mth.sqrt(Math.max(0.0F, 1.0F - cosZ * cosZ));
        float reach = BURST_RADIUS * (0.4F + 0.6F * h3(index, seed)) * eased;
        out[0] = chestX + Mth.cos(angle) * sinZ * reach;
        out[1] = chestY + cosZ * reach;
        out[2] = chestZ + Mth.sin(angle) * sinZ * reach;
    }

    // ---- size -------------------------------------------------------------------------------

    /**
     * Half the cube's edge at this instant. Fading is by shrinking, never by alpha: this render
     * type writes depth, and a translucent cube would punch a hole in whatever is behind it.
     */
    public static float halfExtent(VoxelStyle style, int index, int seed, float grow, float erosion,
            float lodEdge) {
        float variation = 0.8F + 0.4F * h2(index, seed);
        return PITCH * style.cube() * 0.5F * grow * (1.0F - erosion) * lodEdge * variation;
    }
}
