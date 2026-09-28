package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SigilTest {

    @Test
    void thereAreFortyThreeSymbolsInFiveFamilies() {
        assertEquals(43, Sigil.COUNT);
        assertEquals(Sigil.values().length, Sigil.COUNT);
        assertEquals(8, count(Sigil.Family.RUNE));
        assertEquals(9, count(Sigil.Family.ELEMENT));
        assertEquals(7, count(Sigil.Family.CREATURE));
        assertEquals(11, count(Sigil.Family.OBJECT));
        assertEquals(8, count(Sigil.Family.MARK));
    }

    @Test
    void everyNameIsLowerCaseUniqueAndReadsBack() {
        Set<String> names = new HashSet<>();
        for (Sigil sigil : Sigil.values()) {
            String name = sigil.serializedName();
            assertEquals(sigil.name().toLowerCase(Locale.ROOT), name);
            assertTrue(names.add(name), name);
            assertEquals(sigil, Sigil.byName(name).orElseThrow());
        }
        assertTrue(Sigil.byName("no_such_sigil").isEmpty());
    }

    @Test
    void theRunesAreTheFirstEightInOrder() {
        List<Sigil> runes = Sigil.runes();
        assertEquals(8, runes.size());
        for (int i = 0; i < runes.size(); i++) {
            assertEquals(i, runes.get(i).ordinal());
            assertEquals("rune_" + i, runes.get(i).serializedName());
        }
    }

    private static long count(Sigil.Family family) {
        return Arrays.stream(Sigil.values()).filter(sigil -> sigil.family() == family).count();
    }
}
