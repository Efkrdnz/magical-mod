package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Draws a glyph template as a tiny icon: the chain strip's cells and the codex's row thumbnails. */
public final class ForgeGlyphIcons {

    private static final int INSET = 1;

    private ForgeGlyphIcons() {
    }

    /** Maps the template's unit strokes into the {@code size}-square box at {@code (x, y)}. */
    public static void draw(GuiGraphics graphics, GlyphTemplate template, int x, int y, int size, int color) {
        int span = Math.max(1, size - INSET * 2);
        for (List<GlyphPoint> stroke : template.strokes()) {
            List<int[]> points = new ArrayList<>(stroke.size());
            for (GlyphPoint point : stroke) {
                points.add(new int[] {
                        x + INSET + Math.round(point.x() * span),
                        y + INSET + Math.round(point.y() * span)});
            }
            ForgeInk.polyline(graphics, points, 1, color);
        }
    }
}
