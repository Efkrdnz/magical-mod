package com.efkrdnz.magical.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class SphericalBlockRemover {
    private SphericalBlockRemover() {
    }

    public static int removeFromCenterOut(ServerLevel level, Vec3 center, float radius, int maxRemoved) {
        int range = Mth.ceil(radius);
        double radiusSqr = radius * radius;
        BlockPos origin = BlockPos.containing(center);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int removed = 0;
        for (int shell = 0; shell <= range && removed < maxRemoved; shell++) {
            for (int dx = -shell; dx <= shell && removed < maxRemoved; dx++) {
                for (int dy = -shell; dy <= shell && removed < maxRemoved; dy++) {
                    for (int dz = -shell; dz <= shell && removed < maxRemoved; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != shell) {
                            continue;
                        }
                        mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (mutable.distToCenterSqr(center) <= radiusSqr && !level.getBlockState(mutable).isAir()) {
                            level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                            removed++;
                        }
                    }
                }
            }
        }
        return removed;
    }
}
