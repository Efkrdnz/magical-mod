package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Where Daydream's cursor lands: in front of the first solid cell on the ray, or in the air at reach. */
public final class DraftRay {
    public record Hit(BlockPos solid, BlockPos place) {}

    private DraftRay() {}

    public static Hit march(Vec3 from, Vec3 direction, double reach, Predicate<BlockPos> solid) {
        Vec3 d = direction.normalize();
        int x = (int) Math.floor(from.x);
        int y = (int) Math.floor(from.y);
        int z = (int) Math.floor(from.z);
        int stepX = d.x > 0 ? 1 : -1;
        int stepY = d.y > 0 ? 1 : -1;
        int stepZ = d.z > 0 ? 1 : -1;
        double deltaX = d.x == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.x);
        double deltaY = d.y == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.y);
        double deltaZ = d.z == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.z);
        double maxX = d.x == 0 ? Double.POSITIVE_INFINITY : (d.x > 0 ? x + 1 - from.x : from.x - x) * deltaX;
        double maxY = d.y == 0 ? Double.POSITIVE_INFINITY : (d.y > 0 ? y + 1 - from.y : from.y - y) * deltaY;
        double maxZ = d.z == 0 ? Double.POSITIVE_INFINITY : (d.z > 0 ? z + 1 - from.z : from.z - z) * deltaZ;
        BlockPos previous = new BlockPos(x, y, z);
        double travelled = 0.0;
        while (travelled <= reach) {
            BlockPos cell = new BlockPos(x, y, z);
            if (solid.test(cell)) {
                return new Hit(cell, previous);
            }
            previous = cell;
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                travelled = maxX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                travelled = maxY;
                maxY += deltaY;
            } else {
                z += stepZ;
                travelled = maxZ;
                maxZ += deltaZ;
            }
        }
        return new Hit(null, BlockPos.containing(from.add(d.scale(reach))));
    }
}
