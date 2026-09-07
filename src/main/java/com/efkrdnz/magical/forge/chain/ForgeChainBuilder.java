package com.efkrdnz.magical.forge.chain;

import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphNormalizer;
import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import com.efkrdnz.magical.forge.glyph.GlyphQuality;
import com.efkrdnz.magical.forge.glyph.GlyphRecognizer;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;
import com.efkrdnz.magical.forge.glyph.RecognitionResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The forge canvas state machine: strokes are drawn into a current glyph, recognized, and committed
 * into a chain. It holds no Minecraft state, so the client screen drives it directly and the same
 * quantized strokes go over the wire to the server.
 */
public final class ForgeChainBuilder {

    /**
     * A glyph that has been accepted into the chain, either by being drawn and recognized or by
     * being carried over from the weapon already in the forge slot.
     *
     * @param id       recognized template id
     * @param category recognized category
     * @param quality  0..100 quality: the drawing score, or the weapon's recorded quality when kept
     * @param kept     true when this glyph came off the weapon rather than off the canvas
     * @param strokes  the quantized strokes it was drawn with; always empty for a kept glyph
     */
    public record CommittedGlyph(
            String id, GlyphCategory category, int quality, boolean kept, List<List<GlyphPoint>> strokes) {

        public CommittedGlyph {
            strokes = copyStrokes(strokes);
            if (kept && !strokes.isEmpty()) {
                throw new IllegalArgumentException("a kept glyph has no strokes to carry");
            }
        }

        public static CommittedGlyph ofDrawn(
                String id, GlyphCategory category, int quality, List<List<GlyphPoint>> strokes) {
            return new CommittedGlyph(id, category, quality, false, strokes);
        }

        /** A glyph lifted off the weapon in the slot, carrying that weapon's recorded quality. */
        public static CommittedGlyph ofKept(ForgeKeptGlyphs.Kept kept, int quality) {
            return new CommittedGlyph(kept.id(), kept.category(), quality, true, List.of());
        }
    }

    private final GlyphRecognizer recognizer;

    private final List<List<GlyphPoint>> strokes = new ArrayList<>();

    private final List<CommittedGlyph> committed = new ArrayList<>();

    private List<GlyphPoint> openStroke;

    private RecognitionResult current = RecognitionResult.tooSmall();

    private int idleTicks;

    public ForgeChainBuilder(GlyphRecognizer recognizer) {
        if (recognizer == null) {
            throw new IllegalArgumentException("a chain builder needs a recognizer");
        }
        this.recognizer = recognizer;
    }

    /** Starts a new stroke, unless the glyph already uses every stroke it is allowed. */
    public boolean beginStroke(float x, float y) {
        if (strokes.size() >= ForgeRules.MAX_STROKES_PER_GLYPH) {
            return false;
        }
        finishOpenStroke();
        openStroke = new ArrayList<>();
        openStroke.add(new GlyphPoint(x, y, strokes.size()));
        strokes.add(openStroke);
        return true;
    }

    /** Appends a point to the stroke being drawn; does nothing when no stroke is open. */
    public void extendStroke(float x, float y) {
        if (openStroke == null) {
            return;
        }
        openStroke.add(new GlyphPoint(x, y, strokes.size() - 1));
    }

    /** Closes the open stroke, caps and quantizes it, then re-recognizes the current glyph. */
    public RecognitionResult endStroke() {
        finishOpenStroke();
        idleTicks = 0;
        return recognizeCurrent();
    }

    /** The most recent recognition of the current glyph. */
    public RecognitionResult current() {
        return current;
    }

    /**
     * Advances the idle counter. Nothing commits on its own: several glyphs need more strokes than
     * a hand can lay down without pausing, and an idle clock cannot tell a finished drawing from a
     * half-finished one. The player says when a drawing becomes a sigil, with Apply or Enter.
     */
    public void tick() {
        idleTicks++;
    }

    /**
     * Turns the current drawing into a sigil. This is the only way a glyph is committed.
     *
     * <p>An AMBIGUOUS drawing is refused rather than committed as its best match. The server
     * re-recognizes every submitted glyph and rejects AMBIGUOUS outright
     * ({@code BlacksmithForgeService.recognizeGlyphs}), so committing one only ever built a chain
     * that looked complete, lit Inscribe up READY, and then failed every submit with
     * {@code forge.magical.error.ambiguous_glyph} and no way to tell which cell was at fault. The
     * canvas already says so while the glyph is still current - amber readout, both candidate
     * ghosts - which is the moment the player can still act on it.</p>
     */
    public Optional<CommittedGlyph> commitNow() {
        if (current.status() != RecognitionResult.Status.ACCEPTED) {
            return Optional.empty();
        }
        return commit();
    }

    /** Undoes the last stroke, or the last committed glyph when nothing is being drawn. */
    public boolean undoStroke() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            openStroke = null;
            recognizeCurrent();
            idleTicks = 0;
            return true;
        }
        if (committed.isEmpty()) {
            return false;
        }
        committed.remove(committed.size() - 1);
        idleTicks = 0;
        return true;
    }

    /** Throws the current drawing away, keeping the committed chain. */
    public void clearCurrent() {
        strokes.clear();
        openStroke = null;
        current = RecognitionResult.tooSmall();
        idleTicks = 0;
    }

    /** Removes one committed glyph; false when {@code index} is outside the chain. */
    public boolean removeCommitted(int index) {
        if (index < 0 || index >= committed.size()) {
            return false;
        }
        committed.remove(index);
        return true;
    }

    /** Unmodifiable snapshot of the strokes of the glyph being drawn. */
    public List<List<GlyphPoint>> currentStrokes() {
        return copyStrokes(strokes);
    }

    /** Unmodifiable snapshot of the committed chain. */
    public List<CommittedGlyph> committed() {
        return List.copyOf(committed);
    }

    /** The committed chain as grammar input. */
    public List<RecognizedGlyph> recognizedChain() {
        List<RecognizedGlyph> chain = new ArrayList<>(committed.size());
        for (CommittedGlyph glyph : committed) {
            chain.add(new RecognizedGlyph(glyph.id(), glyph.category(), glyph.quality()));
        }
        return List.copyOf(chain);
    }

    /**
     * Preloads glyphs carried over from the weapon in the forge slot, each at that weapon's
     * recorded quality, stopping at the chain's glyph budget. Kept and drawn glyphs sit in the same
     * chain from here on: the grammar, the preview and the submit path cannot tell them apart.
     *
     * @return how many were actually added
     */
    public int preloadKept(List<ForgeKeptGlyphs.Kept> kept, int quality) {
        int added = 0;
        for (ForgeKeptGlyphs.Kept glyph : kept) {
            if (committed.size() >= ForgeRules.MAX_GLYPHS) {
                break;
            }
            committed.add(CommittedGlyph.ofKept(glyph, quality));
            added++;
        }
        return added;
    }

    /** Drops every kept glyph, leaving what the player drew. Used when the weapon leaves the slot. */
    public boolean removeKept() {
        return committed.removeIf(CommittedGlyph::kept);
    }

    public int idleTicks() {
        return idleTicks;
    }

    private Optional<CommittedGlyph> commit() {
        Optional<GlyphTemplate> best = current.best();
        if (best.isEmpty() || committed.size() >= ForgeRules.MAX_GLYPHS) {
            return Optional.empty();
        }
        GlyphTemplate template = best.get();
        CommittedGlyph glyph = CommittedGlyph.ofDrawn(
                template.id(),
                template.category(),
                GlyphQuality.toQuality(current.bestScore()),
                strokes);
        committed.add(glyph);
        clearCurrent();
        return Optional.of(glyph);
    }

    private RecognitionResult recognizeCurrent() {
        current = strokes.isEmpty() ? RecognitionResult.tooSmall() : recognizer.recognize(strokes);
        return current;
    }

    /** Caps the open stroke at the point budget and snaps it onto the canvas grid. */
    private void finishOpenStroke() {
        if (openStroke == null) {
            return;
        }
        int index = strokes.size() - 1;
        List<GlyphPoint> capped = openStroke.size() > ForgeRules.MAX_POINTS_PER_STROKE
                ? GlyphNormalizer.resampleStroke(openStroke, ForgeRules.MAX_POINTS_PER_STROKE)
                : openStroke;
        List<GlyphPoint> quantized = new ArrayList<>(capped.size());
        for (GlyphPoint point : capped) {
            quantized.add(new GlyphPoint(quantize(point.x()), quantize(point.y()), index));
        }
        strokes.set(index, quantized);
        openStroke = null;
    }

    /**
     * Snaps one coordinate onto the wire grid.
     *
     * <p>Deferred to {@link StrokeQuantizer} rather than repeated here. That class exists so client
     * and server agree bit-for-bit on what a stroke's coordinates are, and a second copy of the
     * rounding - with its own {@code CANVAS_UNITS - 1} - is exactly the drift it was written to
     * prevent: the canvas would commit one number and {@code ForgeSubmitPayload} would carry
     * another.</p>
     */
    private static float quantize(float value) {
        return StrokeQuantizer.fromUnits(StrokeQuantizer.toUnits(value));
    }

    private static List<List<GlyphPoint>> copyStrokes(List<List<GlyphPoint>> source) {
        List<List<GlyphPoint>> out = new ArrayList<>(source.size());
        for (List<GlyphPoint> stroke : source) {
            out.add(List.copyOf(stroke));
        }
        return Collections.unmodifiableList(out);
    }
}
