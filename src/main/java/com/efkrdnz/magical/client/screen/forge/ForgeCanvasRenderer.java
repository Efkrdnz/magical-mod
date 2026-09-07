package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;
import com.efkrdnz.magical.forge.glyph.RecognitionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The drawing plate: the ink the player is laying down, the ghost of whatever the recognizer thinks
 * it is, the score readout, and the short animation that carries a committed glyph into the chain
 * strip. Reads the canvas state, never mutates it.
 */
public final class ForgeCanvasRenderer {

    private static final int GLOW = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0x3A);
    private static final int CORE = MagicalGuiStyle.ACCENT_GOLD;
    private static final int HIGHLIGHT = 0xFFFFF4D6;
    private static final int AMBER = 0xFFFFB347;
    private static final int GOOD_TEXT = 0xA6E3A1;
    private static final int AMBER_TEXT = 0xFFB347;
    private static final int BAD_TEXT = 0xF38BA8;
    private static final int GRID_LINES = 4;
    private static final int GHOST_ALPHA = 0x59;
    private static final int SECOND_GHOST_ALPHA = 0x33;

    /**
     * A glyph on its way from the canvas into the strip: {@code SNAP_TICKS} of the ink pulling onto
     * the recognized template, then {@code FLY_TICKS} of it shrinking into the target cell.
     *
     * @param startTick screen tick the commit happened on
     * @param strokes   the ink as drawn, in unit canvas coordinates
     * @param snapped   each point's nearest template point, precomputed so frames stay cheap
     * @param targetX   screen x of the strip cell the glyph lands in
     * @param targetY   screen y of that cell
     */
    public record CommitAnimation(
            int startTick, List<List<GlyphPoint>> strokes, List<List<GlyphPoint>> snapped,
            int targetX, int targetY) {

        public static final int SNAP_TICKS = 8;
        public static final int FLY_TICKS = 6;
        public static final int TOTAL_TICKS = SNAP_TICKS + FLY_TICKS;

        /** Most points an animation may carry; a denser glyph simply skips its animation. */
        private static final int POINT_BUDGET = 320;

        /** Builds an animation, or empty when the drawing is too dense to animate cheaply. */
        public static Optional<CommitAnimation> of(
                int startTick, List<List<GlyphPoint>> strokes, GlyphTemplate template, int targetX, int targetY) {
            int points = 0;
            for (List<GlyphPoint> stroke : strokes) {
                points += stroke.size();
            }
            if (points == 0 || points > POINT_BUDGET) {
                return Optional.empty();
            }
            List<List<GlyphPoint>> ghost = ghostStrokes(strokes, template);
            if (ghost.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CommitAnimation(startTick, strokes, nearestPoints(strokes, ghost), targetX, targetY));
        }

        private static List<List<GlyphPoint>> nearestPoints(
                List<List<GlyphPoint>> strokes, List<List<GlyphPoint>> ghost) {
            List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
            for (List<GlyphPoint> stroke : strokes) {
                List<GlyphPoint> mapped = new ArrayList<>(stroke.size());
                for (GlyphPoint point : stroke) {
                    mapped.add(nearest(point, ghost));
                }
                out.add(List.copyOf(mapped));
            }
            return List.copyOf(out);
        }

        private static GlyphPoint nearest(GlyphPoint point, List<List<GlyphPoint>> ghost) {
            GlyphPoint best = point;
            float bestDistance = Float.MAX_VALUE;
            for (List<GlyphPoint> stroke : ghost) {
                for (GlyphPoint candidate : stroke) {
                    float dx = candidate.x() - point.x();
                    float dy = candidate.y() - point.y();
                    float distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
            return best;
        }
    }

    /** Draws the plate and everything on it. {@code animation} may be null. */
    public void render(GuiGraphics graphics, Font font, int x, int y, int size,
            ForgeChainBuilder builder, CommitAnimation animation, int ticks) {
        drawPlate(graphics, x, y, size);
        List<List<GlyphPoint>> strokes = builder.currentStrokes();
        RecognitionResult result = builder.current();
        drawGhosts(graphics, x, y, size, strokes, result);
        drawInk(graphics, x, y, size, strokes);
        if (animation != null) {
            drawAnimation(graphics, x, y, size, animation, ticks);
        }
        drawReadout(graphics, font, x, y, size, strokes, result);
    }

    private void drawPlate(GuiGraphics graphics, int x, int y, int size) {
        MagicalGuiStyle.inset(graphics, x, y, x + size, y + size);
        int grid = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0x14);
        for (int step = 1; step < GRID_LINES; step++) {
            int offset = size * step / GRID_LINES;
            graphics.fill(x + offset, y, x + offset + 1, y + size, grid);
            graphics.fill(x, y + offset, x + size, y + offset + 1, grid);
        }
        int border = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0x4D);
        graphics.fill(x, y, x + size, y + 1, border);
        graphics.fill(x, y + size - 1, x + size, y + size, border);
        graphics.fill(x, y, x + 1, y + size, border);
        graphics.fill(x + size - 1, y, x + size, y + size, border);
    }

    private void drawInk(GuiGraphics graphics, int x, int y, int size, List<List<GlyphPoint>> strokes) {
        drawPass(graphics, x, y, size, strokes, 5, GLOW);
        drawPass(graphics, x, y, size, strokes, 3, CORE);
        drawPass(graphics, x, y, size, strokes, 1, HIGHLIGHT);
    }

    private void drawPass(GuiGraphics graphics, int x, int y, int size,
            List<List<GlyphPoint>> strokes, int thickness, int color) {
        for (List<GlyphPoint> stroke : strokes) {
            ForgeInk.polyline(graphics, project(stroke, x, y, size), thickness, color);
        }
    }

    // --- ghost overlay ---------------------------------------------------------------------

    private void drawGhosts(GuiGraphics graphics, int x, int y, int size,
            List<List<GlyphPoint>> strokes, RecognitionResult result) {
        if (strokes.isEmpty() || result.best().isEmpty()) {
            return;
        }
        boolean ambiguous = result.status() == RecognitionResult.Status.AMBIGUOUS;
        if (!ambiguous && result.status() != RecognitionResult.Status.ACCEPTED) {
            return;
        }
        GlyphTemplate best = result.best().get();
        int color = ambiguous ? AMBER : templateColor(best);
        drawGhost(graphics, x, y, size, strokes, best, MagicalGuiStyle.withAlpha(color, GHOST_ALPHA));
        if (ambiguous) {
            result.second().ifPresent(second -> drawGhost(graphics, x, y, size, strokes, second,
                    MagicalGuiStyle.withAlpha(AMBER, SECOND_GHOST_ALPHA)));
        }
    }

    private void drawGhost(GuiGraphics graphics, int x, int y, int size,
            List<List<GlyphPoint>> strokes, GlyphTemplate template, int color) {
        for (List<GlyphPoint> stroke : ghostStrokes(strokes, template)) {
            ForgeInk.polyline(graphics, project(stroke, x, y, size), 2, color);
        }
    }

    /** The element's primary colour for element glyphs, the forge's gold for everything else. */
    private static int templateColor(GlyphTemplate template) {
        if (template.category() != GlyphCategory.ELEMENT) {
            return MagicalGuiStyle.ACCENT_GOLD;
        }
        return ForgeElements.get(ForgeIds.id(template.id()))
                .map(element -> 0xFF000000 | element.primaryColor())
                .orElse(MagicalGuiStyle.ACCENT_GOLD);
    }

    /** The template's strokes scaled uniformly into the drawing's bounding box, centred on it. */
    static List<List<GlyphPoint>> ghostStrokes(List<List<GlyphPoint>> strokes, GlyphTemplate template) {
        float[] drawn = bounds(strokes);
        float[] reference = bounds(template.strokes());
        if (drawn == null || reference == null) {
            return List.of();
        }
        float referenceSpan = Math.max(reference[2] - reference[0], reference[3] - reference[1]);
        if (referenceSpan <= 0f) {
            return List.of();
        }
        float scale = Math.max(drawn[2] - drawn[0], drawn[3] - drawn[1]) / referenceSpan;
        float drawnCentreX = (drawn[0] + drawn[2]) * 0.5f;
        float drawnCentreY = (drawn[1] + drawn[3]) * 0.5f;
        float referenceCentreX = (reference[0] + reference[2]) * 0.5f;
        float referenceCentreY = (reference[1] + reference[3]) * 0.5f;
        List<List<GlyphPoint>> out = new ArrayList<>(template.strokes().size());
        for (List<GlyphPoint> stroke : template.strokes()) {
            List<GlyphPoint> mapped = new ArrayList<>(stroke.size());
            for (GlyphPoint point : stroke) {
                mapped.add(new GlyphPoint(
                        drawnCentreX + (point.x() - referenceCentreX) * scale,
                        drawnCentreY + (point.y() - referenceCentreY) * scale,
                        point.strokeId()));
            }
            out.add(List.copyOf(mapped));
        }
        return List.copyOf(out);
    }

    // --- commit animation ------------------------------------------------------------------

    private void drawAnimation(GuiGraphics graphics, int x, int y, int size,
            CommitAnimation animation, int ticks) {
        int elapsed = ticks - animation.startTick();
        if (elapsed < 0 || elapsed >= CommitAnimation.TOTAL_TICKS) {
            return;
        }
        float snap = Math.min(1f, elapsed / (float) CommitAnimation.SNAP_TICKS);
        float fly = elapsed <= CommitAnimation.SNAP_TICKS
                ? 0f
                : (elapsed - CommitAnimation.SNAP_TICKS) / (float) CommitAnimation.FLY_TICKS;
        int alpha = Math.round(0xFF * (1f - fly));
        int color = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, alpha);
        for (int index = 0; index < animation.strokes().size(); index++) {
            List<GlyphPoint> drawn = animation.strokes().get(index);
            List<GlyphPoint> snapped = animation.snapped().get(index);
            List<int[]> points = new ArrayList<>(drawn.size());
            for (int point = 0; point < drawn.size(); point++) {
                points.add(animatedPoint(drawn.get(point), snapped.get(point), x, y, size, snap, fly, animation));
            }
            ForgeInk.polyline(graphics, points, fly > 0.5f ? 1 : 2, color);
        }
    }

    private static int[] animatedPoint(GlyphPoint drawn, GlyphPoint snapped, int x, int y, int size,
            float snap, float fly, CommitAnimation animation) {
        float unitX = Mth.lerp(snap, drawn.x(), snapped.x());
        float unitY = Mth.lerp(snap, drawn.y(), snapped.y());
        float pixelX = x + unitX * size;
        float pixelY = y + unitY * size;
        return new int[] {
                Math.round(Mth.lerp(fly, pixelX, animation.targetX())),
                Math.round(Mth.lerp(fly, pixelY, animation.targetY()))};
    }

    // --- readout ----------------------------------------------------------------------------

    private void drawReadout(GuiGraphics graphics, Font font, int x, int y, int size,
            List<List<GlyphPoint>> strokes, RecognitionResult result) {
        if (strokes.isEmpty()) {
            drawHint(graphics, font, x, y, size);
            return;
        }
        if (result.best().isEmpty()) {
            return;
        }
        GlyphTemplate best = result.best().get();
        String label = Component.translatable("forge.magical.glyph." + best.id()).getString()
                + " " + String.format(Locale.ROOT, "%.2f", result.bestScore());
        graphics.drawString(font, label, x + size - 5 - font.width(label), y + size - 13,
                readoutColor(result.status()), false);
    }

    private static int readoutColor(RecognitionResult.Status status) {
        return switch (status) {
            case ACCEPTED -> GOOD_TEXT;
            case AMBIGUOUS -> AMBER_TEXT;
            default -> BAD_TEXT;
        };
    }

    private void drawHint(GuiGraphics graphics, Font font, int x, int y, int size) {
        List<FormattedCharSequence> lines =
                font.split(Component.translatable("screen.magical.forge_canvas_hint"), size - 16);
        int lineY = y + size - 8 - lines.size() * 10;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + 8, lineY, MagicalGuiStyle.TEXT_MUTED, false);
            lineY += 10;
        }
    }

    // --- geometry ---------------------------------------------------------------------------

    private static List<int[]> project(List<GlyphPoint> stroke, int x, int y, int size) {
        List<int[]> points = new ArrayList<>(stroke.size());
        for (GlyphPoint point : stroke) {
            points.add(new int[] {x + Math.round(point.x() * size), y + Math.round(point.y() * size)});
        }
        return points;
    }

    /** {@code {minX, minY, maxX, maxY}} of every point, or null when there are none. */
    private static float[] bounds(List<List<GlyphPoint>> strokes) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean any = false;
        for (List<GlyphPoint> stroke : strokes) {
            for (GlyphPoint point : stroke) {
                any = true;
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
        }
        return any ? new float[] {minX, minY, maxX, maxY} : null;
    }
}
