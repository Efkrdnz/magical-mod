package com.efkrdnz.magical.forge.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import com.efkrdnz.magical.forge.glyph.RecognitionResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForgeChainBuilderTest {

    private ForgeChainBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ForgeChainBuilder(ForgeGlyphLibrary.recognizer());
    }

    /** Draws the horizontal "slash" stroke as a run of sampled points. */
    private RecognitionResult drawSlash() {
        builder.beginStroke(0.08f, 0.50f);
        for (int i = 1; i <= 20; i++) {
            builder.extendStroke(0.08f + 0.84f * (i / 20f), 0.50f);
        }
        return builder.endStroke();
    }

    private void commitSlash() {
        drawSlash();
        builder.commitNow();
    }

    @Test
    void aDrawnSlashIsRecognizedAsSlash() {
        RecognitionResult result = drawSlash();

        assertEquals(RecognitionResult.Status.ACCEPTED, result.status());
        assertEquals("slash", result.best().orElseThrow().id());
        assertEquals(result.status(), builder.current().status());
        assertEquals(1, builder.currentStrokes().size());
    }

    /**
     * {@link StrokeQuantizer} exists so client and server agree bit-for-bit on a stroke's
     * coordinates; the builder's own snapping used to be a second copy of that arithmetic, with its
     * own {@code CANVAS_UNITS - 1}, and nothing said the two agreed. Every coordinate the builder
     * stores has to be exactly what a round trip through the wire grid produces.
     */
    @Test
    void everyStoredCoordinateIsTheWireGridsOwnRoundTrip() {
        float[] probes = {
            -0.4f, -1e-6f, 0f, 1e-6f, 0.000489f, 0.0005f, 0.1f, 1f / 3f, 0.5f, 0.500489f,
            0.6180339f, 0.75f, 0.9995f, 1f - 1e-6f, 1f, 1.0001f, 1.6f};

        builder.beginStroke(probes[0], probes[probes.length - 1]);
        for (int i = 1; i < probes.length; i++) {
            builder.extendStroke(probes[i], probes[probes.length - 1 - i]);
        }
        builder.endStroke();

        List<GlyphPoint> stored = builder.currentStrokes().get(0);
        assertEquals(probes.length, stored.size());
        for (int i = 0; i < probes.length; i++) {
            float expectedX = StrokeQuantizer.fromUnits(StrokeQuantizer.toUnits(probes[i]));
            float expectedY = StrokeQuantizer.fromUnits(StrokeQuantizer.toUnits(probes[probes.length - 1 - i]));
            assertEquals(expectedX, stored.get(i).x(), 0f, "x drifted from the wire grid at probe " + i);
            assertEquals(expectedY, stored.get(i).y(), 0f, "y drifted from the wire grid at probe " + i);
        }
    }

    /** And the grid the builder snaps to is the whole grid, not a coarser one that happens to agree. */
    @Test
    void neighbouringGridStepsStayDistinctThroughTheBuilder() {
        int step = ForgeRules.CANVAS_UNITS / 2;

        builder.beginStroke(StrokeQuantizer.fromUnits(step), 0.5f);
        builder.extendStroke(StrokeQuantizer.fromUnits(step + 1), 0.5f);
        builder.endStroke();

        List<GlyphPoint> stored = builder.currentStrokes().get(0);
        assertEquals(StrokeQuantizer.fromUnits(step), stored.get(0).x(), 0f);
        assertEquals(StrokeQuantizer.fromUnits(step + 1), stored.get(1).x(), 0f);
    }

    @Test
    void anEmptyCanvasHasATooSmallCurrentResult() {
        assertEquals(RecognitionResult.Status.TOO_SMALL, builder.current().status());
        assertTrue(builder.current().best().isEmpty());
        assertEquals(0, builder.idleTicks());
    }

    @Test
    void restingOnAnAcceptedDrawingNeverCommitsItOnItsOwn() {
        drawSlash();

        for (int i = 0; i < 200; i++) {
            builder.tick();
        }

        assertEquals(RecognitionResult.Status.ACCEPTED, builder.current().status());
        assertTrue(builder.committed().isEmpty(), "a drawing committed itself without Apply");
        assertEquals(1, builder.currentStrokes().size(), "the drawing was taken off the canvas");
    }

    @Test
    void applyingAnAcceptedDrawingCommitsItAndClearsTheCanvas() {
        drawSlash();

        Optional<ForgeChainBuilder.CommittedGlyph> committed = builder.commitNow();

        assertTrue(committed.isPresent());
        assertEquals("slash", committed.orElseThrow().id());
        assertEquals(GlyphCategory.FORM, committed.orElseThrow().category());
        assertTrue(committed.orElseThrow().quality() > 0);
        assertEquals(1, builder.committed().size());
        assertEquals(0, builder.currentStrokes().size());
        assertEquals(0, builder.idleTicks());
    }

    @Test
    void undoRemovesTheLastCurrentStrokeFirst() {
        drawSlash();
        builder.beginStroke(0.50f, 0.10f);
        builder.extendStroke(0.50f, 0.90f);
        builder.endStroke();
        assertEquals(2, builder.currentStrokes().size());

        assertTrue(builder.undoStroke());

        assertEquals(1, builder.currentStrokes().size());
        assertEquals("slash", builder.current().best().orElseThrow().id());
    }

    @Test
    void undoWithoutACurrentStrokeRemovesTheLastCommittedGlyph() {
        commitSlash();
        assertEquals(1, builder.committed().size());

        assertTrue(builder.undoStroke());

        assertEquals(0, builder.committed().size());
        assertFalse(builder.undoStroke());
    }

    @Test
    void aSeventhStrokeIsRefused() {
        for (int i = 0; i < ForgeRules.MAX_STROKES_PER_GLYPH; i++) {
            assertTrue(builder.beginStroke(0.10f + i * 0.10f, 0.20f), "stroke " + i + " refused");
            builder.extendStroke(0.10f + i * 0.10f, 0.80f);
            builder.endStroke();
        }

        assertFalse(builder.beginStroke(0.90f, 0.20f));
        assertEquals(ForgeRules.MAX_STROKES_PER_GLYPH, builder.currentStrokes().size());
    }

    @Test
    void aLongStrokeIsCappedAtTheStrokePointBudget() {
        builder.beginStroke(0.05f, 0.50f);
        for (int i = 1; i < 200; i++) {
            builder.extendStroke(0.05f + 0.90f * (i / 199f), 0.50f);
        }

        builder.endStroke();

        assertEquals(ForgeRules.MAX_POINTS_PER_STROKE, builder.currentStrokes().get(0).size());
    }

    @Test
    void endStrokeQuantizesEveryCoordinateOntoTheCanvasGrid() {
        builder.beginStroke(0.1234567f, 0.7654321f);
        builder.extendStroke(0.5f, 0.3333333f);
        builder.extendStroke(0.9876543f, 0.2222222f);

        builder.endStroke();

        int units = ForgeRules.CANVAS_UNITS - 1;
        for (GlyphPoint point : builder.currentStrokes().get(0)) {
            assertEquals(Math.round(point.x() * units), point.x() * units, 1e-4f, "x of " + point);
            assertEquals(Math.round(point.y() * units), point.y() * units, 1e-4f, "y of " + point);
            assertTrue(point.x() >= 0f && point.x() <= 1f);
            assertTrue(point.y() >= 0f && point.y() <= 1f);
        }
    }

    @Test
    void commitNowIsRefusedOnceTheChainIsFull() {
        for (int i = 0; i < ForgeRules.MAX_GLYPHS; i++) {
            commitSlash();
        }
        assertEquals(ForgeRules.MAX_GLYPHS, builder.committed().size());

        drawSlash();

        assertEquals(Optional.empty(), builder.commitNow());
        assertEquals(ForgeRules.MAX_GLYPHS, builder.committed().size());
    }

    @Test
    void commitNowIsRefusedWithoutAnAcceptableGlyph() {
        assertEquals(Optional.empty(), builder.commitNow());

        builder.beginStroke(0.50f, 0.50f);
        builder.extendStroke(0.51f, 0.50f);
        builder.endStroke();

        assertEquals(RecognitionResult.Status.TOO_SMALL, builder.current().status());
        assertEquals(Optional.empty(), builder.commitNow());
    }

    @Test
    void payloadAndChainMirrorTheCommittedGlyphs() {
        commitSlash();
        commitSlash();

        List<List<List<GlyphPoint>>> payload = builder.toPayloadGlyphs();
        List<RecognizedGlyph> chain = builder.recognizedChain();

        assertEquals(2, payload.size());
        assertEquals(1, payload.get(0).size());
        assertEquals(2, chain.size());
        assertEquals("slash", chain.get(0).id());
        assertEquals(GlyphCategory.FORM, chain.get(0).category());
        assertEquals(builder.committed().get(0).quality(), chain.get(0).quality());
    }

    @Test
    void clearCurrentDropsTheDrawingButKeepsTheChain() {
        commitSlash();
        drawSlash();

        builder.clearCurrent();

        assertEquals(0, builder.currentStrokes().size());
        assertEquals(RecognitionResult.Status.TOO_SMALL, builder.current().status());
        assertEquals(1, builder.committed().size());
    }

    @Test
    void removeCommittedDropsOneGlyphByIndex() {
        commitSlash();
        builder.beginStroke(0.50f, 0.08f);
        builder.extendStroke(0.50f, 0.92f);
        builder.endStroke();
        builder.commitNow();
        assertEquals(List.of("slash", "cleave"),
                builder.committed().stream().map(ForgeChainBuilder.CommittedGlyph::id).toList());

        assertTrue(builder.removeCommitted(0));

        assertEquals(List.of("cleave"),
                builder.committed().stream().map(ForgeChainBuilder.CommittedGlyph::id).toList());
        assertFalse(builder.removeCommitted(5));
        assertFalse(builder.removeCommitted(-1));
    }

    @Test
    void snapshotsAreUnmodifiable() {
        commitSlash();
        drawSlash();

        List<List<GlyphPoint>> strokes = builder.currentStrokes();
        List<ForgeChainBuilder.CommittedGlyph> committed = builder.committed();

        assertTrue(assertThrowsUnsupported(() -> strokes.clear()));
        assertTrue(assertThrowsUnsupported(() -> committed.clear()));
    }

    private static boolean assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }
}
