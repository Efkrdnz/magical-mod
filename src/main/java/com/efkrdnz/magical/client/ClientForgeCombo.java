package com.efkrdnz.magical.client;

import com.efkrdnz.magical.network.ForgeComboSyncPayload;

import net.minecraft.client.Minecraft;

/**
 * Client mirror of the server's combo state for the wielded forged weapon. Replaced wholesale on
 * every {@link ForgeComboSyncPayload}, exactly like {@code ClientMagicState}; nothing here is
 * authoritative, it only feeds the HUD.
 */
public final class ClientForgeCombo {

    private static final int UNKNOWN_COLOR = 0xD8E4FF;

    private static int comboIndex;
    private static int chainLength;
    private static int windowTicksLeft;
    private static int readyInTicks;
    private static int elementColor = UNKNOWN_COLOR;
    private static long updatedAtTick;
    /**
     * The window this chain started with, so the drain bar has something to drain from. Every sync
     * reports a freshly opened window at its full duration, never a mid-window update, so the
     * value at sync time is the peak.
     */
    private static int windowPeak;

    private ClientForgeCombo() {}

    public static void accept(ForgeComboSyncPayload payload) {
        comboIndex = payload.comboIndex();
        chainLength = payload.chainLength();
        windowTicksLeft = payload.windowTicksLeft();
        readyInTicks = payload.readyInTicks();
        elementColor = payload.elementColor();
        windowPeak = payload.windowTicksLeft();
        updatedAtTick = currentTick();
    }

    public static void clear() {
        comboIndex = 0;
        chainLength = 0;
        windowTicksLeft = 0;
        readyInTicks = 0;
        elementColor = UNKNOWN_COLOR;
        updatedAtTick = 0L;
        windowPeak = 0;
    }

    public static int comboIndex() {
        return comboIndex;
    }

    public static int chainLength() {
        return chainLength;
    }

    public static int windowTicksLeft() {
        return windowTicksLeft;
    }

    public static int readyInTicks() {
        return readyInTicks;
    }

    public static int elementColor() {
        return elementColor;
    }

    public static int windowPeak() {
        return windowPeak;
    }

    /** Client game time of the last sync, so the HUD can age the pips out between packets. */
    public static long updatedAtTick() {
        return updatedAtTick;
    }

    private static long currentTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
