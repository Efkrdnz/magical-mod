package com.efkrdnz.magical.magic.mind;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** A viewer who has seen through a trick is slower to fall for its materials again, for a while. */
public final class Scepticism {
    public static final int MEMORY_TICKS = 6000;

    private record Memory(int times, long until) {}

    private final Map<String, Memory> memories = new HashMap<>();

    public void seenThrough(String viewer, Collection<String> impressions, long now) {
        for (String impression : impressions) {
            String key = viewer + "|" + impression;
            Memory memory = memories.get(key);
            int times = memory == null || memory.until() < now ? 1 : memory.times() + 1;
            memories.put(key, new Memory(times, now + MEMORY_TICKS));
        }
    }

    public float novelty(String viewer, Collection<String> impressions, long now) {
        float novelty = 1.0F;
        for (String impression : impressions) {
            Memory memory = memories.get(viewer + "|" + impression);
            if (memory != null && memory.until() >= now) {
                novelty = Math.min(novelty, (float) Math.pow(0.5, memory.times()));
            }
        }
        return novelty;
    }

    public void prune(long now) {
        memories.values().removeIf(memory -> memory.until() < now);
    }

    /** Every memory gone: a memory holds an absolute game time, which means nothing in another world. */
    public void clear() {
        memories.clear();
    }
}
