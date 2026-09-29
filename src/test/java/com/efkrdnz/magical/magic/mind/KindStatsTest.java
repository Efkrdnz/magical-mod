package com.efkrdnz.magical.magic.mind;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KindStatsTest {
    private static final float EPSILON = 1.0E-5F;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aKindIsAsToughAndAsStrongAsItsVanillaSelf() {
        assertEquals(20.0F, KindStats.maxHealth("minecraft:zombie"), EPSILON);
        assertEquals(3.0F, KindStats.attack("minecraft:zombie"), EPSILON);
        assertEquals(100.0F, KindStats.maxHealth("minecraft:iron_golem"), EPSILON);
        assertEquals(15.0F, KindStats.attack("minecraft:iron_golem"), EPSILON);
        assertEquals(500.0F, KindStats.maxHealth("minecraft:warden"), EPSILON);
        assertEquals(30.0F, KindStats.attack("minecraft:warden"), EPSILON);
        assertEquals(20.0F, KindStats.maxHealth("minecraft:villager"), EPSILON);
        assertEquals(0.0F, KindStats.attack("minecraft:villager"), EPSILON);
    }

    @Test
    void anUnknownKindIsAnOrdinaryBodyWithNoBite() {
        assertEquals(20.0F, KindStats.maxHealth("minecraft:no_such_thing"), EPSILON);
        assertEquals(0.0F, KindStats.attack("minecraft:no_such_thing"), EPSILON);
        assertEquals(20.0F, KindStats.maxHealth("not an id"), EPSILON);
        assertEquals(0.0F, KindStats.attack("not an id"), EPSILON);
    }
}
