package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class BlackFlamesInput {
    private static final int WHEEL_SLOT = -2;
    private static final ResourceLocation[] MODES = {
            MagicContent.BLACK_FLAMES_CAST.id(),
            MagicContent.BLACK_FLAMES_IMBUE.id(),
            MagicContent.BLACK_FLAMES_BRAND.id()
    };
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];
    private static int activeSlot = -1;
    private static int selectedMode;
    private static float fade;
    private static boolean wheelConfirmWasDown;

    private BlackFlamesInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        if (!MagicContent.BLACK_FLAMES.id().equals(skillId)) {
            resetSlot(slot);
            return false;
        }
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (down && !WAS_DOWN[slot]) {
            activeSlot = slot;
            fade = 0.0F;
        } else if (!down && WAS_DOWN[slot]) {
            MagicalNetwork.sendBlackFlamesCast(slot, selectedMode);
            resetSlot(slot);
            return true;
        }
        if (down) {
            activeSlot = slot;
            fade = Math.min(1.0F, fade + 0.18F);
        }
        WAS_DOWN[slot] = down;
        return true;
    }

    public static boolean beginWheelCast() {
        activeSlot = WHEEL_SLOT;
        wheelConfirmWasDown = true;
        selectedMode = 0;
        fade = 0.0F;
        return true;
    }

    public static void tickWheelCast(boolean confirmDown) {
        if (activeSlot != WHEEL_SLOT) {
            return;
        }
        if (!confirmDown && wheelConfirmWasDown) {
            MagicalNetwork.sendWheelSubSkillCastRequest(MagicContent.BLACK_FLAMES.id(), selectedMode);
            resetWheel();
            return;
        }
        if (confirmDown) {
            fade = Math.min(1.0F, fade + 0.18F);
        }
        wheelConfirmWasDown = confirmDown;
    }

    public static boolean handleScroll(double delta) {
        if (activeSlot == -1 || MODES.length <= 1) {
            return activeSlot != -1;
        }
        if (delta > 0.0D) {
            selectedMode = Math.floorMod(selectedMode - 1, MODES.length);
        } else if (delta < 0.0D) {
            selectedMode = Math.floorMod(selectedMode + 1, MODES.length);
        }
        return true;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (activeSlot == -1 || minecraft.player == null) {
            fade = Math.max(0.0F, fade - 0.12F);
            return;
        }
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int centerX = width / 2;
        int centerY = height / 2 + 72;
        int radius = 82;
        float age = minecraft.player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawPizzaWheel(guiGraphics, centerX, centerY, radius, age);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable(MagicContent.BLACK_FLAMES.nameKey()), centerX, centerY - radius - 22, 0xE43A16);
        for (int i = 0; i < MODES.length; i++) {
            float angle = sliceCenter(i);
            int x = centerX + Math.round(Mth.cos(angle) * radius * 0.56F);
            int y = centerY + Math.round(Mth.sin(angle) * radius * 0.56F);
            boolean selected = i == selectedMode;
            if (selected) {
                drawSliceFill(guiGraphics, centerX, centerY, radius - 6, i, 0x663A061D);
                drawArc(guiGraphics, centerX, centerY, radius + 3, sliceStart(i), sliceEnd(i), 4, 0xE8E43A16);
            }
            guiGraphics.drawCenteredString(minecraft.font, Component.translatable(MagicContent.get(MODES[i]).nameKey()), x, y - 4, selected ? 0xFF6A39 : 0xD8B8D8);
        }
        drawHub(guiGraphics, centerX, centerY, age);
        guiGraphics.drawCenteredString(minecraft.font, Component.literal("Mouse wheel selects, release to cast"), centerX, centerY + radius + 14, 0xE6B8C8);
    }

    private static void drawPizzaWheel(GuiGraphics guiGraphics, int centerX, int centerY, int radius, float age) {
        guiGraphics.fill(centerX - radius - 24, centerY - radius - 34, centerX + radius + 24, centerY + radius + 30, 0x7A05030A);
        for (int i = 0; i < MODES.length; i++) {
            drawSliceFill(guiGraphics, centerX, centerY, radius - 4, i, i == selectedMode ? 0x443A061D : 0x33100618);
        }
        drawCircle(guiGraphics, centerX, centerY, radius, 3, 0xD6E43A16);
        drawCircle(guiGraphics, centerX, centerY, radius - 16, 1, 0x887A0F55);
        drawCircle(guiGraphics, centerX, centerY, radius * 2 / 5, 1, 0x66FF6A39);
        for (int i = 0; i < MODES.length; i++) {
            float angle = sliceStart(i);
            drawLine(guiGraphics, centerX, centerY, centerX + Math.round(Mth.cos(angle) * radius), centerY + Math.round(Mth.sin(angle) * radius), 3, 0xC9E43A16);
            drawArc(guiGraphics, centerX, centerY, radius, sliceStart(i), sliceEnd(i), 2, 0xA87A0F55);
        }
        for (int i = 0; i < 24; i++) {
            float angle = age * 0.02F + Mth.TWO_PI * i / 24.0F;
            int inner = radius - 20 - (i % 4) * 4;
            int outer = radius - 7;
            drawLine(guiGraphics,
                    centerX + Math.round(Mth.cos(angle) * inner),
                    centerY + Math.round(Mth.sin(angle) * inner),
                    centerX + Math.round(Mth.cos(angle + 0.05F) * outer),
                    centerY + Math.round(Mth.sin(angle + 0.05F) * outer),
                    1,
                    i % 2 == 0 ? 0x77E43A16 : 0x665A083A);
        }
    }

    private static void drawHub(GuiGraphics guiGraphics, int centerX, int centerY, float age) {
        drawCircle(guiGraphics, centerX, centerY, 18, 3, 0xE8E43A16);
        drawCircle(guiGraphics, centerX, centerY, 10, 2, 0xB77A0F55);
        for (int i = 0; i < 8; i++) {
            float angle = -age * 0.04F + Mth.TWO_PI * i / 8.0F;
            drawLine(guiGraphics, centerX, centerY,
                    centerX + Math.round(Mth.cos(angle) * 16.0F),
                    centerY + Math.round(Mth.sin(angle) * 16.0F),
                    1,
                    0xAAFF6A39);
        }
    }

    private static void drawSliceFill(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int index, int color) {
        float start = sliceStart(index);
        float end = sliceEnd(index);
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            float angle = start + (end - start) * i / steps;
            drawLine(guiGraphics, centerX, centerY,
                    centerX + Math.round(Mth.cos(angle) * radius),
                    centerY + Math.round(Mth.sin(angle) * radius),
                    3,
                    color);
        }
    }

    private static void drawCircle(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int thickness, int color) {
        drawArc(guiGraphics, centerX, centerY, radius, 0.0F, Mth.TWO_PI, thickness, color);
    }

    private static void drawArc(GuiGraphics guiGraphics, int centerX, int centerY, int radius, float start, float end, int thickness, int color) {
        int steps = Math.max(10, Math.round(Math.abs(end - start) * radius / 5.0F));
        int lastX = centerX + Math.round(Mth.cos(start) * radius);
        int lastY = centerY + Math.round(Mth.sin(start) * radius);
        for (int i = 1; i <= steps; i++) {
            float angle = start + (end - start) * i / steps;
            int x = centerX + Math.round(Mth.cos(angle) * radius);
            int y = centerY + Math.round(Mth.sin(angle) * radius);
            drawLine(guiGraphics, lastX, lastY, x, y, thickness, color);
            lastX = x;
            lastY = y;
        }
    }

    private static void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            guiGraphics.fill(x1 - thickness, y1 - thickness, x1 + thickness + 1, y1 + thickness + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i += Math.max(1, thickness)) {
            int x = x1 + Math.round(dx * (i / (float) steps));
            int y = y1 + Math.round(dy * (i / (float) steps));
            guiGraphics.fill(x - thickness / 2, y - thickness / 2, x + thickness / 2 + 1, y + thickness / 2 + 1, color);
        }
    }

    private static float sliceStart(int index) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / MODES.length - Mth.PI / MODES.length;
    }

    private static float sliceCenter(int index) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / MODES.length;
    }

    private static float sliceEnd(int index) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / MODES.length + Mth.PI / MODES.length;
    }

    private static void resetSlot(int slot) {
        if (slot >= 0 && slot < WAS_DOWN.length) {
            WAS_DOWN[slot] = false;
            if (activeSlot == slot) {
                activeSlot = -1;
            }
        }
    }

    private static void resetWheel() {
        if (activeSlot == WHEEL_SLOT) {
            activeSlot = -1;
        }
        wheelConfirmWasDown = false;
    }
}
