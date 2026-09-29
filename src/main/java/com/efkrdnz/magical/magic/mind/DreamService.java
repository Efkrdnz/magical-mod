package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * The Dream: a level of plots, one per wielder, where a mind that believed completely is kept until it
 * finds the Flaw. This class owns the level, the plots, the sessions and Lull.
 */
public final class DreamService {
    public static final ResourceKey<Level> DREAM = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream"));
    /** Every mob a wielder dreams into their plot wears this. */
    public static final String DREAM_TAG = "magical_dream";

    /**
     * Game tests only. The vanilla test server discards datapack dimensions, so a test lays the plots
     * out in its own level. They start a million blocks out, and {@link #isDream} only counts the plot
     * grid there, so nothing near a test template is ever a dream.
     */
    static ServerLevel testLevel;

    private DreamService() {}

    public static ServerLevel dreamLevel(MinecraftServer server) {
        if (testLevel != null && testLevel.getServer() == server) {
            return testLevel;
        }
        return server.getLevel(DREAM);
    }

    public static boolean isDream(Level level, double x, double z) {
        if (level.dimension().equals(DREAM)) {
            return true;
        }
        return level == testLevel && DreamRules.plotAt(x, z) >= 0;
    }

    public static boolean isDream(Level level, BlockPos pos) {
        return isDream(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean isDream(Entity entity) {
        return isDream(entity.level(), entity.getX(), entity.getZ());
    }

    /** The owner's Dreamscape, claimed and floored the first time anybody asks for it. */
    static Dreamscape dreamscape(ServerLevel dream, UUID owner) {
        DreamPlots plots = DreamPlots.of(dream);
        Dreamscape existing = plots.get(owner);
        if (existing != null) {
            return existing;
        }
        Dreamscape claimed = plots.claim(owner);
        floor(dream, claimed.plot());
        return claimed;
    }

    static BlockPos at(int plot, Offset offset) {
        Offset origin = DreamRules.origin(plot);
        return new BlockPos(origin.dx() + offset.dx(), origin.dy() + offset.dy(), origin.dz() + offset.dz());
    }

    static Offset offsetIn(int plot, BlockPos pos) {
        Offset origin = DreamRules.origin(plot);
        return new Offset(pos.getX() - origin.dx(), pos.getY() - origin.dy(), pos.getZ() - origin.dz());
    }

    private static void floor(ServerLevel dream, int plot) {
        for (int x = -DreamRules.PLATFORM_HALF; x <= DreamRules.PLATFORM_HALF; x++) {
            for (int z = -DreamRules.PLATFORM_HALF; z <= DreamRules.PLATFORM_HALF; z++) {
                dream.setBlock(at(plot, new Offset(x, -1, z)), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
