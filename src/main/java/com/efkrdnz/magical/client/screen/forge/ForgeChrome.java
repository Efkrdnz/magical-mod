package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.menu.BlacksmithForgeMenu;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Runeforge's fixed furniture: the header, the button row with its gate message, and the slot
 * wells. It owns the geometry of everything the screen only has to place once, so the screen keeps
 * its constants for the parts the player actually draws in.
 */
public final class ForgeChrome {

    /** Which piece of furniture a click landed on. */
    public enum Hit { NONE, BACK, APPLY, INSCRIBE, UNDO, CLEAR, CODEX }

    private static final int BACK_X = 14;
    private static final int BACK_Y = 6;
    private static final int BACK_W = 44;
    private static final int BACK_H = 16;
    private static final int APPLY_X = 14;
    private static final int APPLY_Y = 224;
    private static final int APPLY_W = 64;
    private static final int APPLY_H = 16;
    private static final int BUTTON_Y = 208;
    private static final int BUTTON_H = 18;
    private static final int INSCRIBE_X = 240;
    private static final int INSCRIBE_W = 70;
    private static final int UNDO_X = 314;
    private static final int UNDO_W = 44;
    private static final int CLEAR_X = 362;
    private static final int CLEAR_W = 44;
    private static final int CODEX_X = 410;
    private static final int CODEX_W = 20;
    private static final int GATE_Y = 228;
    private static final int GATE_W = 190;
    private static final int READY_BUTTON = 0xFF8A6E1E;
    private static final int ACTIVE_BUTTON = 0xFF3F5C1A;
    private static final int DISABLED_BUTTON = 0xFF27354A;
    private static final int NEUTRAL_BUTTON = 0xFF2D3F5C;
    private static final int FAILURE_TEXT = 0xF38BA8;

    private ForgeChrome() {
    }

    /** The codex button is the overlay toggle; the screen wants its rectangle for the tooltip. */
    public static Hit hit(double mouseX, double mouseY, int leftPos, int topPos) {
        if (inside(mouseX, mouseY, leftPos + BACK_X, topPos + BACK_Y, BACK_W, BACK_H)) {
            return Hit.BACK;
        }
        if (inside(mouseX, mouseY, leftPos + APPLY_X, topPos + APPLY_Y, APPLY_W, APPLY_H)) {
            return Hit.APPLY;
        }
        if (!inside(mouseX, mouseY, leftPos + INSCRIBE_X, topPos + BUTTON_Y, CODEX_X + CODEX_W - INSCRIBE_X,
                BUTTON_H)) {
            return Hit.NONE;
        }
        if (mouseX < leftPos + INSCRIBE_X + INSCRIBE_W) {
            return Hit.INSCRIBE;
        }
        if (mouseX >= leftPos + UNDO_X && mouseX < leftPos + UNDO_X + UNDO_W) {
            return Hit.UNDO;
        }
        if (mouseX >= leftPos + CLEAR_X && mouseX < leftPos + CLEAR_X + CLEAR_W) {
            return Hit.CLEAR;
        }
        return mouseX >= leftPos + CODEX_X ? Hit.CODEX : Hit.NONE;
    }

    public static void header(GuiGraphics graphics, Font font, int leftPos, int topPos) {
        MagicalGuiStyle.button(graphics, font, leftPos + BACK_X, topPos + BACK_Y, BACK_W, BACK_H,
                NEUTRAL_BUTTON, Component.translatable("screen.magical.forge_back"));
        MagicalGuiStyle.sectionLabel(graphics, font, leftPos + BACK_X + BACK_W + 8, topPos + BACK_Y + 3,
                Component.translatable("screen.magical.runeforge"), MagicalGuiStyle.ACCENT_GOLD);
    }

    /** The Apply button: turns the current drawing into a sigil. Lit only when one would be taken. */
    public static void apply(GuiGraphics graphics, Font font, int leftPos, int topPos, boolean applicable) {
        MagicalGuiStyle.button(graphics, font, leftPos + APPLY_X, topPos + APPLY_Y, APPLY_W, APPLY_H,
                applicable ? READY_BUTTON : DISABLED_BUTTON,
                Component.translatable("screen.magical.forge_apply"));
    }

    public static void buttons(GuiGraphics graphics, Font font, int leftPos, int topPos,
            boolean ready, boolean codexOpen, Optional<Component> gate) {
        MagicalGuiStyle.button(graphics, font, leftPos + INSCRIBE_X, topPos + BUTTON_Y, INSCRIBE_W, BUTTON_H,
                ready ? READY_BUTTON : DISABLED_BUTTON, Component.translatable("screen.magical.forge_inscribe"));
        MagicalGuiStyle.button(graphics, font, leftPos + UNDO_X, topPos + BUTTON_Y, UNDO_W, BUTTON_H,
                NEUTRAL_BUTTON, Component.translatable("screen.magical.forge_undo"));
        MagicalGuiStyle.button(graphics, font, leftPos + CLEAR_X, topPos + BUTTON_Y, CLEAR_W, BUTTON_H,
                NEUTRAL_BUTTON, Component.translatable("screen.magical.forge_clear"));
        MagicalGuiStyle.button(graphics, font, leftPos + CODEX_X, topPos + BUTTON_Y, CODEX_W, BUTTON_H,
                codexOpen ? ACTIVE_BUTTON : NEUTRAL_BUTTON, Component.literal("?"));
        gate.ifPresent(message -> graphics.drawString(font,
                font.plainSubstrByWidth(message.getString(), GATE_W),
                leftPos + INSCRIBE_X, topPos + GATE_Y, FAILURE_TEXT, false));
    }

    public static void weaponSlot(GuiGraphics graphics, Font font, int leftPos, int topPos) {
        int x = leftPos + BlacksmithForgeMenu.WEAPON_SLOT_X;
        int y = topPos + BlacksmithForgeMenu.WEAPON_SLOT_Y;
        graphics.drawString(font, Component.translatable("screen.magical.forge_weapon_slot"), x - 8, y - 12,
                MagicalGuiStyle.TEXT_MUTED, false);
        MagicalGuiStyle.slot(graphics, x, y, MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0xAA));
    }

    public static void inventory(GuiGraphics graphics, Font font, int leftPos, int topPos) {
        int x = leftPos + BlacksmithForgeMenu.PLAYER_INV_X;
        int y = topPos + BlacksmithForgeMenu.PLAYER_INV_Y;
        MagicalGuiStyle.panel(graphics, x - 10, y - 18, x + 172, topPos + BlacksmithForgeMenu.HOTBAR_Y + 28,
                MagicalGuiStyle.ACCENT_GOLD);
        graphics.drawString(font, Component.translatable("container.inventory"), x, y - 12,
                MagicalGuiStyle.TEXT_MUTED, false);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                MagicalGuiStyle.slot(graphics, x + column * 18, y + row * 18, DISABLED_BUTTON);
            }
        }
        for (int column = 0; column < 9; column++) {
            MagicalGuiStyle.slot(graphics, x + column * 18, topPos + BlacksmithForgeMenu.HOTBAR_Y, DISABLED_BUTTON);
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
