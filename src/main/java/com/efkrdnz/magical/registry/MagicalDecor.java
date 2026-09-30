package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.block.decor.DecorKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The decorative blocks: every {@link DecorKind} in every dye colour, 128 blocks and their items.
 *
 * <p>Registered in a loop, like the armoury, because a field apiece would be 128 lines that say the
 * same thing and a second list to keep in step with the kinds. Look one up with {@link #block}.
 */
public final class MagicalDecor {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MagicalMod.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagicalMod.MODID);

    private static final List<DyeColor> COLOURS = DecorKind.COLOURS;

    private static final Map<DecorKind, Map<DyeColor, DeferredBlock<Block>>> BLOCK_TABLE = new EnumMap<>(DecorKind.class);
    private static final List<DeferredItem<BlockItem>> ITEMS_IN_ORDER = new ArrayList<>();

    static {
        for (DecorKind kind : DecorKind.values()) {
            Map<DyeColor, DeferredBlock<Block>> row = new EnumMap<>(DyeColor.class);
            for (DyeColor colour : COLOURS) {
                DeferredBlock<Block> block = BLOCKS.registerBlock(kind.path(colour),
                        properties -> kind.create(colour, properties), kind.properties(colour));
                row.put(colour, block);
                ITEMS_IN_ORDER.add(ITEMS.registerSimpleBlockItem(block));
            }
            BLOCK_TABLE.put(kind, row);
        }
    }

    private MagicalDecor() {}

    public static DeferredBlock<Block> block(DecorKind kind, DyeColor colour) {
        return BLOCK_TABLE.get(kind).get(colour);
    }

    /** Every decor item, kind by kind and each kind in {@link DecorKind#COLOURS} order: the tab's order. */
    public static List<DeferredItem<BlockItem>> items() {
        return Collections.unmodifiableList(ITEMS_IN_ORDER);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(MagicalDecor::commonSetup);
    }

    /**
     * Fire's table lives on the fire block and is filled at bootstrap, before any mod block exists,
     * so cloth and planks join it here - with vanilla wool's and planks' own numbers.
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            for (DecorKind kind : DecorKind.values()) {
                if (!kind.flammable()) {
                    continue;
                }
                for (DyeColor colour : COLOURS) {
                    fire.setFlammable(block(kind, colour).get(), kind.encouragement(), kind.flammability());
                }
            }
        });
    }
}
