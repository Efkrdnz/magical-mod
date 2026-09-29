package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhantomHarmTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void lavaBurnsHardestAndACactusPricks() {
        assertEquals(4.0F, PhantomHarm.of("minecraft:lava"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:fire"), EPSILON);
        assertEquals(2.0F, PhantomHarm.of("minecraft:soul_fire"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:magma_block"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:cactus"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:sweet_berry_bush"), EPSILON);
        assertEquals(0.0F, PhantomHarm.of("minecraft:stone"), EPSILON);
    }

    @Test
    void aClusterHurtsAsMuchAsItsWorstBlock() {
        assertEquals(4.0F, PhantomHarm.of(List.of("minecraft:stone", "minecraft:cactus", "minecraft:lava")), EPSILON);
        assertEquals(0.0F, PhantomHarm.of(List.of()), EPSILON);
    }

    @Test
    void harmIsScaledByHowFirmlyItIsBelieved() {
        assertEquals(2.4F, PhantomHarm.amount(4.0F, 0.6F), EPSILON);
    }

    @Test
    void fireAndFluidsNeverManifest() {
        assertTrue(PhantomHarm.unmanifestable("minecraft:fire"));
        assertTrue(PhantomHarm.unmanifestable("minecraft:soul_fire"));
        assertTrue(PhantomHarm.unmanifestable("minecraft:lava"));
        assertTrue(PhantomHarm.unmanifestable("minecraft:water"));
        assertFalse(PhantomHarm.unmanifestable("minecraft:stone"));
        assertFalse(PhantomHarm.unmanifestable("minecraft:cactus"));
    }
}
