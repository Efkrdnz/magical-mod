package com.efkrdnz.magical.magic.cast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side "is the slot key still held" state for holdable skills (sustain / steer). The client
 * sends CastHoldPayload(slot, held) on press and release; entries expire if no refresh arrives.
 */
public final class HoldService {
    private static final Map<UUID, long[]> HELD = new HashMap<>();
    private static final int SLOTS = com.efkrdnz.magical.magic.MagicContent.LOADOUT_SIZE;
    private static final long TIMEOUT_TICKS = 40L;

    private HoldService() {}

    public static void setHeld(ServerPlayer player, int slot, boolean held) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        long[] slots = HELD.computeIfAbsent(player.getUUID(), k -> new long[SLOTS]);
        slots[slot] = held ? player.serverLevel().getGameTime() + TIMEOUT_TICKS : 0L;
    }

    public static boolean isHeld(ServerPlayer player, int slot) {
        long[] slots = HELD.get(player.getUUID());
        if (slots == null || slot < 0 || slot >= SLOTS) {
            return false;
        }
        return slots[slot] > player.serverLevel().getGameTime();
    }

    /** True while any slot equipped with the given skill is held. */
    public static boolean isHeldSkill(ServerPlayer player, net.minecraft.resources.ResourceLocation skillId) {
        var state = player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
        for (int i = 0; i < SLOTS; i++) {
            if (skillId.equals(state.equippedSkill(i)) && isHeld(player, i)) {
                return true;
            }
        }
        return false;
    }

    public static void clear(ServerPlayer player) {
        HELD.remove(player.getUUID());
    }
}
