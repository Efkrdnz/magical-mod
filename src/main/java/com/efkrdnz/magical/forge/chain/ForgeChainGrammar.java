package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.fusion.ForgeFusion;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Turns a drawn chain of recognized glyphs into a {@link ForgeRecipe}, or into the first rule it
 * breaks. The client uses it for live feedback and the server re-runs it as the authority.
 */
public final class ForgeChainGrammar {

    /**
     * A vocabulary that refuses nothing, for the callers that have no weapon to check against.
     *
     * <p>The vocabulary arrives as a predicate over glyph ids rather than as the weapon itself so
     * this class stays free of Minecraft types - which is what lets the whole grammar be pinned by
     * a plain unit test with no bootstrap. {@code ForgeVocabulary.forWeapon} builds the real one.
     */
    public static final Predicate<String> ANY_GLYPH = id -> true;

    /** Modifier that only makes sense on a projectile form. */
    public static final String SEEKING_MODIFIER = "seeking";

    /** Forms the seeking modifier accepts. */
    public static final Set<String> PROJECTILE_FORMS = Set.of("wave", "rising");

    private ForgeChainGrammar() {
    }

    /** Validates {@code glyphs} in draw order; the first broken rule wins. */
    public static ForgeValidation validate(List<RecognizedGlyph> glyphs, Predicate<String> vocabulary) {
        if (glyphs.size() > ForgeRules.MAX_GLYPHS) {
            return invalid(ForgeError.BAD_PAYLOAD, ForgeRules.MAX_GLYPHS);
        }
        // Before any structural rule, because "this weapon cannot take that shape" is the more
        // useful answer than "you have too many forms" about a chain that was never legal here.
        for (int index = 0; index < glyphs.size(); index++) {
            if (!vocabulary.test(glyphs.get(index).id())) {
                return invalid(ForgeError.GLYPH_NOT_IN_VOCABULARY, index);
            }
        }
        List<Integer> grades = indicesOf(glyphs, GlyphCategory.GRADE);
        ForgeValidation single = requireExactlyOne(
                grades, ForgeError.MISSING_GRADE, ForgeError.DUPLICATE_GRADE);
        if (single != null) {
            return single;
        }
        List<Integer> elements = indicesOf(glyphs, GlyphCategory.ELEMENT);
        if (elements.isEmpty()) {
            return invalid(ForgeError.MISSING_ELEMENT, 0);
        }
        if (elements.size() > 2) {
            return invalid(ForgeError.DUPLICATE_ELEMENT, elements.get(2));
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
        ForgeValidation operatorFailure = checkOperators(glyphs, grade);
        if (operatorFailure != null) {
            return operatorFailure;
        }
        List<String> modifiers = idsOf(glyphs, GlyphCategory.MODIFIER);
        if (modifiers.contains(SEEKING_MODIFIER) && forms.stream().noneMatch(PROJECTILE_FORMS::contains)) {
            return invalid(ForgeError.SEEKING_NEEDS_PROJECTILE, 0);
        }
        ElementOutcome element = resolveElement(glyphs, elements, grade);
        if (element.failure() != null) {
            return element.failure();
        }
        return valid(glyphs, grade, element.id(), forms, modifiers, programOf(glyphs), tempers);
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

    /**
     * One element rune stands for itself; two fuse into a compound.
     *
     * <p>The fusion is settled here, before a recipe exists, so what leaves the grammar is always a
     * single element id. Everything downstream - the component, the rider, the impact style - deals
     * with one element and never learns that two runes went in.
     *
     * <p>Whether the smith is allowed the fusion is not decided here. This layer is Minecraft-free
     * and knows nothing about who is holding the hammer; {@code ForgeGate} answers that on both
     * sides, from the fusion this returns.
     */
    private static ElementOutcome resolveElement(
            List<RecognizedGlyph> glyphs, List<Integer> elements, ForgeGrade grade) {
        String first = glyphs.get(elements.get(0)).id();
        if (elements.size() == 1) {
            return new ElementOutcome(first, null);
        }
        if (grade.elementSlots() < 2) {
            return new ElementOutcome(null, invalid(ForgeError.FUSION_NEEDS_GRADE, elements.get(1)));
        }
        String second = glyphs.get(elements.get(1)).id();
        Optional<ForgeFusion> fusion = ForgeFusion.of(first, second);
        return fusion
                .map(found -> new ElementOutcome(found.resultPath(), null))
                .orElseGet(() -> new ElementOutcome(
                        null, invalid(ForgeError.FUSION_UNKNOWN_PAIR, elements.get(1))));
    }

    /** Either the element id the chain resolved to, or the rule it broke reaching for one. */
    private record ElementOutcome(String id, ForgeValidation failure) {
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

    /**
     * Operators are budgeted by the grade, and each one has to have a form after it to act on.
     *
     * <p>An operator drawn last would be paid for and then do nothing, which is exactly the trap
     * the trailing-modifier wrap avoids for runes. There is no sensible wrap for an operator - a
     * fork that binds the first step to nothing is not a weapon - so it is refused instead.
     */
    private static ForgeValidation checkOperators(List<RecognizedGlyph> glyphs, ForgeGrade grade) {
        int operators = 0;
        int lastOperator = -1;
        int lastForm = -1;
        for (int i = 0; i < glyphs.size(); i++) {
            GlyphCategory category = glyphs.get(i).category();
            if (category == GlyphCategory.OPERATOR) {
                operators++;
                lastOperator = i;
            } else if (category == GlyphCategory.FORM) {
                lastForm = i;
            }
        }
        if (operators > grade.operatorSlots()) {
            return invalid(ForgeError.TOO_MANY_OPERATORS, grade.operatorSlots());
        }
        return lastOperator > lastForm && lastOperator >= 0
                ? invalid(ForgeError.OPERATOR_NEEDS_FORM, lastOperator)
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
    /**
     * The run the weapon fires: every form, modifier and operator, in the order they were drawn.
     *
     * <p>Operators belong here as much as the runes they act on. A trigger only means anything
     * relative to the form that follows it, so dropping it would leave the chain compiling as
     * though it had never been drawn - charged for at the forge, absent from the weapon.
     *
     * <p>Grade, element and temper are lifted out before this and are properties of the blade
     * rather than steps of the program.
     */
    private static List<String> programOf(List<RecognizedGlyph> glyphs) {
        List<String> out = new ArrayList<>();
        for (RecognizedGlyph glyph : glyphs) {
            switch (glyph.category()) {
                case FORM, MODIFIER, OPERATOR -> out.add(glyph.id());
                case GRADE, ELEMENT, TEMPER -> {
                    // properties of the weapon, not steps of the program
                }
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
