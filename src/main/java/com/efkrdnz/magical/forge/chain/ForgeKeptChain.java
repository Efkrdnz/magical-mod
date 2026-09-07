package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The inscription already sitting on the weapon in the forge slot, as bare glyph paths plus the
 * quality it was forged at.
 *
 * <p>Deliberately Minecraft-free: {@code BlacksmithForgeService} builds one from the
 * {@code ForgedWeapon} component and the client builds one from its own copy of the same
 * component, so everything below this record - which glyphs a weapon can back, and what a kept
 * glyph is worth - stays pure and unit-testable alongside the rest of {@code forge/chain}.</p>
 *
 * @param grade     bare grade sigil path, e.g. {@code "high"}
 * @param elements  bare element core paths; two when the weapon element was fused
 * @param temper    optional bare temper path
 * @param forms     bare form paths, with repeats, in the order the weapon stores them
 * @param modifiers bare modifier paths, in the order the weapon stores them
 * @param program   forms and modifiers together in drawn order; empty for a pre-program weapon
 * @param quality   0..100 quality the weapon was forged at
 */
public record ForgeKeptChain(
        String grade,
        List<String> elements,
        Optional<String> temper,
        List<String> forms,
        List<String> modifiers,
        List<String> program,
        int quality) {

    public ForgeKeptChain {
        elements = List.copyOf(elements);
        forms = List.copyOf(forms);
        modifiers = List.copyOf(modifiers);
        program = List.copyOf(program);
    }

    /** Every id this inscription offers in {@code category}, with repeats, in stored order. */
    public List<String> idsOf(GlyphCategory category) {
        return switch (category) {
            case GRADE -> List.of(grade);
            case ELEMENT -> elements;
            case TEMPER -> temper.map(List::of).orElseGet(List::of);
            case FORM -> forms;
            case MODIFIER -> modifiers;
            case OPERATOR -> runOfCategory(GlyphCategory.OPERATOR);
        };
    }

    /**
     * The whole inscription as kept glyphs, in the order the chain strip lays them out: grade,
     * element, temper, then the run exactly as the weapon stores it.
     *
     * <p>The run goes back in its own order, not bucketed into forms-then-modifiers. Once a chain
     * is read as a program, that order is the weapon - preloading a reforge as "every form, then
     * every modifier" would hand the player back a different weapon than the one they put in the
     * slot, and they would only find out after inscribing it.
     */
    public List<ForgeKeptGlyphs.Kept> preloadOrder() {
        List<ForgeKeptGlyphs.Kept> out = new ArrayList<>();
        out.add(new ForgeKeptGlyphs.Kept(grade, GlyphCategory.GRADE));
        for (String element : elements) {
            out.add(new ForgeKeptGlyphs.Kept(element, GlyphCategory.ELEMENT));
        }
        temper.ifPresent(id -> out.add(new ForgeKeptGlyphs.Kept(id, GlyphCategory.TEMPER)));
        for (String id : runOrder()) {
            out.add(new ForgeKeptGlyphs.Kept(id, categoryOf(id)));
        }
        return List.copyOf(out);
    }

    /**
     * The run in drawn order, falling back to forms-then-modifiers for a weapon forged before
     * programs existed. That weapon has no order left to recover, and forms-then-modifiers is the
     * order it always behaved as.
     */
    private List<String> runOrder() {
        if (!program.isEmpty()) {
            return program;
        }
        List<String> out = new ArrayList<>(forms.size() + modifiers.size());
        out.addAll(forms);
        out.addAll(modifiers);
        return out;
    }

    /** The ids in the stored run that belong to {@code category}, in order. */
    private List<String> runOfCategory(GlyphCategory category) {
        List<String> out = new ArrayList<>();
        for (String id : program) {
            if (categoryOf(id) == category) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The category of a rune in the run, read from the glyph library rather than guessed from which
     * stored list happens to contain it. Operators appear only in the run, so membership of the
     * flat form and modifier lists cannot tell them apart.
     */
    private static GlyphCategory categoryOf(String id) {
        return ForgeGlyphLibrary.byId(id).map(GlyphTemplate::category).orElse(GlyphCategory.MODIFIER);
    }
}
