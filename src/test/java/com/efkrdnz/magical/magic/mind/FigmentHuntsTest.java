package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FigmentHuntsTest {
    @Test
    void theUndeadHuntVillagersAndGolemsHuntTheUndead() {
        assertTrue(FigmentHunts.hunts("minecraft:zombie", "minecraft:villager"));
        assertTrue(FigmentHunts.hunts("minecraft:husk", "minecraft:iron_golem"));
        assertTrue(FigmentHunts.hunts("minecraft:iron_golem", "minecraft:zombie"));
        assertFalse(FigmentHunts.hunts("minecraft:iron_golem", "minecraft:villager"));
        assertFalse(FigmentHunts.hunts("minecraft:cow", "minecraft:villager"));
        assertTrue(FigmentHunts.huntsAny("minecraft:skeleton"));
        assertFalse(FigmentHunts.huntsAny("minecraft:cow"));
    }

    @Test
    void creepersFearCatsAndSkeletonsFearWolves() {
        assertTrue(FigmentHunts.fears("minecraft:creeper", "minecraft:cat"));
        assertTrue(FigmentHunts.fears("minecraft:creeper", "minecraft:ocelot"));
        assertTrue(FigmentHunts.fears("minecraft:skeleton", "minecraft:wolf"));
        assertFalse(FigmentHunts.fears("minecraft:zombie", "minecraft:cat"));
        assertTrue(FigmentHunts.fearsAny("minecraft:creeper"));
        assertFalse(FigmentHunts.fearsAny("minecraft:zombie"));
    }
}
