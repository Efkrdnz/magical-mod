package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What survives the trip to the server, and what must not.
 *
 * <p>The payload is written by a client, so the interesting cases are the hostile ones. Negatives
 * come first: half the canvas lies behind or to the left of the caster, and a point packed into one
 * int comes apart on the unpacker's sign extension if that is ever got wrong - the symptom being a
 * shape that is correct in front of you and mirrored behind.
 */
class BloodShapeSubmitPayloadTest {

    private static int[] stroke(int... units) {
        int[] packed = new int[units.length / 2];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = BloodShapeRules.pack(units[i * 2], units[i * 2 + 1]);
        }
        return packed;
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> out = new ArrayList<>(values.length);
        for (int value : values) {
            out.add(value);
        }
        return out;
    }

    @Test
    void aShapeSurvivesTheRoundTripIntact() {
        BloodShape original = BloodShape.of(List.of(
                        stroke(0, 0, 16, 32, 48, 64),
                        stroke(-16, -32, -48, -64)),
                42, BloodShapeRules.FLAG_TRACK_YAW | BloodShapeRules.FLAG_KEEP_ROTATING);

        BloodShape back = BloodShapeSubmitPayload.of(3, original).toShape();

        assertEquals(original.pointCount(), back.pointCount());
        assertEquals(original.strokeCount(), back.strokeCount());
        assertEquals(original.heightPercent(), back.heightPercent());
        assertEquals(original.flags(), back.flags());
        for (int i = 0; i < original.pointCount(); i++) {
            assertEquals(original.packed(i), back.packed(i), "point " + i + " changed on the wire");
        }
    }

    @Test
    void negativeCoordinatesSurviveTheWire() {
        BloodShape original = BloodShape.of(List.of(stroke(-320, -320, -1, -1, 320, 320)), 50, 0);
        BloodShape back = BloodShapeSubmitPayload.of(0, original).toShape();

        assertEquals(-20.0D, back.u(0), 1.0E-9D);
        assertEquals(-20.0D, back.v(0), 1.0E-9D);
        assertEquals(-1.0D / BloodShapeRules.UNITS_PER_BLOCK, back.u(1), 1.0E-9D);
        assertEquals(20.0D, back.u(2), 1.0E-9D);
    }

    @Test
    void theSlotAndTheSettingsRideAlongUnchanged() {
        BloodShapeSubmitPayload payload = BloodShapeSubmitPayload.of(8,
                BloodShape.of(List.of(stroke(0, 0, 8, 8)), 0, BloodShapeRules.FLAG_TRACK_PITCH));
        assertEquals(8, payload.slot());
        assertEquals(0, payload.heightPercent());
        assertEquals(BloodShapeRules.FLAG_TRACK_PITCH, payload.flags());
    }

    @Test
    void anEmptyShapeCrossesAsAnEmptyShape() {
        BloodShapeSubmitPayload payload = BloodShapeSubmitPayload.of(1, BloodShape.EMPTY);
        assertTrue(payload.points().isEmpty());
        assertTrue(payload.ends().isEmpty());
        assertTrue(payload.toShape().isEmpty());
    }

    @Test
    void aFullShapeStaysWellInsideTheListCaps() {
        // Worst case is four strokes of thirty-two points, which is exactly what the codec's caps
        // are set to. Anything larger is refused while it is read, before it reaches this code.
        List<int[]> strokes = new ArrayList<>();
        for (int s = 0; s < BloodShapeRules.MAX_STROKES_PER_SHAPE; s++) {
            int[] points = new int[BloodShapeRules.MAX_POINTS_PER_STROKE];
            for (int i = 0; i < points.length; i++) {
                points[i] = BloodShapeRules.pack(i - 16, s * 8 - i);
            }
            strokes.add(points);
        }
        BloodShapeSubmitPayload payload =
                BloodShapeSubmitPayload.of(0, BloodShape.of(strokes, 100, 0));

        assertEquals(BloodShapeRules.MAX_STROKES_PER_SHAPE * BloodShapeRules.MAX_POINTS_PER_STROKE,
                payload.points().size());
        assertEquals(BloodShapeRules.MAX_STROKES_PER_SHAPE, payload.ends().size());
    }

    @Test
    void anOverlongStrokeIsTruncatedRatherThanAccepted() {
        int[] tooMany = new int[BloodShapeRules.MAX_POINTS_PER_STROKE * 4];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = BloodShapeRules.pack(i, i);
        }
        BloodShape back = new BloodShapeSubmitPayload(0, 50, 0,
                boxed(tooMany), List.of(tooMany.length)).toShape();
        assertTrue(back.pointCount() <= BloodShapeRules.MAX_POINTS_PER_STROKE,
                "a long stroke reached " + back.pointCount() + " points");
    }

    @Test
    void aCoordinateBeyondTheStorableRangeIsClampedNotTrusted() {
        // The format check, not the reach check: what lands here only has to be storable, and
        // whether the caster may reach it is decided at cast time against the reach they have then.
        int[] wild = {BloodShapeRules.pack(30000, -30000), BloodShapeRules.pack(0, 0)};
        BloodShape back = new BloodShapeSubmitPayload(0, 50, 0, boxed(wild), List.of(2)).toShape();

        assertEquals(BloodShapeRules.MAX_UNIT / (double) BloodShapeRules.UNITS_PER_BLOCK,
                back.u(0), 1.0E-9D);
        assertEquals(-BloodShapeRules.MAX_UNIT / (double) BloodShapeRules.UNITS_PER_BLOCK,
                back.v(0), 1.0E-9D);
    }

    @Test
    void aStrokeTableThatRunsPastItsPointsIsRefusedWithoutThrowing() {
        // A hand-edited packet, or one from an older build. The read stops where it stops rather
        // than taking the connection down with it.
        int[] points = stroke(0, 0, 16, 16);
        BloodShape back =
                new BloodShapeSubmitPayload(0, 50, 0, boxed(points), List.of(99)).toShape();
        assertTrue(back.isEmpty());
    }

    @Test
    void aStrokeTableThatRunsBackwardsIsRefusedWithoutThrowing() {
        int[] points = stroke(0, 0, 16, 16, 32, 32);
        BloodShape back =
                new BloodShapeSubmitPayload(0, 50, 0, boxed(points), List.of(3, 1)).toShape();
        assertEquals(1, back.strokeCount(), "the descending end should have stopped the read");
        assertEquals(3, back.pointCount());
    }

    @Test
    void anOutOfRangeHeightOrFlagBitIsClamped() {
        BloodShape back = new BloodShapeSubmitPayload(0, 900, 0xFF,
                boxed(stroke(0, 0, 8, 8)), List.of(2)).toShape();
        assertEquals(100, back.heightPercent());
        assertEquals(BloodShapeRules.clampFlags(0xFF), back.flags());
    }
}
