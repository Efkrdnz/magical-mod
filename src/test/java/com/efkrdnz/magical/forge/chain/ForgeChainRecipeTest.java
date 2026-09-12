package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;

/**
 * End-to-end chains a player would actually draw, checked all the way from glyph ids to the
 * program the weapon fires. These are the recipes worth being sure about.
 */
class ForgeChainRecipeTest {

    private static final int CLEAN = 90;

    private static ForgeRecipe forge(String... ids) {
        List<RecognizedGlyph> glyphs = new ArrayList<>();
        for (String id : ids) {
            GlyphTemplate template = ForgeGlyphLibrary.byId(id)
                    .orElseThrow(() -> new AssertionError("no glyph named " + id));
            glyphs.add(new RecognizedGlyph(id, template.category(), CLEAN));
        }
        ForgeValidation validation = ForgeChainGrammar.validate(glyphs, ForgeChainGrammar.ANY_GLYPH);
        assertInstanceOf(ForgeValidation.Valid.class, validation,
                () -> "chain was refused: " + validation);
        return ((ForgeValidation.Valid) validation).recipe();
    }

    @Test
    void anExplosionWaveDetonatesWhereItLands() {
        ForgeRecipe recipe = forge("mythic", "fire", "gale", "wave");

        assertEquals("explosion", recipe.element());
        ForgeProgram program = recipe.compiled();
        assertEquals(1, program.length());
        assertEquals("wave", program.stepAt(0).leadForm());
    }

    @Test
    void aBlackFlameWaveCanBurstOnImpact() {
        ForgeRecipe recipe = forge("mythic", "fire", "dark", "trigger", "wave", "slam");

        assertEquals("black_flame", recipe.element());
        ForgeProgram program = recipe.compiled();
        assertEquals(1, program.length(), "the slam is nested, not a press of its own");

        ForgeStep carrier = program.stepAt(0);
        assertEquals("wave", carrier.leadForm());
        assertTrue(carrier.payload().isPresent());
        assertEquals(TriggerKind.IMPACT, carrier.payload().get().kind());
        assertEquals("slam", carrier.payload().get().step().leadForm());
    }

    @Test
    void aBlackFlameWaveCanBurstWhereItExpiresInstead() {
        ForgeRecipe recipe = forge("mythic", "fire", "dark", "wake", "wave", "slam");

        assertEquals(TriggerKind.EXPIRY,
                recipe.compiled().stepAt(0).payload().get().kind());
    }

    @Test
    void theWeaponOfMassDestruction() {
        // Three explosive crescents on one press, each birthing a slam where its flight ends.
        // The wake is drawn before the waves it acts on: an operator names the carrier that
        // follows it, and the form after that carrier becomes the payload.
        ForgeRecipe recipe = forge(
                "divine", "fire", "gale", "fork", "fork", "wake", "wave", "wave", "wave", "slam");

        assertEquals("explosion", recipe.element());
        ForgeProgram program = recipe.compiled();
        assertEquals(1, program.length());
        assertEquals(3, program.stepAt(0).width());
        assertEquals(TriggerKind.EXPIRY, program.stepAt(0).payload().get().kind());
    }
}
