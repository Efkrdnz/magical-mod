package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The enforcement pass, which is the ultimate and the failure state and the collapse, all one
 * method.
 *
 * <p>The strain is the only number in the structure the wielder does not own - a bound opponent
 * running away inflates it - so every one of these is a scale and a bill worked out by hand, never
 * a number read back out of {@code settle}. The tie-break in particular has to be pinned rather
 * than observed: "lowest slot index, never by map order" is invisible until the day somebody
 * swaps the list for a map keyed by bearing and every shed starts picking a different blade.
 */
class SettleTest {

    private static SwordArray written(SwordRules rules, Station... stations) {
        SwordArray array = new SwordArray();
        array.setRules(rules);
        for (Station station : stations) {
            assertTrue(array.plant(station, 0).accepted(), "the fixture itself must be legal: " + station);
        }
        return array;
    }

    /** Weights 12, 20, 15 - so the farthest station is deliberately not the heaviest one. */
    private static SwordArray unevenSaint() {
        return written(SwordRules.SAINT,
                new Station(0, 0, 6, 2),
                new Station(3, 0, 2, 10),
                new Station(6, 0, 5, 3));
    }

    /** Four stations of weight 12 each, written in an order their bearings do not agree with. */
    private static SwordArray evenSaint() {
        return written(SwordRules.SAINT,
                new Station(9, 0, 3, 4),
                new Station(0, 0, 4, 3),
                new Station(3, 0, 2, 6),
                new Station(6, 0, 6, 2));
    }

    /** Weights 30, 30, 20 against the apex's draw of 84: four short of overrunning by standing still. */
    private static SwordArray loadedGod() {
        return written(SwordRules.GOD,
                new Station(0, 0, 6, 5),
                new Station(3, 0, 6, 5),
                new Station(6, 0, 4, 5));
    }

    // ---- the strain itself --------------------------------------------------------------------

    @Test
    void theStrainIsTheBillTheFrameActuallyPresents() {
        SwordArray array = unevenSaint();
        assertEquals(47, array.bill());
        assertEquals(64, SwordRules.SAINT.draw());

        assertEquals(47, array.billAt(1.0D));
        assertEquals(0, array.strainAt(1.0D), "at rest a legal shape can never be strained: plant saw to it");
        assertEquals(71, array.billAt(1.5D), "ceil and not floor: half a block of shape is still over budget");
        assertEquals(7, array.strainAt(1.5D));
        assertEquals(141, array.billAt(3.0D));
        assertEquals(77, array.strainAt(3.0D));
        assertEquals(0, array.billAt(0.0D));
        assertEquals(0, array.strainAt(-1.0D), "and a nonsense scale never invents strain");
    }

    @Test
    void noStrainSettlesNothing() {
        SwordArray array = unevenSaint();
        Settlement quiet = array.settle(0, false);
        assertFalse(quiet.shedAnything());
        assertEquals(0, quiet.strainLeft());
        assertEquals(47, array.bill());
        assertEquals(Settlement.quiet(0), array.settle(-9, true));
    }

    // ---- the shed ------------------------------------------------------------------------------

    @Test
    void theFarthestHeaviestStationShedsFirst() {
        SwordArray array = unevenSaint();
        assertEquals(77, array.strainAt(3.0D));

        // 47 at scale 3 is 141 against a draw of 64. Shed the 20 and 27 remains, which is 81 -
        // still 17 over. Shed the 15 and 12 remains, which is 36, and the bill fits.
        Settlement settled = array.settle(77, false);
        assertArrayEquals(new int[] {1, 2}, settled.shedSlots(),
                "reach times Edge, so the two-block station carrying ten goes before the six-block one carrying two");
        assertEquals(0, settled.strainLeft());
        assertEquals(12, array.bill());
        assertEquals(0, array.strainAt(3.0D), "and the bill now fits at the scale that broke it");
        assertEquals(new Station(0, 0, 6, 2), array.station(0), "the station left standing is the farthest one");
    }

    @Test
    void tiesGoToTheLowestSlotAndNeverToMapOrder() {
        SwordArray array = evenSaint();
        assertEquals(48, array.bill());
        assertEquals(9, array.station(0).yaw(),
                "slot 0 is yaw 9: a shed sorted by bearing, or walking a map, would pick another one");
        assertEquals(32, array.strainAt(2.0D));

        Settlement settled = array.settle(32, false);
        assertArrayEquals(new int[] {0, 1}, settled.shedSlots(), "four equal weights, and the slots break it");
        assertEquals(0, settled.strainLeft());
        assertEquals(24, array.bill());
        assertTrue(array.station(2).manned());
        assertTrue(array.station(3).manned());
    }

    @Test
    void withoutOverdrawTheSettleRunsUntilTheBillFits() {
        SwordArray array = evenSaint();
        assertEquals(128, array.strainAt(4.0D), "48 at scale 4 is 192 against a draw of 64");

        Settlement settled = array.settle(128, false);
        assertArrayEquals(new int[] {0, 1, 2}, settled.shedSlots(), "three of the four, and not the fourth");
        assertEquals(0, settled.strainLeft(), "the pass does not stop while there is any strain left");
        assertEquals(12, array.bill());
        assertEquals(0, array.strainAt(4.0D), "the last station alone is 12, which at scale 4 is 48 and fits");
        assertEquals(4, array.size(), "and nothing was unwritten: four bearings, one of them manned");
        assertEquals(1, array.manned());
    }

    @Test
    void aShedReturnsExactlyWhatItHeldAndNotAGrainMore() {
        SwordArray array = unevenSaint();
        assertEquals(15, array.bound());
        assertEquals(11, array.loose(0));

        array.settle(77, false);

        assertEquals(2, array.bound(), "the 10 and the 3 came home");
        assertEquals(24, array.loose(0));
        assertEquals(26, array.bound() + array.loose(0), "which is the whole, because a shed creates nothing");
        assertEquals(3, array.size(), "and the shape survives the metal leaving it");
        assertEquals(new Station(3, 0, 2, 0), array.station(1));
        assertEquals(new Station(6, 0, 5, 0), array.station(2));
    }

    // ---- the apex -------------------------------------------------------------------------------

    @Test
    void withOverdrawNothingShedsUntilStrainReachesTheDraw() {
        SwordArray array = loadedGod();
        assertEquals(80, array.bill());
        assertEquals(84, SwordRules.GOD.draw());

        Settlement carried = array.settle(83, true);
        assertFalse(carried.shedAnything(), "the apex's whole rule is that exceeding the draw is legal");
        assertEquals(83, carried.strainLeft(), "so the strain simply stands, and it is sharpening every blade");
        assertEquals(80, array.bill());
        assertEquals(15, array.bound());

        Settlement letGo = array.settle(84, true);
        assertArrayEquals(new int[] {0, 1, 2}, letGo.shedSlots(), "at the draw, everything goes in one tick");
        assertEquals(0, letGo.strainLeft());
        assertEquals(0, array.bill());
        assertEquals(0, array.bound());
        assertEquals(36, array.loose(0), "converging cuts, no cooldown, and the whole of the Edge back at once");
        assertEquals(3, array.size(), "the shape is still there to be re-manned");
    }

    @Test
    void theSameStrainWithoutOverdrawWouldHaveShedTwoOfThem() {
        SwordArray array = loadedGod();
        // 80 at 167/80 is what a strain of 83 means. Shed a 30 and 50 remains, which is 105 -
        // still 21 over. Shed the other and 20 remains, which is 42, and it fits.
        Settlement settled = array.settle(83, false);
        assertArrayEquals(new int[] {0, 1}, settled.shedSlots());
        assertEquals(0, settled.strainLeft());
        assertEquals(20, array.bill());
        assertTrue(array.station(2).manned(), "which is exactly the rule Sword God removes");
    }

    // ---- the collapse ----------------------------------------------------------------------------

    @Test
    void scaleZeroDrivesTheBillToZero() {
        SwordArray array = loadedGod();
        assertEquals(0, array.billAt(0.0D), "the frame's scale multiplies every reach, so zero is a zero bill");
        assertEquals(0, array.strainAt(0.0D), "and a zero bill is not an overrun, which is why fuse is explicit");

        Settlement fused = array.fuse();
        assertArrayEquals(new int[] {0, 1, 2}, fused.shedSlots());
        assertEquals(0, fused.strainLeft());
        assertEquals(0, array.bill());
        assertEquals(0, array.bound());
        assertEquals(36, array.loose(0), "all of it summed into one blade");
        assertEquals(3, array.size(), "and every bearing still authored for the next Call the Blade");
    }

    @Test
    void fusingIsTheSettleRunAtItsLimitAndNotASecondMechanism() {
        SwordArray fused = loadedGod();
        SwordArray settled = loadedGod();

        assertEquals(settled.settle(SwordRules.GOD.draw(), true), fused.fuse(),
                "One Blade is the overdraw branch at the draw: the same code path, the same answer");
        assertEquals(settled.bill(), fused.bill());
        assertEquals(settled.bound(), fused.bound());
        assertEquals(settled.stations(), fused.stations());
    }

    @Test
    void aSaintMayFuseWithoutOwningTheApexsRule() {
        SwordArray array = unevenSaint();
        assertFalse(SwordRules.SAINT.overdraw(), "the rung does not have the flag");
        Settlement fused = array.fuse();
        assertArrayEquals(new int[] {0, 1, 2}, fused.shedSlots(),
                "and the fusion passes it by hand, because a collapse is a choice and not an overrun");
        assertEquals(0, array.bill());
        assertEquals(26, array.loose(0));
    }

    @Test
    void anEmptyArraySettlesIntoNothingWhateverItIsToldAboutTheStrain() {
        SwordArray array = new SwordArray();
        array.setRules(SwordRules.GOD);
        assertEquals(Settlement.quiet(0), array.settle(500, false), "no bill to reduce, so no shed to run");
        assertEquals(Settlement.quiet(0), array.fuse());
        assertEquals(0, array.size());
    }
}
