package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.ForgeModifierKind;

class ForgeChainGrammarTest {

    private static RecognizedGlyph grade(String id) {
        return new RecognizedGlyph(id, GlyphCategory.GRADE, 70);
    }

    private static RecognizedGlyph element(String id) {
        return new RecognizedGlyph(id, GlyphCategory.ELEMENT, 70);
    }

    private static RecognizedGlyph form(String id) {
        return new RecognizedGlyph(id, GlyphCategory.FORM, 70);
    }

    private static RecognizedGlyph temper(String id) {
        return new RecognizedGlyph(id, GlyphCategory.TEMPER, 70);
    }

    private static RecognizedGlyph modifier(String id) {
        return new RecognizedGlyph(id, GlyphCategory.MODIFIER, 70);
    }

    private static ForgeRecipe validRecipe(List<RecognizedGlyph> glyphs) {
        ForgeValidation validation = ForgeChainGrammar.validate(glyphs);
        return assertInstanceOf(ForgeValidation.Valid.class, validation).recipe();
    }

    private static ForgeValidation.Invalid invalid(List<RecognizedGlyph> glyphs) {
        return assertInstanceOf(ForgeValidation.Invalid.class, ForgeChainGrammar.validate(glyphs));
    }

    @Test
    void aMinimalChainIsValid() {
        ForgeRecipe recipe = validRecipe(List.of(grade("crude"), element("fire"), form("slash")));

        assertEquals("fire", recipe.element());
        assertEquals(ForgeGrade.CRUDE, recipe.grade());
        assertEquals(Optional.empty(), recipe.temper());
        assertEquals(List.of("slash"), recipe.forms());
        assertEquals(List.of(), recipe.modifiers());
        assertEquals(70, recipe.meanGlyphQuality());
    }

    @Test
    void formsKeepTheirDrawOrder() {
        ForgeRecipe recipe = validRecipe(List.of(
                grade("high"), element("frost"), form("rising"), form("slash"), modifier("seeking")));

        assertEquals(List.of("rising", "slash"), recipe.forms());
        assertEquals(List.of("seeking"), recipe.modifiers());
    }

    @Test
    void meanGlyphQualityRoundsHalfUp() {
        ForgeRecipe recipe = validRecipe(List.of(
                new RecognizedGlyph("crude", GlyphCategory.GRADE, 50),
                new RecognizedGlyph("fire", GlyphCategory.ELEMENT, 51),
                new RecognizedGlyph("slash", GlyphCategory.FORM, 51),
                new RecognizedGlyph("keen", GlyphCategory.TEMPER, 50)));

        assertEquals(51, recipe.meanGlyphQuality());
        assertEquals(Optional.of("keen"), recipe.temper());
    }

    @Test
    void anOverlongPayloadIsRejected() {
        List<RecognizedGlyph> glyphs = new ArrayList<>();
        glyphs.add(grade("crude"));
        glyphs.add(element("fire"));
        for (int i = 0; i < 11; i++) {
            glyphs.add(form("slash"));
        }

        ForgeValidation.Invalid invalid = invalid(glyphs);

        assertEquals(13, glyphs.size());
        assertEquals(ForgeError.BAD_PAYLOAD, invalid.error());
        assertEquals(ForgeRules.MAX_GLYPHS, invalid.argument());
    }

    @Test
    void aChainWithoutAGradeIsRejected() {
        ForgeValidation.Invalid invalid = invalid(List.of(element("fire"), form("slash")));

        assertEquals(ForgeError.MISSING_GRADE, invalid.error());
        assertEquals(0, invalid.argument());
    }

    @Test
    void aSecondGradeIsRejectedAtItsIndex() {
        ForgeValidation.Invalid invalid =
                invalid(List.of(grade("crude"), element("fire"), grade("fine"), form("slash")));

        assertEquals(ForgeError.DUPLICATE_GRADE, invalid.error());
        assertEquals(2, invalid.argument());
    }

    @Test
    void aChainWithoutAnElementIsRejected() {
        ForgeValidation.Invalid invalid = invalid(List.of(grade("crude"), form("slash")));

        assertEquals(ForgeError.MISSING_ELEMENT, invalid.error());
        assertEquals(0, invalid.argument());
    }

    @Test
    void aSecondElementBelowMythicIsRejectedAtItsIndex() {
        ForgeValidation.Invalid invalid =
                invalid(List.of(grade("crude"), element("fire"), form("slash"), element("void")));

        assertEquals(ForgeError.FUSION_NEEDS_GRADE, invalid.error());
        assertEquals(3, invalid.argument());
    }

    @Test
    void aThirdElementIsRejectedHoweverGoodTheGrade() {
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("divine"), element("fire"), element("void"), element("frost"), form("slash")));

        assertEquals(ForgeError.DUPLICATE_ELEMENT, invalid.error());
        assertEquals(3, invalid.argument());
    }

    @Test
    void twoElementsAtMythicFuseIntoOne() {
        ForgeRecipe recipe = validRecipe(List.of(
                grade("mythic"), element("fire"), element("void"), form("slash")));

        assertEquals("black_flame", recipe.element(),
                "the chain leaves the grammar carrying one element, not two");
    }

    @Test
    void fusionReadsTheSameInEitherOrder() {
        ForgeRecipe drawn = validRecipe(List.of(
                grade("mythic"), element("fire"), element("gale"), form("slash")));
        ForgeRecipe reversed = validRecipe(List.of(
                grade("mythic"), element("gale"), element("fire"), form("slash")));

        assertEquals("explosion", drawn.element());
        assertEquals(drawn.element(), reversed.element(),
                "the element is the material of the blade, not a step in the program");
    }

    @Test
    void twoElementsThatDoNotFuseAreRejected() {
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("mythic"), element("terra"), element("radiant"), form("slash")));

        assertEquals(ForgeError.FUSION_UNKNOWN_PAIR, invalid.error());
    }

    @Test
    void aChainWithoutAFormIsRejected() {
        ForgeValidation.Invalid invalid = invalid(List.of(grade("crude"), element("fire")));

        assertEquals(ForgeError.MISSING_FORM, invalid.error());
        assertEquals(0, invalid.argument());
    }

    @Test
    void moreFormsThanTheGradeAllowsIsRejected() {
        ForgeValidation.Invalid invalid =
                invalid(List.of(grade("crude"), element("fire"), form("slash"), form("cleave")));

        assertEquals(ForgeError.TOO_MANY_FORMS, invalid.error());
        assertEquals(ForgeGrade.CRUDE.formSlots(), invalid.argument());
        assertEquals(1, invalid.argument());
    }

    @Test
    void aSecondTemperIsRejectedAtItsIndex() {
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("crude"), element("fire"), form("slash"), temper("keen"), temper("heavy")));

        assertEquals(ForgeError.DUPLICATE_TEMPER, invalid.error());
        assertEquals(4, invalid.argument());
    }

    @Test
    void aRepeatedModifierIsAcceptedWhenTheGradeCanPayForIt() {
        ForgeRecipe recipe = validRecipe(List.of(
                grade("divine"), element("fire"), form("slash"), modifier("pierce"), modifier("pierce")));

        assertEquals(List.of("pierce", "pierce"), recipe.modifiers(),
                "both copies are kept - the stack is what the player drew");
        assertEquals(2, recipe.compiled().stepAt(0).mods().stacks(ForgeModifierKind.PIERCE));
    }

    @Test
    void aModifierRepeatedPastItsOwnCapIsRejectedAtThatIndex() {
        // PIERCE caps at three copies. A fourth would be charged for and do nothing.
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("divine"), element("fire"), form("slash"),
                modifier("pierce"), modifier("pierce"), modifier("pierce"), modifier("pierce")));

        assertEquals(ForgeError.DUPLICATE_MODIFIER, invalid.error());
        assertEquals(6, invalid.argument(), "the index of the copy that went too far");
    }

    @Test
    void guardIsRejectedOneCopyEarlierThanTheOtherRunes() {
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("divine"), element("fire"), form("slash"),
                modifier("guard"), modifier("guard"), modifier("guard")));

        assertEquals(ForgeError.DUPLICATE_MODIFIER, invalid.error());
        assertEquals(5, invalid.argument());
    }

    @Test
    void aStackSpendsTheGradeModifierSlotsLikeTwoDifferentRunesWould() {
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("high"), element("fire"), form("slash"), modifier("pierce"), modifier("pierce")));

        assertEquals(ForgeError.TOO_MANY_MODIFIERS, invalid.error(),
                "HIGH has one modifier slot, and two copies spend two of them");
    }

    @Test
    void moreModifiersThanTheGradeAllowsIsRejected() {
        ForgeValidation.Invalid invalid = invalid(List.of(
                grade("high"), element("fire"), form("slash"), modifier("pierce"), modifier("reach")));

        assertEquals(ForgeError.TOO_MANY_MODIFIERS, invalid.error());
        assertEquals(ForgeGrade.HIGH.modifierSlots(), invalid.argument());
        assertEquals(1, invalid.argument());
    }

    @Test
    void seekingNeedsAProjectileForm() {
        ForgeValidation.Invalid invalid =
                invalid(List.of(grade("high"), element("fire"), form("slash"), modifier("seeking")));

        assertEquals(ForgeError.SEEKING_NEEDS_PROJECTILE, invalid.error());
        assertEquals(0, invalid.argument());
    }

    @Test
    void seekingIsAllowedAlongsideRisingOrWave() {
        assertEquals(List.of("seeking"),
                validRecipe(List.of(grade("high"), element("fire"), form("rising"), modifier("seeking")))
                        .modifiers());
        assertEquals(List.of("seeking"),
                validRecipe(List.of(grade("high"), element("fire"), form("wave"), modifier("seeking")))
                        .modifiers());
    }

    @Test
    void anUnknownGradeIdIsABadPayload() {
        ForgeValidation.Invalid invalid =
                invalid(List.of(grade("legendary"), element("fire"), form("slash")));

        assertEquals(ForgeError.BAD_PAYLOAD, invalid.error());
    }

    @Test
    void errorLangKeysAreDerivedFromTheName() {
        for (ForgeError error : ForgeError.values()) {
            assertEquals("forge.magical.error." + error.name().toLowerCase(Locale.ROOT), error.langKey());
        }
        assertEquals("forge.magical.error.seeking_needs_projectile",
                ForgeError.SEEKING_NEEDS_PROJECTILE.langKey());
        assertTrue(ForgeError.values().length >= 22);
    }

    @Test
    void recipeListsAreDefensiveCopies() {
        List<String> forms = new ArrayList<>(List.of("slash"));
        List<String> modifiers = new ArrayList<>(List.of("pierce"));
        List<String> program = new ArrayList<>(List.of("pierce", "slash"));
        ForgeRecipe recipe = new ForgeRecipe(
                "fire", ForgeGrade.HIGH, Optional.empty(), forms, modifiers, program, 70);

        forms.add("cleave");
        modifiers.clear();
        program.clear();

        assertEquals(List.of("slash"), recipe.forms());
        assertEquals(List.of("pierce"), recipe.modifiers());
        assertEquals(List.of("pierce", "slash"), recipe.program());
    }
}
