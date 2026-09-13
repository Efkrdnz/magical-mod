package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.client.Minecraft;

public final class ClientMagicState {
    private static PlayerMagicState current = new PlayerMagicState();
    private static int version;
    private static long receivedAtTick;

    private ClientMagicState() {}

    public static PlayerMagicState get() {
        return current;
    }

    public static void set(PlayerMagicState data) {
        current = data.copy();
        version++;
        Minecraft minecraft = Minecraft.getInstance();
        receivedAtTick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    /** Bumped on every packet, so the HUD rebuilds its snapshot only when something arrived. */
    public static int version() {
        return version;
    }

    /**
     * Game time of the last packet. Tick counters in the state (the loadout swap lock) never count
     * down on the client, so anything that shows one extrapolates from here.
     */
    public static long receivedAtTick() {
        return receivedAtTick;
    }
}
