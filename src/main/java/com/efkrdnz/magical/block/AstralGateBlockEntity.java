package com.efkrdnz.magical.block;

import com.efkrdnz.magical.registry.MagicalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AstralGateBlockEntity extends BlockEntity {
    public AstralGateBlockEntity(BlockPos pos, BlockState state) {
        super(MagicalBlockEntities.ASTRAL_GATE.get(), pos, state);
    }
}
