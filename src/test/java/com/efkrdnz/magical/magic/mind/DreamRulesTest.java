package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DreamRulesTest {
    @Test
    void theNumbersAreTheSpecs() {
        assertEquals(Belief.SURE, DreamRules.LULL_BELIEF);
        assertEquals(600, DreamRules.MOB_SLEEP_TICKS);
        assertEquals(1200, DreamRules.DREAM_TICKS);
        assertEquals(60, DreamRules.LULL_MANA);
        assertEquals(2.0F, DreamRules.ONE_HEART);
    }

    @Test
    void plotsAreLaidOutInRowsFarFromTheOrigin() {
        assertEquals(new Offset(1_000_000, 100, 1_000_000), DreamRules.origin(0));
        assertEquals(new Offset(1_000_256, 100, 1_000_000), DreamRules.origin(1));
        assertEquals(new Offset(1_000_000, 100, 1_000_256), DreamRules.origin(64));
    }

    @Test
    void aPositionKnowsItsPlotAndNothingNearTheOriginIsOne() {
        assertEquals(0, DreamRules.plotAt(1_000_000.5, 1_000_000.5));
        assertEquals(1, DreamRules.plotAt(1_000_256 + 40, 1_000_000 - 40));
        assertEquals(64, DreamRules.plotAt(1_000_000.5, 1_000_256.5));
        assertEquals(-1, DreamRules.plotAt(0.5, 0.5));
        assertEquals(-1, DreamRules.plotAt(1_000_000 + 64 * 256, 1_000_000));
    }

    @Test
    void thePlotBoundsAreThirtyTwoEachWaySixteenDownAndFortyEightUp() {
        assertTrue(DreamRules.inside(0, 1_000_000.5, 100, 1_000_000.5));
        assertTrue(DreamRules.inside(0, 1_000_000 + 32.9, 100 - 16, 1_000_000 - 32));
        assertFalse(DreamRules.inside(0, 1_000_000 + 33.1, 100, 1_000_000.5));
        assertFalse(DreamRules.inside(0, 1_000_000.5, 100 - 16.5, 1_000_000.5));
        assertFalse(DreamRules.inside(0, 1_000_000.5, 100 + 48.5, 1_000_000.5));
        assertFalse(DreamRules.inside(1, 1_000_000.5, 100, 1_000_000.5));
    }

    @Test
    void aBlowThatWouldLeaveLessThanAHeartLeavesExactlyOneAndWakes() {
        assertFalse(DreamRules.wakes(20.0F, 4.0F));
        assertEquals(4.0F, DreamRules.dealt(20.0F, 4.0F));
        assertTrue(DreamRules.wakes(20.0F, 18.0F));
        assertEquals(18.0F, DreamRules.dealt(20.0F, 18.0F));
        assertTrue(DreamRules.wakes(20.0F, 100.0F));
        assertEquals(18.0F, DreamRules.dealt(20.0F, 100.0F));
        assertTrue(DreamRules.wakes(1.5F, 0.1F));
        assertEquals(0.0F, DreamRules.dealt(1.5F, 0.1F));
    }

    @Test
    void portalsAndBossesAreNeverDreamed() {
        for (String id : new String[] {"minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway",
                "minecraft:end_portal_frame", "minecraft:wither", "minecraft:ender_dragon"}) {
            assertTrue(DreamRules.refused(id), id);
        }
        assertFalse(DreamRules.refused("minecraft:stone"));
        assertFalse(DreamRules.refused("minecraft:zombie"));
    }
}
