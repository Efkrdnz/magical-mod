package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything the wielder can imagine, and how well: a count of gazes per impression. Fidelity is
 * read off the count and the budget off the number of impressions, so studying the world is the
 * only way a reverie gets bigger or better.
 */
public final class Lexicon {
    public static final int FIDELITY_2_AT = 5;
    public static final int FIDELITY_3_AT = 20;
    public static final int BASE_BUDGET = 16;
    public static final int BUDGET_PER_IMPRESSION = 2;
    public static final int MAX_BUDGET = 128;

    private final TreeMap<String, Integer> gazes = new TreeMap<>();

    public void gaze(String key) {
        gazes.merge(key, 1, Integer::sum);
    }

    /** Raises the count to at least {@code count}; never lowers it. */
    public void learn(String key, int count) {
        if (count > 0) {
            gazes.merge(key, count, Math::max);
        }
    }

    public boolean knows(String key) {
        return gazes(key) >= 1;
    }

    public int gazes(String key) {
        return gazes.getOrDefault(key, 0);
    }

    public int fidelity(String key) {
        int count = gazes(key);
        if (count >= FIDELITY_3_AT) {
            return 3;
        }
        if (count >= FIDELITY_2_AT) {
            return 2;
        }
        return count >= 1 ? 1 : 0;
    }

    /**
     * What a look at {@code key} can still teach: nothing once it is fully learned, and nothing inside
     * a dream, where everything seen is already something the dreamer knew. Null when there is nothing.
     */
    public String studyable(String key, boolean dreaming) {
        return key == null || dreaming || gazes(key) >= FIDELITY_3_AT ? null : key;
    }

    public int size() {
        return gazes.size();
    }

    public int budget() {
        return Math.min(MAX_BUDGET, BASE_BUDGET + BUDGET_PER_IMPRESSION * size());
    }

    public SortedSet<String> keys() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(gazes.keySet()));
    }

    public void copyFrom(Lexicon other) {
        gazes.clear();
        gazes.putAll(other.gazes);
    }

    public void clear() {
        gazes.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        gazes.forEach(tag::putInt);
        return tag;
    }

    public void load(CompoundTag tag) {
        gazes.clear();
        for (String key : tag.getAllKeys()) {
            if (Impression.parse(key) != null) {
                learn(key, tag.getInt(key));
            }
        }
    }
}
