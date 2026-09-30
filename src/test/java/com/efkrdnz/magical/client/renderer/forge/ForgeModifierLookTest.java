package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;
import org.junit.jupiter.api.Test;

/**
 * The runes that change the steel. A blade with none must look exactly as it did, each copy of a
 * rune must show as more of it, and CHORUS's copies must stand clear of the blade on both sides -
 * a copy on top of the blade reads as one brighter blade, which says nothing.
 */
class ForgeModifierLookTest {

    private static final float EPSILON = 1.0E-5f;

    @Test
    void aBladeWithNoRunesIsUnchanged() {
        assertEquals(0, ForgeModifierLook.ghosts(ModifierStack.EMPTY));
        assertEquals(0.0f, ForgeModifierLook.ward(ModifierStack.EMPTY), EPSILON);
    }

    @Test
    void eachCopyOfChorusDrawsTheBladeOnceMore() {
        ModifierStack mods = ModifierStack.EMPTY;
        for (int copies = 1; copies <= ForgeModifierKind.CHORUS.maxStacks(); copies++) {
            mods = mods.plus(ForgeModifierKind.CHORUS);
            assertEquals(copies, ForgeModifierLook.ghosts(mods));
        }
    }

    @Test
    void chorusCopiesStandOffTheBladeOnBothSidesAndSpreadOutward() {
        // Copies drawn on the blade's own circle lay over it and read as nothing: each stands clear
        // of the blade by at least its thickness, the first two on opposite sides.
        assertTrue(Math.abs(ForgeModifierLook.ghostOffset(1)) >= 1.0f, "the first copy lies on the blade");
        assertTrue(ForgeModifierLook.ghostOffset(1) * ForgeModifierLook.ghostOffset(2) < 0.0f,
                "the first two copies stack on one side");
        assertTrue(Math.abs(ForgeModifierLook.ghostOffset(3)) > Math.abs(ForgeModifierLook.ghostOffset(1)),
                "the third copy lies on top of the first");
        assertTrue(ForgeModifierLook.ghostLag(2) > ForgeModifierLook.ghostLag(1));
        assertTrue(ForgeModifierLook.ghostLag(1) > 0.0f, "a copy lands with the blade rather than after it");
    }

    @Test
    void theWardStandsBetweenTheBladeAndTheWielder() {
        ForgeRibbon.Sweep blade = new ForgeRibbon.Sweep(ForgeRibbon.Plane.FORWARD, 2.0f, 0.5f, -60.0f, 60.0f);
        ForgeRibbon.Sweep ward = ForgeModifierLook.wardSweep(blade);
        assertTrue(ward.radius() < blade.radius() - blade.thickness(), "the ward overlaps the blade");
        assertTrue(ward.radius() - ward.thickness() > 0.0f, "the ward reaches into the wielder");
        assertEquals(blade.fromDegrees(), ward.fromDegrees(), EPSILON);
        assertEquals(blade.toDegrees(), ward.toDegrees(), EPSILON);
    }

    @Test
    void theWardGrowsWithGuardAndNeverPassesTheBladeItsGlow() {
        ModifierStack one = ModifierStack.EMPTY.plus(ForgeModifierKind.GUARD);
        ModifierStack two = one.plus(ForgeModifierKind.GUARD);
        assertTrue(ForgeModifierLook.ward(one) > 0.0f);
        assertTrue(ForgeModifierLook.ward(two) > ForgeModifierLook.ward(one));
        assertTrue(ForgeModifierLook.ward(two) <= 1.0f);
    }
}
