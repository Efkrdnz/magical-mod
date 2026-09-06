package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagicalMod.MODID);

    public static final DeferredItem<BlockItem> ASTRAL_STEP_SLAB = ITEMS.registerSimpleBlockItem(
            MagicalBlocks.ASTRAL_STEP_SLAB,
            new Item.Properties());

    public static final DeferredItem<BlockItem> ASTRAL_GATE = ITEMS.registerSimpleBlockItem(
            MagicalBlocks.ASTRAL_GATE,
            new Item.Properties());

    public static final DeferredItem<BlockItem> SACRIFICIAL_CORE = ITEMS.registerSimpleBlockItem(
            MagicalBlocks.SACRIFICIAL_CORE,
            new Item.Properties());

    private MagicalItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
