package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The frame every full-size magical screen shares: a 444x340 panel with a title, an identity
 * chip, an XP bar and one corner button on the top strip, a hairline, a row of tabs, and one body
 * panel under them. The codex and the Spell Creator both draw through this, so Back and forth
 * between them moves nothing but the contents.
 *
 * <p>The geometry is constants and the paint helpers draw strictly inside those rectangles.
 * Everything is screen-local: add the screen's left and top to place it.
 */
public final class ScreenChrome {
    public static final int PANEL_W = 444;
    public static final int PANEL_H = 340;
    public static final int TEXT_H = 10;

    public static final int TITLE_X = 12;
    public static final int TITLE_Y = 8;
    public static final int TITLE_W = 108;

    public static final int CHIP_X = 126;
    public static final int CHIP_Y = 6;
    public static final int CHIP_W = 110;
    public static final int CHIP_H = 14;

    public static final int XP_X = 242;
    public static final int XP_Y = 6;
    public static final int XP_W = 140;
    public static final int XP_H = 14;

    /** Back on the creator, Close on the codex: the one button in the corner. */
    public static final int CORNER_X = 390;
    public static final int CORNER_Y = 5;
    public static final int CORNER_W = 44;
    public static final int CORNER_H = 16;
    public static final int CORNER_BASE = 0xFF27354A;

    public static final int TAB_X = 12;
    public static final int TAB_Y = 28;
    public static final int TAB_W = 90;
    public static final int TAB_H = 14;
    public static final int TAB_GAP = 2;
    /** The hairline between the top strip and the tabs. */
    public static final int DIVIDER_Y = TAB_Y - 4;

    public static final int BODY_X = 10;
    public static final int BODY_Y = 44;
    public static final int BODY_W = 424;
    public static final int BODY_H = 284;

    private ScreenChrome() {}

    // ---- geometry -------------------------------------------------------------------------------

    public static Rect panel() {
        return new Rect("panel", 0, 0, PANEL_W, PANEL_H);
    }

    public static Rect title() {
        return new Rect("title", TITLE_X, TITLE_Y, TITLE_W, TEXT_H);
    }

    public static Rect chip() {
        return new Rect("chip", CHIP_X, CHIP_Y, CHIP_W, CHIP_H);
    }

    public static Rect xpBar() {
        return new Rect("xp bar", XP_X, XP_Y, XP_W, XP_H);
    }

    public static Rect cornerButton() {
        return new Rect("corner button", CORNER_X, CORNER_Y, CORNER_W, CORNER_H);
    }

    public static Rect tab(int index) {
        return new Rect("tab " + index, TAB_X + index * (TAB_W + TAB_GAP), TAB_Y, TAB_W, TAB_H);
    }

    public static Rect body() {
        return new Rect("body", BODY_X, BODY_Y, BODY_W, BODY_H);
    }

    public static List<Rect> headerRects() {
        return List.of(title(), chip(), xpBar(), cornerButton());
    }

    public static List<Rect> tabRects(int count) {
        List<Rect> rects = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rects.add(tab(index));
        }
        return rects;
    }

    public static boolean hit(Rect rect, double lx, double ly) {
        return lx >= rect.x() && lx < rect.right() && ly >= rect.y() && ly < rect.bottom();
    }

    /** Which of {@code count} tabs a point lands on; -1 in the gaps between them or anywhere else. */
    public static int tabAt(int count, double lx, double ly) {
        if (ly < TAB_Y || ly >= TAB_Y + TAB_H || lx < TAB_X) {
            return -1;
        }
        double along = lx - TAB_X;
        int index = (int) (along / (TAB_W + TAB_GAP));
        if (index >= count || along - index * (TAB_W + TAB_GAP) >= TAB_W) {
            return -1;
        }
        return index;
    }

    // ---- painting --------------------------------------------------------------------------------

    /**
     * The fills of the top strip: the chip well with its accent bar, the XP well and its gold fill,
     * the corner button, and the hairline under it all. Text comes later, in {@link #textHeader}.
     */
    public static void paintHeader(GuiGraphics g, Font font, int x0, int y0, int accent, float xpFraction, Component cornerLabel) {
        Rect chip = chip();
        MagicalGuiStyle.inset(g, x0 + chip.x(), y0 + chip.y(), x0 + chip.right(), y0 + chip.bottom());
        g.fill(x0 + chip.x(), y0 + chip.y(), x0 + chip.x() + 2, y0 + chip.bottom(), accent);
        Rect xp = xpBar();
        MagicalGuiStyle.inset(g, x0 + xp.x(), y0 + xp.y(), x0 + xp.right(), y0 + xp.bottom());
        int fill = Math.round((xp.w() - 2) * Math.max(0.0F, Math.min(1.0F, xpFraction)));
        if (fill > 0) {
            g.fillGradient(x0 + xp.x() + 1, y0 + xp.y() + 1, x0 + xp.x() + 1 + fill, y0 + xp.bottom() - 1,
                    MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0xAA), MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0x55));
        }
        Rect corner = cornerButton();
        MagicalGuiStyle.button(g, font, x0 + corner.x(), y0 + corner.y(), corner.w(), corner.h(), CORNER_BASE, cornerLabel);
        g.fill(x0 + BODY_X, y0 + DIVIDER_Y, x0 + PANEL_W - BODY_X, y0 + DIVIDER_Y + 1, MagicalGuiStyle.withAlpha(accent, 0x3A));
    }

    /** The tab plates; the selected one is a lifted list row, the rest sit flat. */
    public static void paintTabs(GuiGraphics g, int x0, int y0, int count, int selected) {
        for (int index = 0; index < count; index++) {
            Rect rect = tab(index);
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), index == selected, MagicalGuiStyle.ACCENT_ARCANE);
        }
    }

    public static void paintBody(GuiGraphics g, int x0, int y0, int accent) {
        Rect body = body();
        MagicalGuiStyle.panel(g, x0 + body.x(), y0 + body.y(), x0 + body.right(), y0 + body.bottom(), accent);
    }

    /** The text of the top strip, drawn after the emblem flush so it lands over everything. */
    public static void textHeader(GuiGraphics g, Font font, int x0, int y0, Component title, Component chip, int chipColor, Component xpLabel) {
        Rect titleRect = title();
        g.drawString(font, font.plainSubstrByWidth(title.getString(), TITLE_W), x0 + titleRect.x(), y0 + titleRect.y(),
                MagicalGuiStyle.TEXT_PRIMARY, false);
        Rect chipRect = chip();
        g.drawCenteredString(font, font.plainSubstrByWidth(chip.getString(), chipRect.w() - 8),
                x0 + chipRect.x() + chipRect.w() / 2 + 1, y0 + chipRect.y() + 3, chipColor);
        Rect xp = xpBar();
        g.drawCenteredString(font, font.plainSubstrByWidth(xpLabel.getString(), xp.w() - 6),
                x0 + xp.x() + xp.w() / 2, y0 + xp.y() + 3, MagicalGuiStyle.TEXT_PRIMARY);
    }

    public static void textTabs(GuiGraphics g, Font font, int x0, int y0, List<Component> labels, int selected) {
        for (int index = 0; index < labels.size(); index++) {
            Rect rect = tab(index);
            g.drawCenteredString(font, font.plainSubstrByWidth(labels.get(index).getString(), rect.w() - 6),
                    x0 + rect.x() + rect.w() / 2, y0 + rect.y() + 3,
                    index == selected ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED);
        }
    }
}
