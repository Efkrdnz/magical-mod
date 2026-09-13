package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The notation the rule flash draws: one formula per category with one changed symbol, one
 * change kind per operation. Pinned here so the table is edited on purpose, and so no formula
 * ever reaches for a glyph the default font's bitmap sheets do not carry.
 */
class SpaceRuleNotationTest {

    /**
     * Every glyph verified in the 1.21.4 default font's ascii, accented and nonlatin_european
     * sheets: printable ASCII plus the Greek and symbols the formulas need. Anything else falls
     * back to unifont, which draws with half the shadow and a wider advance.
     */
    private static final String BITMAP_GLYPHS = "\u0394\u03c1\u03c4\u03b3\u03bc\u00b7\u00bd\u00b2\u2264";

    private static boolean inBitmapFont(char c) {
        return (c >= ' ' && c <= '~') || BITMAP_GLYPHS.indexOf(c) >= 0;
    }

    @Test
    void everyCategoryHasAFormulaWithExactlyOneChangedSymbol() {
        for (SpaceRuleCategory category : SpaceRuleCategory.values()) {
            SpaceRuleNotation.Formula formula = SpaceRuleNotation.formula(category);
            assertNotNull(formula, category + " has no formula");
            assertEquals(1, formula.symbol().length(), category + ": the changed symbol is one glyph");
            String whole = formula.before() + formula.symbol() + formula.after();
            assertFalse(whole.contains("[") || whole.contains("]"), category + ": the brackets leaked into the text: " + whole);
            assertTrue(whole.length() >= 5, category + ": " + whole + " is not a formula");
        }
    }

    @Test
    void everyFormulaGlyphIsInTheBitmapFont() {
        for (SpaceRuleCategory category : SpaceRuleCategory.values()) {
            SpaceRuleNotation.Formula formula = SpaceRuleNotation.formula(category);
            String whole = formula.before() + formula.symbol() + formula.after();
            for (char c : whole.toCharArray()) {
                assertTrue(inBitmapFont(c), category + ": U+" + Integer.toHexString(c) + " is not a bitmap glyph");
            }
        }
    }

    @Test
    void theGravityFormulaReadsAsExpected() {
        SpaceRuleNotation.Formula gravity = SpaceRuleNotation.formula(SpaceRuleCategory.GRAVITY);
        assertEquals("F = m\u00b7", gravity.before());
        assertEquals("g", gravity.symbol());
        assertEquals("", gravity.after());
        SpaceRuleNotation.Formula velocity = SpaceRuleNotation.formula(SpaceRuleCategory.VELOCITY);
        assertEquals("", velocity.before());
        assertEquals("v", velocity.symbol());
        assertEquals(" = \u0394x/\u0394t", velocity.after());
    }

    @Test
    void everyOperationHasAChangeAndOnlyClearRestores() {
        for (SpaceRuleOperation operation : SpaceRuleOperation.values()) {
            SpaceRuleChange change = SpaceRuleNotation.change(operation);
            assertNotNull(change, operation + " has no change kind");
            assertEquals(operation.clear(), change == SpaceRuleChange.RESTORE, operation + " maps to " + change);
        }
        assertEquals(SpaceRuleChange.FLIP, SpaceRuleNotation.change(SpaceRuleOperation.REVERSE_GRAVITY));
        assertEquals(SpaceRuleChange.ZERO, SpaceRuleNotation.change(SpaceRuleOperation.VACUUM));
        assertEquals(SpaceRuleChange.LOCK, SpaceRuleNotation.change(SpaceRuleOperation.SEAL_BOUNDARY));
        assertEquals(SpaceRuleChange.SURGE, SpaceRuleNotation.change(SpaceRuleOperation.IMPLODE_PRESSURE));
    }

    @Test
    void theChangeKindsSplitTheOperationsAsDesigned() {
        Map<SpaceRuleChange, Integer> counts = new EnumMap<>(SpaceRuleChange.class);
        for (SpaceRuleOperation operation : SpaceRuleOperation.values()) {
            counts.merge(SpaceRuleNotation.change(operation), 1, Integer::sum);
        }
        assertEquals(10, counts.get(SpaceRuleChange.RAISE));
        assertEquals(9, counts.get(SpaceRuleChange.LOWER));
        assertEquals(6, counts.get(SpaceRuleChange.ZERO));
        assertEquals(5, counts.get(SpaceRuleChange.FLIP));
        assertEquals(8, counts.get(SpaceRuleChange.LOCK));
        assertEquals(3, counts.get(SpaceRuleChange.SURGE));
        assertEquals(4, counts.get(SpaceRuleChange.AIM));
        assertEquals(12, counts.get(SpaceRuleChange.RESTORE));
        assertEquals(SpaceRuleOperation.values().length, counts.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void aimVariantsAreTheFourDirectionsAndNothingElseHasOne() {
        Set<Integer> variants = new HashSet<>();
        for (SpaceRuleOperation operation : SpaceRuleOperation.values()) {
            int variant = SpaceRuleNotation.variant(operation);
            if (SpaceRuleNotation.change(operation) == SpaceRuleChange.AIM) {
                assertTrue(variants.add(variant), operation + " repeats variant " + variant);
            } else {
                assertEquals(0, variant, operation + " has a variant but is not an aim");
            }
        }
        assertEquals(Set.of(0, 1, 2, 3), variants);
        assertEquals(0, SpaceRuleNotation.variant(SpaceRuleOperation.PULL_NORTH));
        assertEquals(1, SpaceRuleNotation.variant(SpaceRuleOperation.PULL_SOUTH));
        assertEquals(2, SpaceRuleNotation.variant(SpaceRuleOperation.ORBIT));
        assertEquals(3, SpaceRuleNotation.variant(SpaceRuleOperation.CONVERGE));
    }

    @Test
    void theChangeIdsAreStableForTheShader() {
        SpaceRuleChange[] kinds = SpaceRuleChange.values();
        assertEquals(8, kinds.length, "the shader packs the kind into three bits");
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(i, kinds[i].id());
        }
    }
}
