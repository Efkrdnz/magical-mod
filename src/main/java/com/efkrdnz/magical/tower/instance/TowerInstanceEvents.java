package com.efkrdnz.magical.tower.instance;

import com.efkrdnz.magical.MagicalMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives the instance sweep and clears tracking on shutdown.
 *
 * <p>Kept in its own class so the framework wires itself up, rather than depending on a gameplay
 * class remembering to call it. Server-side only — nothing here touches client classes.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class TowerInstanceEvents {
    private TowerInstanceEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        InstanceManager.tick(event.getServer());
    }

    /**
     * Instance state is deliberately not persisted, so it is dropped rather than saved. Blocks
     * left behind in the world are handled by {@code /towerdev wipe} on the next run.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        InstanceManager.clear();
    }
}
