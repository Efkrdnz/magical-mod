package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleChange;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The band is the twenty-four synced ints made visible, so these pin the reduction rather than the
 * drawing: an empty domain must cost nothing, a legislated one must keep its count, and the slot a
 * category owns must never move.
 */
class SubspaceLedgerTest {

    private static int[] noLaws() {
        int[] ops = new int[SpaceRuleCategory.values().length];
        Arrays.fill(ops, SubspaceLedger.NO_RULE);
        return ops;
    }

    @Test
    @DisplayName("an empty domain draws no marks at all")
    void nothingWrittenMeansNothingDrawn() {
        SubspaceLedger ledger = SubspaceLedger.of(noLaws());
        assertEquals(0, ledger.lawCount());
        for (int slot = 0; slot < SpaceRuleCategory.values().length; slot++) {
            assertFalse(ledger.lit(slot), "slot " + slot + " lit with no law");
            assertEquals(-1, ledger.change(slot));
        }
        assertFalse(ledger.sealed());
        assertEquals(SubspaceLedger.GRAVITY_NORMAL, ledger.gravityState());
    }

    @Test
    @DisplayName("each category keeps its own slot, so the band never renumbers itself")
    void slotsAreTheCategoryOrdinal() {
        for (SpaceRuleCategory category : SpaceRuleCategory.values()) {
            int[] ops = noLaws();
            SpaceRuleOperation operation = firstNonClear(category);
            ops[category.ordinal()] = operation.ordinal();
            SubspaceLedger ledger = SubspaceLedger.of(ops);
            assertEquals(1, ledger.lawCount());
            assertTrue(ledger.lit(category.ordinal()), category + " did not light its own slot");
        }
    }

    /**
     * The count is the read that needs no vocabulary, which is the whole argument for twelve slots
     * rather than eight: laws that happen to share a change must still show as separate laws.
     */
    @Test
    @DisplayName("laws sharing one change still count separately")
    void theCountSurvivesCollision() {
        int[] ops = noLaws();
        int written = 0;
        for (SpaceRuleCategory category : SpaceRuleCategory.values()) {
            ops[category.ordinal()] = firstNonClear(category).ordinal();
            written++;
        }
        SubspaceLedger ledger = SubspaceLedger.of(ops);
        assertEquals(written, ledger.lawCount());
        assertEquals(SpaceRuleCategory.values().length, written);
    }

    @Test
    @DisplayName("the twelve slots sit on one arc centred on north, and none of them covers it")
    void theMeridianFallsInAGap() {
        for (int slot = 0; slot < SpaceRuleCategory.values().length; slot++) {
            double bearing = SubspaceLedger.slotBearingDegrees(slot);
            assertTrue(Math.abs(bearing) <= 50.0D, "slot " + slot + " left the band: " + bearing);
            assertTrue(Math.abs(bearing) > SubspaceLedger.SLOT_WIDTH_DEGREES * 0.5D,
                    "slot " + slot + " covers the meridian");
        }
        for (int slot = 1; slot < SpaceRuleCategory.values().length; slot++) {
            double gap = SubspaceLedger.slotBearingDegrees(slot) - SubspaceLedger.slotBearingDegrees(slot - 1);
            assertEquals(SubspaceLedger.SLOT_PITCH_DEGREES, gap, 1.0E-6D);
        }
    }

    @Test
    @DisplayName("gravity is read twice: once as its own slot and once as the horizon")
    void gravityAlsoSetsTheHorizon() {
        int[] flip = noLaws();
        flip[SpaceRuleCategory.GRAVITY.ordinal()] = SpaceRuleOperation.REVERSE_GRAVITY.ordinal();
        assertEquals(SubspaceLedger.GRAVITY_FLIP, SubspaceLedger.of(flip).gravityState());
        assertTrue(SubspaceLedger.of(flip).lit(SpaceRuleCategory.GRAVITY.ordinal()));

        int[] zero = noLaws();
        zero[SpaceRuleCategory.GRAVITY.ordinal()] = SpaceRuleOperation.REMOVE_GRAVITY.ordinal();
        assertEquals(SubspaceLedger.GRAVITY_ZERO, SubspaceLedger.of(zero).gravityState());
    }

    @Test
    @DisplayName("a boundary law is the one law that thickens the wall itself")
    void sealingIsReadOffTheBoundarySlot() {
        int[] ops = noLaws();
        ops[SpaceRuleCategory.BOUNDARY.ordinal()] = firstNonClear(SpaceRuleCategory.BOUNDARY).ordinal();
        assertTrue(SubspaceLedger.of(ops).sealed());
    }

    /**
     * {@code applyRule} writes NO_RULE for every clearing operation, so a cleared law is an absent
     * law on the wire and RESTORE can never arrive from synced state. The shader still carries a
     * RESTORE form because the client plays the settle backwards when a slot goes dark.
     */
    @Test
    @DisplayName("a cleared law arrives as no law, never as a restore mark")
    void restoreIsUnreachableFromTheWire() {
        for (SpaceRuleOperation operation : SpaceRuleOperation.values()) {
            if (!operation.clear()) {
                continue;
            }
            int[] ops = noLaws();
            ops[operation.category().ordinal()] = SubspaceLedger.NO_RULE;
            SubspaceLedger ledger = SubspaceLedger.of(ops);
            assertEquals(0, ledger.lawCount());
            assertFalse(ledger.lit(operation.category().ordinal()));
        }
        for (int slot = 0; slot < SpaceRuleCategory.values().length; slot++) {
            int[] ops = noLaws();
            ops[slot] = firstNonClear(SpaceRuleCategory.values()[slot]).ordinal();
            assertTrue(SubspaceLedger.of(ops).change(slot) != SpaceRuleChange.RESTORE.id(),
                    "RESTORE arrived from synced state on slot " + slot);
        }
    }

    /**
     * A subspace is centred on its caster's chest and raised where they stand, so its whole lower
     * hemisphere is underground. Anything written down there is written for nobody.
     */
    @Test
    @DisplayName("everything written on the wall is above the horizon, because the rest is buried")
    void theWritingIsAboveGround() {
        assertTrue(SubspaceLedger.MARK_LATITUDE_DEGREES - SubspaceLedger.MARK_HALF_HEIGHT_DEGREES > 0.0D,
                "the law band reaches below the horizon");
    }

    @Test
    @DisplayName("the graduations keep to the corridor the marks leave empty")
    void theScaleAndTheBandDoNotCollide() {
        double graduationReach = SubspaceLedger.TICK_OFFSET_DEGREES + SubspaceLedger.TICK_LONG_SWEEP_DEGREES * 0.5D;
        for (int slot = 0; slot < SpaceRuleCategory.values().length; slot++) {
            double nearEdge = Math.abs(SubspaceLedger.slotBearingDegrees(slot)) - SubspaceLedger.SLOT_WIDTH_DEGREES * 0.5D;
            assertTrue(nearEdge > graduationReach,
                    "slot " + slot + " reaches in to " + nearEdge + " where the scale reaches out to " + graduationReach);
        }
    }

    @Test
    @DisplayName("a nonsense ordinal on the wire is ignored rather than drawn")
    void aForgedOrdinalDrawsNothing() {
        int[] ops = noLaws();
        ops[0] = 9999;
        assertEquals(0, SubspaceLedger.of(ops).lawCount());
    }

    private static SpaceRuleOperation firstNonClear(SpaceRuleCategory category) {
        for (SpaceRuleOperation operation : SpaceRuleOperation.values()) {
            if (operation.category() == category && !operation.clear()) {
                return operation;
            }
        }
        throw new IllegalStateException("no writable operation for " + category);
    }
}
