package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.incantation.Matter;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The clocks on conjured matter. Every block a material verse lays is one
 * {@link ConjuredTerrainService} edit handed here with the matter's lifetime; on the tick the clock
 * runs out the edit is restored wherever the laid block still stands or nothing does, and yielded
 * wherever a player has since built over it. Held in memory per level and never saved: a server
 * that stops with matter standing leaves the edits in the ledger, and the ledger restores them as
 * orphans on the next load. An edit the ledger no longer knows (another world opened in the same
 * client, the ledger restored behind our back) is dropped without touching a block.
 */
public final class MatterKeeper {

    private record Kept(ConjuredTerrainService.Edit edit, Matter matter, long dueAt) {
    }

    private static final Map<ResourceKey<Level>, List<Kept>> KEPT = new HashMap<>();

    private MatterKeeper() {
    }

    /** Hold {@code edit} for {@code lifetimeTicks}, then give it back. An edit that laid nothing is closed at once. */
    public static void keep(ServerLevel level, ConjuredTerrainService.Edit edit, Matter matter, int lifetimeTicks) {
        if (edit.size() == 0) {
            ConjuredTerrainService.restore(level, edit);
            return;
        }
        KEPT.computeIfAbsent(level.dimension(), key -> new ArrayList<>())
                .add(new Kept(edit, matter, level.getGameTime() + Math.max(1, lifetimeTicks)));
    }

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<Kept> kept = KEPT.get(level.dimension());
            if (kept == null || kept.isEmpty()) {
                continue;
            }
            long now = level.getGameTime();
            Iterator<Kept> it = kept.iterator();
            while (it.hasNext()) {
                Kept entry = it.next();
                if (ConjuredTerrainService.lookup(level, entry.edit().id()) != entry.edit()) {
                    it.remove();
                    continue;
                }
                if (now >= entry.dueAt()) {
                    give(level, entry);
                    it.remove();
                }
            }
        }
    }

    /** Everything standing in this level, given back now; how many edits that was. For tests and the debug command. */
    public static int restoreAll(ServerLevel level) {
        List<Kept> kept = KEPT.remove(level.dimension());
        if (kept == null) {
            return 0;
        }
        int count = 0;
        for (Kept entry : kept) {
            if (ConjuredTerrainService.lookup(level, entry.edit().id()) == entry.edit()) {
                give(level, entry);
                count++;
            }
        }
        return count;
    }

    /** How many edits stand in this level. */
    public static int pending(ServerLevel level) {
        List<Kept> kept = KEPT.get(level.dimension());
        return kept == null ? 0 : kept.size();
    }

    private static void give(ServerLevel level, Kept entry) {
        Matter matter = entry.matter();
        ConjuredTerrainService.restoreUnlessBuiltOver(level, entry.edit(), state -> VerseMatter.isMatter(matter, state));
    }
}
