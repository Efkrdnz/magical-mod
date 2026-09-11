package com.efkrdnz.magical.magic.blood.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The storage format of a drawn shape, and the caps that keep it from ever costing more than it is
 * allowed to.
 *
 * <p>These run without a Minecraft bootstrap on purpose: the whole shape package is written with no
 * engine imports so the server hit test and the client painter can share it, and a test that needed
 * the game to start would quietly stop proving that.
 */
class BloodShapeRulesTest {

    @Test
    void aPackedPointSurvivesTheRoundTripInEveryDirection() {
        // The sign is the whole risk here. Packing is a shift and a mask, and unpacking only gets a
        // negative coordinate back because of the short cast - drop it and the left half of every
        // shape lands 65536 blocks away.
        for (int x = -BloodShapeRules.MAX_UNIT; x <= BloodShapeRules.MAX_UNIT; x += 7) {
            for (int y = -BloodShapeRules.MAX_UNIT; y <= BloodShapeRules.MAX_UNIT; y += 11) {
                int packed = BloodShapeRules.pack(x, y);
                assertEquals(x, BloodShapeRules.unpackX(packed), "x at (" + x + ", " + y + ")");
                assertEquals(y, BloodShapeRules.unpackY(packed), "y at (" + x + ", " + y + ")");
            }
        }
    }

    @Test
    void aSixteenthOfABlockIsExactSoBothSidesReadTheSameNumber() {
        // Sixteen is a power of two, so units / 16.0 carries no rounding error at all. That is why
        // the quantization is sixteenths rather than the forge's 1023 steps: client and server agree
        // bit for bit without anyone having to arrange it.
        for (int units = -BloodShapeRules.MAX_UNIT; units <= BloodShapeRules.MAX_UNIT; units++) {
            assertEquals(units, BloodShapeRules.toUnits(BloodShapeRules.fromUnits(units)),
                    "sixteenths must survive the trip through blocks");
        }
    }

    @Test
    void reachIsClampedAtBothEndsAndNeverInverts() {
        assertEquals(BloodShapeRules.MIN_HALF_EXTENT_BLOCKS, BloodShapeRules.halfExtentBlocks(0.0D));
        assertEquals(BloodShapeRules.MIN_HALF_EXTENT_BLOCKS, BloodShapeRules.halfExtentBlocks(-50.0D));
        assertEquals(BloodShapeRules.MAX_HALF_EXTENT_BLOCKS, BloodShapeRules.halfExtentBlocks(999.0D));
        assertEquals(6.0D, BloodShapeRules.halfExtentBlocks(6.0D));

        double previous = -1.0D;
        for (double size = 0.0D; size <= 30.0D; size += 0.25D) {
            double extent = BloodShapeRules.halfExtentBlocks(size);
            assertTrue(extent >= previous, "more reach must never mean a smaller canvas");
            previous = extent;
        }
    }

    @Test
    void aDrawnShapeIsNeverStorableOutsideTheLargestCanvasThisBuildHas() {
        // The clamp is the format guard, not the balance one. A packet claiming a point a thousand
        // blocks out is bounded here; whether the caster can actually reach six blocks is decided at
        // cast time, against the reach they have then.
        assertTrue(BloodShapeRules.isStorableUnit(BloodShapeRules.MAX_UNIT));
        assertTrue(BloodShapeRules.isStorableUnit(-BloodShapeRules.MAX_UNIT));
        assertFalse(BloodShapeRules.isStorableUnit(BloodShapeRules.MAX_UNIT + 1));

        BloodShape shape = BloodShape.of(List.of(new int[] {
                BloodShapeRules.pack(30000, -30000),
                BloodShapeRules.pack(0, 0),
        }), 50, 0);
        assertEquals(BloodShapeRules.MAX_UNIT,
                BloodShapeRules.unpackX(shape.packed(0)), "a hostile x is clamped, not trusted");
        assertEquals(-BloodShapeRules.MAX_UNIT,
                BloodShapeRules.unpackY(shape.packed(0)), "and so is a hostile y");
    }

    @Test
    void costRisesWithTheDrawingAndThenStops() {
        assertEquals(0, BloodShapeRules.bloodCost(0.0D), "an empty shape is free");
        int previous = 0;
        for (double length = 0.5D; length <= 400.0D; length += 0.5D) {
            int cost = BloodShapeRules.bloodCost(length);
            assertTrue(cost >= previous, "drawing more must never cost less");
            assertTrue(cost <= BloodShapeRules.MAX_COST, "the cap is what stops a cast being unpayable");
            previous = cost;
        }
        assertEquals(BloodShapeRules.MAX_COST, BloodShapeRules.bloodCost(1000.0D));
    }

    @Test
    void theDefaultHeightLandsOnEyeLevel() {
        // 1.6 is where a player looks from, and an untouched slider should draw there rather than
        // around their ankles.
        double height = BloodShapeRules.heightOffsetBlocks(BloodShapeRules.DEFAULT_HEIGHT_PERCENT, 1.8D);
        assertTrue(Math.abs(height - 1.6D) < 0.01D, "default height was " + height + ", wanted about 1.6");
        assertEquals(0.0D, BloodShapeRules.heightOffsetBlocks(0, 1.8D), "zero percent is the feet");
        assertEquals(1.8D, BloodShapeRules.heightOffsetBlocks(100, 1.8D), "a hundred is the top of the head");
    }

    @Test
    void anUnknownFlagBitCannotSwitchOnBehaviour() {
        BloodShape shape = BloodShape.of(List.of(new int[] {0, 0}), 50, 0xFFFF);
        assertTrue(shape.trackYaw() && shape.trackPitch() && shape.keepRotating(),
                "the three real flags still come through");
        assertEquals(BloodShapeRules.FLAG_TRACK_YAW | BloodShapeRules.FLAG_TRACK_PITCH
                | BloodShapeRules.FLAG_KEEP_ROTATING, shape.flags(), "and nothing else does");
    }

    @Test
    void everyCapIsEnforcedOnTheWayInRatherThanTrusted() {
        int[] longStroke = new int[BloodShapeRules.MAX_POINTS_PER_STROKE + 40];
        for (int i = 0; i < longStroke.length; i++) {
            longStroke[i] = BloodShapeRules.pack(i, i);
        }
        BloodShape shape = BloodShape.of(List.of(longStroke, longStroke, longStroke, longStroke,
                longStroke, longStroke), 50, 0);

        assertEquals(BloodShapeRules.MAX_STROKES_PER_SHAPE, shape.strokeCount(),
                "extra strokes are dropped");
        assertEquals(BloodShapeRules.MAX_STROKES_PER_SHAPE * BloodShapeRules.MAX_POINTS_PER_STROKE,
                shape.pointCount(), "and every stroke is truncated to its own cap");
    }

    @Test
    void aStrokeWithNothingToDrawIsNotAStroke() {
        assertTrue(BloodShape.of(List.of(new int[] {BloodShapeRules.pack(1, 1)}), 50, 0).isEmpty(),
                "one point is a dot, not a path");
        assertTrue(BloodShape.of(List.of(), 50, 0).isEmpty());
        assertTrue(BloodShape.of(null, 50, 0).isEmpty());
    }

    @Test
    void aCorruptStrokeTableStopsTheReadRatherThanTakingTheStateDown() {
        // This is the load path for a hand-edited or older tag. Throwing here would fail the whole
        // player state, which is a far worse outcome than one slot coming back short.
        int[] points = {BloodShapeRules.pack(0, 0), BloodShapeRules.pack(16, 0),
                BloodShapeRules.pack(32, 0), BloodShapeRules.pack(48, 0)};

        assertEquals(2, BloodShape.ofFlat(points, new int[] {2, 4}, 50, 0).strokeCount());
        assertEquals(1, BloodShape.ofFlat(points, new int[] {2, 900}, 50, 0).strokeCount(),
                "an end past the points keeps what parsed");
        assertEquals(1, BloodShape.ofFlat(points, new int[] {2, 2}, 50, 0).strokeCount(),
                "a table that does not ascend stops there");
        assertTrue(BloodShape.ofFlat(points, new int[0], 50, 0).isEmpty());
        assertTrue(BloodShape.ofFlat(null, new int[] {2}, 50, 0).isEmpty());
    }

    @Test
    void theStoredPointsCannotBeReachedThroughAndChanged() {
        BloodShape shape = BloodShape.of(List.of(new int[] {
                BloodShapeRules.pack(0, 0), BloodShapeRules.pack(16, 16)}), 50, 0);
        int[] leaked = shape.pointsCopy();
        leaked[0] = 12345;
        assertEquals(BloodShapeRules.pack(0, 0), shape.packed(0),
                "a shape handed to the wire writer must not be editable through the array it handed back");
    }
}
