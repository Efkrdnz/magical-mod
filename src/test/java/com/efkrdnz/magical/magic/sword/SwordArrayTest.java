package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.IntArrayTag;
import org.junit.jupiter.api.Test;

/**
 * The structure, with no world under it.
 *
 * <p>Every rule the Sword Summoner has about what may be written is arithmetic, which is the whole
 * point of {@code SwordArray} taking a {@link SwordRules} rather than a class definition: the
 * separation, the conservation and the bill are checkable exactly, and every assertion here is on
 * a number worked out by hand rather than one read back out of the implementation.
 */
class SwordArrayTest {

    private static SwordArray at(SwordRules rules) {
        SwordArray array = new SwordArray();
        array.setRules(rules);
        return array;
    }

    // ---- the lattice and the save format ------------------------------------------------------

    @Test
    void theLatticeConstantsAreTheOnesTheSaveWasWrittenAgainst() {
        assertEquals(24, Station.YAW_STEPS, "24 yaw steps");
        assertEquals(15.0D, Station.YAW_STEP_DEGREES, 1.0E-12D, "15 degrees each");
        assertEquals(-4, Station.PITCH_MIN);
        assertEquals(4, Station.PITCH_MAX);
        assertEquals(18.0D, Station.PITCH_STEP_DEGREES, 1.0E-12D, "+4 is 72 degrees up");
        assertEquals(1, Station.REACH_MIN);
        assertEquals(6, Station.REACH_MAX);
        assertEquals(36, Station.EDGE_MAX);
        assertEquals(1296, Station.YAW_STEPS * (Station.PITCH_MAX - Station.PITCH_MIN + 1) * Station.REACH_MAX,
                "1296 distinct places, which is what makes the record fit in 18 bits with the Edge");
        assertEquals(1, SwordArray.SAVE_VERSION, "bump this and the packing below has to move with it");

        // yaw 7 << 13 | (-2 + 4) << 9 | (4 - 1) << 6 | 5  ==  57344 + 1024 + 192 + 5
        assertEquals(58565, new Station(7, -2, 4, 5).packed(), "the four shifts are the save format");
        assertEquals(new Station(7, -2, 4, 5), Station.unpack(58565));
        assertEquals(0, new Station(0, -4, 1, 0).packed(), "the bottom corner of the lattice is zero");
        assertEquals(192868, new Station(23, 4, 6, 36).packed(), "and the top corner is 18 bits");
    }

    @Test
    void everyPlaceOnTheLatticeSurvivesTheRoundTrip() {
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
            for (int pitch = Station.PITCH_MIN; pitch <= Station.PITCH_MAX; pitch++) {
                for (int reach = Station.REACH_MIN; reach <= Station.REACH_MAX; reach++) {
                    for (int edge : new int[] {0, 1, 17, 36}) {
                        Station station = new Station(yaw, pitch, reach, edge);
                        assertEquals(station, Station.unpack(station.packed()));
                    }
                }
            }
        }
    }

    @Test
    void anUnpackIsTotalAndClampsRatherThanThrowing() {
        assertTrue(Station.unpack(-1).onLattice(), "every bit set still reads as a place");
        assertEquals(new Station(23, 4, 6, 36), Station.unpack(-1), "and it is the far corner, clamped");
        assertTrue(Station.unpack(0).onLattice());
    }

    // ---- separation ---------------------------------------------------------------------------

    @Test
    void twelveIsExactlyAFullRing() {
        assertEquals(12, SwordArray.MAX_STATIONS);
        assertEquals(2, SwordArray.SEPARATION_MIN);
        assertEquals(SwordArray.MAX_STATIONS,
                (int) (360.0D / (SwordArray.SEPARATION_MIN * Station.YAW_STEP_DEGREES)),
                "two yaw steps is 30 degrees and 360/30 is 12: the cap and the separation are one fact");
        assertEquals(SwordArray.MAX_STATIONS, SwordRules.GOD.maxStations(), "the apex reaches it and stops");

        SwordArray array = at(SwordRules.GOD);
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw += SwordArray.SEPARATION_MIN) {
            assertEquals(PlantResult.PLANTED, array.plant(new Station(yaw, 0, 1, 1), 0), "yaw " + yaw);
        }
        assertEquals(12, array.size(), "the ring closes at exactly twelve");
        assertEquals(12, array.bill());

        // Pitch 4 is four steps clear of every one of them, so this is FULL on its own merits and
        // not TOO_CLOSE wearing FULL's name - which is what pins the order plant tests them in.
        assertEquals(PlantResult.FULL, array.plant(new Station(1, 4, 1, 1), 0));
        assertEquals(12, array.size());
    }

    @Test
    void aStationMayNotShareABearing() {
        // Rider, because Saint and God are granted coincidence and this is the rule they lift.
        for (int reach = Station.REACH_MIN; reach <= Station.REACH_MAX; reach++) {
            SwordArray array = at(SwordRules.RIDER);
            assertEquals(PlantResult.PLANTED, array.plant(new Station(5, 0, reach, 1), 0), "reach " + reach);
            assertEquals(PlantResult.TOO_CLOSE, array.plant(new Station(6, 0, reach, 1), 0),
                    "one yaw step apart at reach " + reach);
            assertEquals(PlantResult.TOO_CLOSE, array.plant(new Station(5, 1, reach, 1), 0),
                    "one pitch step apart at reach " + reach);
            assertEquals(PlantResult.PLANTED, array.plant(new Station(7, 0, reach, 1), 0),
                    "two yaw steps apart at reach " + reach);
            assertEquals(PlantResult.PLANTED, array.plant(new Station(5, 2, reach, 1), 0),
                    "two pitch steps apart at reach " + reach);
            assertEquals(3, array.size());
        }
    }

    @Test
    void aFullArrayIsFullBeforeItIsAnythingElse() {
        // The order plant tests its refusals in is a decision and this is where it is pinned: the
        // fifth bearing here breaks the station cap and the separation rule at the same time, and
        // FULL is the one the wielder can do something about with the Bearing.
        SwordArray array = at(SwordRules.SUMMONER);
        for (int yaw = 0; yaw < 8; yaw += 2) {
            assertEquals(PlantResult.PLANTED, array.plant(new Station(yaw, 0, 1, 1), 0), "yaw " + yaw);
        }
        assertEquals(4, array.size());
        assertEquals(PlantResult.FULL, array.plant(new Station(1, 0, 1, 1), 0));
    }

    @Test
    void separationIsTheShortWayRoundTheRing() {
        assertEquals(1, Station.circularYawSteps(23, 0), "23 and 0 are neighbours, not strangers");
        assertEquals(12, Station.circularYawSteps(0, 12), "and the antipode is the furthest there is");
        assertEquals(2, new Station(23, 0, 1, 1).separationFrom(new Station(1, 0, 1, 1)));
        assertEquals(4, new Station(0, 0, 1, 1).separationFrom(new Station(1, 4, 1, 1)),
                "the max of the two axes, never the sum");
    }

    // ---- every one of the seven refusals ------------------------------------------------------

    @Test
    void aBearingOffTheLatticeIsOutOfReach() {
        SwordArray array = at(SwordRules.GOD);
        assertEquals(PlantResult.OUT_OF_REACH, array.plant(new Station(0, 0, 7, 1), 0), "reach 7");
        assertEquals(PlantResult.OUT_OF_REACH, array.plant(new Station(0, 0, 0, 1), 0), "reach 0");
        assertEquals(PlantResult.OUT_OF_REACH, array.plant(new Station(24, 0, 1, 1), 0), "yaw 24");
        assertEquals(PlantResult.OUT_OF_REACH, array.plant(new Station(-1, 0, 1, 1), 0), "yaw -1");
        assertEquals(PlantResult.OUT_OF_REACH, array.plant(new Station(0, 5, 1, 1), 0), "pitch 5");
        assertEquals(PlantResult.OUT_OF_REACH, array.plant(null, 0), "and nothing at all");
        assertEquals(0, array.size());
    }

    @Test
    void aBearingAlreadyWrittenIsToppedUpAndKeepsItsAuthoredReach() {
        SwordArray array = at(SwordRules.GOD);
        assertEquals(PlantResult.PLANTED, array.plant(new Station(0, 0, 3, 2), 0));
        assertEquals(6, array.bill());

        assertEquals(PlantResult.TOPPED_UP, array.plant(new Station(0, 0, 5, 2), 0));
        assertEquals(1, array.size(), "the same bearing is never a second station");
        assertEquals(new Station(0, 0, 3, 4), array.station(0),
                "the Edge went up and the reach stayed as it was written");
        assertEquals(12, array.bill());
    }

    @Test
    void aPlantWithNothingLooseIsRefusedByName() {
        SwordArray array = at(SwordRules.SUMMONER);
        assertEquals(PlantResult.PLANTED, array.plant(new Station(0, 0, 1, 3), 0));
        assertEquals(PlantResult.PLANTED, array.plant(new Station(2, 0, 1, 3), 0));

        // Only 2 of the 8 whole are left, so the cap clamps rather than refusing: a cap is a cap.
        assertEquals(PlantResult.PLANTED, array.plant(new Station(4, 0, 1, 3), 0));
        assertEquals(2, array.station(2).edge(), "clamped to what was loose, not refused");
        assertEquals(8, array.bound());
        assertEquals(0, array.loose(0));

        assertEquals(PlantResult.NO_EDGE, array.plant(new Station(6, 0, 1, 1), 0));
        assertEquals(3, array.size(), "and the refusal wrote nothing");
    }

    @Test
    void aPlantThatOverrunsTheDrawIsRefusedByName() {
        SwordArray array = at(SwordRules.SUMMONER);
        assertEquals(24, SwordRules.SUMMONER.draw());
        assertEquals(PlantResult.PLANTED, array.plant(new Station(0, 0, 6, 3), 0));
        assertEquals(18, array.bill(), "6 blocks out at 3 Edge");

        // 18 + 6 * 2 is 30, and the draw is 24. There is loose Edge, a free slot and clear
        // separation, so TOO_DEAR is the only thing that can be refusing this.
        assertEquals(5, array.loose(0));
        assertEquals(PlantResult.TOO_DEAR, array.plant(new Station(2, 0, 6, 2), 0));
        assertEquals(1, array.size());
        assertEquals(18, array.bill(), "a refusal costs nothing and changes nothing");
    }

    @Test
    void theFirstMinuteIsExactlyFullAndTheGameNeverSaysSo() {
        SwordArray array = at(SwordRules.SUMMONER);
        for (int yaw = 0; yaw < 8; yaw += 2) {
            assertEquals(PlantResult.PLANTED, array.plant(new Station(yaw, 0, 3, 2), 0), "yaw " + yaw);
        }
        assertEquals(4, array.size());
        assertEquals(24, array.bill(), "4 stations at reach 3 and 2 Edge is exactly the draw");
        assertEquals(SwordRules.SUMMONER.draw(), array.bill());
        assertEquals(0, array.strainAt(1.0D));
    }

    @Test
    void coincidenceIsRefusedBelowSaintAndAllowedAtIt() {
        assertFalse(SwordRules.SUMMONER.coincidence());
        assertFalse(SwordRules.RIDER.coincidence());
        assertTrue(SwordRules.SAINT.coincidence());
        assertTrue(SwordRules.GOD.coincidence());

        SwordArray rider = at(SwordRules.RIDER);
        assertEquals(PlantResult.PLANTED, rider.plant(new Station(0, 0, 2, 1), 0));
        assertEquals(PlantResult.TOO_CLOSE, rider.plant(new Station(1, 0, 2, 1), 0));
        assertEquals(1, rider.size());

        SwordArray saint = at(SwordRules.SAINT);
        assertEquals(PlantResult.PLANTED, saint.plant(new Station(0, 0, 2, 1), 0));
        assertEquals(PlantResult.PLANTED, saint.plant(new Station(1, 0, 2, 1), 0),
                "one bearing's separation, two stations: that is what fusion is arithmetically");
        assertEquals(2, saint.size());
        assertEquals(PlantResult.TOPPED_UP, saint.plant(new Station(1, 0, 2, 1), 0),
                "but the very same bearing still merges rather than making a third");
        assertEquals(2, saint.size());
    }

    @Test
    void everyRungIsTheOneTheChainWasPricedAgainst() {
        assertEquals(4, SwordRules.rungs(), "four rungs, because ClassTreeLayout cannot lay out a fifth");
        assertEquals(SwordRules.SUMMONER, SwordRules.forRung(0));
        assertEquals(SwordRules.RIDER, SwordRules.forRung(1));
        assertEquals(SwordRules.SAINT, SwordRules.forRung(2));
        assertEquals(SwordRules.GOD, SwordRules.forRung(3));
        assertEquals(SwordRules.SUMMONER, SwordRules.forRung(-5), "clamped, both ends");
        assertEquals(SwordRules.GOD, SwordRules.forRung(9));

        assertEquals(new SwordRules(4, 24, 3, 8, false, false, false, false), SwordRules.SUMMONER);
        assertEquals(new SwordRules(7, 40, 5, 16, true, false, false, false), SwordRules.RIDER);
        assertEquals(new SwordRules(10, 64, 12, 26, true, true, true, false), SwordRules.SAINT);
        assertEquals(new SwordRules(12, 84, 36, 36, true, true, true, true), SwordRules.GOD);

        assertTrue(SwordRules.RIDER.worldOrigin(), "Rider is the rung the origin comes off the body");
        assertFalse(SwordRules.SUMMONER.worldOrigin());
        assertTrue(SwordRules.SAINT.freeScale(), "Saint is the rung the scale starts moving");
        assertFalse(SwordRules.RIDER.freeScale());
        assertTrue(SwordRules.GOD.overdraw(), "and the apex removes a rule rather than adding an active");
        assertFalse(SwordRules.SAINT.overdraw());
    }

    // ---- conservation -------------------------------------------------------------------------

    @Test
    void edgeIsNeverCreatedAndNeverDestroyed() {
        SwordArray array = at(SwordRules.GOD);
        assertEquals(36, array.whole());
        conserved(array, 0);

        assertEquals(PlantResult.PLANTED, array.plant(new Station(0, 0, 2, 5), 0));
        assertEquals(5, array.bound());
        assertEquals(31, array.loose(0));
        conserved(array, 0);

        // Seven points are out in the world as spent metal, and the Array never learns what they
        // are - it is a parameter, which is exactly why none of this needs a level.
        assertEquals(24, array.loose(7));
        assertEquals(PlantResult.PLANTED, array.plant(new Station(2, 0, 2, 10), 7));
        assertEquals(15, array.bound());
        assertEquals(14, array.loose(7));
        conserved(array, 7);

        assertTrue(array.pull(0), "the Bearing unwrites a station and the metal comes home");
        assertEquals(10, array.bound());
        assertEquals(19, array.loose(7));
        assertEquals(1, array.size());
        conserved(array, 7);

        assertEquals(PlantResult.PLANTED, array.plant(new Station(4, 0, 1, 25), 7));
        assertEquals(19, array.station(1).edge(), "clamped to the last of the loose Edge");
        assertEquals(29, array.bound());
        assertEquals(0, array.loose(7));
        conserved(array, 7);

        assertEquals(PlantResult.NO_EDGE, array.plant(new Station(6, 0, 1, 1), 7));
        conserved(array, 7);
    }

    @Test
    void spendingAStationEmptiesItWithoutUnwritingIt() {
        SwordArray array = at(SwordRules.GOD);
        assertEquals(PlantResult.PLANTED, array.plant(new Station(9, -2, 4, 6), 0));
        assertEquals(1, array.manned());

        assertEquals(1, array.spend(0, 1), "Ward takes one point per interception");
        assertEquals(new Station(9, -2, 4, 5), array.station(0));
        assertEquals(5, array.spend(0, 9), "and never more than is there");
        assertEquals(0, array.spend(0, 4), "an empty station has nothing left to give");

        assertEquals(1, array.size(), "the bearing stays authored: the shape survives, the metal does not");
        assertEquals(0, array.manned());
        assertEquals(0, array.bill());
        assertEquals(36, array.loose(0));
        assertEquals(0, array.spend(4, 1), "and a slot that is not there gives nothing");
    }

    @Test
    void pullingAnythingThatIsNotThereDoesNothing() {
        SwordArray array = at(SwordRules.GOD);
        assertFalse(array.pull(0));
        assertFalse(array.pull(-1));
        assertNull(array.station(0), "and there is no clamping an index into a list of bearings");
    }

    // ---- persistence --------------------------------------------------------------------------

    @Test
    void aSaveAndLoadKeepsTheShapeAndTheUnmannedBearingsWithIt() {
        SwordArray array = at(SwordRules.GOD);
        array.plant(new Station(0, 0, 3, 4), 0);
        array.plant(new Station(3, 1, 2, 6), 0);
        array.plant(new Station(9, -3, 5, 2), 0);
        array.spend(1, 6);
        assertEquals(34 - 12, array.bill(), "the emptied station draws no bill");

        IntArrayTag tag = array.save();
        assertEquals(4, tag.getAsIntArray().length, "the version plus one int a station");
        assertEquals(SwordArray.SAVE_VERSION, tag.getAsIntArray()[0]);

        SwordArray back = at(SwordRules.GOD);
        back.load(tag);
        assertEquals(3, back.size());
        assertEquals(new Station(0, 0, 3, 4), back.station(0));
        assertEquals(new Station(3, 1, 2, 0), back.station(1), "an emptied bearing comes back emptied");
        assertEquals(new Station(9, -3, 5, 2), back.station(2));
        assertEquals(array.bill(), back.bill());
        assertEquals(array.bound(), back.bound());
    }

    @Test
    void anEmptyArraySavesToNothingButItsVersion() {
        SwordArray array = at(SwordRules.GOD);
        assertTrue(array.isEmpty());
        assertEquals(1, array.save().getAsIntArray().length);
        assertEquals(SwordArray.SAVE_VERSION, array.save().getAsIntArray()[0]);
    }

    @Test
    void loadReRunsThePlantRulesSoAHandEditedSaveCannotHoldAnIllegalShape() {
        // A save nobody's game could have written: 36 Edge on a rung whose cap is 3, a second
        // bearing one step away, and a third that would put the bill at 36 against a draw of 24.
        IntArrayTag forged = new IntArrayTag(new int[] {
                SwordArray.SAVE_VERSION,
                new Station(0, 0, 6, 36).packed(),
                new Station(1, 0, 6, 36).packed(),
                new Station(4, 0, 6, 3).packed(),
        });

        SwordArray array = at(SwordRules.SUMMONER);
        array.load(forged);

        assertEquals(1, array.size(), "two of the three broke a rule and were dropped");
        assertEquals(new Station(0, 0, 6, 3), array.station(0), "and the survivor was clamped to the cap");
        assertEquals(18, array.bill());
        assertTrue(array.bill() <= SwordRules.SUMMONER.draw());
        assertEquals(3, array.bound());
        assertTrue(array.loose(0) >= 0);
    }

    @Test
    void aLoadIsTotalWhateverItIsHanded() {
        SwordArray array = at(SwordRules.GOD);
        array.plant(new Station(0, 0, 3, 4), 0);

        array.load(null);
        assertEquals(0, array.size(), "nothing to read is an empty Array, not the old one");

        array.plant(new Station(0, 0, 3, 4), 0);
        array.load(new IntArrayTag(new int[0]));
        assertEquals(0, array.size());

        array.plant(new Station(0, 0, 3, 4), 0);
        array.load(new IntArrayTag(new int[] {SwordArray.SAVE_VERSION + 1, new Station(0, 0, 3, 4).packed()}));
        assertEquals(0, array.size(), "a version this build cannot read is dropped whole, never guessed at");

        array.load(new IntArrayTag(new int[] {SwordArray.SAVE_VERSION, -1, 0, Integer.MAX_VALUE}));
        assertTrue(array.bill() <= SwordRules.GOD.draw(), "and rubbish still comes out legal");
        assertTrue(array.loose(0) >= 0);
    }

    @Test
    void aRungChangePutsTheShapeThroughTheNewRungsRules() {
        SwordArray array = at(SwordRules.GOD);
        array.plant(new Station(0, 0, 6, 12), 0);
        array.plant(new Station(3, 0, 6, 2), 0);
        assertEquals(84, array.bill(), "exactly the apex's draw");

        array.setRules(SwordRules.SUMMONER);
        assertEquals(8, array.whole());
        assertEquals(new Station(0, 0, 6, 3), array.station(0), "clamped to the base rung's maxEdge");
        assertEquals(1, array.size(), "and the second bearing no longer fits the base rung's draw");
        assertTrue(array.bill() <= SwordRules.SUMMONER.draw());
    }

    @Test
    void aCopyIsACopyAndNotAView() {
        SwordArray array = at(SwordRules.SAINT);
        array.plant(new Station(0, 0, 3, 4), 0);

        SwordArray copy = new SwordArray();
        copy.copyFrom(array);
        assertEquals(SwordRules.SAINT, copy.rules());
        assertEquals(1, copy.size());

        array.plant(new Station(6, 0, 3, 4), 0);
        assertEquals(1, copy.size(), "writing the original must not reach the copy");

        array.clear();
        assertEquals(0, array.size());
        assertEquals(1, copy.size());
    }

    private static void conserved(SwordArray array, int spent) {
        assertTrue(array.loose(spent) >= 0, "loose Edge is never negative");
        assertEquals(array.whole(), array.bound() + spent + array.loose(spent),
                "bound plus spent plus loose is the whole, always");
    }
}
