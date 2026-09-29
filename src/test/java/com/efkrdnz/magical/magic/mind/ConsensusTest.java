package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsensusTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void aBlockWeighsAHalfAndAFigmentAQuarterOfItsHealth() {
        assertEquals(7.5F, Consensus.clusterWeight(15), EPSILON);
        assertEquals(5.0F, Consensus.figmentWeight(20.0F), EPSILON);
        assertEquals(25.0F, Consensus.figmentWeight(100.0F), EPSILON);
        assertEquals(125.0F, Consensus.figmentWeight(500.0F), EPSILON);
    }

    @Test
    void aPlayerCountsThreeABossFiveAndAnyOtherMindOne() {
        assertEquals(3.0F, Consensus.voter("minecraft:player"), EPSILON);
        assertEquals(5.0F, Consensus.voter("minecraft:wither"), EPSILON);
        assertEquals(5.0F, Consensus.voter("minecraft:ender_dragon"), EPSILON);
        assertEquals(5.0F, Consensus.voter("minecraft:elder_guardian"), EPSILON);
        assertEquals(1.0F, Consensus.voter("minecraft:zombie"), EPSILON);
        assertEquals(1.0F, Consensus.voter("minecraft:warden"), EPSILON);
    }

    @Test
    void itManifestsAtItsWeightAndHoldsDownToHalf() {
        assertFalse(Consensus.real(false, 4.99F, 5.0F));
        assertTrue(Consensus.real(false, 5.0F, 5.0F));
        assertTrue(Consensus.real(true, 2.5F, 5.0F));
        assertFalse(Consensus.real(true, 2.49F, 5.0F));
        assertFalse(Consensus.real(false, 10.0F, 0.0F), "nothing that weighs nothing is ever real");
    }

    @Test
    void theForecastCountsMindsThatAreSure() {
        // A fifteen-block wall weighs 7.5: ten zombies at 0.8, or four players.
        assertEquals(10, Consensus.needed(7.5F, 1.0F));
        assertEquals(4, Consensus.needed(7.5F, 3.0F));
        assertEquals(7, Consensus.needed(5.0F, 1.0F));
        assertEquals(1, Consensus.needed(0.8F, 1.0F), "an exact fit is not rounded up by float error");
        assertEquals(-1, Consensus.needed(5.0F, 0.0F));
    }

    @Test
    void aRealFigmentIsAsHaleAsItsConsensus() {
        assertEquals(0.5F, Consensus.healthFraction(2.5F, 5.0F), EPSILON);
        assertEquals(1.0F, Consensus.healthFraction(9.0F, 5.0F), EPSILON);
        assertEquals(1.0F, Consensus.healthFraction(1.0F, 0.0F), EPSILON);
    }
}
