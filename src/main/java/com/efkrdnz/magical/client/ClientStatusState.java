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
    private static int version;

    /** A visitor over the active statuses, so the HUD can read them without boxing or a list. */
    @FunctionalInterface
    public interface StatusVisitor {
        void visit(MagicStatus status, int remainingTicks, int amplifier, float value);
    }

    private ClientStatusState() {}

    public static void handle(StatusSyncPayload payload) {
        MagicStatus[] values = MagicStatus.values();
        if (payload.status() < 0 || payload.status() >= values.length) {
            return;
        }
        MagicStatus status = values[payload.status()];
        version++;
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

    public static int remainingTicks(MagicStatus status) {
        int[] entry = ACTIVE.get(status);
        return entry == null ? 0 : Math.max(0, entry[0]);
    }

    public static int amplifier(MagicStatus status) {
        int[] entry = ACTIVE.get(status);
        return entry == null ? 0 : entry[1];
    }

    public static void forEachActive(StatusVisitor visitor) {
        for (Map.Entry<MagicStatus, int[]> entry : ACTIVE.entrySet()) {
            visitor.visit(entry.getKey(), entry.getValue()[0], entry.getValue()[1], VALUES.getOrDefault(entry.getKey(), 0.0F));
        }
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** Bumped when a status arrives, ends or is cleared; the HUD rebuilds its chips on it. */
    public static int version() {
        return version;
    }

    public static void tick(Minecraft minecraft) {
        int before = ACTIVE.size();
        ACTIVE.entrySet().removeIf(e -> --e.getValue()[0] <= 0);
        if (ACTIVE.size() != before) {
            version++;
        }
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
        version++;
    }
}
