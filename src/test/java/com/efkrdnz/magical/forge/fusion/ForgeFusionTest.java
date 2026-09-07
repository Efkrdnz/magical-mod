package com.efkrdnz.magical.forge.fusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;

class ForgeFusionTest {

    @Test
    void everyFusionIsMadeOfTwoRealElementGlyphs() {
        Set<String> elements = new HashSet<>();
        for (GlyphTemplate template : ForgeGlyphLibrary.all()) {
            if (template.category() == GlyphCategory.ELEMENT) {
                elements.add(template.id());
            }
        }
        for (ForgeFusion fusion : ForgeFusion.values()) {
            List<String> components = fusion.components();
            assertEquals(2, components.size(), fusion + " must fuse exactly two runes");
            assertNotEquals(components.get(0), components.get(1), fusion + " cannot fuse a rune with itself");
            for (String component : components) {
                assertTrue(elements.contains(component),
                        fusion + " names " + component + ", which is not an element glyph");
            }
        }
    }

    @Test
    void everyFusionProducesAnElementTheRuntimeCanCarry() {
        // A fusion whose result has no ForgeElementKind would resolve in the grammar and then fall
        // straight through to vanilla, silently, on every swing.
        for (ForgeFusion fusion : ForgeFusion.values()) {
            String constant = fusion.resultPath().toUpperCase(Locale.ROOT);
            boolean found = false;
            for (ForgeElementKind kind : ForgeElementKind.values()) {
                if (kind.name().equals(constant)) {
                    found = true;
                    assertTrue(kind.isCompound(), kind + " is a fusion result and must read as compound");
                }
            }
            assertTrue(found, fusion + " produces " + fusion.resultPath() + ", which is not an element kind");
        }
    }

    @Test
    void noFusionResultCollidesWithADrawableGlyph() {
        // A fusion result sharing a glyph id would make the strip ambiguous and leave the reforge
        // path unable to tell a drawn rune from a fused one.
        for (ForgeFusion fusion : ForgeFusion.values()) {
            assertTrue(ForgeGlyphLibrary.byId(fusion.resultPath()).isEmpty(),
                    fusion.resultPath() + " is both a fusion result and a drawable glyph");
        }
    }

    @Test
    void lookupIsOrderInsensitiveAndTotal() {
        for (ForgeFusion fusion : ForgeFusion.values()) {
            String a = fusion.components().get(0);
            String b = fusion.components().get(1);
            assertEquals(Optional.of(fusion), ForgeFusion.of(a, b));
            assertEquals(Optional.of(fusion), ForgeFusion.of(b, a));
            assertEquals(Optional.of(fusion), ForgeFusion.byResult(fusion.resultPath()));
        }
        assertTrue(ForgeFusion.of("terra", "radiant").isEmpty());
        assertTrue(ForgeFusion.byResult("nothing").isEmpty());
    }

    @Test
    void noPairFusesIntoTwoDifferentThings() {
        List<String> pairs = new ArrayList<>();
        for (ForgeFusion fusion : ForgeFusion.values()) {
            List<String> sorted = new ArrayList<>(fusion.components());
            Collections.sort(sorted);
            pairs.add(String.join("+", sorted));
        }
        assertEquals(pairs.size(), new HashSet<>(pairs).size(), "two fusions claim the same pair");
    }

    @Test
    void blackFlameIsTheGatedOneAndExplosionIsNot() {
        assertTrue(ForgeFusion.BLACK_FLAME.isGated());
        assertEquals(Optional.of("divinesmith"), ForgeFusion.BLACK_FLAME.requiredClass());
        assertEquals(List.of("black_flames"), ForgeFusion.BLACK_FLAME.requiredSkills());

        assertFalse(ForgeFusion.EXPLOSION.isGated(),
                "explosion is what teaches a player that fusion exists");
    }
}
