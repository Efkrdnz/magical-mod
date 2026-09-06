package com.efkrdnz.magical.tower.archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Generates nothing but a small stone pad to stand on.
 *
 * <p>This is the framework's deliberate placeholder. Dungeon content is not designed yet, so every
 * archetype registers with this until it has a real generator. It exists so the rest of the system
 * — allocation, entry, teleport, release, wipe — can be exercised end to end against an otherwise
 * empty plot.
 *
 * <p>The pad is the smallest thing that keeps a test player from falling through a void dimension.
 * Do not grow it into a dungeon; write a new {@link ArchetypeGenerator} instead.
 */
public final class VoidGenerator implements ArchetypeGenerator {
    public static final VoidGenerator INSTANCE = new VoidGenerator();

    /** Half-width of the landing pad, in blocks. */
    private static final int PAD_RADIUS = 4;

    private VoidGenerator() {}

    @Override
    public void generate(ServerLevel level, BlockPos origin, int floor, RandomSource random, PlacementSink sink) {
        for (int x = -PAD_RADIUS; x <= PAD_RADIUS; x++) {
            for (int z = -PAD_RADIUS; z <= PAD_RADIUS; z++) {
                sink.set(origin.offset(x, 0, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
    }

    @Override
    public AABB footprint(BlockPos origin) {
        // One block of headroom above the pad so a future generator swap still wipes cleanly.
        return new AABB(
                origin.getX() - PAD_RADIUS, origin.getY(), origin.getZ() - PAD_RADIUS,
                origin.getX() + PAD_RADIUS + 1, origin.getY() + 2, origin.getZ() + PAD_RADIUS + 1);
    }
}
