package com.efkrdnz.magical.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ModifierStackTest {

    @Test
    void oneOfEachKindRoundTripsWithoutBleedingIntoNeighbours() {
        for (ForgeModifierKind kind : ForgeModifierKind.values()) {
            ModifierStack stack = ModifierStack.of(Set.of(kind));
            assertTrue(stack.has(kind), kind + " should be set");
            assertEquals(1, stack.stacks(kind));
            for (ForgeModifierKind other : ForgeModifierKind.values()) {
                if (other != kind) {
                    assertFalse(stack.has(other), other + " should not be set for " + kind);
                    assertEquals(0, stack.stacks(other));
                }
            }
        }
    }

    @Test
    void everyKindAtOnceFitsInOneStack() {
        ModifierStack all = ModifierStack.of(EnumSet.allOf(ForgeModifierKind.class));
        for (ForgeModifierKind kind : ForgeModifierKind.values()) {
            assertTrue(all.has(kind));
            assertEquals(1, all.stacks(kind));
        }
    }

    @Test
    void anEmptyCollectionIsTheEmptyStack() {
        ModifierStack stack = ModifierStack.of(Set.of());
        assertTrue(stack.isEmpty());
        assertEquals(ModifierStack.EMPTY, stack);
        for (ForgeModifierKind kind : ForgeModifierKind.values()) {
            assertFalse(stack.has(kind), kind + " should not be set");
        }
    }

    @Test
    void repeatsCountAndStopAtTheKindsOwnCap() {
        ModifierStack three = ModifierStack.of(List.of(
                ForgeModifierKind.PIERCE, ForgeModifierKind.PIERCE, ForgeModifierKind.PIERCE));
        assertEquals(3, three.stacks(ForgeModifierKind.PIERCE));

        ModifierStack overStacked = three.plus(ForgeModifierKind.PIERCE);
        assertEquals(3, overStacked.stacks(ForgeModifierKind.PIERCE), "PIERCE caps at 3");
        assertEquals(three, overStacked, "a capped plus() changes nothing at all");
    }

    @Test
    void guardCapsOneLowerThanTheRest() {
        ModifierStack guards = ModifierStack.of(List.of(
                ForgeModifierKind.GUARD, ForgeModifierKind.GUARD, ForgeModifierKind.GUARD));
        assertEquals(2, guards.stacks(ForgeModifierKind.GUARD));
        assertEquals(2, ForgeModifierKind.GUARD.maxStacks());
    }

    @Test
    void withoutClearsEveryCopyAndLeavesTheRestAlone() {
        ModifierStack stack = ModifierStack.of(List.of(
                ForgeModifierKind.ECHO, ForgeModifierKind.ECHO, ForgeModifierKind.PIERCE));
        ModifierStack trimmed = stack.without(ForgeModifierKind.ECHO);

        assertFalse(trimmed.has(ForgeModifierKind.ECHO));
        assertEquals(0, trimmed.stacks(ForgeModifierKind.ECHO));
        assertEquals(1, trimmed.stacks(ForgeModifierKind.PIERCE), "PIERCE survives untouched");
    }

    @Test
    void theTopKindStillFitsInsideOneLong() {
        // GUARD is the last ordinal, so its bits sit highest. If the packing ever overflows a long
        // this is the kind that loses, and it would do so silently.
        ForgeModifierKind last = ForgeModifierKind.values()[ForgeModifierKind.values().length - 1];
        ModifierStack stack = ModifierStack.EMPTY.plus(last).plus(last);
        assertEquals(2, stack.stacks(last));
        assertTrue(stack.packed() > 0L, "packed bits must not have shifted off the end");
    }
}
