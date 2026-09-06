package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.block.AstralGateBlockEntity;
import com.efkrdnz.magical.block.SacrificialCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MagicalMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AstralGateBlockEntity>> ASTRAL_GATE = BLOCK_ENTITIES.register(
            "astral_gate",
            () -> new BlockEntityType<>(AstralGateBlockEntity::new, MagicalBlocks.ASTRAL_GATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SacrificialCoreBlockEntity>> SACRIFICIAL_CORE = BLOCK_ENTITIES.register(
            "sacrificial_core",
            () -> new BlockEntityType<>(SacrificialCoreBlockEntity::new, MagicalBlocks.SACRIFICIAL_CORE.get()));

    private MagicalBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
