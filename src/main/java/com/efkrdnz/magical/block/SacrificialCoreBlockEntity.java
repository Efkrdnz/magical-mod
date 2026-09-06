package com.efkrdnz.magical.block;

import com.efkrdnz.magical.registry.MagicalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SacrificialCoreBlockEntity extends BlockEntity {
    public SacrificialCoreBlockEntity(BlockPos pos, BlockState state) {
        super(MagicalBlockEntities.SACRIFICIAL_CORE.get(), pos, state);
    }
}
