package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The four ways of looking at a shape that never changes.
 *
 * <p>These are the reason the kit is six verbs rather than five skills, and they are all pure, so
 * "a wielder who planted a full ring Looses exactly half" is a thing that can be asserted rather
 * than a thing somebody watched once. The cap on a Loose <em>is</em> the projection, and there is
 * no number in it to pin - what gets pinned is the filter, the ordering and the tie-break.
 */
class ProjectionTest {

    private static final double[] AHEAD = {0.0D, 0.0D, 1.0D};

    private static Frame held(float yaw) {
        return new Frame(0.0D, 0.0D, 0.0D, yaw, 0.0F, 1.0F);
    }

    private static SwordArray written(SwordRules rules, Station... stations) {
        SwordArray array = new SwordArray();
        array.setRules(rules);
        for (Station station : stations) {
            assertTrue(array.plant(station, 0).accepted(), "the fixture itself must be legal: " + station);
        }
        return array;
    }

    // ---- forward ------------------------------------------------------------------------------

    @Test
    void forwardTakesWhatFacesTheLookNearestTheLookFirst() {
        SwordArray array = written(SwordRules.GOD,
                new Station(2, 0, 1, 1),    // 30 degrees off the look
                new Station(0, 0, 1, 1),    // straight down it
                new Station(6, 0, 1, 1),    // exactly abeam
                new Station(12, 0, 1, 1));  // behind

        assertArrayEquals(new int[] {1, 0}, Projection.forward(array, held(0.0F), AHEAD),
                "descending by the dot, so the blade most nearly on the line leaves first");
    }

    @Test
    void aStationExactlyAbeamDoesNotFly() {
        // cos(90 degrees) is 6.1e-17 in doubles and not zero, and a twelve-station ring puts two
        // blades exactly there every time, so the filter carries a hair of slack on purpose.
        SwordArray array = written(SwordRules.GOD, new Station(6, 0, 1, 1), new Station(18, 0, 1, 1));
        assertEquals(0, Projection.forward(array, held(0.0F), AHEAD).length);
    }

    @Test
    void aFullRingLoosesExactlyHalfOfItself() {
        SwordArray array = new SwordArray();
        array.setRules(SwordRules.GOD);
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw += SwordArray.SEPARATION_MIN) {
            array.plant(new Station(yaw, 0, 1, 1), 0);
        }
        assertEquals(12, array.size());

        // Five forward of abeam on each side, plus the one dead ahead: eleven of twelve would be
        // the count if the two abeam counted, and the whole read of the skill is that it is half.
        assertEquals(5, Projection.forward(array, held(0.0F), AHEAD).length,
                "yaw 0, 2, 4 and 20, 22 - the ring is even, so half of it is behind and two are abeam");
    }

    @Test
    void aTieInTheForwardOrderGoesToTheLowerSlot() {
        SwordArray array = written(SwordRules.GOD, new Station(2, 0, 1, 1), new Station(22, 0, 1, 1));
        assertArrayEquals(new int[] {0, 1}, Projection.forward(array, held(0.0F), AHEAD),
                "30 degrees either side of the look is the same dot, and the slot breaks it");
    }

    @Test
    void anUnmannedBearingFiresNothing() {
        SwordArray array = written(SwordRules.GOD, new Station(2, 0, 1, 1), new Station(0, 0, 1, 1));
        assertEquals(1, array.spend(1, 1));
        assertArrayEquals(new int[] {0}, Projection.forward(array, held(0.0F), AHEAD),
                "the bearing is still authored and there is no metal on it");
    }

    @Test
    void forwardIsReadThroughTheFrameAndNotThroughTheWielder() {
        SwordArray array = written(SwordRules.GOD, new Station(0, 0, 1, 1));
        assertArrayEquals(new int[] {0}, Projection.forward(array, held(0.0F), AHEAD));
        assertEquals(0, Projection.forward(array, held(180.0F), AHEAD).length,
                "turn the frame and the same station is behind you: a SET array does not follow your head");
        assertEquals(0, Projection.forward(array, held(0.0F), new double[] {0.0D, 0.0D, 0.0D}).length,
                "and a look that is not a direction is not a volley");
    }

    // ---- below --------------------------------------------------------------------------------

    @Test
    void belowTakesOnlyWhatWasAimedAtTheFloorDeepestFirst() {
        SwordArray array = written(SwordRules.GOD,
                new Station(0, -1, 1, 1),
                new Station(3, 2, 1, 1),
                new Station(6, -4, 1, 1),
                new Station(9, -4, 1, 1),
                new Station(12, 0, 1, 1));

        assertArrayEquals(new int[] {2, 3, 0}, Projection.below(array),
                "ascending pitch, ties to the lower slot; pitch 0 is not below anything");

        assertEquals(1, array.spend(2, 1));
        assertArrayEquals(new int[] {3, 0}, Projection.below(array), "and an emptied bearing erupts with nothing");
    }

    @Test
    void aFlatRingHasNothingToEruptWith() {
        SwordArray array = written(SwordRules.GOD,
                new Station(0, 0, 1, 1), new Station(6, 0, 1, 1), new Station(12, 0, 1, 1));
        assertEquals(0, Projection.below(array).length,
                "Below over a flat ring is a mark on the ground and nothing else, by construction");
    }

    // ---- covers -------------------------------------------------------------------------------

    @Test
    void coversAnswersTheBearingTheBlowIsComingOutOf() {
        assertEquals(30.0D, Projection.WARD_CONE, 1.0E-12D);
        SwordArray array = written(SwordRules.GOD, new Station(0, 0, 1, 1));

        assertEquals(0, Projection.covers(array, held(0.0F), AHEAD), "straight down the bearing");
        assertEquals(0, Projection.covers(array, held(0.0F), new double[] {0.0D, 0.0D, 5.0D}),
                "and it need not be handed a unit vector");
        assertEquals(0, Projection.covers(array, held(0.0F), atBearing(29.0D)), "29 degrees is inside the cone");
        assertEquals(-1, Projection.covers(array, held(0.0F), atBearing(31.0D)), "31 is outside it");
        assertEquals(-1, Projection.covers(array, held(0.0F), new double[] {0.0D, 0.0D, -1.0D}),
                "a station facing +Z guards what arrives out of +Z and nothing at its back");
        assertEquals(-1, Projection.covers(array, held(0.0F), new double[] {0.0D, 0.0D, 0.0D}));
        assertEquals(-1, Projection.covers(array, held(0.0F), null));
    }

    @Test
    void coversTurnsWithTheFrameAndNotWithTheStation() {
        SwordArray array = written(SwordRules.GOD, new Station(0, 0, 1, 1));
        assertEquals(-1, Projection.covers(array, held(180.0F), AHEAD),
                "a frozen array keeps the bearings it was frozen with: walk round it and it guards nothing");
        assertEquals(0, Projection.covers(array, held(180.0F), new double[] {0.0D, 0.0D, -1.0D}));
    }

    @Test
    void anEmptiedStationGuardsNothing() {
        SwordArray array = written(SwordRules.GOD, new Station(0, 0, 1, 1));
        assertEquals(0, Projection.covers(array, held(0.0F), AHEAD));
        assertEquals(1, array.spend(0, 1));
        assertEquals(-1, Projection.covers(array, held(0.0F), AHEAD), "Ward spends the metal it turns with");
    }

    @Test
    void theClosestOfSeveralCoveringStationsIsTheOneThatMeetsIt() {
        SwordArray array = written(SwordRules.SAINT, new Station(0, 0, 1, 1), new Station(1, 0, 1, 1));
        assertEquals(1, Projection.covers(array, held(0.0F), atBearing(15.0D)),
                "both are inside the cone and the blade pointing most nearly at it answers");
        assertEquals(0, Projection.covers(array, held(0.0F), AHEAD));
    }

    // ---- mirror -------------------------------------------------------------------------------

    @Test
    void mirrorHalvesTheEdgeAndDedupesAgainstRealStations() {
        SwordArray array = written(SwordRules.GOD,
                new Station(0, 2, 3, 7),
                new Station(12, -2, 4, 5),
                new Station(6, 0, 2, 1),
                new Station(3, -3, 2, 9));

        // The first two are each other's antipodes, so both their twins land on a bearing the
        // wielder already paid for and both are dropped. The apex does not double what you bought.
        assertEquals(List.of(new Station(18, 0, 2, 1), new Station(15, 3, 2, 4)), Projection.mirror(array),
                "yaw + 12, pitch negated, reach unchanged, Edge halved with a floor of one");

        assertEquals(61, array.bill(), "and the reflections cost no bill, because they are not metal");
        assertEquals(22, array.bound(), "nor any Edge");
    }

    @Test
    void anUnmannedBearingCastsNoReflection() {
        SwordArray array = written(SwordRules.GOD, new Station(0, 0, 2, 4), new Station(4, 2, 2, 4));
        assertEquals(2, Projection.mirror(array).size());
        assertEquals(4, array.spend(0, 4));
        assertEquals(List.of(new Station(16, -2, 2, 2)), Projection.mirror(array));
    }

    @Test
    void aMirroredStationIsNeverInForward() {
        // Both real stations are behind the look and both their twins are in front of it, so a
        // forward projection that had ever learned about the reflections would be length two.
        SwordArray array = written(SwordRules.GOD, new Station(12, 0, 1, 1), new Station(14, 0, 1, 1));
        assertEquals(2, Projection.mirror(array).size());
        assertEquals(0, Projection.forward(array, held(0.0F), AHEAD).length,
                "a reflection defends and comes up from underneath; it does not fly");
    }

    /** A unit vector that many degrees clockwise of +Z, which is how the lattice measures yaw. */
    private static double[] atBearing(double degrees) {
        double radians = Math.toRadians(degrees);
        return new double[] {-Math.sin(radians), 0.0D, Math.cos(radians)};
    }
}
