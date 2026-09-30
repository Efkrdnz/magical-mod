package com.efkrdnz.magical.magic.primordial;

import java.util.ArrayList;
import java.util.List;

/**
 * The cells a catastrophe carves or raises, as offsets {@code {dx, dy, dz}} from a surface block.
 * Pure. {@code dy == 0} is the surface block itself (the top solid block) for what is carved, and
 * the first block above the surface for what is raised.
 */
public final class Shapes {
    private Shapes() {}

    /** A crater: a bowl {@code depth} deep at the middle, shallowing to nothing at {@code radius}. */
    public static List<int[]> crater(int radius, int depth) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int k = craterDepth(dx, dz, radius, depth);
                for (int dy = 0; dy > -k; dy--) {
                    cells.add(new int[] {dx, dy, dz});
                }
            }
        }
        return cells;
    }

    /** The cell under the floor of every carved column: where a crater is lined. */
    public static List<int[]> craterLining(int radius, int depth) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int k = craterDepth(dx, dz, radius, depth);
                if (k > 0) {
                    cells.add(new int[] {dx, -k, dz});
                }
            }
        }
        return cells;
    }

    static int craterDepth(int dx, int dz, int radius, int depth) {
        double d = Math.sqrt(dx * dx + dz * dz) / Math.max(1, radius);
        if (d > 1.0D) {
            return 0;
        }
        return (int) Math.ceil(depth * (1.0D - d * d) - 1.0E-9D);
    }

    /** A volcano: layers narrowing from {@code baseRadius} to a vent at the top. */
    public record Cone(List<int[]> body, int[] vent) {}

    public static Cone cone(int baseRadius, int height) {
        List<int[]> body = new ArrayList<>();
        int[] vent = {0, height - 1, 0};
        for (int dy = 0; dy < height; dy++) {
            double r = baseRadius * (1.0D - dy / (double) height);
            double limit = r * r + 0.5D;
            for (int dx = -baseRadius; dx <= baseRadius; dx++) {
                for (int dz = -baseRadius; dz <= baseRadius; dz++) {
                    if (dx * dx + dz * dz > limit) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && dy == height - 1) {
                        continue;
                    }
                    body.add(new int[] {dx, dy, dz});
                }
            }
        }
        return new Cone(body, vent);
    }

    /** The disc of ground Upheaval tears up: {@code radius} across, {@code depth} down from the surface. */
    public static List<int[]> plate(int radius, int depth) {
        List<int[]> cells = new ArrayList<>();
        double limit = radius * radius + radius * 0.5D;
        for (int dy = 0; dy > -depth; dy--) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= limit) {
                        cells.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return cells;
    }
}
