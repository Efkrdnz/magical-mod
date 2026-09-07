package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * The Phase 0 gate. Programs are a new way of holding the same chain, so a chain drawn today
     * must fire exactly as it did before: one step per form, in draw order, every step carrying
     * every modifier. If this ever fails, weapons players already own have quietly changed.
     */
    @Test
    void aChainWithoutOperatorsStillGivesEveryModifierToEveryForm() {
        List<String> program = List.of("pierce", "slash", "thrust", "haste", "spin");

        ForgeProgram compiled = ForgeChainCompiler.compile(program);

        assertEquals(3, compiled.length(), "one step per form, however the modifiers were interleaved");
        assertEquals(List.of("slash", "thrust", "spin"),
                compiled.steps().stream().map(ForgeStep::leadForm).toList());

        ModifierStack expected = ModifierStack.of(List.of(ForgeModifierKind.PIERCE, ForgeModifierKind.HASTE));
        for (ForgeStep step : compiled.steps()) {
            assertEquals(expected, step.mods(), "every step carries the whole weapon's modifiers");
        }
    }

    @Test
    void drawOrderDoesNotYetChangeWhatCompiles() {
        // Both orders are the same weapon today. Phase 1 is where this assertion is meant to break.
        ForgeProgram before = ForgeChainCompiler.compile(List.of("pierce", "slash", "spin"));
        ForgeProgram after = ForgeChainCompiler.compile(List.of("slash", "pierce", "spin"));
        assertEquals(before, after);
    }

    @Test
    void aProgramBuiltFromAChainMatchesTheLegacyRebuildOfTheSameChain() {
        List<String> program = List.of("slash", "pierce", "cleave", "leech");

        ForgeProgram compiled = ForgeChainCompiler.compile(program);
        ForgeProgram legacy = ForgeChainCompiler.fromLegacy(
                List.of("slash", "cleave"), List.of("pierce", "leech"));

        assertEquals(legacy, compiled,
                "a weapon with a stored program and one without must fire identically");
    }

    @Test
    void repeatedModifiersInOneChainStack() {
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("pierce", "pierce", "slash"));
        assertEquals(2, compiled.stepAt(0).mods().stacks(ForgeModifierKind.PIERCE));
    }

    @Test
    void unknownRunesAreSkippedRatherThanFailingTheChain() {
        // A weapon forged by a newer build can name a rune this one has never heard of.
        ForgeProgram compiled = ForgeChainCompiler.compile(List.of("slash", "wormhole", "pierce"));

        assertEquals(1, compiled.length());
        assertEquals("slash", compiled.stepAt(0).leadForm());
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
