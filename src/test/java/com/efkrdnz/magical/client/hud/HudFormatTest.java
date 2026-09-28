package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The corner block's two small arithmetics: how the vault is written, and when the passives are cut short. */
class HudFormatTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Never more than four characters, and never rounded up: a vault that reads 1.2k holds at least 1200. */
    @Test
    void theVaultIsWrittenInFourCharactersAndNeverRoundedUp() {
        assertEquals("0", HudState.compact(0));
        assertEquals("999", HudState.compact(999));
        assertEquals("1k", HudState.compact(1000));
        assertEquals("1.2k", HudState.compact(1200));
        assertEquals("1.2k", HudState.compact(1299));
        assertEquals("9.9k", HudState.compact(9999));
        assertEquals("12k", HudState.compact(12_345));
        assertEquals("999k", HudState.compact(999_999));
        assertEquals("1m", HudState.compact(1_000_000));
        assertEquals("1.2m", HudState.compact(1_250_000));
        assertEquals("99m", HudState.compact(Integer.MAX_VALUE));
        for (int value = 0; value < 20_000_000; value += 997) {
            assertTrue(HudState.compact(value).length() <= 4, value + " is written as " + HudState.compact(value));
        }
    }

    /**
     * The passives are written out in full only while every one of them is placed; one left off and
     * the whole group is cut short, so a line never mixes "Pride 9%" with a bare "13%".
     */
    @Test
    void thePassivesAreCutShortTogetherWhenAnyOneIsLeftOff() {
        HudLayout layout = HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);
        int line = layout.readoutWidth();
        // Two resources on a line each, then two passives that fit the last line together.
        assertTrue(HudState.placesEvery(layout.flow(new int[] {line, line, 60, 60}, 2), 2, 4));
        // The same passives written too long to share it: the second is left off.
        assertFalse(HudState.placesEvery(layout.flow(new int[] {line, line, 90, 90}, 2), 2, 4));
        // No passives at all is every passive placed.
        assertTrue(HudState.placesEvery(layout.flow(new int[] {40, 40}, 2), 2, 2));
        // A resource left off is not a passive left off: the vault still counts as placed.
        assertTrue(HudState.placesEvery(layout.flow(new int[] {line, line, line, 60}, 3), 3, 4));
    }
}
