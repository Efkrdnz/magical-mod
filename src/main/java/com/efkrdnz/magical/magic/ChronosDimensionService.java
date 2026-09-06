package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Chronos End: the paradox dimension at the end of time. Everyone inside is held aloft by
 * the dying current of time itself - creative-style flight, no mana cost.
 */
public final class ChronosDimensionService {
    public static final ResourceKey<Level> CHRONOS_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "chronos_end"));

    private ChronosDimensionService() {}

    public static boolean isChronos(Level level) {
        return level.dimension().equals(CHRONOS_DIMENSION);
    }

    public static boolean grantsFreeFlight(ServerPlayer player) {
        return isChronos(player.level());
    }
}
