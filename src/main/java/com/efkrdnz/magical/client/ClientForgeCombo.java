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

    private ClientForgeCombo() {}

    public static void accept(ForgeComboSyncPayload payload) {
        comboIndex = payload.comboIndex();
        chainLength = payload.chainLength();
        windowTicksLeft = payload.windowTicksLeft();
        readyInTicks = payload.readyInTicks();
        elementColor = payload.elementColor();
        updatedAtTick = currentTick();
    }

    public static void clear() {
        comboIndex = 0;
        chainLength = 0;
        windowTicksLeft = 0;
        readyInTicks = 0;
        elementColor = UNKNOWN_COLOR;
        updatedAtTick = 0L;
        // The HUD remembers a window peak and a hold across frames; both are derived from what is
        // being cleared here, so they have to go with it or the drain bar reopens partly drained.
        ForgeComboHud.reset();
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

    /** Client game time of the last sync, so the HUD can age the pips out between packets. */
    public static long updatedAtTick() {
        return updatedAtTick;
    }

    private static long currentTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
