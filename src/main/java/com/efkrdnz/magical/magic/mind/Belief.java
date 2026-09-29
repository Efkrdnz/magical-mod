package com.efkrdnz.magical.magic.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

/**
 * Belief, viewer by element, for one live scene. It climbs slowly while a viewer perceives the
 * element, leaks away while it does not, and falls hard on contradiction; below {@link #SHATTER}
 * the viewer has seen through it and never sees that element again.
 */
public final class Belief {
    public static final float RATE = 0.02F;
    public static final float DECAY = 0.002F;
    public static final float CONVINCED = 0.5F;
    public static final float SHATTER = 0.1F;
    public static final float PATHING = 0.3F;
    public static final float SURE = 0.8F;
    private static final int FORECAST_CAP = 20 * 600;

    public record Row(int viewer, int element, float belief, boolean shattered) {}

    private final Map<Long, Float> values = new HashMap<>();
    private final Set<Long> shattered = new HashSet<>();

    private static long key(int viewer, int element) {
        return ((long) viewer << 32) | (element & 0xFFFFFFFFL);
    }

    public float get(int viewer, int element) {
        return values.getOrDefault(key(viewer, element), 0.0F);
    }

    public boolean convinced(int viewer, int element) {
        return get(viewer, element) >= CONVINCED;
    }

    public boolean shattered(int viewer, int element) {
        return shattered.contains(key(viewer, element));
    }

    public float gain(int viewer, int element, float p, float senses, float susceptibility, float novelty) {
        long key = key(viewer, element);
        if (shattered.contains(key)) {
            return 0.0F;
        }
        float b = values.getOrDefault(key, 0.0F);
        float next = Math.min(1.0F, b + RATE * p * senses * susceptibility * novelty * (1.0F - b));
        if (next > 0.0F) {
            values.put(key, next);
        }
        return next;
    }

    public void decay(int viewer, int element) {
        long key = key(viewer, element);
        Float b = values.get(key);
        if (b == null) {
            return;
        }
        float next = b - DECAY;
        if (next <= 0.0F) {
            values.remove(key);
        } else {
            values.put(key, next);
        }
    }

    /** True when this contradiction is the one that shattered the element for this viewer. */
    public boolean contradict(int viewer, int element, Contradiction contradiction) {
        long key = key(viewer, element);
        Float b = values.get(key);
        if (b == null || shattered.contains(key)) {
            return false;
        }
        float next = b - contradiction.penalty();
        if (next < SHATTER) {
            values.remove(key);
            shattered.add(key);
            return true;
        }
        values.put(key, next);
        return false;
    }

    /**
     * First-hand evidence - a body inside the element, a blow through it, a blow from it that lands on
     * nothing. It dents a belief exactly as {@link #contradict} does, and on a viewer who holds no
     * belief at all it shatters the element outright: one who walked through a wall before ever seeing
     * it must not watch it fade in afterwards. True when this is the evidence that shattered it.
     */
    public boolean expose(int viewer, int element, Contradiction contradiction) {
        long key = key(viewer, element);
        if (shattered.contains(key)) {
            return false;
        }
        if (!values.containsKey(key)) {
            shattered.add(key);
            return true;
        }
        return contradict(viewer, element, contradiction);
    }

    public void forget(int viewer) {
        values.keySet().removeIf(key -> (int) (key >> 32) == viewer);
        shattered.removeIf(key -> (int) (key >> 32) == viewer);
    }

    /**
     * Every element's consensus in one pass: the sum, over viewers convinced of it, of their belief
     * times what their mind counts ({@code voter}, by entity id). Rows for elements past the end are
     * ignored; shattered rows hold no belief and count for nothing.
     */
    public float[] consensus(int elements, IntToDoubleFunction voter) {
        float[] sums = new float[elements];
        for (Map.Entry<Long, Float> entry : values.entrySet()) {
            float b = entry.getValue();
            if (b < CONVINCED) {
                continue;
            }
            long key = entry.getKey();
            int element = (int) (key & 0xFFFFFFFFL);
            if (element < 0 || element >= elements) {
                continue;
            }
            sums[element] += (float) voter.applyAsDouble((int) (key >> 32)) * b;
        }
        return sums;
    }

    /**
     * Moves a belief by a step, from outside the viewer's own senses (Insist, a lie that hurts). It is
     * never evidence: it cannot shatter, a shattered row ignores it, and doubt alone never writes a row.
     */
    public void nudge(int viewer, int element, float delta) {
        long key = key(viewer, element);
        if (shattered.contains(key)) {
            return;
        }
        Float b = values.get(key);
        if (b == null) {
            if (delta > 0.0F) {
                values.put(key, Math.min(1.0F, delta));
            }
            return;
        }
        float next = Math.min(1.0F, b + delta);
        if (next <= 0.0F) {
            values.remove(key);
        } else {
            values.put(key, next);
        }
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        values.forEach((key, b) -> rows.add(new Row((int) (key >> 32), (int) (long) key, b, false)));
        for (long key : shattered) {
            rows.add(new Row((int) (key >> 32), (int) key, 0.0F, true));
        }
        return rows;
    }

    void set(int viewer, int element, float belief) {
        values.put(key(viewer, element), belief);
    }

    public static int ticksToReach(float target, float p, float senses, float susceptibility, float novelty) {
        float rate = RATE * p * senses * susceptibility * novelty;
        if (rate <= 0.0F) {
            return -1;
        }
        float b = 0.0F;
        for (int tick = 1; tick <= FORECAST_CAP; tick++) {
            b += rate * (1.0F - b);
            if (b >= target) {
                return tick;
            }
        }
        return -1;
    }
}
