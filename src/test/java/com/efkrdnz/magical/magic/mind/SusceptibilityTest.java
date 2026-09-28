package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SusceptibilityTest {
    @Test
    void theTableIsTheSpecs() {
        assertEquals(1.3F, Susceptibility.of("minecraft:zombie"));
        assertEquals(1.3F, Susceptibility.of("minecraft:husk"));
        assertEquals(1.3F, Susceptibility.of("minecraft:drowned"));
        assertEquals(1.2F, Susceptibility.of("minecraft:villager"));
        assertEquals(1.1F, Susceptibility.of("minecraft:creeper"));
        assertEquals(1.0F, Susceptibility.of("minecraft:skeleton"));
        assertEquals(1.0F, Susceptibility.of("minecraft:player"));
        assertEquals(0.9F, Susceptibility.of("minecraft:spider"));
        assertEquals(0.5F, Susceptibility.of("minecraft:witch"));
        assertEquals(0.4F, Susceptibility.of("minecraft:enderman"));
        assertEquals(0.2F, Susceptibility.of("minecraft:wither"));
        assertEquals(0.2F, Susceptibility.of("minecraft:ender_dragon"));
        assertEquals(1.0F, Susceptibility.of("minecraft:cow"), "anything unlisted is ordinary");
    }

    @Test
    void theWardenIsBlindAndNothingElseIs() {
        assertTrue(Susceptibility.blind("minecraft:warden"));
        assertFalse(Susceptibility.blind("minecraft:zombie"));
    }
}
