package com.efkrdnz.magical.magic.passive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelStyle;

import org.junit.jupiter.api.Test;

/**
 * The numbers the harvest runs on, and the relationships between them that the server and the
 * renderer both rely on without either being able to check.
 */
class BloodHarvestRulesTest {

    /**
     * Vein Walk teleports along pooled blood, and its players have learned how long a pool waits.
     * The harvest inherits that number rather than choosing its own.
     */
    @Test
    void aPoolWaitsExactlyAsLongAsAMoteUsedTo() {
        assertEquals(300, BloodHarvestRules.POOL_LIFETIME);
    }

    @Test
    void flightGrowsWithDistanceAndStaysInsideItsBounds() {
        int previous = BloodHarvestRules.flightTicks(0.0D);
        assertEquals(BloodHarvestRules.MIN_FLIGHT_TICKS, previous,
                "blood on the spot still takes the minimum flight, so it is seen to move");
        for (double distance = 0.0D; distance <= 64.0D; distance += 0.25D) {
            int flight = BloodHarvestRules.flightTicks(distance);
            assertTrue(flight >= previous, "flight shortened between " + (distance - 0.25D) + " and " + distance);
            assertTrue(flight >= BloodHarvestRules.MIN_FLIGHT_TICKS && flight <= BloodHarvestRules.MAX_FLIGHT_TICKS,
                    "flight " + flight + " at " + distance + " blocks is out of bounds");
            previous = flight;
        }
        assertEquals(BloodHarvestRules.MAX_FLIGHT_TICKS, BloodHarvestRules.flightTicks(64.0D),
                "the far end of the range is capped, so a lance kill does not take three seconds to pay");
        assertEquals(BloodHarvestRules.MIN_FLIGHT_TICKS, BloodHarvestRules.flightTicks(-5.0D),
                "a negative distance is a caller bug, not a reason to fly backwards in time");
    }

    @Test
    void bloodscentPullsFromItsWholeRevealRange() {
        assertTrue(BloodHarvestRules.pullRange(true) > BloodHarvestRules.pullRange(false));
        assertEquals(BloodHarvestRules.BLOODSCENT_PULL_RANGE, BloodHarvestRules.pullRange(true));
        assertEquals(BloodHarvestRules.PULL_RANGE, BloodHarvestRules.pullRange(false));
    }

    @Test
    void aDoubledDropIsWorthExactlyTwice() {
        assertEquals(0, BloodHarvestRules.yield(0));
        assertEquals(BloodHarvestRules.VESSEL_PER_DROP, BloodHarvestRules.yield(1));
        assertEquals(2 * BloodHarvestRules.yield(1), BloodHarvestRules.yield(2));
        assertEquals(0, BloodHarvestRules.yield(-3), "a negative drop count cannot drain the Vessel");
    }

    /**
     * The renderer draws {@code cubes(yield)} of the style's {@code cap}. If the doubled harvest
     * ever asks for more than the cap, Bloodscent's pool silently draws the same as a plain one and
     * nobody can tell the passive is on.
     */
    @Test
    void theRichestHarvestFitsInsideTheStylesCap() {
        int doubled = BloodHarvestRules.cubes(BloodHarvestRules.yield(2));
        assertTrue(doubled <= VoxelStyle.HARVEST.cap(),
                "a doubled harvest wants " + doubled + " cubes but the style caps at " + VoxelStyle.HARVEST.cap());
        assertTrue(doubled > BloodHarvestRules.cubes(BloodHarvestRules.yield(1)),
                "the doubled pool has to be visibly bigger");
        assertEquals(1, BloodHarvestRules.cubes(0), "even an empty harvest draws one cube rather than nothing");
    }

    /**
     * Two invariants the motion relies on and cannot see from where it stands.
     *
     * <p>The shortest flight has to hold a cube's jitter and its minimum launch, or the last cube
     * lands after the server has already paid. And drying has to fit inside the window after the
     * lift deadline - otherwise a pool lifted at the last moment carries half-shrunk cubes into the
     * air.
     */
    @Test
    void theShortestFlightHoldsTheJitterAndTheDryingWindowHoldsTheDissolve() {
        VoxelStyle.Timeline timing = VoxelStyle.HARVEST.timing();
        assertTrue(BloodHarvestRules.MIN_FLIGHT_TICKS >= timing.jitter() + timing.launch(),
                "the minimum flight is shorter than one cube's jitter plus launch");
        assertTrue(timing.dissolve() + timing.dissolveSpread() <= BloodHarvestRules.DRYING_TICKS,
                "the pool would still be drying after its lifetime ran out");
        assertTrue(BloodHarvestRules.DRYING_TICKS < BloodHarvestRules.POOL_LIFETIME);
        int life = BloodHarvestRules.POOL_LIFETIME;
        assertTrue(BloodHarvestRules.canLift(0, life));
        assertTrue(BloodHarvestRules.canLift(life - BloodHarvestRules.DRYING_TICKS - 1, life));
        assertFalse(BloodHarvestRules.canLift(life - BloodHarvestRules.DRYING_TICKS, life),
                "a pool that has begun to dry must not lift");
    }
    @Test
    void clottingDoublesHowLongAPoolWaits() {
        assertEquals(BloodHarvestRules.POOL_LIFETIME, BloodHarvestRules.lifetime(false));
        assertEquals(2 * BloodHarvestRules.POOL_LIFETIME, BloodHarvestRules.lifetime(true),
                "with Clotting, spilled blood dries half as fast");
    }

    @Test
    void liftingReadsThePoolsOwnLife() {
        // A pool owns its life now - Clotting stretches it, the Rite and the trace set theirs - so
        // the lift deadline moves with it rather than sitting on the old constant.
        assertTrue(BloodHarvestRules.canLift(0, 300));
        assertFalse(BloodHarvestRules.canLift(290, 300), "a pool that has begun to dry must not lift");
        assertTrue(BloodHarvestRules.canLift(295, 600), "the same age is fine in a longer life");
        assertFalse(BloodHarvestRules.canLift(590, 600));
    }

    @Test
    void onlyAHarvestPoolLiftsOnItsOwn() {
        assertTrue(BloodHarvestRules.liftsOnItsOwn(BloodHarvestRules.KIND_HARVEST));
        assertFalse(BloodHarvestRules.liftsOnItsOwn(BloodHarvestRules.KIND_BATTERY),
                "a battery waits to be spent: by Coagulate, the Spear, or a Vein Walk landing on it");
        assertFalse(BloodHarvestRules.liftsOnItsOwn(BloodHarvestRules.KIND_TRACE),
                "a trace is only ever somewhere to step");
    }

    @Test
    void aTraceIsSeenThoughItIsWorthNothing() {
        assertTrue(BloodHarvestRules.cubes(BloodHarvestRules.KIND_TRACE, 0) > 1,
                "a destination nobody can see is not a destination");
        assertEquals(BloodHarvestRules.cubes(BloodHarvestRules.KIND_HARVEST, 12),
                BloodHarvestRules.cubes(BloodHarvestRules.KIND_BATTERY, 12),
                "a battery is drawn at the size of a harvest of the same worth");
        assertEquals(BloodHarvestRules.cubes(12), BloodHarvestRules.cubes(BloodHarvestRules.KIND_HARVEST, 12));
    }

    @Test
    void aVeinWalksStreamIsLongEnoughForEveryCubeToLand() {
        // The vein reuses the harvest timeline, whose jitter and launch the minimum flight was sized
        // for; a shorter stream would still be in the air when its entity is gone.
        assertTrue(BloodHarvestRules.VEIN_FLIGHT_TICKS >= BloodHarvestRules.MIN_FLIGHT_TICKS);
        assertTrue(BloodHarvestRules.FEED_GRACE_TICKS > 0,
                "a fed pool waits a moment after its last drop before it may lift");
    }
}
