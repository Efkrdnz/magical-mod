package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The kept-glyph security boundary: what a weapon can back, and what a kept glyph is worth.
 *
 * <p>Every glyph here is built directly rather than drawn, because that is exactly the shape a
 * modified client would send: an id with no strokes behind it.</p>
 */
class ForgeKeptGlyphsTest {

    /**
     * A High-grade fire sword with two forms, a temper and a modifier, forged at 30% quality. Its
     * run was drawn slash, pierce, cleave - so the modifier sits between the two forms, which is
     * the order a reforge has to hand back.
     */
    private static final ForgeKeptChain WEAPON = new ForgeKeptChain(
            "high", List.of("fire"), Optional.of("keen"), List.of("slash", "cleave"), List.of("pierce"),
            List.of("slash", "pierce", "cleave"), 30);

    private static final int DRAWN_QUALITY = 90;

    // --- what the weapon can back ------------------------------------------------------------

    @Test
    void aKeptGlyphTheWeaponDoesNotCarryIsRejected() {
        ForgeKeptGlyphs.Resolution resolution = resolve(
                kept("high", GlyphCategory.GRADE),
                kept("divine", GlyphCategory.GRADE));

        assertFalse(resolution.ok());
        assertEquals(1, resolution.unbackedIndex());
        assertTrue(resolution.chain().isEmpty());
    }

    @Test
    void aKeptGlyphInTheWrongCategorySlotIsRejected() {
        // "fire" really is on the weapon - as its element core, not as a form. Claiming it in the
        // FORM slot must not find it, or a client could move any rune into any part of the chain.
        ForgeKeptGlyphs.Resolution resolution = resolve(kept("fire", GlyphCategory.FORM));

        assertFalse(resolution.ok());
        assertEquals(0, resolution.unbackedIndex());
    }

    @Test
    void aKeptGlyphClaimedMoreOftenThanTheWeaponCarriesItIsRejected() {
        ForgeKeptGlyphs.Resolution resolution = resolve(
                kept("slash", GlyphCategory.FORM),
                kept("slash", GlyphCategory.FORM));

        assertFalse(resolution.ok());
        assertEquals(1, resolution.unbackedIndex());
    }

    @Test
    void everyKeptGlyphIsRejectedWhenTheSlotHoldsNoForgedWeapon() {
        ForgeKeptGlyphs.Resolution resolution = ForgeKeptGlyphs.resolve(
                List.of(new ForgeKeptGlyphs.Entry.FromWeapon(
                        new ForgeKeptGlyphs.Kept("high", GlyphCategory.GRADE))),
                Optional.empty());

        assertFalse(resolution.ok());
        assertEquals(0, resolution.unbackedIndex());
    }

    @Test
    void aDrawnGlyphNeedsNoBackingAndKeepsItsOwnQuality() {
        ForgeKeptGlyphs.Resolution resolution = resolve(drawn("divine", GlyphCategory.GRADE));

        assertTrue(resolution.ok());
        assertEquals(DRAWN_QUALITY, resolution.chain().get(0).quality());
    }

    // --- what a kept glyph is worth -----------------------------------------------------------

    @Test
    void aKeptGlyphCarriesTheWeaponsRecordedQuality() {
        ForgeKeptGlyphs.Resolution resolution = resolve(kept("slash", GlyphCategory.FORM));

        assertTrue(resolution.ok());
        assertEquals(WEAPON.quality(), resolution.chain().get(0).quality());
    }

    @Test
    void qualityIsTheMeanOverKeptAndDrawnGlyphsAlike() {
        ForgeKeptGlyphs.Resolution resolution = resolve(
                kept("high", GlyphCategory.GRADE),
                kept("fire", GlyphCategory.ELEMENT),
                drawn("slam", GlyphCategory.FORM));

        assertTrue(resolution.ok());
        // (30 + 30 + 90) / 3
        assertEquals(50, ForgeChainGrammar.meanQuality(resolution.chain()));
        assertEquals(50, recipeOf(resolution).meanGlyphQuality());
    }

    /**
     * The anti-laundering rule. Keeping every rune and drawing nothing must reproduce the quality
     * the weapon already had - not a free 100 - or a 30%-quality weapon could be reforged to
     * perfect for the price of one submit, with no drawing at all.
     */
    @Test
    void aChainOfOnlyKeptGlyphsReproducesTheWeaponsOwnQuality() {
        ForgeKeptGlyphs.Resolution resolution = resolve(
                kept("high", GlyphCategory.GRADE),
                kept("fire", GlyphCategory.ELEMENT),
                kept("slash", GlyphCategory.FORM),
                kept("cleave", GlyphCategory.FORM),
                kept("keen", GlyphCategory.TEMPER),
                kept("pierce", GlyphCategory.MODIFIER));

        assertTrue(resolution.ok());
        assertEquals(WEAPON.quality(), recipeOf(resolution).meanGlyphQuality());
        assertEquals(WEAPON.quality(), ForgeRules.quality(recipeOf(resolution).meanGlyphQuality(), 0));
    }

    // --- the grammar cannot tell the two apart -------------------------------------------------

    @Test
    void theGrammarReadsAKeptChainExactlyAsItReadsADrawnOne() {
        ForgeRecipe fromKept = recipeOf(resolve(
                kept("high", GlyphCategory.GRADE),
                kept("fire", GlyphCategory.ELEMENT),
                kept("slash", GlyphCategory.FORM),
                kept("keen", GlyphCategory.TEMPER),
                kept("pierce", GlyphCategory.MODIFIER)));
        ForgeRecipe fromDrawn = recipeOf(resolve(
                drawn("high", GlyphCategory.GRADE),
                drawn("fire", GlyphCategory.ELEMENT),
                drawn("slash", GlyphCategory.FORM),
                drawn("keen", GlyphCategory.TEMPER),
                drawn("pierce", GlyphCategory.MODIFIER)));

        assertEquals(fromDrawn.grade(), fromKept.grade());
        assertEquals(fromDrawn.element(), fromKept.element());
        assertEquals(fromDrawn.temper(), fromKept.temper());
        assertEquals(fromDrawn.forms(), fromKept.forms());
        assertEquals(fromDrawn.modifiers(), fromKept.modifiers());
    }

    @Test
    void aKeptGlyphBreaksTheSameGrammarRuleADrawnOneWould() {
        // The weapon carries one grade sigil, so keeping it and drawing another is a duplicate -
        // not a way past the rule.
        ForgeValidation validation = ForgeChainGrammar.validate(resolve(
                kept("high", GlyphCategory.GRADE),
                drawn("crude", GlyphCategory.GRADE),
                kept("fire", GlyphCategory.ELEMENT),
                kept("slash", GlyphCategory.FORM)).chain());

        assertEquals(ForgeError.DUPLICATE_GRADE, ((ForgeValidation.Invalid) validation).error());
    }

    @Test
    void keptFormsStillCountAgainstTheGradesFormSlots() {
        // CRUDE holds one form; two kept forms overflow it exactly as two drawn ones would.
        ForgeValidation validation = ForgeChainGrammar.validate(resolve(
                drawn("crude", GlyphCategory.GRADE),
                kept("fire", GlyphCategory.ELEMENT),
                kept("slash", GlyphCategory.FORM),
                kept("cleave", GlyphCategory.FORM)).chain());

        assertEquals(ForgeError.TOO_MANY_FORMS, ((ForgeValidation.Invalid) validation).error());
    }

    // --- preload order --------------------------------------------------------------------------

    @Test
    void preloadOrderIsGradeElementTemperThenTheRunAsDrawn() {
        List<String> ids = WEAPON.preloadOrder().stream().map(ForgeKeptGlyphs.Kept::id).toList();

        assertEquals(List.of("high", "fire", "keen", "slash", "pierce", "cleave"), ids);
        assertEquals(GlyphCategory.TEMPER, WEAPON.preloadOrder().get(2).category());
        assertEquals(GlyphCategory.MODIFIER, WEAPON.preloadOrder().get(4).category(),
                "the modifier keeps its place between the two forms");
    }

    @Test
    void preloadOrderFallsBackToFormsThenModifiersForAPreProgramWeapon() {
        // No stored program: there is no drawn order left to recover, and forms-then-modifiers is
        // the order that weapon always behaved as.
        ForgeKeptChain legacy = new ForgeKeptChain(
                "high", List.of("fire"), Optional.of("keen"), List.of("slash", "cleave"), List.of("pierce"),
                List.of(), 30);

        assertEquals(List.of("high", "fire", "keen", "slash", "cleave", "pierce"),
                legacy.preloadOrder().stream().map(ForgeKeptGlyphs.Kept::id).toList());
    }

    @Test
    void preloadOrderSkipsATemperTheWeaponDoesNotHave() {
        ForgeKeptChain plain = new ForgeKeptChain(
                "crude", List.of("frost"), Optional.empty(), List.of("thrust"), List.of(), List.of("thrust"), 55);

        assertEquals(List.of("crude", "frost", "thrust"),
                plain.preloadOrder().stream().map(ForgeKeptGlyphs.Kept::id).toList());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static ForgeKeptGlyphs.Resolution resolve(ForgeKeptGlyphs.Entry... entries) {
        return ForgeKeptGlyphs.resolve(List.of(entries), Optional.of(WEAPON));
    }

    private static ForgeKeptGlyphs.Entry kept(String id, GlyphCategory category) {
        return new ForgeKeptGlyphs.Entry.FromWeapon(new ForgeKeptGlyphs.Kept(id, category));
    }

    private static ForgeKeptGlyphs.Entry drawn(String id, GlyphCategory category) {
        return new ForgeKeptGlyphs.Entry.Drawn(new RecognizedGlyph(id, category, DRAWN_QUALITY));
    }

    private static ForgeRecipe recipeOf(ForgeKeptGlyphs.Resolution resolution) {
        return ((ForgeValidation.Valid) ForgeChainGrammar.validate(resolution.chain())).recipe();
    }
}
