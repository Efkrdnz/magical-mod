package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ForgeMaterialTest {

    @Test
    void woodAndStoneCapAtFine() {
        assertEquals(ForgeGrade.FINE, ForgeMaterial.WOOD.maxGrade());
        assertEquals(ForgeGrade.FINE, ForgeMaterial.STONE.maxGrade());
    }

    @Test
    void ironAndGoldCapAtHigh() {
        assertEquals(ForgeGrade.HIGH, ForgeMaterial.IRON.maxGrade());
        assertEquals(ForgeGrade.HIGH, ForgeMaterial.GOLD.maxGrade());
    }

    @Test
    void diamondCapsAtMaster() {
        assertEquals(ForgeGrade.MASTER, ForgeMaterial.DIAMOND.maxGrade());
    }

    @Test
    void netheriteCapsAtMythic() {
        assertEquals(ForgeGrade.MYTHIC, ForgeMaterial.NETHERITE.maxGrade());
    }

    @Test
    void unknownCapsAtHigh() {
        assertEquals(ForgeGrade.HIGH, ForgeMaterial.UNKNOWN.maxGrade());
    }
}
