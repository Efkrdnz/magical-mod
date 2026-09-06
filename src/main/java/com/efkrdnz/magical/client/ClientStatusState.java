package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.network.StatusSyncPayload;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client mirror of the local player's synced statuses (input locks, HUD cues). */
public final class ClientStatusState {
    private static final Map<MagicStatus, int[]> ACTIVE = new EnumMap<>(MagicStatus.class); // [remainingTicks, amplifier]
    private static final Map<MagicStatus, Float> VALUES = new EnumMap<>(MagicStatus.class);
    private static float lockedYaw;
    private static float lockedPitch;
    private static boolean lockCaptured;

    private ClientStatusState() {}

    public static void handle(StatusSyncPayload payload) {
        MagicStatus[] values = MagicStatus.values();
        if (payload.status() < 0 || payload.status() >= values.length) {
            return;
        }
        MagicStatus status = values[payload.status()];
        if (payload.ticks() <= 0) {
            ACTIVE.remove(status);
            VALUES.remove(status);
            if (status == MagicStatus.FACING_PINNED) {
                lockCaptured = false;
            }
            return;
        }
        ACTIVE.put(status, new int[] {payload.ticks(), payload.amplifier()});
        VALUES.put(status, payload.value());
        if (status == MagicStatus.FACING_PINNED) {
            lockCaptured = false;
        }
    }

    public static boolean has(MagicStatus status) {
        return ACTIVE.containsKey(status);
    }

    public static float value(MagicStatus status) {
        return VALUES.getOrDefault(status, 0.0F);
    }

    public static void tick(Minecraft minecraft) {
        ACTIVE.entrySet().removeIf(e -> --e.getValue()[0] <= 0);
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lockCaptured = false;
            return;
        }
        if (has(MagicStatus.FACING_PINNED)) {
            if (!lockCaptured) {
                lockedYaw = player.getYRot();
                lockedPitch = player.getXRot();
                lockCaptured = true;
            }
            player.setYRot(lockedYaw);
            player.setXRot(lockedPitch);
            player.yHeadRot = lockedYaw;
        } else {
            lockCaptured = false;
        }
    }

    public static void clear() {
        ACTIVE.clear();
        VALUES.clear();
        lockCaptured = false;
    }
}
