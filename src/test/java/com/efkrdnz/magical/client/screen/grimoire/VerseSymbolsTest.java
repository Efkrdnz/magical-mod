package com.efkrdnz.magical.client.screen.grimoire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.Verse;
import com.efkrdnz.magical.magic.incantation.VerseContent;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VerseType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every verse in the catalogue wears a symbol nobody else wears, from a row of its own rather than
 * a fallback, so a renamed or added verse fails here before it is drawn as a blank or as its
 * neighbour. The marks count along the top row and the glyphs that carry them keep that row clear;
 * the rail has a glyph per type; the badges are all different from one another.
 */
class VerseSymbolsTest {

    @Test
    void everyVerseWearsASymbolNoOtherVerseWears() {
        Map<String, String> owners = new HashMap<>();
        int count = 0;
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            String key = VerseSymbols.of(verse).key();
            String other = owners.put(key, verse.path());
            assertNull(other, verse.path() + " wears the same symbol as " + other + ": " + key);
            count++;
        }
        assertTrue(count >= 100, "the catalogue has shrunk to " + count + " verses");
    }

    @Test
    void everyVerseIsInTheTableRatherThanFallingBack() {
        Set<String> paths = new HashSet<>();
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            paths.add(verse.path());
            assertTrue(VerseSymbols.table().containsKey(verse.path()), verse.path() + " has no symbol of its own");
        }
        for (String path : VerseSymbols.table().keySet()) {
            assertTrue(paths.contains(path), "the table names " + path + ", which is not a verse");
        }
    }

    @Test
    void aVerseWithoutAnEntryFallsBackToItsLookThenItsType() {
        VerseSymbols.Symbol byLook = VerseSymbols.symbolFor("no_such_verse", VersePrototype.Look.ORB, VerseType.PROJECTILE);
        assertSame(VerseSymbols.lookGlyph(VersePrototype.Look.ORB), byLook.glyph());
        assertFalse(byLook.hasBadge());
        assertFalse(byLook.hasMarks());
        VerseSymbols.Symbol byType = VerseSymbols.symbolFor("no_such_verse", null, VerseType.CONTROL);
        assertSame(VerseSymbols.forType(VerseType.CONTROL), byType.glyph());
        for (VersePrototype.Look look : VersePrototype.Look.values()) {
            assertNotNull(VerseSymbols.lookGlyph(look), look + " has no glyph");
        }
    }

    @Test
    void theRailHasADistinctGlyphPerType() {
        Set<String> names = new HashSet<>();
        for (VerseType type : VerseType.values()) {
            VerseSymbols.Glyph glyph = VerseSymbols.forType(type);
            assertNotNull(glyph, type + " has no rail glyph");
            assertTrue(glyph.litCount() > 0, type + " has a blank rail glyph");
            assertTrue(names.add(glyph.name()), type + " shares its rail glyph " + glyph.name());
        }
    }

    @Test
    void theMarksCountAlongTheTopAndTenIsABar() {
        assertEquals(0, VerseSymbols.marksRow(0));
        for (int n = 1; n < VerseSymbols.MARKS_BAR; n++) {
            assertEquals(n, Integer.bitCount(VerseSymbols.marksRow(n)), n + " marks");
        }
        assertEquals(0b111111111, VerseSymbols.marksRow(VerseSymbols.MARKS_BAR));
        assertEquals(0b111111111, VerseSymbols.marksRow(VerseSymbols.MARKS_BAR + 5), "more than a bar is still a bar");
    }

    @Test
    void aGlyphThatCarriesMarksKeepsItsTopRowClear() {
        int carrying = 0;
        for (Map.Entry<String, VerseSymbols.Symbol> entry : VerseSymbols.table().entrySet()) {
            if (!entry.getValue().hasMarks()) {
                continue;
            }
            carrying++;
            assertEquals(0, entry.getValue().glyph().rows()[0], entry.getKey() + " draws its marks over its glyph");
        }
        assertTrue(carrying > 0, "no verse carries marks");
    }

    @Test
    void badgesAreThreeByThreeAndDistinct() {
        Map<List<Integer>, String> byPattern = new HashMap<>();
        for (VerseSymbols.Symbol symbol : VerseSymbols.table().values()) {
            if (!symbol.hasBadge()) {
                continue;
            }
            VerseSymbols.Badge badge = symbol.badge();
            assertEquals(VerseSymbols.BADGE_SIZE, badge.rows().length, badge.name());
            List<Integer> rows = Arrays.stream(badge.rows()).boxed().toList();
            assertTrue(rows.stream().anyMatch(row -> row != 0), badge.name() + " is blank");
            String other = byPattern.put(rows, badge.name());
            assertTrue(other == null || other.equals(badge.name()), badge.name() + " looks exactly like " + other);
        }
        assertTrue(byPattern.size() >= 20, "only " + byPattern.size() + " badges");
    }

    @Test
    void everyGlyphInTheTableIsDrawn() {
        for (Map.Entry<String, VerseSymbols.Symbol> entry : VerseSymbols.table().entrySet()) {
            VerseSymbols.Glyph glyph = entry.getValue().glyph();
            assertEquals(VerseSymbols.SIZE, glyph.rows().length, entry.getKey());
            assertTrue(glyph.litCount() >= 6, entry.getKey() + " wears a glyph of " + glyph.litCount() + " pixels");
        }
    }
}
