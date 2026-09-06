package com.efkrdnz.magical.tower.archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Receives block placements from a generator.
 *
 * <p>Generators must place blocks through this sink rather than calling {@code level.setBlock}
 * directly. The instance manager can only track and clean up what the sink records. A placement
 * made outside the sink will leak and persist indefinitely.
 *
 * <p>A typical implementation both writes the block to the level and records the position for
 * later cleanup.
 */
public interface PlacementSink {
    /**
     * Place a block at a position.
     *
     * @param pos the block position
     * @param state the block state to place
     */
    void set(BlockPos pos, BlockState state);
}
