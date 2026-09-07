package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.glyph.GlyphCategory;
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
 * @param element   bare element core path, e.g. {@code "fire"}
 * @param temper    optional bare temper path
 * @param forms     bare form paths, with repeats, in the order the weapon stores them
 * @param modifiers bare modifier paths, in the order the weapon stores them
 * @param quality   0..100 quality the weapon was forged at
 */
public record ForgeKeptChain(
        String grade,
        String element,
        Optional<String> temper,
        List<String> forms,
        List<String> modifiers,
        int quality) {

    public ForgeKeptChain {
        forms = List.copyOf(forms);
        modifiers = List.copyOf(modifiers);
    }

    /** Every id this inscription offers in {@code category}, with repeats, in stored order. */
    public List<String> idsOf(GlyphCategory category) {
        return switch (category) {
            case GRADE -> List.of(grade);
            case ELEMENT -> List.of(element);
            case TEMPER -> temper.map(List::of).orElseGet(List::of);
            case FORM -> forms;
            case MODIFIER -> modifiers;
        };
    }

    /**
     * The whole inscription as kept glyphs, in the order the chain strip lays them out: grade,
     * element, the forms as stored, temper, then modifiers. This is what the client preloads into
     * an empty strip when a forged weapon enters the slot.
     */
    public List<ForgeKeptGlyphs.Kept> preloadOrder() {
        List<ForgeKeptGlyphs.Kept> out = new ArrayList<>();
        out.add(new ForgeKeptGlyphs.Kept(grade, GlyphCategory.GRADE));
        out.add(new ForgeKeptGlyphs.Kept(element, GlyphCategory.ELEMENT));
        for (String form : forms) {
            out.add(new ForgeKeptGlyphs.Kept(form, GlyphCategory.FORM));
        }
        temper.ifPresent(id -> out.add(new ForgeKeptGlyphs.Kept(id, GlyphCategory.TEMPER)));
        for (String modifier : modifiers) {
            out.add(new ForgeKeptGlyphs.Kept(modifier, GlyphCategory.MODIFIER));
        }
        return List.copyOf(out);
    }
}
