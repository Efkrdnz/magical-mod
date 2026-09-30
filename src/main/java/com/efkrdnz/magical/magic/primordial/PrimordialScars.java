package com.efkrdnz.magical.magic.primordial;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wounds the land keeps for a while and then closes: a crater, the hole a plate was torn out of.
 *
 * <p>Nothing here is saved, and nothing needs to be: the edits themselves live in the
 * {@link ConjuredTerrainService} ledger, which gives back every edit a level loads with, so a
 * crash or a restart closes the scar at the next load instead of at the scheduled tick.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class PrimordialScars {
    private record Pending(ResourceKey<Level> level, UUID edit, long due) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private PrimordialScars() {}

    public static void giveBackLater(ServerLevel level, ConjuredTerrainService.Edit edit, int ticks) {
        if (edit == null || edit.size() == 0) {
            return;
        }
        PENDING.add(new Pending(level.dimension(), edit.id(), level.getGameTime() + ticks));
    }

    /** What the land may overwrite when it closes: nothing a player built. */
    public static boolean ours(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.BLACKSTONE)
                || s.is(Blocks.BASALT) || s.is(Blocks.POLISHED_BASALT) || s.is(Blocks.SMOOTH_BASALT);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            ServerLevel level = event.getServer().getLevel(p.level());
            if (level == null) {
                it.remove();
            } else if (level.getGameTime() >= p.due()) {
                it.remove();
                due.add(p);
            }
        }
        for (Pending p : due) {
            giveBack(event.getServer().getLevel(p.level()), p.edit());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Pending p : List.copyOf(PENDING)) {
            ServerLevel level = event.getServer().getLevel(p.level());
            if (level != null) {
                giveBack(level, p.edit());
            }
        }
        PENDING.clear();
        PrimordialService.forgetAllMatter();
    }

    /** Conjured matter is worth nothing: broken before the ground comes back, it drops nothing. */
    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (PrimordialService.isMatter(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void giveBack(ServerLevel level, UUID id) {
        if (level == null) {
            return;
        }
        PrimordialService.giveBack(level, ConjuredTerrainService.lookup(level, id), PrimordialScars::ours);
    }

    /** For tests: close every scar now. */
    public static void closeAll(ServerLevel level) {
        for (Pending p : List.copyOf(PENDING)) {
            if (p.level() == level.dimension()) {
                PENDING.remove(p);
                giveBack(level, p.edit());
            }
        }
    }
}
