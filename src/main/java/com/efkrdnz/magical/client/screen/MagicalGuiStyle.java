package com.efkrdnz.magical.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Shared visual language for the Magic Codex and its sub-views: gradient panels with
 * accent-tinted borders, beveled buttons, striped list rows, and styled scrollbars.
 * All helpers draw strictly inside the rectangles they are given so existing layout
 * and hit-testing stay untouched.
 */
public final class MagicalGuiStyle {
    public static final int ACCENT_ARCANE = 0xFF5FD4FF;
    public static final int ACCENT_GOLD = 0xFFF7D774;
    public static final int ACCENT_VIOLET = 0xFFB48AFF;
    public static final int ACCENT_BLOOD = 0xFFE06470;
    static final int ACCENT_NATURE = 0xFF8FEA9C;
    public static final int TEXT_PRIMARY = 0xF4F9FF;
    public static final int TEXT_MUTED = 0x8292AB;

    private MagicalGuiStyle() {}

    /** Full-screen backdrop: vertical night gradient with a lit top edge. */
    public static void screenBackground(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF03060C);
        g.fillGradient(x0, y0, x1, y1, 0xF80C1322, 0xF804070F);
        g.fill(x0, y0, x1, y0 + 1, 0xFF31435F);
        g.fill(x0, y0 + 1, x1, y0 + 2, 0x66233450);
        g.fill(x0, y0, x0 + 1, y1, 0xFF16213A);
        g.fill(x1 - 1, y0, x1, y1, 0xFF16213A);
        g.fill(x0, y1 - 1, x1, y1, 0xFF02040A);
    }

    /** Raised content panel with an accent-tinted top edge and corner ticks. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF040810);
        g.fillGradient(x0, y0, x1, y1, 0xFF1C2539, 0xFF121828);
        g.fill(x0, y0, x1, y0 + 1, withAlpha(accent, 0x77));
        g.fill(x0, y0 + 1, x1, y0 + 2, 0x1EFFFFFF);
        int tick = withAlpha(accent, 0xCC);
        g.fill(x0, y0, x0 + 5, y0 + 1, tick);
        g.fill(x0, y0, x0 + 1, y0 + 5, tick);
        g.fill(x1 - 5, y0, x1, y0 + 1, tick);
        g.fill(x1 - 1, y0, x1, y0 + 5, tick);
        g.fill(x0, y1 - 1, x0 + 5, y1, tick);
        g.fill(x0, y1 - 5, x0 + 1, y1, tick);
        g.fill(x1 - 5, y1 - 1, x1, y1, tick);
        g.fill(x1 - 1, y1 - 5, x1, y1, tick);
    }

    /** Sunken area for lists and drawing pads. */
    public static void inset(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF060B16);
        g.fillGradient(x0, y0, x1, y1, 0xFF0A101D, 0xFF0E1526);
        g.fill(x0, y0, x1, y0 + 1, 0x66000000);
        g.fill(x0, y0, x0 + 1, y1, 0x44000000);
    }

    /** Generic card whose gradient is derived from a base color (for stateful rows). */
    static void card(GuiGraphics g, int x0, int y0, int x1, int y1, int base) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF060A12);
        g.fillGradient(x0, y0, x1, y1, brighten(base, 1.22F), brighten(base, 0.74F));
        g.fill(x0, y0, x1, y0 + 1, 0x2EFFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x55000000);
    }

    /** Beveled gradient button; geometry matches a plain fill of the same rectangle. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, int base, Component label) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF060A12);
        g.fillGradient(x, y, x + w, y + h, brighten(base, 1.38F), brighten(base, 0.68F));
        g.fill(x, y, x + w, y + 1, 0x48FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x22FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x66000000);
        g.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), w - 6), x + w / 2, y + (h - 8) / 2, TEXT_PRIMARY);
    }

    /** List row with a left accent bar; selection gets a bright border and lifted body. */
    public static void listRow(GuiGraphics g, int x, int y, int w, int h, boolean selected, int accent) {
        if (selected) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, withAlpha(accent, 0xDD));
            g.fillGradient(x, y, x + w, y + h, 0xFF2A4A63, 0xFF1D3549);
            g.fill(x, y, x + w, y + 1, 0x33FFFFFF);
        } else {
            g.fillGradient(x, y, x + w, y + h, 0xFF1E2737, 0xFF161E2C);
            g.fill(x, y + h - 1, x + w, y + h, 0x44000000);
        }
        g.fill(x, y, x + 2, y + h, selected ? accent : withAlpha(accent, 0x77));
    }

    /** Legend chip that sits on a panel's top border, like a titled fieldset. */
    static void legend(GuiGraphics g, Font font, int x, int y, Component label, int color) {
        int width = font.width(label);
        g.fill(x - 4, y - 2, x + width + 4, y + 9, 0xFF0A101C);
        g.fill(x - 4, y - 2, x + width + 4, y - 1, withAlpha(color, 0x66));
        g.drawString(font, label, x, y, color, false);
    }

    /** Section label with an accent underline that fades out to the right. */
    public static void sectionLabel(GuiGraphics g, Font font, int x, int y, Component label, int color) {
        g.drawString(font, label, x, y, color, false);
        int width = Math.max(28, font.width(label));
        g.fill(x, y + 10, x + width, y + 11, withAlpha(color, 0xAA));
        g.fill(x + width, y + 10, x + width + 14, y + 11, withAlpha(color, 0x33));
    }

    /** Scrollbar with a sunken track and gradient thumb. */
    public static void scrollbar(GuiGraphics g, int x, int y, int height, int totalUnits, int visibleUnits, int scrollUnits) {
        if (totalUnits <= visibleUnits) {
            return;
        }
        g.fill(x - 1, y, x + 3, y + height, 0xCC070C16);
        int thumbHeight = Math.max(12, height * visibleUnits / totalUnits);
        int thumbY = y + (height - thumbHeight) * scrollUnits / Math.max(1, totalUnits - visibleUnits);
        g.fillGradient(x, thumbY, x + 2, thumbY + thumbHeight, 0xFF86D6F5, 0xFF3E7FA6);
        g.fill(x, thumbY, x + 2, thumbY + 1, 0x66FFFFFF);
    }

    /** Checkbox with border, sunken well, and glowing check. */
    static void checkbox(GuiGraphics g, int x, int y, boolean checked) {
        g.fill(x - 1, y - 1, x + 11, y + 11, checked ? withAlpha(ACCENT_NATURE, 0xCC) : 0xFF37465F);
        g.fillGradient(x, y, x + 10, y + 10, 0xFF0B1220, 0xFF101B2E);
        if (checked) {
            g.fill(x + 3, y + 5, x + 5, y + 7, 0xFFA6E3A1);
            g.fill(x + 5, y + 3, x + 8, y + 5, 0xFFA6E3A1);
            g.fill(x + 2, y + 4, x + 4, y + 6, 0x5FA6E3A1);
        }
    }

    /** Item slot: dark well with a beveled border. */
    public static void slot(GuiGraphics g, int x, int y, int borderColor) {
        g.fill(x - 1, y - 1, x + 17, y + 17, borderColor);
        g.fillGradient(x, y, x + 16, y + 16, 0xFF0A0F1B, 0xFF111A2C);
        g.fill(x, y, x + 16, y + 1, 0x77000000);
        g.fill(x, y + 15, x + 16, y + 16, 0x22FFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static int brighten(int color, float factor) {
        int red = Mth.clamp(Math.round(((color >> 16) & 0xFF) * factor), 0, 255);
        int green = Mth.clamp(Math.round(((color >> 8) & 0xFF) * factor), 0, 255);
        int blue = Mth.clamp(Math.round((color & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
