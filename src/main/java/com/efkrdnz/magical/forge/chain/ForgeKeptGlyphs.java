package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a submitted chain in which some glyphs were drawn and some were kept off the weapon
 * already in the forge slot.
 *
 * <p>A kept glyph arrives as an id with no strokes, so the recognizer cannot vouch for it and the
 * id alone is worth nothing: this class is the security boundary. Every kept glyph must be backed
 * by the inscription actually on the weapon, in its own category, counting repeats - a client
 * claiming two kept {@code slash} forms on a weapon carrying one gets the second refused, exactly
 * like a client claiming a {@code divine} sigil on a weapon that never had one.</p>
 *
 * <p>What a backed kept glyph is worth is the other half of the boundary. It carries the weapon's
 * <em>recorded</em> quality, never a fresh perfect score. If kept glyphs scored 100 a player could
 * take a 30%-quality weapon, keep every rune, draw nothing at all, and reforge it to 100% for the
 * price of one submit - laundering quality out of thin air. Carrying the recorded quality instead
 * means keeping runes preserves exactly what you had, and the only way to raise quality is to
 * redraw the runes by hand.</p>
 */
public final class ForgeKeptGlyphs {

    /**
     * One glyph kept off the weapon: the id claimed, and the category that id belongs to. The
     * category is looked up from the glyph library by the caller, never taken from the client.
     */
    public record Kept(String id, GlyphCategory category) {
    }

    /** One submitted glyph: either already recognized from its strokes, or kept off the weapon. */
    public sealed interface Entry {

        /** A glyph the recognizer accepted from the strokes the client drew. */
        record Drawn(RecognizedGlyph glyph) implements Entry {
        }

        /** A glyph carried over from the weapon in the slot, still to be checked against it. */
        record FromWeapon(Kept kept) implements Entry {
        }
    }

    /**
     * The resolved chain, or the submit-order index of the first kept glyph the weapon cannot back.
     *
     * @param chain         every glyph in submit order; empty when a kept glyph was refused
     * @param unbackedIndex index of the refused kept glyph, or -1 when the whole chain resolved
     */
    public record Resolution(List<RecognizedGlyph> chain, int unbackedIndex) {

        public Resolution {
            chain = List.copyOf(chain);
        }

        public boolean ok() {
            return unbackedIndex < 0;
        }
    }

    private ForgeKeptGlyphs() {
    }

    /**
     * Turns a mixed submission into a plain chain of recognized glyphs. Drawn glyphs pass through
     * untouched; kept glyphs are checked against {@code inscribed} and take its quality. The result
     * is ordinary {@link RecognizedGlyph}s, so {@link ForgeChainGrammar} cannot tell the two apart
     * and every grammar rule and every payload cap applies to both identically.
     */
    public static Resolution resolve(List<Entry> entries, Optional<ForgeKeptChain> inscribed) {
        Map<GlyphCategory, List<String>> remaining = remainingIds(inscribed);
        int quality = inscribed.map(ForgeKeptChain::quality).orElse(0);
        List<RecognizedGlyph> chain = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index) instanceof Entry.Drawn drawn) {
                chain.add(drawn.glyph());
                continue;
            }
            Kept kept = ((Entry.FromWeapon) entries.get(index)).kept();
            if (!remaining.get(kept.category()).remove(kept.id())) {
                return new Resolution(List.of(), index);
            }
            chain.add(new RecognizedGlyph(kept.id(), kept.category(), quality));
        }
        return new Resolution(chain, -1);
    }

    /** A mutable per-category pool of the ids the weapon can still back, so repeats are counted. */
    private static Map<GlyphCategory, List<String>> remainingIds(Optional<ForgeKeptChain> inscribed) {
        Map<GlyphCategory, List<String>> remaining = new EnumMap<>(GlyphCategory.class);
        for (GlyphCategory category : GlyphCategory.values()) {
            remaining.put(category, inscribed.map(chain -> new ArrayList<>(chain.idsOf(category)))
                    .orElseGet(ArrayList::new));
        }
        return remaining;
    }
}
