package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A multicast draws k and nothing else; a formation also writes the pattern and takes spread off
 * the shot; a scatter adds spread; Epic draws whatever is left. Pattern and spread are read from the
 * shot's final state, which is why a formation may set them after the draw, as the Lua does.
 */
class MulticastTest {

    @Test
    void formationsSetThePatternOnTheFinalState() {
        RecitePlan plan = press(session("column", "needle", "needle", "needle"));
        assertEquals(3, plan.bodies().size());
        assertEquals(90.0D, plan.root().state().patternDegrees(), 1e-9);
        assertEquals(-11.0D, plan.root().state().spreadDegrees(), 1e-9);
        assertEquals(0.0D, plan.bodies().get(0).stamped().patternDegrees(), 1e-9, "a stamp is taken before the formation writes");
    }

    @Test
    void scattersAddSpread() {
        RecitePlan plan = press(session("loose_tercet", "needle", "needle", "needle"));
        assertEquals(3, plan.bodies().size());
        assertEquals(17.0D, plan.root().state().spreadDegrees(), 1e-9);
        assertEquals(0.0D, plan.root().state().patternDegrees(), 1e-9);
    }

    @Test
    void plainMulticastsOnlyDraw() {
        ReciteSession session = session("quatrain", "needle", "needle", "needle", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(4, plan.bodies().size());
        assertEquals(List.of("needle"), unread(session));
        assertEquals(0.0D, plan.root().state().patternDegrees(), 1e-9);
    }

    @Test
    void theRingOfFormationsIsComplete() {
        assertEquals(45.0D, patternOf("cleft", 2), 1e-9);
        assertEquals(20.0D, patternOf("trident", 3), 1e-9);
        assertEquals(180.0D, patternOf("mirror", 2), 1e-9);
        assertEquals(180.0D, patternOf("pentacle", 5), 1e-9);
        assertEquals(180.0D, patternOf("hexad", 6), 1e-9);
        assertEquals(8, press(session("octave", "needle", "needle", "needle", "needle", "needle", "needle", "needle", "needle")).bodies().size());
    }

    private static double patternOf(String formation, int draw) {
        String[] paths = new String[draw + 1];
        paths[0] = formation;
        for (int i = 1; i <= draw; i++) {
            paths[i] = "needle";
        }
        RecitePlan plan = press(session(paths));
        assertEquals(draw, plan.bodies().size(), formation);
        return plan.root().state().patternDegrees();
    }

    @Test
    void epicDrawsTheRest() {
        Incantation incantation = tape(1, "epic", "needle", "needle", "needle", "needle", "needle");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(5, plan.bodies().size());
        assertTrue(plan.rests());
        session.writeBack(incantation);
        assertEquals(9, incantation.entries().get(0).usesRemaining());
    }

    @Test
    void aMulticastRunningDryOverrunsOnce() {
        ReciteSession session = session("needle", "tercet");
        press(session);
        RecitePlan second = press(session);
        assertEquals(1, second.bodies().size(), "the one card that wrapped in");
        assertTrue(second.rests());
    }
}
