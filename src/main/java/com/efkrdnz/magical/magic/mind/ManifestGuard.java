package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * A real block is only as real as the minds agreeing on it: breaking one yields nothing, and an
 * explosion does not break it at all. Without this an imagined diamond wall is real diamonds.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class ManifestGuard {
    private ManifestGuard() {}

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (MindService.anyLive() && Manifestation.holds(event.getLevel(), event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (MindService.anyLive() && event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> Manifestation.holds(level, pos, level.getBlockState(pos)));
        }
    }
}
