package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a drawn chain of recognized glyphs into a {@link ForgeRecipe}, or into the first rule it
 * breaks. The client uses it for live feedback and the server re-runs it as the authority.
 */
public final class ForgeChainGrammar {

    /** Modifier that only makes sense on a projectile form. */
    public static final String SEEKING_MODIFIER = "seeking";

    /** Forms the seeking modifier accepts. */
    public static final Set<String> PROJECTILE_FORMS = Set.of("wave", "rising");

    private ForgeChainGrammar() {
    }

    /** Validates {@code glyphs} in draw order; the first broken rule wins. */
    public static ForgeValidation validate(List<RecognizedGlyph> glyphs) {
        if (glyphs.size() > ForgeRules.MAX_GLYPHS) {
            return invalid(ForgeError.BAD_PAYLOAD, ForgeRules.MAX_GLYPHS);
        }
        List<Integer> grades = indicesOf(glyphs, GlyphCategory.GRADE);
        ForgeValidation single = requireExactlyOne(
                grades, ForgeError.MISSING_GRADE, ForgeError.DUPLICATE_GRADE);
        if (single != null) {
            return single;
        }
        List<Integer> elements = indicesOf(glyphs, GlyphCategory.ELEMENT);
        single = requireExactlyOne(elements, ForgeError.MISSING_ELEMENT, ForgeError.DUPLICATE_ELEMENT);
        if (single != null) {
            return single;
        }
        RecognizedGlyph gradeGlyph = glyphs.get(grades.get(0));
        Optional<ForgeGrade> resolved = ForgeGrade.byName(gradeGlyph.id());
        if (resolved.isEmpty()) {
            return invalid(ForgeError.BAD_PAYLOAD, grades.get(0));
        }
        ForgeGrade grade = resolved.get();

        List<String> forms = idsOf(glyphs, GlyphCategory.FORM);
        if (forms.isEmpty()) {
            return invalid(ForgeError.MISSING_FORM, 0);
        }
        if (forms.size() > grade.formSlots()) {
            return invalid(ForgeError.TOO_MANY_FORMS, grade.formSlots());
        }
        List<Integer> tempers = indicesOf(glyphs, GlyphCategory.TEMPER);
        if (tempers.size() > 1) {
            return invalid(ForgeError.DUPLICATE_TEMPER, tempers.get(1));
        }
        ForgeValidation modifierFailure = checkModifiers(glyphs, grade);
        if (modifierFailure != null) {
            return modifierFailure;
        }
        List<String> modifiers = idsOf(glyphs, GlyphCategory.MODIFIER);
        if (modifiers.contains(SEEKING_MODIFIER) && forms.stream().noneMatch(PROJECTILE_FORMS::contains)) {
            return invalid(ForgeError.SEEKING_NEEDS_PROJECTILE, 0);
        }
        return valid(glyphs, grade, glyphs.get(elements.get(0)).id(), forms, modifiers,
                programOf(glyphs), tempers);
    }

    private static ForgeValidation valid(
            List<RecognizedGlyph> glyphs,
            ForgeGrade grade,
            String element,
            List<String> forms,
            List<String> modifiers,
            List<String> program,
            List<Integer> tempers) {
        Optional<String> temper = tempers.isEmpty()
                ? Optional.empty()
                : Optional.of(glyphs.get(tempers.get(0)).id());
        return new ForgeValidation.Valid(
                new ForgeRecipe(element, grade, temper, forms, modifiers, program, meanQuality(glyphs)));
    }

    /** Integer mean of every glyph quality in the chain, rounded half up. */
    public static int meanQuality(List<RecognizedGlyph> glyphs) {
        if (glyphs.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (RecognizedGlyph glyph : glyphs) {
            sum += glyph.quality();
        }
        return (int) Math.round((double) sum / glyphs.size());
    }

    /**
     * Modifier runes may now repeat: two PIERCE pierce harder than one, and the cost is paid in
     * mana and in the stability the extra glyph spends.
     *
     * <p>Two ceilings still bind. A single rune cannot exceed its own
     * {@link ForgeModifierKind#maxStacks() cap}, because past that the copy would do nothing while
     * still charging for itself. And the grade's modifier slots now count glyphs rather than
     * distinct kinds, so a stack is spent out of the same budget a second rune would have been.
     */
    private static ForgeValidation checkModifiers(List<RecognizedGlyph> glyphs, ForgeGrade grade) {
        Map<String, Integer> copies = new LinkedHashMap<>();
        int total = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            RecognizedGlyph glyph = glyphs.get(i);
            if (glyph.category() != GlyphCategory.MODIFIER) {
                continue;
            }
            total++;
            int seen = copies.merge(glyph.id(), 1, Integer::sum);
            if (seen > maxStacks(glyph.id())) {
                return invalid(ForgeError.DUPLICATE_MODIFIER, i);
            }
        }
        return total > grade.modifierSlots()
                ? invalid(ForgeError.TOO_MANY_MODIFIERS, grade.modifierSlots())
                : null;
    }

    /** The stack cap of the rune with this glyph id; unknown ids get the ordinary cap. */
    private static int maxStacks(String modifierId) {
        for (ForgeModifierKind kind : ForgeModifierKind.values()) {
            if (kind.name().equalsIgnoreCase(modifierId)) {
                return kind.maxStacks();
            }
        }
        return 1;
    }

    private static ForgeValidation requireExactlyOne(
            List<Integer> indices, ForgeError missing, ForgeError duplicate) {
        if (indices.isEmpty()) {
            return invalid(missing, 0);
        }
        return indices.size() > 1 ? invalid(duplicate, indices.get(1)) : null;
    }

    private static List<Integer> indicesOf(List<RecognizedGlyph> glyphs, GlyphCategory category) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) {
            if (glyphs.get(i).category() == category) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * The forms and modifiers in the order they were drawn.
     *
     * <p>Grade, element and temper are properties of the whole weapon and are lifted out; what is
     * left is the run the weapon actually executes, and its order is the part that carries meaning.
     */
    private static List<String> programOf(List<RecognizedGlyph> glyphs) {
        List<String> out = new ArrayList<>();
        for (RecognizedGlyph glyph : glyphs) {
            if (glyph.category() == GlyphCategory.FORM || glyph.category() == GlyphCategory.MODIFIER) {
                out.add(glyph.id());
            }
        }
        return out;
    }

    private static List<String> idsOf(List<RecognizedGlyph> glyphs, GlyphCategory category) {
        List<String> out = new ArrayList<>();
        for (RecognizedGlyph glyph : glyphs) {
            if (glyph.category() == category) {
                out.add(glyph.id());
            }
        }
        return out;
    }

    private static ForgeValidation invalid(ForgeError error, int argument) {
        return new ForgeValidation.Invalid(error, argument);
    }
}
