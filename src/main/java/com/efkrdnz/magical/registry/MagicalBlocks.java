package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.block.AstralGateBlock;
import com.efkrdnz.magical.block.AstralStepSlabBlock;
import com.efkrdnz.magical.block.SacrificialCoreBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MagicalMod.MODID);

    public static final DeferredBlock<AstralStepSlabBlock> ASTRAL_STEP_SLAB = BLOCKS.registerBlock(
            "astral_step_slab",
            AstralStepSlabBlock::new,
            BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "astral_step_slab")))
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false));

    public static final DeferredBlock<AstralGateBlock> ASTRAL_GATE = BLOCKS.registerBlock(
            "astral_gate",
            AstralGateBlock::new,
            BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "astral_gate")))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 13)
                    .noOcclusion()
                    .noCollission()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false));

    public static final DeferredBlock<SacrificialCoreBlock> SACRIFICIAL_CORE = BLOCKS.registerBlock(
            "sacrificial_core",
            SacrificialCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sacrificial_core")))
                    .mapColor(MapColor.NETHER)
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 9)
                    .noOcclusion()
                    .noCollission()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false));

    private MagicalBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
