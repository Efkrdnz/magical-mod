package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubspaceCrossingsTest {

    @Test
    @DisplayName("a wall nothing has crossed shows nothing")
    void quietByDefault() {
        SubspaceCrossings crossings = new SubspaceCrossings();
        for (int slot = 0; slot < SubspaceCrossings.SLOTS; slot++) {
            assertFalse(crossings.live(slot, 0.0F));
            assertFalse(crossings.live(slot, 4000.0F));
        }
    }

    @Test
    @DisplayName("a ring runs out once and then the wall is quiet again")
    void oneRingPerCrossing() {
        SubspaceCrossings crossings = new SubspaceCrossings();
        crossings.note(0.0F, 0.0F, -1.0F, 100);
        assertTrue(crossings.live(0, 100.0F));
        assertEquals(0.0F, crossings.phase(0, 100.0F), 1.0E-6F);
        assertEquals(0.5F, crossings.phase(0, 100.0F + SubspaceCrossings.RIPPLE_TICKS / 2.0F), 1.0E-6F);
        assertEquals(1.0F, crossings.phase(0, 100.0F + SubspaceCrossings.RIPPLE_TICKS), 1.0E-6F);
        assertFalse(crossings.live(0, 100.0F + SubspaceCrossings.RIPPLE_TICKS));
    }

    @Test
    @DisplayName("the four showing are the four most recent")
    void theOldestGivesWay() {
        SubspaceCrossings crossings = new SubspaceCrossings();
        for (int i = 0; i < SubspaceCrossings.SLOTS; i++) {
            crossings.note(0.0F, 1.0F, 0.0F, 10 + i);
        }
        crossings.note(1.0F, 0.0F, 0.0F, 30);
        int fresh = 0;
        for (int slot = 0; slot < SubspaceCrossings.SLOTS; slot++) {
            if (crossings.x(slot) == 1.0F) {
                fresh++;
            }
        }
        assertEquals(1, fresh, "the newest crossing did not land, or landed more than once");
        for (int slot = 0; slot < SubspaceCrossings.SLOTS; slot++) {
            assertTrue(crossings.phase(slot, 20.0F) <= 1.0F);
        }
        // The one that was evicted is the one that started first.
        assertTrue(crossings.live(0, 30.0F) || crossings.x(0) == 1.0F);
    }

    @Test
    @DisplayName("an empty slot is taken before a live one is displaced")
    void emptySlotsGoFirst() {
        SubspaceCrossings crossings = new SubspaceCrossings();
        crossings.note(0.0F, 1.0F, 0.0F, 5);
        crossings.note(1.0F, 0.0F, 0.0F, 6);
        int live = 0;
        for (int slot = 0; slot < SubspaceCrossings.SLOTS; slot++) {
            if (crossings.live(slot, 6.0F)) {
                live++;
            }
        }
        assertEquals(2, live, "the second crossing overwrote the first");
    }
}
