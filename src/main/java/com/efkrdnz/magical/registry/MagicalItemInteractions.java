package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MagicalItemInteractions {
    private MagicalItemInteractions() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // The mod no longer exposes gameplay items; keybinds and commands drive the UI.
    }
}
