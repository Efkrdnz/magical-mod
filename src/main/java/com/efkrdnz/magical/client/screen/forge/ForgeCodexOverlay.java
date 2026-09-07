package com.efkrdnz.magical.client.screen.forge;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The glyph codex as a modal overlay over the whole Runeforge, rather than a second tenant of the
 * right column: the preview never gives up its place, and browsing the reference cannot touch the
 * drawing, the committed chain or the idle counter. While it is up it eats every click, scroll
 * and key that is not its own, and Escape closes the overlay without closing the screen.
 */
public final class ForgeCodexOverlay {

    /** The GUI rectangle the overlay lays itself out inside. */
    public record Rect(int x0, int y0, int x1, int y1) {}

    private static final int MARGIN_LEFT = 18;
    /**
     * The right margin deliberately clears the {@code ?} toggle at x410, so pressing it a second
     * time lands outside the panel and dismisses the overlay through the same path as any other
     * outside click - no special case, and the button stays visible under the backdrop.
     */
    private static final int MARGIN_RIGHT = 38;
    private static final int MARGIN_TOP = 16;
    private static final int MARGIN_BOTTOM = 14;
    private static final int LIST_INSET = 6;
    private static final int BACKDROP = 0xC8050A14;

    private final ForgeGlyphCodexPanel panel = new ForgeGlyphCodexPanel();

    private boolean open;

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        open = !open;
    }

    /** Draws the dimmed backdrop and the panel; call it after the screen's own contents. */
    public void render(GuiGraphics graphics, Font font, Rect gui, int screenWidth, int screenHeight,
            double mouseX, double mouseY) {
        if (!open) {
            return;
        }
        graphics.fill(0, 0, screenWidth, screenHeight, BACKDROP);
        Rect panelRect = panelRect(gui);
        panel.render(graphics, font, panelRect.x0(), panelRect.y0(), panelRect.x1(), panelRect.y1(),
                listBottom(panelRect), mouseX, mouseY);
    }

    public List<Component> tooltipAt(double mouseX, double mouseY, Rect gui) {
        if (!open) {
            return List.of();
        }
        Rect panelRect = panelRect(gui);
        return panel.tooltipAt(mouseX, mouseY, panelRect.x0(), panelRect.y0(), panelRect.x1(),
                listBottom(panelRect));
    }

    /** Swallows every click while open; one landing outside the panel dismisses it. */
    public boolean mouseClicked(double mouseX, double mouseY, Rect gui) {
        if (!open) {
            return false;
        }
        Rect panelRect = panelRect(gui);
        if (!contains(panelRect, mouseX, mouseY)) {
            open = false;
            return true;
        }
        panel.mouseClicked(mouseX, mouseY, panelRect.x0(), panelRect.y0(), panelRect.x1(), panelRect.y1());
        return true;
    }

    /** Scrolling goes to the codex list, and is swallowed even when the cursor is off the panel. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, Rect gui) {
        if (!open) {
            return false;
        }
        Rect panelRect = panelRect(gui);
        panel.mouseScrolled(mouseX, mouseY, amount, panelRect.x0(), panelRect.y0(), panelRect.x1(),
                panelRect.y1(), listBottom(panelRect));
        return true;
    }

    /**
     * Escape closes the overlay and stops there. Left to {@code AbstractContainerScreen} it would
     * close the Runeforge itself and tip the weapon back out of its slot, so this has to intercept
     * before {@code super.keyPressed}. Every other key is swallowed too, so Enter, Backspace and
     * Delete cannot edit the chain from behind the panel.
     */
    public boolean keyPressed(int keyCode) {
        if (!open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
        }
        return true;
    }

    private static boolean contains(Rect rect, double x, double y) {
        return x >= rect.x0() && x < rect.x1() && y >= rect.y0() && y < rect.y1();
    }

    private static Rect panelRect(Rect gui) {
        return new Rect(gui.x0() + MARGIN_LEFT, gui.y0() + MARGIN_TOP,
                gui.x1() - MARGIN_RIGHT, gui.y1() - MARGIN_BOTTOM);
    }

    private static int listBottom(Rect panelRect) {
        return panelRect.y1() - LIST_INSET;
    }
}
