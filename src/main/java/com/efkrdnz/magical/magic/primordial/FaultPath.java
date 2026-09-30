package com.efkrdnz.magical.magic.primordial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The columns a Fault Line opens, in the order the crack reaches them. Pure.
 *
 * <p>The crack starts {@link #START} blocks out from the caster, so the caster is never standing on
 * the first column, and walks along the horizontal direction in half-block steps; {@code width}
 * lines side by side, centred on the path. A column is listed once, at the first step that touches
 * it, and carries the whole-block distance from the start ({@code step}) so the opening can be paced
 * at so many blocks a tick.
 */
public final class FaultPath {
    public static final double START = 1.5D;
    private static final double STEP = 0.5D;

    public record Cell(int x, int z, int step) {}

    private FaultPath() {}

    public static List<Cell> columns(double ox, double oz, double dirX, double dirZ, int length, int width) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double dx = len > 1.0E-6D ? dirX / len : 0.0D;
        double dz = len > 1.0E-6D ? dirZ / len : 1.0D;
        double px = -dz;
        double pz = dx;
        Map<Long, Cell> seen = new LinkedHashMap<>();
        int lines = Math.max(1, width);
        for (double d = START; d <= START + length; d += STEP) {
            int step = (int) Math.floor(d - START);
            for (int w = 0; w < lines; w++) {
                double off = w - (lines - 1) / 2.0D;
                int x = (int) Math.floor(ox + dx * d + px * off);
                int z = (int) Math.floor(oz + dz * d + pz * off);
                long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                seen.putIfAbsent(key, new Cell(x, z, step));
            }
        }
        return new ArrayList<>(seen.values());
    }
}
