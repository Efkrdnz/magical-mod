package com.efkrdnz.magical.client.screen.causality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.causality.Cause;
import com.efkrdnz.magical.magic.causality.Condition;
import com.efkrdnz.magical.magic.causality.Effect;
import com.efkrdnz.magical.magic.causality.Modifier;
import com.efkrdnz.magical.magic.causality.NodeKind;
import com.efkrdnz.magical.magic.causality.Scope;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every word on the board wears a mark of its own.
 *
 * <p>Fifty-four nine-by-nine silhouettes drawn by hand is exactly the kind of table that rots: a
 * word added to a vocabulary with no glyph draws blank, and two words that happen to share one make
 * a board unreadable at precisely the moment it has enough pins on it to be worth reading. Neither
 * fails a build on its own, so this does.
 */
class CausalGlyphsTest {

    @Test
    void everyWordOfEveryVocabularyHasAGlyph() {
        for (Cause cause : Cause.values()) {
            assertNotNull(CausalGlyphs.of(cause), "no glyph for cause " + cause);
        }
        for (Condition condition : Condition.values()) {
            assertNotNull(CausalGlyphs.of(condition), "no glyph for condition " + condition);
        }
        for (Effect effect : Effect.values()) {
            assertNotNull(CausalGlyphs.of(effect), "no glyph for effect " + effect);
        }
        for (Modifier modifier : Modifier.values()) {
            assertNotNull(CausalGlyphs.of(modifier), "no glyph for modifier " + modifier);
        }
        for (Scope scope : Scope.values()) {
            assertNotNull(CausalGlyphs.of(scope), "no glyph for scope " + scope);
        }
    }

    @Test
    void theTableHoldsExactlyTheWordsThereAre() {
        int words = Cause.values().length + Condition.values().length + Effect.values().length
                + Modifier.values().length + Scope.values().length;
        assertEquals(words, CausalGlyphs.all().size(),
                "a glyph with nothing to name is as much a bug as a word with no glyph");
    }

    @Test
    void noTwoWordsWearTheSameMark() {
        Map<String, String> seen = new HashMap<>();
        CausalGlyphs.all().forEach((name, glyph) -> {
            String other = seen.put(glyph.key(), name);
            assertNull(other, name + " is drawn exactly like " + other);
        });
    }

    @Test
    void everyGlyphHasEnoughInkToReadAsAShape() {
        // A silhouette of three or four pixels is a speck at nine by nine on a dimmed world. Twelve
        // is about the least that carries a shape at a glance, which is the whole job of the table.
        CausalGlyphs.all().forEach((name, glyph) -> assertTrue(glyph.litCount() >= 12,
                name + " has only " + glyph.litCount() + " lit pixels"));
    }

    @Test
    void everyGlyphFitsItsNineRows() {
        CausalGlyphs.all().forEach((name, glyph) -> {
            assertEquals(CausalGlyphs.SIZE, glyph.rows().length, name);
            for (int row : glyph.rows()) {
                assertTrue(row >= 0 && row < (1 << CausalGlyphs.SIZE), name + " overflows its row");
            }
        });
    }

    @Test
    void theThreeVocabulariesAreToldApartByColourBeforeTheyAreRead() {
        assertEquals(3, Set.of(CausalGlyphs.tint(NodeKind.CAUSE),
                CausalGlyphs.tint(NodeKind.CONDITION), CausalGlyphs.tint(NodeKind.EFFECT)).size());
    }
}
