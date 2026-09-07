package com.efkrdnz.magical.forge.chain;

import java.util.ArrayList;
import java.util.List;

import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;

/**
 * Turns the ordered run of a rune chain - the forms and modifiers, with grade, element and temper
 * already lifted out - into the {@link ForgeProgram} a weapon fires.
 *
 * <p>Minecraft-free on purpose: the client compiles a program to preview it and the server compiles
 * the same run to store it, and the two must agree exactly.
 */
public final class ForgeChainCompiler {

    private ForgeChainCompiler() {
    }

    /**
     * Compiles a program in which every modifier applies to every form.
     *
     * <p>This is what the forge has always done, and it is deliberately still what it does: the
     * step machinery lands first and behaves identically, so a weapon forged before this change and
     * one forged after resolve to the same strike. Making a modifier attach only to the form drawn
     * after it is a separate, visible change.
     *
     * <p>It is also the permanent path for weapons already in the world, which stored a flat form
     * list and a flat modifier list and no order to recover. Those keep these semantics forever -
     * see {@link #fromLegacy}.
     */
    public static ForgeProgram compile(List<String> program) {
        List<String> forms = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        for (String id : program) {
            categoryOf(id).ifPresent(category -> {
                if (category == GlyphCategory.FORM) {
                    forms.add(id);
                } else if (category == GlyphCategory.MODIFIER) {
                    modifiers.add(id);
                }
            });
        }
        return fromLegacy(forms, modifiers);
    }

    /**
     * Builds a program from a weapon's flat form and modifier lists: one step per form, carrying
     * every modifier.
     *
     * <p>A weapon forged before programs existed has no draw order left to read - the component
     * stored the forms in order but collapsed the modifiers into a set that applied to all of them.
     * Reproducing that exactly is the only migration that does not silently re-roll a weapon the
     * player already owns.
     */
    public static ForgeProgram fromLegacy(List<String> forms, List<String> modifiers) {
        ModifierStack shared = stackOf(modifiers);
        List<ForgeStep> steps = new ArrayList<>(forms.size());
        for (String form : forms) {
            steps.add(ForgeStep.of(form, shared));
        }
        return new ForgeProgram(steps);
    }

    /** The modifier ids a program run carries, in draw order, repeats included. */
    public static ModifierStack stackOf(List<String> modifierIds) {
        List<ForgeModifierKind> kinds = new ArrayList<>();
        for (String id : modifierIds) {
            kindOf(id).ifPresent(kinds::add);
        }
        return ModifierStack.of(kinds);
    }

    private static java.util.Optional<GlyphCategory> categoryOf(String id) {
        return ForgeGlyphLibrary.byId(id).map(template -> template.category());
    }

    /**
     * Maps a modifier glyph id onto its kind by name. The glyph ids and the enum constants are the
     * same words in different cases, which is what lets this layer stay free of the registry that
     * holds the full {@code ModifierDefinition}.
     */
    private static java.util.Optional<ForgeModifierKind> kindOf(String id) {
        for (ForgeModifierKind kind : ForgeModifierKind.values()) {
            if (kind.name().equalsIgnoreCase(id)) {
                return java.util.Optional.of(kind);
            }
        }
        return java.util.Optional.empty();
    }
}
