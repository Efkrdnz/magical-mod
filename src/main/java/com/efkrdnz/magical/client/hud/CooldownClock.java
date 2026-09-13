package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.network.CooldownSyncPayload;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The arithmetic behind {@link ClientCooldowns}: every cooldown as the tick it was heard about, what
 * was left then, and what it started from. Remaining time is extrapolated from those three numbers,
 * so nothing ticks over the network and the sweep on a card moves every frame, not every packet.
 *
 * <p>No Minecraft client imports, so the maths is testable without a game.
 */
public final class CooldownClock {

    public record Entry(long startTick, int remaining, int total) {
        int remainingAt(long now) {
            return (int) Math.max(0L, remaining - (now - startTick));
        }
    }

    private final Map<ResourceLocation, Entry> active = new LinkedHashMap<>();
    private final Set<ResourceLocation> justFinished = new LinkedHashSet<>();
    private int version;

    public void accept(CooldownSyncPayload payload, long now) {
        if (payload.replaceAll()) {
            active.clear();
        }
        for (CooldownSyncPayload.Entry entry : payload.entries()) {
            if (entry.remainingTicks() <= 0) {
                if (active.remove(entry.skillId()) != null) {
                    justFinished.add(entry.skillId());
                }
            } else {
                active.put(entry.skillId(), new Entry(now, entry.remainingTicks(), Math.max(entry.totalTicks(), entry.remainingTicks())));
            }
        }
        version++;
    }

    /** Once per client tick: forget the last tick's finishes, expire what ran out. */
    public void tick(long now) {
        justFinished.clear();
        if (active.isEmpty()) {
            return;
        }
        int before = active.size();
        active.entrySet().removeIf(e -> {
            if (e.getValue().remainingAt(now) <= 0) {
                justFinished.add(e.getKey());
                return true;
            }
            return false;
        });
        if (active.size() != before) {
            version++;
        }
    }

    public int remaining(ResourceLocation id, long now) {
        Entry entry = active.get(id);
        return entry == null ? 0 : entry.remainingAt(now);
    }

    /** 1 the moment it was cast, 0 when ready; the partial tick makes it continuous. */
    public float fraction(ResourceLocation id, float nowWithPartial) {
        Entry entry = active.get(id);
        if (entry == null || entry.total <= 0) {
            return 0.0F;
        }
        float left = entry.remaining - (nowWithPartial - entry.startTick);
        return Math.max(0.0F, Math.min(1.0F, left / entry.total));
    }

    public boolean isOnCooldown(ResourceLocation id, long now) {
        return remaining(id, now) > 0;
    }

    /** True for exactly the one tick after a cooldown ran out or was cleared. */
    public boolean justFinished(ResourceLocation id) {
        return justFinished.contains(id);
    }

    public Entry entry(ResourceLocation id) {
        return active.get(id);
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    public void reset() {
        active.clear();
        justFinished.clear();
        version++;
    }

    /** Bumped on every change, so a snapshot builder knows when to look again. */
    public int version() {
        return version;
    }
}
