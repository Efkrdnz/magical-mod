package com.efkrdnz.magical.client.screen.forge;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Interpolated-fill ink, the drawing primitive the whole Runeforge screen is built from. A line is
 * a run of {@code thickness}-wide squares stepped along the segment, so the same routine draws the
 * canvas' three-pass glow, the strip's tiny glyph icons and the codex thumbnails.
 */
public final class ForgeInk {

    private ForgeInk() {
    }

    /** One segment, drawn as squares of {@code thickness} pixels centred on the path. */
    public static void line(GuiGraphics graphics, int fromX, int fromY, int toX, int toY, int thickness, int color) {
        int width = Math.max(1, thickness);
        int half = width / 2;
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0f : (float) step / steps;
            int x = Mth.floor(Mth.lerp(progress, fromX, toX));
            int y = Mth.floor(Mth.lerp(progress, fromY, toY));
            graphics.fill(x - half, y - half, x - half + width, y - half + width, color);
        }
    }

    /**
     * A polyline of already-projected pixel points; a single point still leaves a dot so a tap is
     * visible while the player is deciding where the stroke goes.
     */
    public static void polyline(GuiGraphics graphics, List<int[]> points, int thickness, int color) {
        if (points.isEmpty()) {
            return;
        }
        if (points.size() == 1) {
            int[] only = points.get(0);
            line(graphics, only[0], only[1], only[0], only[1], thickness, color);
            return;
        }
        for (int i = 1; i < points.size(); i++) {
            int[] from = points.get(i - 1);
            int[] to = points.get(i);
            line(graphics, from[0], from[1], to[0], to[1], thickness, color);
        }
    }
}
