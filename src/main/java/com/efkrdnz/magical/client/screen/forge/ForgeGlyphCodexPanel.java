package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.art.ForgeArt;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The glyph reference: one tab per category, one scrollable row per glyph carrying its thumbnail,
 * name, stroke order, description and whatever its category adds - the element rider or the grade's
 * slot/mana/threshold/material line - plus the Art matrix condensed onto a line of its own. Nothing
 * here is unlock-gated: the player is meant to be able to look a rune up while drawing it.
 *
 * <p>{@link ForgeCodexOverlay} owns where this is drawn. The row and tab metrics below are sized
 * for that overlay's width, not for a column beside the preview.</p>
 */
public final class ForgeGlyphCodexPanel {

    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 2;
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 2;
    private static final int THUMB = 30;
    private static final int PADDING = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int TITLE_TOP = 5;
    private static final int TABS_TOP = 22;
    private static final int ROWS_TOP = TABS_TOP + TAB_HEIGHT + 6;

    private static final GlyphCategory[] TABS = GlyphCategory.values();

    private int tab;
    private int scroll;

    private GlyphCategory category() {
        return TABS[tab];
    }

    /** Draws tabs and rows; {@code listBottom} is where the rows stop, above the button row. */
    public void render(GuiGraphics graphics, Font font, int x0, int y0, int x1, int y1, int listBottom,
            double mouseX, double mouseY) {
        MagicalGuiStyle.panel(graphics, x0, y0, x1, y1, MagicalGuiStyle.ACCENT_ARCANE);
        drawHeader(graphics, font, x0, y0, x1);
        drawTabs(graphics, font, x0, y0, x1);
        List<GlyphTemplate> rows = rows();
        int visible = visibleRows(y0, listBottom);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        int rowX = x0 + PADDING;
        int rowWidth = x1 - x0 - PADDING * 2 - SCROLLBAR_WIDTH;
        int rowY = y0 + ROWS_TOP;
        MagicalGuiStyle.inset(graphics, rowX - 2, rowY - 2, rowX + rowWidth + 2, listBottom);
        for (int index = 0; index < visible && scroll + index < rows.size(); index++) {
            GlyphTemplate template = rows.get(scroll + index);
            int top = rowY + index * ROW_HEIGHT;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth && mouseY >= top && mouseY < top + ROW_HEIGHT;
            drawRow(graphics, font, template, rowX, top, rowWidth, hovered);
        }
        MagicalGuiStyle.scrollbar(graphics, x1 - PADDING - 2, rowY, visible * ROW_HEIGHT,
                rows.size(), visible, scroll);
    }

    /** Names the overlay and says how to dismiss it, since it covers the button row underneath. */
    private static void drawHeader(GuiGraphics graphics, Font font, int x0, int y0, int x1) {
        MagicalGuiStyle.sectionLabel(graphics, font, x0 + PADDING + 2, y0 + TITLE_TOP,
                Component.translatable("screen.magical.forge_codex"), MagicalGuiStyle.ACCENT_ARCANE);
        String hint = Component.translatable("screen.magical.forge_codex_close").getString();
        graphics.drawString(font, hint, x1 - PADDING - 2 - font.width(hint), y0 + TITLE_TOP,
                MagicalGuiStyle.TEXT_MUTED, false);
    }

    private void drawTabs(GuiGraphics graphics, Font font, int x0, int y0, int x1) {
        int width = (x1 - x0 - PADDING * 2 - TAB_GAP * (TABS.length - 1)) / TABS.length;
        for (int index = 0; index < TABS.length; index++) {
            int tabX = x0 + PADDING + index * (width + TAB_GAP);
            MagicalGuiStyle.listRow(graphics, tabX, y0 + TABS_TOP, width, TAB_HEIGHT,
                    index == tab, MagicalGuiStyle.ACCENT_ARCANE);
            String label = categoryName(TABS[index]).getString();
            graphics.drawCenteredString(font, font.plainSubstrByWidth(label, width - 6),
                    tabX + width / 2, y0 + TABS_TOP + 3,
                    index == tab ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED);
        }
    }

    private void drawRow(GuiGraphics graphics, Font font, GlyphTemplate template,
            int x, int y, int width, boolean hovered) {
        MagicalGuiStyle.listRow(graphics, x, y, width, ROW_HEIGHT - ROW_GAP, hovered, MagicalGuiStyle.ACCENT_GOLD);
        ForgeGlyphIcons.draw(graphics, template, x + 4, y + 4, THUMB, MagicalGuiStyle.ACCENT_GOLD);
        int textX = x + THUMB + 12;
        int textWidth = width - (THUMB + 16);
        String name = Component.translatable("forge.magical.glyph." + template.id()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), textX, y + 3,
                MagicalGuiStyle.TEXT_PRIMARY, false);
        drawExtra(graphics, font, template, x, y, width, textWidth - font.width(name) - 6);
        drawLine(graphics, font, glyphText(template, ".stroke"), textX, y + 13, textWidth,
                MagicalGuiStyle.TEXT_MUTED);
        drawLine(graphics, font, glyphText(template, ".desc"), textX, y + 22, textWidth,
                MagicalGuiStyle.TEXT_PRIMARY);
        drawLine(graphics, font, artSummary(template), textX, y + 31, textWidth,
                MagicalGuiStyle.ACCENT_VIOLET);
    }

    private static void drawLine(GuiGraphics graphics, Font font, String text, int x, int y,
            int maxWidth, int color) {
        if (text.isEmpty()) {
            return;
        }
        graphics.drawString(font, font.plainSubstrByWidth(text, maxWidth), x, y, color, false);
    }

    /** The rider or grade line, right-aligned on the name line in whatever room the name leaves. */
    private static void drawExtra(GuiGraphics graphics, Font font, GlyphTemplate template,
            int x, int y, int width, int available) {
        String extra = extraText(template);
        if (extra.isEmpty()) {
            return;
        }
        String trimmed = font.plainSubstrByWidth(extra, Math.max(0, available));
        graphics.drawString(font, trimmed, x + width - 4 - font.width(trimmed), y + 3,
                MagicalGuiStyle.ACCENT_GOLD, false);
    }

    private static String glyphText(GlyphTemplate template, String suffix) {
        return Component.translatable("forge.magical.glyph." + template.id() + suffix).getString();
    }

    /** The Art lines condensed onto one row line; the tooltip still lists them one per line. */
    private static String artSummary(GlyphTemplate template) {
        StringBuilder text = new StringBuilder();
        for (Component line : artLines(template)) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(line.getString());
        }
        return text.toString();
    }

    /** The tooltip for the hovered tab or row, or an empty list when nothing is hovered. */
    public List<Component> tooltipAt(double mouseX, double mouseY, int x0, int y0, int x1, int listBottom) {
        // Tab labels are truncated to fit five across, so the full category name lives here.
        if (mouseY >= y0 + TABS_TOP && mouseY < y0 + TABS_TOP + TAB_HEIGHT) {
            int width = (x1 - x0 - PADDING * 2 - TAB_GAP * (TABS.length - 1)) / TABS.length;
            for (int index = 0; index < TABS.length; index++) {
                int tabX = x0 + PADDING + index * (width + TAB_GAP);
                if (mouseX >= tabX && mouseX < tabX + width) {
                    return List.of(categoryName(TABS[index]));
                }
            }
            return List.of();
        }
        GlyphTemplate template = rowAt(mouseX, mouseY, x0, y0, x1, listBottom);
        if (template == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable("forge.magical.glyph." + template.id()));
        lines.add(Component.translatable("forge.magical.glyph." + template.id() + ".desc"));
        String extra = extraText(template);
        if (!extra.isEmpty()) {
            lines.add(Component.literal(extra));
        }
        lines.addAll(artLines(template));
        return lines;
    }

    /**
     * The Art matrix, read from whichever side the hovered row sits on: an element core lists the
     * forms it turns into a named Art, and a form glyph lists the elements that give it one.
     */
    private static List<Component> artLines(GlyphTemplate template) {
        List<Component> lines = new ArrayList<>();
        if (template.category() == GlyphCategory.ELEMENT) {
            for (ForgeArt art : ForgeArt.forElement(template.id())) {
                lines.add(Component.translatable("screen.magical.forge_art_with",
                        glyphName(art.form()), Component.translatable(art.langKey())));
            }
            return lines;
        }
        if (template.category() != GlyphCategory.FORM) {
            return lines;
        }
        for (ForgeArt art : ForgeArt.forForm(template.id())) {
            lines.add(Component.translatable("screen.magical.forge_art_from",
                    glyphName(art.element()), Component.translatable(art.langKey())));
        }
        return lines;
    }

    private static Component glyphName(String id) {
        return Component.translatable("forge.magical.glyph." + id);
    }

    /** Handles a click on the tab strip; rows are reference-only, so a row click is swallowed. */
    public boolean mouseClicked(double mouseX, double mouseY, int x0, int y0, int x1, int y1) {
        if (mouseX < x0 || mouseX >= x1 || mouseY < y0 || mouseY >= y1) {
            return false;
        }
        int width = (x1 - x0 - PADDING * 2 - TAB_GAP * (TABS.length - 1)) / TABS.length;
        if (mouseY >= y0 + TABS_TOP && mouseY < y0 + TABS_TOP + TAB_HEIGHT) {
            for (int index = 0; index < TABS.length; index++) {
                int tabX = x0 + PADDING + index * (width + TAB_GAP);
                if (mouseX >= tabX && mouseX < tabX + width) {
                    tab = index;
                    scroll = 0;
                    return true;
                }
            }
        }
        return true;
    }

    /** Scrolls the row list; returns false when the cursor is outside the panel. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount,
            int x0, int y0, int x1, int y1, int listBottom) {
        if (mouseX < x0 || mouseX >= x1 || mouseY < y0 || mouseY >= y1) {
            return false;
        }
        int visible = visibleRows(y0, listBottom);
        scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, rows().size() - visible));
        return true;
    }

    private GlyphTemplate rowAt(double mouseX, double mouseY, int x0, int y0, int x1, int listBottom) {
        int rowX = x0 + PADDING;
        int rowWidth = x1 - x0 - PADDING * 2 - SCROLLBAR_WIDTH;
        if (mouseX < rowX || mouseX >= rowX + rowWidth) {
            return null;
        }
        int rowY = y0 + ROWS_TOP;
        int visible = visibleRows(y0, listBottom);
        List<GlyphTemplate> rows = rows();
        for (int index = 0; index < visible && scroll + index < rows.size(); index++) {
            int top = rowY + index * ROW_HEIGHT;
            if (mouseY >= top && mouseY < top + ROW_HEIGHT) {
                return rows.get(scroll + index);
            }
        }
        return null;
    }

    private int visibleRows(int y0, int listBottom) {
        return Math.max(1, (listBottom - (y0 + ROWS_TOP)) / ROW_HEIGHT);
    }

    private List<GlyphTemplate> rows() {
        List<GlyphTemplate> rows = new ArrayList<>();
        for (GlyphTemplate template : ForgeGlyphLibrary.all()) {
            if (template.category() == category()) {
                rows.add(template);
            }
        }
        return rows;
    }

    /** Grade rows carry their slots, mana share, accept threshold and material floor; elements their rider. */
    private static String extraText(GlyphTemplate template) {
        if (template.category() == GlyphCategory.ELEMENT) {
            return Component.translatable("forge.magical.rider." + template.id()).getString();
        }
        if (template.category() != GlyphCategory.GRADE) {
            return "";
        }
        return ForgeGrade.byName(template.id())
                .map(grade -> String.format(Locale.ROOT, "%dF·%dR · %d%% · %.2f · %s",
                        grade.formSlots(), grade.modifierSlots(), grade.manaPercent(), grade.sigilAcceptScore(),
                        Component.translatable(
                                "forge.magical.material." + grade.materialFloor().serializedName()).getString()))
                .orElse("");
    }

    private static Component categoryName(GlyphCategory category) {
        return Component.translatable("forge.magical.category." + category.name().toLowerCase(Locale.ROOT));
    }
}
