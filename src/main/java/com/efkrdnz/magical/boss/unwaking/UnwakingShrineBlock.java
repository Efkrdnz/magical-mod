package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class UnwakingShrineBlock extends Block {
    public UnwakingShrineBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) UnwakingEncounterService.get(serverPlayer.server).ready(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }
}
