package com.efkrdnz.magical.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexPassiveRows.Row;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The order of the passives list, and the clock the pact rows carry.
 *
 * <p>A pact runs for minutes and the list is sixty-odd rows long, so a temporary boon that sorted
 * in with the permanent ones would be unfindable exactly when it matters. These pin that both
 * halves are lifted to the top under their own headings, that each row knows how long it has left,
 * and that a passive with no clock is left where it has always been.
 */
class CodexPassiveRowsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<String> groupsOf(List<Row> rows) {
        return rows.stream().filter(Row::isHeader).map(Row::groupKey).toList();
    }

    @Test
    void aPactIsListedAboveEverythingElseUnderItsOwnTwoHeadings() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 1200);
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);

        List<Row> rows = CodexPassiveRows.rows(state);

        assertEquals(List.of(CodexPassiveRows.GROUP_RITUAL_BOON, CodexPassiveRows.GROUP_RITUAL_PRICE,
                        CodexPassiveRows.GROUP_GENERAL), groupsOf(rows),
                "the pact must sit above the permanent passives, boons first");
        assertTrue(rows.get(0).isHeader());
        assertEquals(MagicPassiveContent.CRIMSON_EDGE.id(), rows.get(1).definition().id());
        assertTrue(rows.get(2).isHeader());
        assertEquals(MagicPassiveContent.GLASS_BONES.id(), rows.get(3).definition().id());
    }

    @Test
    void aHeadingIsOnlyDrawnForAHalfThatHasSomethingInIt() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 1200);

        assertEquals(List.of(CodexPassiveRows.GROUP_RITUAL_BOON, CodexPassiveRows.GROUP_GENERAL),
                groupsOf(CodexPassiveRows.rows(state)), "an empty half still printed its heading");
    }

    @Test
    void withNoPactTheListIsExactlyWhatItAlwaysWas() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());

        List<Row> rows = CodexPassiveRows.rows(state);

        assertEquals(List.of(CodexPassiveRows.GROUP_GENERAL), groupsOf(rows));
        assertFalse(rows.get(1).ritual(), "a permanent passive is showing a countdown");
        assertEquals(0, rows.get(1).ticks());
    }

    @Test
    void everyPactRowCarriesItsOwnRemainingTicks() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 1200);
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);

        for (Row row : CodexPassiveRows.rows(state)) {
            if (row.isHeader()) {
                continue;
            }
            assertTrue(row.ritual(), row.definition().id() + " lost its clock");
            assertEquals(state.ritualRemaining(row.definition().id()), row.ticks(),
                    row.definition().id() + " countdown disagrees with the state");
        }
    }

    @Test
    void aPactRowStillCarriesTheIndexTheToggleButtonIsBuiltFrom() {
        // The row model feeds one button-id band for the whole list. A ritual row that carried the
        // wrong index would not fail here in the codex; it would toggle some other passive.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 1200);

        for (Row row : CodexPassiveRows.rows(state)) {
            if (row.isHeader()) {
                assertEquals(-1, row.index(), "a header claims a toggle index");
                continue;
            }
            assertEquals(MagicPassiveContent.normalPassives().get(row.index()).id(), row.definition().id(),
                    "the row index does not name the row");
        }
    }

    @Test
    void theCountdownReadsAsMinutesAndSeconds() {
        assertEquals("1:00", CodexPassiveRows.countdown(1200));
        assertEquals("0:05", CodexPassiveRows.countdown(100));
        assertEquals("1:30", CodexPassiveRows.countdown(1800));
        assertEquals("0:00", CodexPassiveRows.countdown(0));
        assertEquals("0:00", CodexPassiveRows.countdown(-40), "a negative clock must not read as a huge one");
    }
}
