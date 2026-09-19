package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.chaos.Fault;
import com.efkrdnz.magical.magic.chaos.Fracture;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Five gates in a row, read left to right, and the only thing the Authority of Chaos authors.
 *
 * <p>Deliberately <em>not</em> dials. Manipulate Space is three scrolling columns; another ring of
 * wheels would make two Authorities look like one Authority with two vocabularies, which is the
 * complaint this whole system exists to answer. A Fracture is a sequence, so it is drawn as one:
 * position one is what the first generation of an avalanche does, position five is what every
 * generation after the fifth keeps doing.
 */
public final class FractureOverlay {

    private static final int CHAOS = 0xFF4FD8;
    private static final int GATE_WIDTH = 86;
    private static final int GATE_HEIGHT = 46;
    private static final int GAP = 8;

    private static boolean active;
    private static int focus;
    private static final int[] SELECTED = new int[Fracture.LENGTH];

    private FractureOverlay() {}

    public static void begin() {
        active = true;
        focus = 0;
        Fracture held = ClientMagicState.get().fracture();
        for (int i = 0; i < Fracture.LENGTH; i++) {
            SELECTED[i] = held.get(i).ordinal();
        }
    }

    /** Releasing commits the whole sequence at once, so a half-authored Fracture never exists. */
    public static void finish() {
        if (active) {
            MagicalNetwork.sendFracture(SELECTED);
        }
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static boolean handleScroll(double delta) {
        if (!active || delta == 0.0D) {
            return active;
        }
        int step = delta > 0.0D ? -1 : 1;
        SELECTED[focus] = Math.floorMod(SELECTED[focus] + step, Fault.values().length);
        return true;
    }

    /** Left and right mouse walk the sequence, the way they walk the dials elsewhere. */
    public static boolean handleMouseButton(int button, int action) {
        if (!active || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            focus = Math.floorMod(focus - 1, Fracture.LENGTH);
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            focus = Math.floorMod(focus + 1, Fracture.LENGTH);
            return true;
        }
        return false;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!active) {
            return;
        }
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x7614060F);
        int total = Fracture.LENGTH * GATE_WIDTH + (Fracture.LENGTH - 1) * GAP;
        int left = (guiGraphics.guiWidth() - total) / 2;
        int top = guiGraphics.guiHeight() / 2 - GATE_HEIGHT / 2;

        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("fracture.magical.title"),
                guiGraphics.guiWidth() / 2, top - 40, CHAOS);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("fracture.magical.subtitle"),
                guiGraphics.guiWidth() / 2, top - 28, 0x9A7FA8);

        for (int i = 0; i < Fracture.LENGTH; i++) {
            int x = left + i * (GATE_WIDTH + GAP);
            boolean focused = i == focus;
            Fault fault = Fault.values()[SELECTED[i]];
            guiGraphics.fill(x, top, x + GATE_WIDTH, top + GATE_HEIGHT, focused ? 0xE0160A16 : 0xA0160A16);
            guiGraphics.fill(x, top, x + GATE_WIDTH, top + 1, focused ? 0xFFFF4FD8 : 0x60FF4FD8);
            guiGraphics.fill(x, top + GATE_HEIGHT - 1, x + GATE_WIDTH, top + GATE_HEIGHT,
                    focused ? 0xFFFF4FD8 : 0x60FF4FD8);
            guiGraphics.drawCenteredString(minecraft.font,
                    Component.translatable("fracture.magical.generation", i + 1),
                    x + GATE_WIDTH / 2, top + 6, focused ? 0xE4C8E8 : 0x6E5A78);
            guiGraphics.drawCenteredString(minecraft.font, Component.translatable(fault.translationKey()),
                    x + GATE_WIDTH / 2, top + 20, focused ? 0xFFFFFF : 0xC9A8D4);
            if (i < Fracture.LENGTH - 1) {
                guiGraphics.drawString(minecraft.font, ">", x + GATE_WIDTH + 1, top + 20, 0x7A5C86, false);
            }
        }

        guiGraphics.drawCenteredString(minecraft.font,
                Component.translatable(Fault.values()[SELECTED[focus]].translationKey() + ".desc"),
                guiGraphics.guiWidth() / 2, top + GATE_HEIGHT + 12, 0xCBB2D6);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("fracture.magical.release_hint"),
                guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() - 30, 0x7F6A88);
    }
}
