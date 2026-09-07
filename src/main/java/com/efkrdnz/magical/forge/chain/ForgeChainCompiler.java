package com.efkrdnz.magical.forge.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** Glyph id of the fork operator. */
    public static final String FORK = "fork";

    /** Most forms one press may fire at once. */
    public static final int MAX_FORK_WIDTH = 4;

    private ForgeChainCompiler() {
    }

    /**
     * Compiles a run left to right: each modifier attaches to the next form drawn after it, and is
     * consumed by that form alone.
     *
     * <p>So {@code pierce slash spin} gives PIERCE to the slash, and {@code slash pierce spin}
     * gives it to the spin. Several modifiers drawn before one form all land on that form. This is
     * what makes the order of a chain worth thinking about: two presses of the same weapon can now
     * behave differently, which a single whole-weapon modifier set could never express.
     *
     * <p>Modifiers still waiting when the run ends attach to the <em>first</em> step. The chain is
     * a loop - the combo wraps back to step one after the finisher - so the rune drawn last sits
     * immediately before the rune drawn first, and reading it as attaching to nothing would quietly
     * charge the player mana and stability for a rune that never fires.
     *
     * <p>Weapons forged before programs existed do not come through here at all; they kept no draw
     * order to read and go through {@link #fromLegacy} instead.
     */
    public static ForgeProgram compile(List<String> program) {
        List<ForgeStep> steps = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> group = new ArrayList<>();
        int forkWidth = 1;
        for (String id : program) {
            Optional<GlyphCategory> category = categoryOf(id);
            if (category.isEmpty()) {
                continue;
            }
            switch (category.get()) {
                case FORM -> {
                    group.add(id);
                    if (group.size() >= forkWidth) {
                        steps.add(new ForgeStep(List.copyOf(group), stackOf(pending), Optional.empty()));
                        group.clear();
                        pending.clear();
                        forkWidth = 1;
                    }
                }
                case MODIFIER -> pending.add(id);
                case OPERATOR -> forkWidth = widenFork(id, forkWidth);
                default -> {
                    // grade, element and temper are lifted out before the run reaches here
                }
            }
        }
        // A fork drawn with fewer forms after it than it asked for still fires what it got.
        if (!group.isEmpty()) {
            steps.add(new ForgeStep(List.copyOf(group), stackOf(pending), Optional.empty()));
            pending.clear();
        }
        return new ForgeProgram(wrapTrailing(steps, pending));
    }

    /**
     * A fork binds the next two forms into one press; stacking forks binds three, then four.
     *
     * <p>Four is the ceiling. Past it a single press spawns more strikes than the combo window can
     * account for, and each one is a separate entity with its own hit resolution.
     */
    private static int widenFork(String operatorId, int current) {
        if (!FORK.equals(operatorId)) {
            return current;
        }
        return Math.min(MAX_FORK_WIDTH, Math.max(2, current + 1));
    }

    /** Folds modifiers left over at the end of the run onto the step the chain wraps back to. */
    private static List<ForgeStep> wrapTrailing(List<ForgeStep> steps, List<String> pending) {
        if (pending.isEmpty() || steps.isEmpty()) {
            return steps;
        }
        ForgeStep first = steps.get(0);
        ModifierStack merged = first.mods();
        for (String id : pending) {
            Optional<ForgeModifierKind> kind = kindOf(id);
            if (kind.isPresent()) {
                merged = merged.plus(kind.get());
            }
        }
        steps.set(0, new ForgeStep(first.forms(), merged, first.payload()));
        return steps;
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

    private static Optional<GlyphCategory> categoryOf(String id) {
        return ForgeGlyphLibrary.byId(id).map(template -> template.category());
    }

    /**
     * Maps a modifier glyph id onto its kind by name. The glyph ids and the enum constants are the
     * same words in different cases, which is what lets this layer stay free of the registry that
     * holds the full {@code ModifierDefinition}.
     */
    private static Optional<ForgeModifierKind> kindOf(String id) {
        for (ForgeModifierKind kind : ForgeModifierKind.values()) {
            if (kind.name().equalsIgnoreCase(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
