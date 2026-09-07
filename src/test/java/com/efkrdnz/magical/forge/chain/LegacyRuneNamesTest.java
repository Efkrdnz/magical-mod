package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LegacyRuneNamesTest {

    @Test
    void elementPathStripsNamespaceAndLowerCases() {
        assertEquals(Optional.of("fire"), LegacyRuneNames.elementPath("magical:FIRE"));
        assertEquals(Optional.of("void"), LegacyRuneNames.elementPath("Void"));
    }

    @Test
    void elementPathRejectsUnknownOrMissing() {
        assertTrue(LegacyRuneNames.elementPath("terra").isEmpty());
        assertTrue(LegacyRuneNames.elementPath(null).isEmpty());
    }

    @Test
    void gradeDelegatesToForgeGradeByName() {
        assertEquals(Optional.of(ForgeGrade.HIGH), LegacyRuneNames.grade("high"));
        assertEquals(Optional.of(ForgeGrade.MASTER), LegacyRuneNames.grade("MASTER"));
        assertTrue(LegacyRuneNames.grade("x").isEmpty());
    }

    @Test
    void temperPathIsCaseInsensitiveWithKeenFallback() {
        assertEquals("heavy", LegacyRuneNames.temperPath("HEAVY"));
        assertEquals("keen", LegacyRuneNames.temperPath("nope"));
        assertEquals("keen", LegacyRuneNames.temperPath(null));
    }

    @Test
    void modifiersOnlyIncludesBindingAtTierTwoAndAbove() {
        assertEquals(List.of(), LegacyRuneNames.modifiers(1));
        assertEquals(List.of("binding"), LegacyRuneNames.modifiers(2));
        assertEquals(List.of("binding"), LegacyRuneNames.modifiers(3));
    }

    @Test
    void qualityDefaultsWhenAbsentAndClampsWhenPresent() {
        assertEquals(60, LegacyRuneNames.quality(false, 0));
        assertEquals(100, LegacyRuneNames.quality(true, 150));
        assertEquals(0, LegacyRuneNames.quality(true, -5));
    }
}
