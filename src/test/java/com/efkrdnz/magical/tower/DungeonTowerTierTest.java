package com.efkrdnz.magical.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import org.junit.jupiter.api.Test;

/**
 * The tower is the second consumer of the opponent, and the one a player meets without asking for
 * it. Extending its range is only safe if the floors that already exist keep behaving exactly as
 * they did, so that is what this pins.
 */
class DungeonTowerTierTest {

    @Test
    void theFloorsThatAlreadyExistAreUnchanged() {
        // Exactly what Math.min(5, Math.max(1, floor / 2)) produced before any of this.
        int[] expected = {1, 1, 1, 2, 2, 3, 3, 4, 4, 5};
        for (int floor = 1; floor <= 10; floor++) {
            assertEquals(expected[floor - 1], DungeonTowerService.tierForFloor(floor),
                    "floor " + floor + " changed");
        }
    }

    @Test
    void everyFloorUpToTenSendsACloneAndNoneSendsAnAscendant() {
        for (int floor = 1; floor <= 10; floor++) {
            assertFalse(AscendantTier.isAscendant(DungeonTowerService.tierForFloor(floor)),
                    "floor " + floor + " started sending a boss");
        }
    }

    @Test
    void pastFloorTenItStopsCopyingYou() {
        assertEquals(AscendantTier.MIN_TIER, DungeonTowerService.tierForFloor(11),
                "the handover has to be continuous - floor 10 is tier 5, so floor 11 is tier 6");
        for (int floor = 11; floor <= 40; floor++) {
            assertTrue(AscendantTier.isAscendant(DungeonTowerService.tierForFloor(floor)),
                    "floor " + floor);
        }
    }

    @Test
    void itClimbsOneTierEveryTwoFloorsAndThenStops() {
        assertEquals(6, DungeonTowerService.tierForFloor(12));
        assertEquals(7, DungeonTowerService.tierForFloor(13));
        assertEquals(8, DungeonTowerService.tierForFloor(15));
        assertEquals(9, DungeonTowerService.tierForFloor(17));
        assertEquals(AscendantTier.MAX_TIER, DungeonTowerService.tierForFloor(19));
        assertEquals(AscendantTier.MAX_TIER, DungeonTowerService.tierForFloor(400),
                "the table runs out at 10 and the tower must not ask for more");
    }

    @Test
    void theSequenceNeverGoesBackwards() {
        int previous = 0;
        for (int floor = 1; floor <= 60; floor++) {
            int tier = DungeonTowerService.tierForFloor(floor);
            assertTrue(tier >= previous, "floor " + floor + " is easier than the floor below it");
            previous = tier;
        }
    }
}
