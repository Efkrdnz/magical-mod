package com.efkrdnz.magical.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class MagicalCreativeTabs {
    private MagicalCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MagicalCreativeTabs::addToVanillaTabs);
    }

    private static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(MagicalItems.ASTRAL_STEP_SLAB);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MagicalItems.ASTRAL_GATE);
            event.accept(MagicalItems.SACRIFICIAL_CORE);
        }
    }
}
