package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.network.CooldownSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

/**
 * Client mirror of the local player's skill cooldowns, fed by {@link CooldownSyncPayload}.
 *
 * <p>{@code PlayerMagicState.cooldown(slot)} is always zero on the client - cooldowns are not in
 * the synced blob - so this is the only place the HUD can ask. Times are game-time ticks of the
 * current level; the map is reset when the level changes, checked in {@link #accept} as well as
 * {@link #tick} because the full list after a dimension change arrives before the next tick would
 * notice the new level.
 */
public final class ClientCooldowns {
    private static final CooldownClock CLOCK = new CooldownClock();
    private static ClientLevel lastLevel;

    private ClientCooldowns() {}

    public static void accept(CooldownSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureLevel(minecraft.level);
        CLOCK.accept(payload, now(minecraft));
    }

    public static void tick(Minecraft minecraft) {
        ensureLevel(minecraft.level);
        if (minecraft.level != null) {
            CLOCK.tick(now(minecraft));
        }
    }

    public static int remaining(ResourceLocation id) {
        return CLOCK.remaining(id, now(Minecraft.getInstance()));
    }

    public static float fraction(ResourceLocation id, float partialTick) {
        return CLOCK.fraction(id, now(Minecraft.getInstance()) + partialTick);
    }

    public static boolean isOnCooldown(ResourceLocation id) {
        return CLOCK.isOnCooldown(id, now(Minecraft.getInstance()));
    }

    public static boolean justFinished(ResourceLocation id) {
        return CLOCK.justFinished(id);
    }

    public static CooldownClock.Entry entry(ResourceLocation id) {
        return CLOCK.entry(id);
    }

    public static int version() {
        return CLOCK.version();
    }

    public static void reset() {
        CLOCK.reset();
        lastLevel = null;
    }

    private static void ensureLevel(ClientLevel level) {
        if (level != lastLevel) {
            CLOCK.reset();
            lastLevel = level;
        }
    }

    private static long now(Minecraft minecraft) {
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
