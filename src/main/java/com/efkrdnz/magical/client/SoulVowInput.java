package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class SoulVowInput {
    private static final int ACTION_BIND = 0;
    private static final int ACTION_SWAP = 100;
    private static final int ACTION_CALL = 101;
    private static final int ACTION_SEVER = 102;
    private static final int ACTION_STEP = 103;
    private static final int[] BOND_ACTIONS = {
            ACTION_SWAP,
            ACTION_CALL,
            ACTION_SEVER,
            ACTION_STEP
    };
    private static final Component[] BOND_ACTION_NAMES = {
            Component.translatable("screen.magical.soul_action_swap"),
            Component.translatable("screen.magical.soul_action_call"),
            Component.translatable("screen.magical.soul_action_sever"),
            Component.translatable("screen.magical.soul_action_step")
    };
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];
    private static int activeSlot = -1;
    private static int selectedAction;
    private static float fade;

    private SoulVowInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean valid = ClientMagicState.get().hasAuthority(AuthorityContent.SOUL) && MagicContent.SOUL_VOW.id().equals(skillId);
        if (!valid) {
            resetSlot(slot);
            return false;
        }
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (down && !WAS_DOWN[slot]) {
            if (ClientMagicState.get().hasSoulBond()) {
                activeSlot = slot;
                selectedAction = 0;
                fade = 0.0F;
            }
        } else if (!down && WAS_DOWN[slot]) {
            MagicalNetwork.sendSoulVowCast(slot, activeSlot == slot ? BOND_ACTIONS[selectedAction] : ACTION_BIND);
            resetSlot(slot);
            return true;
        }
        if (down && activeSlot == slot) {
            if (!ClientMagicState.get().hasSoulBond()) {
                resetSlot(slot);
                WAS_DOWN[slot] = true;
                return true;
            }
            activeSlot = slot;
            fade = Math.min(1.0F, fade + 0.18F);
        }
        WAS_DOWN[slot] = down;
        return true;
    }

    public static boolean handleScroll(double delta) {
        if (activeSlot == -1 || BOND_ACTIONS.length <= 1) {
            return activeSlot != -1;
        }
        if (delta > 0.0D) {
            selectedAction = Math.floorMod(selectedAction - 1, BOND_ACTIONS.length);
        } else if (delta < 0.0D) {
            selectedAction = Math.floorMod(selectedAction + 1, BOND_ACTIONS.length);
        }
        return true;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (activeSlot == -1 || minecraft.player == null || !ClientMagicState.get().hasSoulBond()) {
            fade = Math.max(0.0F, fade - 0.12F);
            return;
        }
        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2 + 72;
        int radius = 84;
        float age = minecraft.player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawWheel(guiGraphics, centerX, centerY, radius, age);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable(MagicContent.SOUL_VOW.nameKey()), centerX, centerY - radius - 24, 0xD8F0FF);
        for (int i = 0; i < BOND_ACTIONS.length; i++) {
            float angle = sliceCenter(i);
            int x = centerX + Math.round(Mth.cos(angle) * radius * 0.55F);
            int y = centerY + Math.round(Mth.sin(angle) * radius * 0.55F);
            boolean selected = i == selectedAction;
            if (selected) {
                drawSliceFill(guiGraphics, centerX, centerY, radius - 6, i, 0x664FE3FF);
                drawArc(guiGraphics, centerX, centerY, radius + 4, sliceStart(i), sliceEnd(i), 4, 0xE8D8F0FF);
            }
            guiGraphics.drawCenteredString(minecraft.font, BOND_ACTION_NAMES[i], x, y - 5, selected ? 0xF4FDFF : 0xC9E6F5);
        }
        drawCircle(guiGraphics, centerX, centerY, 18, 3, 0xE8D8F0FF);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("screen.magical.soul_wheel_hint"), centerX, centerY + radius + 14, 0xC9E6F5);
    }

    private static void drawWheel(GuiGraphics guiGraphics, int centerX, int centerY, int radius, float age) {
        guiGraphics.fill(centerX - radius - 26, centerY - radius - 36, centerX + radius + 26, centerY + radius + 32, 0x76101C24);
        for (int i = 0; i < BOND_ACTIONS.length; i++) {
            drawSliceFill(guiGraphics, centerX, centerY, radius - 4, i, i == selectedAction ? 0x4438CFE8 : 0x3314252F);
        }
        drawCircle(guiGraphics, centerX, centerY, radius, 3, 0xD6D8F0FF);
        drawCircle(guiGraphics, centerX, centerY, radius - 17, 1, 0x884FE3FF);
        for (int i = 0; i < BOND_ACTIONS.length; i++) {
            float angle = sliceStart(i);
            drawLine(guiGraphics, centerX, centerY, centerX + Math.round(Mth.cos(angle) * radius), centerY + Math.round(Mth.sin(angle) * radius), 3, 0xC9D8F0FF);
        }
        for (int i = 0; i < 18; i++) {
            float angle = age * 0.016F + Mth.TWO_PI * i / 18.0F;
            drawLine(guiGraphics,
                    centerX + Math.round(Mth.cos(angle) * (radius - 28)),
                    centerY + Math.round(Mth.sin(angle) * (radius - 28)),
                    centerX + Math.round(Mth.cos(angle + 0.05F) * (radius - 8)),
                    centerY + Math.round(Mth.sin(angle + 0.05F) * (radius - 8)),
                    1,
                    i % 2 == 0 ? 0x77D8F0FF : 0x554FE3FF);
        }
    }

    private static void drawSliceFill(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int index, int color) {
        float start = sliceStart(index);
        float end = sliceEnd(index);
        for (int i = 0; i <= 36; i++) {
            float angle = start + (end - start) * i / 36.0F;
            drawLine(guiGraphics, centerX, centerY, centerX + Math.round(Mth.cos(angle) * radius), centerY + Math.round(Mth.sin(angle) * radius), 3, color);
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
        for (int i = 0; i <= steps; i += Math.max(1, thickness)) {
            int x = x1 + Math.round(dx * (i / (float) Math.max(1, steps)));
            int y = y1 + Math.round(dy * (i / (float) Math.max(1, steps)));
            guiGraphics.fill(x - thickness / 2, y - thickness / 2, x + thickness / 2 + 1, y + thickness / 2 + 1, color);
        }
    }

    private static float sliceStart(int index) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / BOND_ACTIONS.length - Mth.PI / BOND_ACTIONS.length;
    }

    private static float sliceCenter(int index) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / BOND_ACTIONS.length;
    }

    private static float sliceEnd(int index) {
        return -Mth.HALF_PI + Mth.TWO_PI * index / BOND_ACTIONS.length + Mth.PI / BOND_ACTIONS.length;
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
