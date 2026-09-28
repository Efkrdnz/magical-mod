package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GazeTrackerTest {
    private static final String STONE = "block:minecraft:stone";
    private static final String COW = "creature:minecraft:cow";

    @Test
    void aBlockTakesFortyStillTicksAndACreatureSixty() {
        GazeTracker tracker = new GazeTracker();
        for (int i = 1; i < GazeTracker.BLOCK_TICKS; i++) {
            assertNull(tracker.tick(STONE, true));
        }
        assertEquals(STONE, tracker.tick(STONE, true));
        assertEquals(0.0F, tracker.progress(), 1.0E-6F, "a finished gaze starts over");

        for (int i = 1; i < GazeTracker.CREATURE_TICKS; i++) {
            assertNull(tracker.tick(COW, true));
        }
        assertEquals(COW, tracker.tick(COW, true));
    }

    @Test
    void movingOrLookingAwayStartsItOver() {
        GazeTracker tracker = new GazeTracker();
        for (int i = 0; i < 30; i++) {
            tracker.tick(STONE, true);
        }
        assertEquals(0.75F, tracker.progress(), 1.0E-6F);
        tracker.tick(STONE, false);
        assertEquals(0.0F, tracker.progress(), 1.0E-6F);
        for (int i = 0; i < 30; i++) {
            tracker.tick(STONE, true);
        }
        tracker.tick(COW, true);
        assertEquals(COW, tracker.key());
        assertEquals(1.0F / GazeTracker.CREATURE_TICKS, tracker.progress(), 1.0E-6F);
        tracker.tick(null, true);
        assertNull(tracker.key());
    }
}
