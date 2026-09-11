package com.efkrdnz.magical.client.screen.blood;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.client.screen.forge.ForgeInk;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The square the shape is drawn in: a map of the ground around the caster, seen from above.
 *
 * <p>The dot in the middle is the player and the plate's edge is the reach. Points stored outside
 * that edge - drawn back when the reach was higher - are painted where they really are, dimmed, and
 * cut off just past the plate. They are not moved inward and they are not deleted, because the rule
 * is that reducing reach <em>ignores</em> the far part: buy the reach back and the shape comes back
 * whole. Drawing them where they actually lie is what makes that legible rather than surprising.
 *
 * <p>With the compass letters showing, the canvas is world-locked and up is north. With the look
 * line showing, the canvas turns with the caster, so up is always straight ahead - which is why the
 * line is drawn straight up rather than at an angle: the canvas rotates, not the indicator.
 */
public final class BloodShapeCanvas {

    /** How far past the plate a ghost may reach before it is cut, in pixels. */
    private static final int GHOST_MARGIN = 8;

    private static final int GRID_LINES = 8;

    private static final int INK_GLOW = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_BLOOD, 0x3A);
    private static final int INK_CORE = 0xFFC03442;
    private static final int INK_HIGHLIGHT = 0xFFF4A8AF;
    private static final int INK_LIVE = 0xFFFF8E97;
    private static final int GHOST = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_BLOOD, 0x40);

    private BloodShapeCanvas() {
    }

    /**
     * @param halfExtent the caster's current reach in blocks, which is what the plate's half-width
     *                   represents; stored points are divided by it rather than by whatever reach
     *                   they happened to be drawn at
     * @param trackYaw   whether this slot turns with the caster, which decides between the compass
     *                   letters and the look line
     */
    public static void draw(GuiGraphics g, Font font, int left, int top,
            BloodStrokeBuilder builder, double halfExtent, boolean trackYaw) {
        int x = left + BloodShapeLayout.CANVAS_X;
        int y = top + BloodShapeLayout.CANVAS_Y;
        int size = BloodShapeLayout.CANVAS_SIZE;

        drawPlate(g, x, y, size);
        drawBearings(g, font, x, y, size, trackYaw);

        // Ghosts get a little way past the plate and are then cut, which reads as "this part is
        // outside your reach" far better than a colour change on its own would.
        g.enableScissor(x - GHOST_MARGIN, y - GHOST_MARGIN,
                x + size + GHOST_MARGIN, y + size + GHOST_MARGIN);
        for (int[] stroke : builder.strokes()) {
            drawStored(g, left, top, stroke, halfExtent);
        }
        drawLive(g, left, top, builder.livePoints());
        g.disableScissor();

        // The player, drawn last so a stroke through the middle does not bury them.
        int centreX = x + size / 2;
        int centreY = y + size / 2;
        g.fill(centreX - 2, centreY - 2, centreX + 3, centreY + 3, 0xFF0A0F1B);
        g.fill(centreX - 1, centreY - 1, centreX + 2, centreY + 2, 0xFFF4F9FF);
    }

    private static void drawPlate(GuiGraphics g, int x, int y, int size) {
        MagicalGuiStyle.inset(g, x, y, x + size, y + size);
        int grid = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_BLOOD, 0x12);
        for (int step = 1; step < GRID_LINES; step++) {
            int offset = size * step / GRID_LINES;
            g.fill(x + offset, y, x + offset + 1, y + size, grid);
            g.fill(x, y + offset, x + size, y + offset + 1, grid);
        }
        int border = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_BLOOD, 0x55);
        g.fill(x, y, x + size, y + 1, border);
        g.fill(x, y + size - 1, x + size, y + size, border);
        g.fill(x, y, x + 1, y + size, border);
        g.fill(x + size - 1, y, x + size, y + size, border);
    }

    /** The compass letters, or the look line that replaces them once the slot tracks the caster. */
    private static void drawBearings(GuiGraphics g, Font font, int x, int y, int size,
            boolean trackYaw) {
        int centreX = x + size / 2;
        int centreY = y + size / 2;
        if (trackYaw) {
            int accent = MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_BLOOD, 0x99);
            g.fill(centreX, y + 4, centreX + 1, centreY, accent);
            // A small head on the line, so which end is the front is never a guess.
            g.fill(centreX - 2, y + 4, centreX + 3, y + 6, accent);
            g.fill(centreX - 1, y + 6, centreX + 2, y + 8, accent);
            return;
        }
        // Yaw 180 is the world-locked reading, and there up is north and right is east.
        letter(g, font, "N", centreX, y + 3);
        letter(g, font, "S", centreX, y + size - 12);
        letter(g, font, "W", x + 5, centreY - 4);
        letter(g, font, "E", x + size - 9, centreY - 4);
    }

    private static void letter(GuiGraphics g, Font font, String text, int centreX, int y) {
        Component label = Component.literal(text);
        g.drawString(font, label, centreX - font.width(label) / 2, y,
                MagicalGuiStyle.TEXT_MUTED, false);
    }

    /**
     * One stored stroke, split where it crosses the plate's edge so the part still in reach reads as
     * ink and the rest as a ghost.
     */
    private static void drawStored(GuiGraphics g, int left, int top, int[] stroke,
            double halfExtent) {
        List<int[]> run = new ArrayList<>();
        boolean runOutside = false;
        for (int packed : stroke) {
            double u = BloodShapeRules.fromUnits(BloodShapeRules.unpackX(packed)) / halfExtent;
            double v = BloodShapeRules.fromUnits(BloodShapeRules.unpackY(packed)) / halfExtent;
            boolean outside = Math.abs(u) > 1.0D || Math.abs(v) > 1.0D;
            int[] point = point(left, top, u, v);
            if (!run.isEmpty() && outside != runOutside) {
                // The crossing point goes into both runs, so the ink and the ghost meet rather than
                // leaving a gap at every boundary crossing.
                run.add(point);
                flush(g, run, runOutside);
                run.add(point);
            }
            runOutside = outside;
            run.add(point);
        }
        flush(g, run, runOutside);
    }

    private static void flush(GuiGraphics g, List<int[]> run, boolean outside) {
        if (run.size() >= 2) {
            if (outside) {
                ForgeInk.polyline(g, run, 2, GHOST);
            } else {
                ForgeInk.polyline(g, run, 5, INK_GLOW);
                ForgeInk.polyline(g, run, 3, INK_CORE);
                ForgeInk.polyline(g, run, 1, INK_HIGHLIGHT);
            }
        }
        run.clear();
    }

    /** The stroke under the cursor right now. Always in reach: the cursor is clamped to the plate. */
    private static void drawLive(GuiGraphics g, int left, int top, List<double[]> live) {
        if (live.size() < 2) {
            return;
        }
        List<int[]> points = new ArrayList<>(live.size());
        for (double[] pair : live) {
            points.add(point(left, top, pair[0], pair[1]));
        }
        ForgeInk.polyline(g, points, 5, INK_GLOW);
        ForgeInk.polyline(g, points, 3, INK_CORE);
        ForgeInk.polyline(g, points, 1, INK_LIVE);
    }

    private static int[] point(int left, int top, double u, double v) {
        return new int[] {
                left + (int) Math.round(BloodShapeLayout.pixelX(u)),
                top + (int) Math.round(BloodShapeLayout.pixelY(v))};
    }
}
