package com.efkrdnz.magical.magic.chaos;

import com.efkrdnz.magical.entity.domain.DomainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How much a thing holds before it gives way, decided by what the thing <em>is</em>.
 *
 * <p>Never by who owns it and never by who is looking - which is the whole reason the wielder is a
 * site like any other and gets no exception from their own avalanche.
 */
public final class ChaosCapacity {

    /** Another Authority is the most fragile thing on the field: the counter ring, made physical. */
    public static final int CONSTRUCT = 1;

    public static final int PLAYER = 3;

    private ChaosCapacity() {}

    public static int ofBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 0;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            // Unbreakable. Not infinite, because a wall a cascade can never cross is a worse thing
            // to have on a field than a very stubborn one.
            return 8;
        }
        if (hardness < 0.4F || state.is(BlockTags.LEAVES)) {
            return 1;
        }
        // Named rather than measured: cobblestone and planks are both hardness 2.0, and stone is
        // 1.5, so any threshold that puts wood in the soft band puts stone there with it.
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.LOGS)
                || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL)) {
            return 2;
        }
        return 4;
    }

    public static int ofEntity(Entity entity) {
        if (entity instanceof DomainEntity) {
            return CONSTRUCT;
        }
        if (entity instanceof Player) {
            return PLAYER;
        }
        if (entity instanceof LivingEntity living) {
            return 2 + Math.min(4, living.getArmorValue() / 5);
        }
        return CONSTRUCT;
    }
}
