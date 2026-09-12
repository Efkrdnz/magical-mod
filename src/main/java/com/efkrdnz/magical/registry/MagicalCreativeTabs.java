package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalCreativeTabs {

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MagicalMod.MODID);

    /**
     * The armoury: every catalogue weapon, in catalogue order, in a tab of its own.
     *
     * <p>Its own tab rather than a pile inside vanilla's COMBAT, because there are three dozen of
     * these and they would bury the vanilla swords under a wall of look-alike sprites.
     *
     * <p>Both the icon and the contents are resolved inside their suppliers rather than captured up
     * front. The tab object is built while the registries are still filling, and an item read any
     * earlier than the moment it is asked for would come back as air.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARMOURY = TABS.register("armoury",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.magical.armoury"))
                    .icon(() -> new ItemStack(MagicalItems.weapon(MagicalWeapons.STARFALL_BLADE.id()).get()))
                    .displayItems((parameters, output) -> MagicalItems.weapons()
                            .forEach(weapon -> output.accept(weapon.get())))
                    .build());

    private MagicalCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
        modEventBus.addListener(MagicalCreativeTabs::addToVanillaTabs);
    }

    private static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(MagicalItems.ASTRAL_STEP_SLAB);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MagicalItems.ASTRAL_GATE);
            event.accept(MagicalItems.SACRIFICIAL_CORE);
            event.accept(MagicalItems.TRAINING_DUMMY);
        }
    }
}
