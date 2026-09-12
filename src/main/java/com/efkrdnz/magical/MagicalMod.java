package com.efkrdnz.magical;

import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalBlockEntities;
import com.efkrdnz.magical.registry.MagicalBlocks;
import com.efkrdnz.magical.registry.MagicalChunkTickets;
import com.efkrdnz.magical.registry.MagicalCommands;
import com.efkrdnz.magical.registry.MagicalCreativeTabs;
import com.efkrdnz.magical.registry.MagicalDataComponents;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.registry.MagicalItems;
import com.efkrdnz.magical.registry.MagicalMenus;
import com.efkrdnz.magical.registry.MagicalSounds;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(MagicalMod.MODID)
public final class MagicalMod {
    public static final String MODID = "magical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MagicalMod(IEventBus modEventBus, ModContainer modContainer) {
        MagicalAttachments.register(modEventBus);
        MagicalDataComponents.register(modEventBus);
        MagicalBlocks.register(modEventBus);
        MagicalBlockEntities.register(modEventBus);
        MagicalItems.register(modEventBus);
        MagicalEntities.register(modEventBus);
        MagicalMenus.register(modEventBus);
        MagicalCreativeTabs.register(modEventBus);
        MagicalCommands.register(modEventBus);
        MagicalSounds.register(modEventBus);
        modEventBus.addListener(MagicalChunkTickets::registerTicketControllers);
        modEventBus.addListener(MagicalNetwork::registerPayloads);
        modEventBus.addListener(com.efkrdnz.magical.magic.visual.MagicVisualContent::onCommonSetup);
        modEventBus.addListener(com.efkrdnz.magical.magic.cast.MagicCastContent::onCommonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, MagicalConfig.SPEC);
    }
}
