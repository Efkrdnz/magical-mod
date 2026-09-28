package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

/** What the pathfinder asks a mob's imagination, cell by cell. Server only. */
public final class MindPathing {
    private MindPathing() {}

    public static PathType override(PathfindingContext context, Mob mob, int x, int y, int z) {
        if (mob == null || mob.level().isClientSide() || !MindService.anyLive()) {
            return null;
        }
        long here = BlockPos.asLong(x, y, z);
        long below = BlockPos.asLong(x, y - 1, z);
        for (LiveScene scene : MindService.scenesIn(mob.level().dimension())) {
            int solid = scene.elementAt(here);
            if (solid >= 0 && scene.belief().get(mob.getId(), solid) >= Belief.PATHING) {
                return PathType.BLOCKED;
            }
            int floor = scene.elementAt(below);
            if (floor >= 0 && scene.belief().get(mob.getId(), floor) >= Belief.PATHING
                    && context.getPathTypeFromState(x, y, z) == PathType.OPEN) {
                return PathType.WALKABLE;
            }
        }
        return null;
    }
}
