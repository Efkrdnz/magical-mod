package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** {@link MindWorld} over a real level. An unloaded cell is air: a lie never loads a chunk. */
public record LevelMindWorld(Level level) implements MindWorld {
    private static final String AIR = "minecraft:air";

    @Override
    public boolean solid(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public String blockId(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) {
            return AIR;
        }
        BlockState state = level.getBlockState(pos);
        // cave_air and void_air are air too: none of them is a material.
        if (state.isAir()) {
            return AIR;
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @Override
    public boolean openSkyDaylight(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && level.isDay() && level.canSeeSky(pos);
    }

    @Override
    public boolean water(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }
}
