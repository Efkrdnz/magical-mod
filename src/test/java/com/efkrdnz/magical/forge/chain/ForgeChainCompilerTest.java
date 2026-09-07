package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;

class ForgeChainCompilerTest {

    @Test
    void aModifierAttachesOnlyToTheFormDrawnAfterIt() {
        ForgeProgram compiled = ForgeChainCompiler.compile(
                List.of("pierce", "slash", "thrust", "haste", "spin"));

        assertEquals(3, compiled.length(), "one step per form, however the modifiers were interleaved");
        assertEquals(List.of("slash", "thrust", "spin"),
                compiled.steps().stream().map(ForgeStep::leadForm).toList());

        assertTrue(compiled.stepAt(0).mods().has(ForgeModifierKind.PIERCE), "pierce lands on the slash");
        assertFalse(compiled.stepAt(1).mods().has(ForgeModifierKind.PIERCE), "and is consumed there");
        assertTrue(compiled.stepAt(2).mods().has(ForgeModifierKind.HASTE), "haste lands on the spin");
        assertFalse(compiled.stepAt(1).mods().has(ForgeModifierKind.HASTE));
        assertTrue(compiled.stepAt(1).mods().isEmpty(), "the thrust was drawn bare");
    }

    @Test
    void drawOrderChangesTheWeapon() {
        ForgeProgram before = ForgeChainCompiler.compile(List.of("pierce", "slash", "spin"));
        ForgeProgram after = ForgeChainCompiler.compile(List.of("slash", "pierce", "spin"));

        assertNotEquals(before, after, "pierce-slash-spin is not the same weapon as slash-pierce-spin");
        assertTrue(before.stepAt(0).mods().has(ForgeModifierKind.PIERCE));
        assertTrue(after.stepAt(1).mods().has(ForgeModifierKind.PIERCE));
    }

    @Test
    void severalModifiersBeforeOneFormAllLandOnIt() {
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("pierce", "haste", "slash", "spin"));

        assertTrue(compiled.stepAt(0).mods().has(ForgeModifierKind.PIERCE));
        assertTrue(compiled.stepAt(0).mods().has(ForgeModifierKind.HASTE));
        assertTrue(compiled.stepAt(1).mods().isEmpty());
    }

    @Test
    void aModifierDrawnAfterTheLastFormWrapsRoundToTheFirstStep() {
        // The combo wraps after the finisher, so the last rune sits just before the first one. A
        // trailing rune that attached to nothing would still be charged for.
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("slash", "spin", "leech"));

        assertTrue(compiled.stepAt(0).mods().has(ForgeModifierKind.LEECH));
        assertFalse(compiled.stepAt(1).mods().has(ForgeModifierKind.LEECH));
    }

    @Test
    void trailingModifiersStackOntoWhatTheFirstStepAlreadyCarries() {
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("pierce", "slash", "spin", "pierce"));
        assertEquals(2, compiled.stepAt(0).mods().stacks(ForgeModifierKind.PIERCE));
    }

    /**
     * A weapon forged before programs existed kept no draw order, so it must still resolve the way
     * it always did: every modifier on every form. If this fails, weapons players already own have
     * quietly changed.
     */
    @Test
    void aWeaponWithoutAStoredProgramKeepsWholeWeaponModifiers() {
        ForgeProgram legacy = ForgeChainCompiler.fromLegacy(
                List.of("slash", "cleave"), List.of("pierce", "leech"));

        ModifierStack both = ModifierStack.of(List.of(ForgeModifierKind.PIERCE, ForgeModifierKind.LEECH));
        assertEquals(2, legacy.length());
        assertEquals(both, legacy.stepAt(0).mods());
        assertEquals(both, legacy.stepAt(1).mods(), "every step carries the whole weapon modifiers");
    }

    @Test
    void repeatedModifiersBeforeOneFormStackOnIt() {
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("pierce", "pierce", "slash"));
        assertEquals(2, compiled.stepAt(0).mods().stacks(ForgeModifierKind.PIERCE));
    }

    @Test
    void unknownRunesAreSkippedRatherThanFailingTheChain() {
        // A weapon forged by a newer build can name a rune this one has never heard of.
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("slash", "wormhole", "pierce"));

        assertEquals(1, compiled.length());
        assertEquals("slash", compiled.stepAt(0).leadForm());
        // The pierce trails the only form, so it wraps onto it.
        assertTrue(compiled.stepAt(0).mods().has(ForgeModifierKind.PIERCE));
    }

    @Test
    void aChainWithNoFormsCompilesToNothingRatherThanThrowing() {
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("pierce", "haste"));
        assertTrue(compiled.isEmpty());
        assertSame(0, compiled.length());
    }

    @Test
    void stepAtClampsInsteadOfRunningOffTheEnd() {
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("slash", "spin"));
        assertEquals("slash", compiled.stepAt(-1).leadForm());
        assertEquals("spin", compiled.stepAt(99).leadForm(), "a stale index resolves, it does not throw");
    }

    @Test
    void aPlainStepIsNeitherForkedNorCarryingAPayload() {
        ForgeStep step = ForgeChainCompiler.compile(List.of("slash")).stepAt(0);
        assertEquals(1, step.width());
        assertFalse(step.isForked());
        assertTrue(step.payload().isEmpty());
    }

    /**
     * The compiler maps a modifier glyph id onto its kind by name, because it has to stay free of
     * the Minecraft-side registry that holds the full definition. That is only safe while the two
     * sets of words agree, so pin them.
     */
    @Test
    void everyModifierGlyphIdNamesAModifierKind() {
        List<String> glyphIds = new ArrayList<>();
        for (GlyphTemplate template : ForgeGlyphLibrary.all()) {
            if (template.category() == GlyphCategory.MODIFIER) {
                glyphIds.add(template.id());
            }
        }
        assertEquals(ForgeModifierKind.values().length, glyphIds.size(),
                "a modifier glyph exists for every kind and no more");
        for (String id : glyphIds) {
            ForgeProgram compiled = ForgeChainCompiler.compile(List.of(id, "slash"));
            assertFalse(compiled.stepAt(0).mods().isEmpty(),
                    id + " is a modifier glyph but compiles to no modifier kind");
        }
    }
}
