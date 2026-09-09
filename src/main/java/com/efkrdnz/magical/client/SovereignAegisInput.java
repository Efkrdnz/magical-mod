package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class SovereignAegisInput {
    private static final ResourceLocation[] MODES = {
            MagicContent.AEGIS_ULTIMATE_PROTECTION.id(),
            MagicContent.AEGIS_SANCTUARY.id(),
            MagicContent.AEGIS_PERFECT_SEAL.id()
    };
    private static final ResourceLocation[] GABRIEL_MODES = {
            MagicContent.GABRIEL_ULTIMATE_PROTECTION.id(),
            MagicContent.GABRIEL_JUDGEMENT.id(),
            MagicContent.GABRIEL_PERFECT_SEAL.id(),
            MagicContent.GABRIEL_HOLY_FIELD.id()
    };
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];
    private static int activeSlot = -1;
    private static int selectedMode;
    private static float fade;
    private static ResourceLocation wheelParentId;

    private SovereignAegisInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        if (!isSupportedParent(skillId)) {
            resetSlot(slot);
            return false;
        }
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (down && !WAS_DOWN[slot]) {
            activeSlot = slot;
            fade = 0.0F;
        } else if (!down && WAS_DOWN[slot]) {
            MagicalNetwork.sendSovereignAegisCast(slot, selectedMode);
            resetSlot(slot);
            return true;
        }
        if (down) {
            activeSlot = slot;
            selectedMode = Math.floorMod(selectedMode, modesFor(skillId).length);
            fade = Math.min(1.0F, fade + 0.18F);
        }
        WAS_DOWN[slot] = down;
        return true;
    }

    public static boolean handleScroll(double delta) {
        if (activeSlot == -1) {
            return false;
        }
        ResourceLocation skillId = activeParentId();
        ResourceLocation[] modes = modesFor(skillId);
        if (delta > 0.0D) {
            selectedMode = Math.floorMod(selectedMode - 1, modes.length);
        } else if (delta < 0.0D) {
            selectedMode = Math.floorMod(selectedMode + 1, modes.length);
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
        ResourceLocation parentId = activeParentId();
        ResourceLocation[] modes = modesFor(parentId);
        selectedMode = Math.floorMod(selectedMode, modes.length);
        drawPizzaWheel(guiGraphics, centerX, centerY, radius, age, modes.length);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable(MagicContent.get(parentId).nameKey()), centerX, centerY - radius - 22, MagicContent.GABRIEL.id().equals(parentId) ? 0xFFD700 : 0xFFF7D7);
        for (int i = 0; i < modes.length; i++) {
            float angle = sliceCenter(i, modes.length);
            int x = centerX + Math.round(Mth.cos(angle) * radius * 0.56F);
            int y = centerY + Math.round(Mth.sin(angle) * radius * 0.56F);
            boolean selected = i == selectedMode;
            if (selected) {
                drawSliceFill(guiGraphics, centerX, centerY, radius - 6, i, modes.length, 0x55FFF4B2);
                drawArc(guiGraphics, centerX, centerY, radius + 3, sliceStart(i, modes.length), sliceEnd(i, modes.length), 4, 0xE8FFF0B5);
            }
            guiGraphics.drawCenteredString(minecraft.font, Component.translatable(MagicContent.get(modes[i]).nameKey()), x, y - 4, selected ? 0xFFF7C6 : 0xD8E9FF);
        }
        drawHub(guiGraphics, centerX, centerY, age);
        guiGraphics.drawCenteredString(minecraft.font, Component.literal("Mouse wheel selects, release to cast"), centerX, centerY + radius + 14, 0xBFD7FF);
    }

    private static void drawPizzaWheel(GuiGraphics guiGraphics, int centerX, int centerY, int radius, float age, int modeCount) {
        guiGraphics.fill(centerX - radius - 24, centerY - radius - 34, centerX + radius + 24, centerY + radius + 30, 0x72040A14);
        for (int i = 0; i < modeCount; i++) {
            drawSliceFill(guiGraphics, centerX, centerY, radius - 4, i, modeCount, i == selectedMode ? 0x44EACB66 : 0x33102038);
        }
        drawCircle(guiGraphics, centerX, centerY, radius, 3, 0xD6D6ECFF);
        drawCircle(guiGraphics, centerX, centerY, radius - 16, 1, 0x88FFF4B2);
        drawCircle(guiGraphics, centerX, centerY, radius * 2 / 5, 1, 0x6678E8FF);
        for (int i = 0; i < modeCount; i++) {
            float angle = sliceStart(i, modeCount);
            drawLine(guiGraphics, centerX, centerY, centerX + Math.round(Mth.cos(angle) * radius), centerY + Math.round(Mth.sin(angle) * radius), 3, 0xC9D6ECFF);
            drawArc(guiGraphics, centerX, centerY, radius, sliceStart(i, modeCount), sliceEnd(i, modeCount), 2, 0xA8FFF4B2);
        }
        for (int i = 0; i < 18; i++) {
            float angle = age * 0.015F + Mth.TWO_PI * i / 18.0F;
            int inner = radius - 18 - (i % 3) * 6;
            int outer = radius - 8;
            drawLine(guiGraphics,
                    centerX + Math.round(Mth.cos(angle) * inner),
                    centerY + Math.round(Mth.sin(angle) * inner),
                    centerX + Math.round(Mth.cos(angle + 0.05F) * outer),
                    centerY + Math.round(Mth.sin(angle + 0.05F) * outer),
                    1,
                    i % 2 == 0 ? 0x66FFF4B2 : 0x5578E8FF);
        }
    }

    private static void drawHub(GuiGraphics guiGraphics, int centerX, int centerY, float age) {
        drawCircle(guiGraphics, centerX, centerY, 18, 3, 0xE8FFF4B2);
        drawCircle(guiGraphics, centerX, centerY, 10, 2, 0xB778E8FF);
        for (int i = 0; i < 6; i++) {
            float angle = age * 0.035F + Mth.TWO_PI * i / 6.0F;
            drawLine(guiGraphics, centerX, centerY,
                    centerX + Math.round(Mth.cos(angle) * 15.0F),
                    centerY + Math.round(Mth.sin(angle) * 15.0F),
                    1,
                    0xAAFFFFFF);
        }
    }

    private static void drawSliceFill(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int index, int modeCount, int color) {
        float start = sliceStart(index, modeCount);
        float end = sliceEnd(index, modeCount);
        int steps = 28;
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

    private static float sliceStart(int index, int modeCount) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / modeCount - Mth.PI / modeCount;
    }

    private static float sliceCenter(int index, int modeCount) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / modeCount;
    }

    private static float sliceEnd(int index, int modeCount) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / modeCount + Mth.PI / modeCount;
    }

    private static boolean isSupportedParent(ResourceLocation skillId) {
        return MagicContent.SOVEREIGN_AEGIS.id().equals(skillId) || MagicContent.GABRIEL.id().equals(skillId);
    }

    private static ResourceLocation[] modesFor(ResourceLocation skillId) {
        return MagicContent.GABRIEL.id().equals(skillId) ? GABRIEL_MODES : MODES;
    }

    private static ResourceLocation activeParentId() {
        return ClientMagicState.get().equippedSkill(activeSlot);
    }

    private static void resetSlot(int slot) {
        if (slot >= 0 && slot < WAS_DOWN.length) {
            WAS_DOWN[slot] = false;
            if (activeSlot == slot) {
                activeSlot = -1;
            }
        }
    }

    /**
     * Abandon whatever this handler was charging.
     *
     * <p>Called when the player switches loadout. Charge state is keyed by slot number, so without
     * this a swap mid-charge would leave slot two still charging the old loadout skill while the
     * key now points at a different one - you would charge one spell and release another.
     */
    public static void cancel() {
        activeSlot = -1;
        fade = 0.0F;
    }
}
