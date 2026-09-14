package com.efkrdnz.magical.client.renderer.fx.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.passive.BloodHarvestRules;

import org.junit.jupiter.api.Test;

/**
 * The harvest's motion, held to the same standard as the field's: continuous everywhere, and in
 * particular across the one seam it has that the field does not - the tick the pool lifts.
 *
 * <p>That seam is where this can look wrong in a way no diff shows. The pool and the stream are two
 * different functions, and if they disagree about where a cube is at the instant one hands over to
 * the other, every cube pops at once.
 */
class BloodHarvestMotionTest {

    private static final VoxelStyle STYLE = VoxelStyle.HARVEST;
    private static final int SEED = 33;
    private static final int[] INDICES = {0, 1, 5, 17, 48, 95};

    private static final float CHEST_X = 7.0F;
    private static final float CHEST_Y = 1.1F;
    private static final float CHEST_Z = -4.0F;

    /** Bound on how far one cube may move in a hundredth of a tick, in blocks. */
    private static final float MAX_STEP = 0.08F;

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Where cube {@code index} is, pooled, at this age - the renderer's pooled branch. */
    private static float[] pooledAt(int index, float age) {
        float[] out = new float[3];
        BloodHarvestMotion.poolSpot(STYLE, index, SEED, out);
        float grow = BloodHarvestMotion.grow(STYLE.timing(), BloodHarvestMotion.rank(index, SEED), age);
        BloodHarvestMotion.wobble(STYLE, index, SEED, age, grow, out);
        return out;
    }

    /** Where cube {@code index} is, streaming, at this age and stream age - the streaming branch. */
    private static float[] streamingAt(int index, float age, float streamAge, float flight) {
        float[] spot = new float[3];
        BloodHarvestMotion.poolSpot(STYLE, index, SEED, spot);
        float delay = BloodHarvestMotion.delayFor(STYLE.timing(), index, SEED);
        float cubeFlight = BloodHarvestMotion.cubeFlight(STYLE.timing(), flight);
        float progress = BloodHarvestMotion.progress(delay, cubeFlight, streamAge);
        float[] out = new float[3];
        BloodHarvestMotion.fly(STYLE, index, SEED, progress, spot[0], spot[1], spot[2],
                CHEST_X, CHEST_Y, CHEST_Z, out);
        float grow = BloodHarvestMotion.grow(STYLE.timing(), BloodHarvestMotion.rank(index, SEED), age);
        BloodHarvestMotion.wobble(STYLE, index, SEED, age, grow * (1.0F - progress), out);
        return out;
    }

    /**
     * The one that matters. The server pays out when {@code flight} ticks have passed since the
     * lift; every cube has to be inside the player by then, for every flight the server can pick.
     */
    @Test
    void everyCubeLandsBeforeTheServerPaysOut() {
        for (int flight = BloodHarvestRules.MIN_FLIGHT_TICKS; flight <= BloodHarvestRules.MAX_FLIGHT_TICKS; flight++) {
            float cubeFlight = BloodHarvestMotion.cubeFlight(STYLE.timing(), flight);
            for (int index = 0; index < STYLE.cap(); index++) {
                float delay = BloodHarvestMotion.delayFor(STYLE.timing(), index, SEED);
                assertTrue(delay + cubeFlight <= flight, "cube " + index + " lands at "
                        + (delay + cubeFlight) + " but the server pays at " + flight);
                assertEquals(1.0F, BloodHarvestMotion.progress(delay, cubeFlight, flight),
                        "cube " + index + " is still in the air at payout");
                assertEquals(1.0F, BloodHarvestMotion.entering(STYLE.timing(), 1.0F, cubeFlight),
                        "cube " + index + " has landed but is still drawn");
            }
        }
    }

    /** At the lift tick, the pooled function and the streaming function name the same point. */
    @Test
    void theLiftLeavesNoSeam() {
        for (int index : INDICES) {
            for (float liftAge : new float[] {1.0F, 3.5F, 12.0F, 120.0F, 290.0F}) {
                float[] pooled = pooledAt(index, liftAge);
                float[] streaming = streamingAt(index, liftAge, 0.0F, BloodHarvestRules.MAX_FLIGHT_TICKS);
                assertTrue(distance(pooled, streaming) < 1.0E-5F,
                        "cube " + index + " jumps " + distance(pooled, streaming) + " blocks when the pool lifts at " + liftAge);
                float rank = BloodHarvestMotion.rank(index, SEED);
                float grow = BloodHarvestMotion.grow(STYLE.timing(), rank, liftAge);
                float dry = BloodHarvestMotion.dryOut(STYLE.timing(), rank, liftAge, BloodHarvestRules.POOL_LIFETIME);
                assertEquals(0.0F, dry, "a pool that can still lift has begun to dry at " + liftAge);
                float pooledHalf = BloodHarvestMotion.halfExtent(STYLE, index, SEED, grow, dry, 1.0F);
                float cubeFlight = BloodHarvestMotion.cubeFlight(STYLE.timing(), BloodHarvestRules.MAX_FLIGHT_TICKS);
                float entering = BloodHarvestMotion.entering(STYLE.timing(), 0.0F, cubeFlight);
                float streamingHalf = BloodHarvestMotion.halfExtent(STYLE, index, SEED, grow, entering, 1.0F);
                assertEquals(pooledHalf, streamingHalf, 1.0E-6F, "cube " + index + " changes size when the pool lifts");
            }
        }
    }

    @Test
    void aStreamingCubeStartsAtItsPoolSpotAndEndsInTheChest() {
        float[] spot = new float[3];
        float[] out = new float[3];
        float[] chest = {CHEST_X, CHEST_Y, CHEST_Z};
        for (int index : INDICES) {
            BloodHarvestMotion.poolSpot(STYLE, index, SEED, spot);
            BloodHarvestMotion.fly(STYLE, index, SEED, 0.0F, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, out);
            assertTrue(distance(spot, out) < 1.0E-6F, "cube " + index + " does not launch from its own pool spot");
            float[] end = new float[3];
            BloodHarvestMotion.fly(STYLE, index, SEED, 1.0F, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, end);
            assertTrue(distance(chest, end) <= BloodHarvestMotion.CHEST_SPREAD + 1.0E-6F,
                    "cube " + index + " ends " + distance(chest, end) + " blocks from the chest");
            // Arcs, not slides: halfway along, the cube is above the straight line from pool to
            // chest. The horizontal travel is linear in the eased progress, so it recovers where
            // on that line the cube would have been.
            BloodHarvestMotion.fly(STYLE, index, SEED, 0.5F, spot[0], spot[1], spot[2], CHEST_X, CHEST_Y, CHEST_Z, out);
            float along = (out[0] - spot[0]) / (end[0] - spot[0]);
            float chordY = spot[1] + (end[1] - spot[1]) * along;
            assertTrue(out[1] > chordY + 0.05F, "cube " + index + " slides instead of arcing");
        }
    }

    @Test
    void aCubeNeverJumpsAlongItsFlight() {
        for (int flight : new int[] {BloodHarvestRules.MIN_FLIGHT_TICKS, 24, BloodHarvestRules.MAX_FLIGHT_TICKS}) {
            for (int index : INDICES) {
                float[] previous = streamingAt(index, 8.0F, 0.0F, flight);
                for (int step = 1; step <= flight * 100 + 200; step++) {
                    float streamAge = step / 100.0F;
                    float[] now = streamingAt(index, 8.0F + streamAge, streamAge, flight);
                    float moved = distance(previous, now);
                    assertTrue(moved < MAX_STEP, "cube " + index + " jumped " + moved
                            + " blocks at stream age " + streamAge + " on a " + flight + " tick flight");
                    previous = now;
                }
            }
        }
    }

    @Test
    void aPooledCubeNeverJumpsEither() {
        for (int index : INDICES) {
            float[] previous = pooledAt(index, 0.0F);
            for (int step = 1; step <= BloodHarvestRules.POOL_LIFETIME * 100; step++) {
                float age = step / 100.0F;
                float[] now = pooledAt(index, age);
                float moved = distance(previous, now);
                assertTrue(moved < MAX_STEP, "pooled cube " + index + " jumped " + moved + " blocks at " + age);
                previous = now;
            }
        }
    }

    @Test
    void poolCubesLieInTheDiscAndWellUpFromTheMiddle() {
        float[] out = new float[3];
        float innermost = 2.0F;
        float outermost = -1.0F;
        for (int index = 0; index < STYLE.cap(); index++) {
            BloodHarvestMotion.poolSpot(STYLE, index, SEED, out);
            float radius = (float) Math.sqrt(out[0] * out[0] + out[2] * out[2]);
            assertTrue(radius <= STYLE.burstRadius() + 1.0E-6F, "cube " + index + " lies outside the pool");
            assertTrue(out[1] >= 0.0F && out[1] <= BloodHarvestMotion.POOL_MOUND + 1.0E-6F,
                    "cube " + index + " is not on the ground");
            float rank = BloodHarvestMotion.rank(index, SEED);
            assertEquals(radius / STYLE.burstRadius(), rank, 1.0E-5F, "rank is not the cube's place in the disc");
            innermost = Math.min(innermost, rank);
            outermost = Math.max(outermost, rank);
        }
        // The middle is grown before the edge has started: that is what "wells up" means.
        float halfway = STYLE.timing().materialise() * 0.5F;
        assertTrue(BloodHarvestMotion.grow(STYLE.timing(), innermost, halfway)
                > BloodHarvestMotion.grow(STYLE.timing(), outermost, halfway));
        assertEquals(1.0F, BloodHarvestMotion.grow(STYLE.timing(), outermost,
                STYLE.timing().materialise() + BloodHarvestMotion.GROW_TICKS));
    }

    @Test
    void aCubeEntersRatherThanStops() {
        float cubeFlight = BloodHarvestMotion.cubeFlight(STYLE.timing(), 24.0F);
        float dissolve = STYLE.timing().dissolve();
        assertEquals(0.0F, BloodHarvestMotion.entering(STYLE.timing(), 0.0F, cubeFlight));
        assertEquals(0.0F, BloodHarvestMotion.entering(STYLE.timing(),
                (cubeFlight - dissolve) / cubeFlight, cubeFlight), 1.0E-6F,
                "shrinking starts before the last dissolve ticks");
        assertEquals(1.0F, BloodHarvestMotion.entering(STYLE.timing(), 1.0F, cubeFlight));
        float mid = (cubeFlight - dissolve * 0.5F) / cubeFlight;
        float half = BloodHarvestMotion.entering(STYLE.timing(), mid, cubeFlight);
        assertTrue(half > 0.0F && half < 1.0F);
    }

    @Test
    void dryingStartsOnlyOnceThePoolCanNoLongerLift() {
        float life = BloodHarvestRules.POOL_LIFETIME;
        float deadline = life - BloodHarvestRules.DRYING_TICKS;
        for (float rank : new float[] {0.0F, 0.3F, 0.8F, 1.0F}) {
            assertEquals(0.0F, BloodHarvestMotion.dryOut(STYLE.timing(), rank, deadline - 0.01F, life),
                    "rank " + rank + " dries before the deadline");
            assertEquals(1.0F, BloodHarvestMotion.dryOut(STYLE.timing(), rank, life, life),
                    "rank " + rank + " is still there when the pool is gone");
        }
        // Tips first, so the pool shrinks toward the corpse rather than vanishing all over at once.
        float during = life - STYLE.timing().dissolve() * 0.5F;
        assertTrue(BloodHarvestMotion.dryOut(STYLE.timing(), 1.0F, during, life)
                > BloodHarvestMotion.dryOut(STYLE.timing(), 0.0F, during, life));
    }

    @Test
    void anOverflowBurstLeavesTheChestAndFadesOut() {
        float[] out = new float[3];
        float[] chest = {CHEST_X, CHEST_Y, CHEST_Z};
        for (int index : INDICES) {
            BloodHarvestMotion.burst(STYLE, index, SEED, 0.0F, CHEST_X, CHEST_Y, CHEST_Z, out);
            assertTrue(distance(chest, out) < 1.0E-6F, "burst cube " + index + " does not start at the chest");
            BloodHarvestMotion.burst(STYLE, index, SEED, 1.0F, CHEST_X, CHEST_Y, CHEST_Z, out);
            float reach = distance(chest, out);
            assertTrue(reach > 0.3F && reach <= BloodHarvestMotion.BURST_RADIUS + 1.0E-6F,
                    "burst cube " + index + " reaches " + reach);
        }
        assertEquals(0.0F, BloodHarvestMotion.bursting(0.0F));
        assertEquals(1.0F, BloodHarvestMotion.bursting(1.0F));
    }
}
