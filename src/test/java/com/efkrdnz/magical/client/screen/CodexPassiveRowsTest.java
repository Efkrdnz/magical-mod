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
 * in with the permanent ones would be unfindable exactly when it matters. The two halves go to
 * the two columns the tab already has: boons to the passives list, prices to the curses list,
 * each pinned above the permanent entries under its own heading. These pin that split, that every
 * pact row knows how long it has left, and that an entry with no clock is left where it was.
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
    void theBoonHalfIsListedAboveThePermanentPassives() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 1200);

        List<Row> rows = CodexPassiveRows.rows(state);

        assertEquals(List.of(CodexPassiveRows.GROUP_RITUAL_BOON, CodexPassiveRows.GROUP_GENERAL),
                groupsOf(rows), "the boons must sit above the permanent passives");
        assertTrue(rows.get(0).isHeader());
        assertEquals(MagicPassiveContent.CRIMSON_EDGE.id(), rows.get(1).definition().id());
    }

    @Test
    void aHeadingIsOnlyDrawnForAHalfThatHasSomethingInIt() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());

        assertEquals(List.of(CodexPassiveRows.GROUP_GENERAL),
                groupsOf(CodexPassiveRows.rows(state)), "an empty half still printed its heading");
    }

    @Test
    void aPriceIsNeverListedInThePassivesColumn() {
        // It lives in the curses column instead. Listed in both, it would carry two countdowns and
        // read as something the player owns rather than something they owe.
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);

        List<Row> rows = CodexPassiveRows.rows(state);

        assertEquals(List.of(CodexPassiveRows.GROUP_GENERAL), groupsOf(rows),
                "the price half still has a heading in the passives column");
        for (Row row : rows) {
            assertTrue(row.isHeader() || !MagicPassiveContent.GLASS_BONES.id().equals(row.definition().id()),
                    "a price is listed in the passives column as well as the curses column");
        }
    }

    @Test
    void aPactPriceIsListedInTheCurseColumnAboveTheLastingOnes() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);
        state.addCurse(MagicPassiveContent.MANA_LEAK_CURSE.id());

        List<Row> rows = CodexPassiveRows.curseRows(state);

        assertEquals(List.of(CodexPassiveRows.GROUP_RITUAL_PRICE, CodexPassiveRows.GROUP_LASTING_CURSE),
                groupsOf(rows), "the temporary curses must sit above the lasting ones");
        assertEquals(MagicPassiveContent.GLASS_BONES.id(), rows.get(1).definition().id());
        assertEquals(MagicPassiveContent.MANA_LEAK_CURSE.id(), rows.get(3).definition().id());
    }

    @Test
    void withNoPactTheCurseColumnIsTheFlatListItAlwaysWas() {
        // Every player who never touches blood magic sees exactly what they saw before: no
        // heading eating a row of a column that only fits a handful.
        PlayerMagicState state = new PlayerMagicState();
        state.addCurse(MagicPassiveContent.MANA_LEAK_CURSE.id());

        List<Row> rows = CodexPassiveRows.curseRows(state);

        assertEquals(List.of(), groupsOf(rows), "a lone group printed a heading it does not need");
        assertEquals(1, rows.size());
        assertEquals(MagicPassiveContent.MANA_LEAK_CURSE.id(), rows.get(0).definition().id());
    }

    @Test
    void aPriceOnItsOwnStillGetsItsHeadingSoItReadsAsTemporary() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);

        assertEquals(List.of(CodexPassiveRows.GROUP_RITUAL_PRICE),
                groupsOf(CodexPassiveRows.curseRows(state)),
                "without its heading a price reads as an ordinary curse");
    }

    @Test
    void aTemporaryCurseCarriesItsClockAndNoDispelIndex() {
        // The index is what the dispel button id is built from. A temporary curse has no dispel
        // button, and -1 falls outside the band, so a button wired off it by mistake does nothing
        // rather than dispelling whichever curse happens to be first.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);

        Row row = CodexPassiveRows.curseRows(state).get(1);

        assertTrue(row.ritual(), "a temporary curse lost its clock");
        assertEquals(state.ritualRemaining(MagicPassiveContent.GLASS_BONES.id()), row.ticks());
        assertEquals(-1, row.index(), "a temporary curse claims a dispel index");
    }

    @Test
    void aLastingCurseCarriesTheIndexItsDispelButtonIsBuiltFrom() {
        PlayerMagicState state = new PlayerMagicState();
        state.addCurse(MagicPassiveContent.SIN_WRATH_CURSE.id());

        Row row = CodexPassiveRows.curseRows(state).get(0);

        assertFalse(row.ritual(), "a lasting curse is showing a countdown");
        assertEquals(MagicPassiveContent.curses().get(row.index()).id(), row.definition().id(),
                "the row index does not name the curse");
    }

    @Test
    void anEmptyCurseColumnIsEmptyRatherThanAHeadingOverNothing() {
        assertEquals(List.of(), CodexPassiveRows.curseRows(new PlayerMagicState()));
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
    void everyPactRowInEitherColumnCarriesItsOwnRemainingTicks() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 1200);
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 1800);

        List<Row> both = new java.util.ArrayList<>(CodexPassiveRows.rows(state));
        both.addAll(CodexPassiveRows.curseRows(state));
        int counted = 0;
        for (Row row : both) {
            if (row.isHeader()) {
                continue;
            }
            assertTrue(row.ritual(), row.definition().id() + " lost its clock");
            assertEquals(state.ritualRemaining(row.definition().id()), row.ticks(),
                    row.definition().id() + " countdown disagrees with the state");
            counted++;
        }
        assertEquals(2, counted, "both halves of the pact must be listed, in one column or the other");
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
