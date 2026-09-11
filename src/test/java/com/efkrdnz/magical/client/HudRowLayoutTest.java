package com.efkrdnz.magical.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The HUD panel grows by one row per forbidden currency the player actually carries, and everything
 * under the bars is positioned off that sum.
 *
 * <p>Written because this is the same shape of bug as the fourth cast card: a row count driving
 * coordinate arithmetic inline, with nothing to catch it when the count changes. Two optional rows
 * is four combinations, and all four are checked here rather than reasoned about.
 */
class HudRowLayoutTest {

    @Test
    void aPlayerWithNeitherSchoolSeesExactlyTheHudTheyAlwaysSaw() {
        assertEquals(0, MagicalHudOverlay.extraRows(false, false),
                "the panel must not grow for the overwhelming majority of players");
    }

    @Test
    void eachCurrencyAddsItsOwnRowAndBothAddBoth() {
        int one = MagicalHudOverlay.EXTRA_ROW_H;
        assertEquals(one, MagicalHudOverlay.extraRows(true, false), "the Vessel is one row");
        assertEquals(one, MagicalHudOverlay.extraRows(false, true), "Corruption is one row");
        assertEquals(2 * one, MagicalHudOverlay.extraRows(true, true), "and carrying both is two");
    }

    @Test
    void theCorruptionBarNeverLandsOnTopOfTheVesselBar() {
        // Both bars are drawn relative to the panel top: the Vessel at +27, Corruption at +27 plus
        // whatever the Vessel row took. A row height smaller than a bar is what would overlap them.
        int barHeight = 9;
        assertTrue(MagicalHudOverlay.EXTRA_ROW_H > barHeight,
                "a row has to be taller than the bar drawn in it, or the two collide");
    }

    @Test
    void thePanelAlwaysEndsBelowEveryBarItContains() {
        // The loadout slots start at panelHeight + 4, so a panel that did not account for a drawn
        // row would put the slots through the last bar.
        int lastBarBottom = 27 + MagicalHudOverlay.extraRows(true, true);
        assertTrue(MagicalHudOverlay.panelHeight(true, true) >= lastBarBottom,
                "the panel closed above its own last bar");
        assertTrue(MagicalHudOverlay.panelHeight(false, false) < MagicalHudOverlay.panelHeight(true, true),
                "and it has to be taller when it is carrying more");
    }
}
