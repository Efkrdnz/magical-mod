package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DreamscapeTest {
    @Test
    void aNewDreamscapeArrivesOnItsFloorAndHasNoFlaw() {
        Dreamscape scape = new Dreamscape(3);
        assertEquals(3, scape.plot());
        assertEquals(new Offset(0, 0, 0), scape.arrival());
        assertNull(scape.flaw());
    }

    @Test
    void thereIsOnlyEverOneFlaw() {
        Dreamscape scape = new Dreamscape(0);
        scape.markBlock(new Offset(1, 0, 0));
        UUID cow = UUID.randomUUID();
        scape.markFigment(cow);
        assertNull(scape.flaw().block());
        assertEquals(cow, scape.flaw().figment());
        scape.markBlock(new Offset(2, 1, 0));
        assertEquals(new Offset(2, 1, 0), scape.flaw().block());
        assertNull(scape.flaw().figment());
    }

    @Test
    void onlyTheFlawItselfClearsIt() {
        Dreamscape scape = new Dreamscape(0);
        scape.markBlock(new Offset(1, 0, 0));
        assertFalse(scape.clearIfFlaw(new Offset(0, 0, 0)));
        assertFalse(scape.clearIfFlaw(UUID.randomUUID()));
        assertNotNull(scape.flaw());
        assertTrue(scape.clearIfFlaw(new Offset(1, 0, 0)));
        assertNull(scape.flaw());
    }

    @Test
    void aFlawIsExactlyOneThing() {
        assertThrows(IllegalArgumentException.class, () -> new Dreamscape.Flaw(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Dreamscape.Flaw(new Offset(0, 0, 0), UUID.randomUUID()));
    }

    @Test
    void theArrivalIsWhereTheOwnerLastStood() {
        Dreamscape scape = new Dreamscape(0);
        scape.setArrival(new Offset(2, 0, -3), 90.0F);
        assertEquals(new Offset(2, 0, -3), scape.arrival());
        assertEquals(90.0F, scape.arrivalYaw());
    }
}
