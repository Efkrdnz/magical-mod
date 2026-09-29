package com.efkrdnz.magical.magic.mind;

import java.util.ArrayList;
import java.util.List;

/** The shapes Daydream lays an impression in, from one corner to the cursor. */
public enum Brush {
    POINT, LINE, WALL, BOX;

    public static final int MAX_CELLS = 128;

    public Brush next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public List<Offset> cells(Offset from, Offset to) {
        List<Offset> cells = new ArrayList<>();
        switch (this) {
            case POINT -> cells.add(to);
            case LINE -> {
                int dx = to.dx() - from.dx();
                int dy = to.dy() - from.dy();
                int dz = to.dz() - from.dz();
                int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                for (int i = 0; i <= steps && cells.size() < MAX_CELLS; i++) {
                    double t = steps == 0 ? 0.0 : (double) i / steps;
                    cells.add(new Offset(from.dx() + (int) Math.round(dx * t), from.dy() + (int) Math.round(dy * t),
                            from.dz() + (int) Math.round(dz * t)));
                }
            }
            case WALL -> {
                boolean alongX = Math.abs(to.dx() - from.dx()) >= Math.abs(to.dz() - from.dz());
                int loY = Math.min(from.dy(), to.dy());
                int hiY = Math.max(from.dy(), to.dy());
                int lo = alongX ? Math.min(from.dx(), to.dx()) : Math.min(from.dz(), to.dz());
                int hi = alongX ? Math.max(from.dx(), to.dx()) : Math.max(from.dz(), to.dz());
                for (int y = loY; y <= hiY; y++) {
                    for (int a = lo; a <= hi && cells.size() < MAX_CELLS; a++) {
                        cells.add(alongX ? new Offset(a, y, from.dz()) : new Offset(from.dx(), y, a));
                    }
                }
            }
            case BOX -> {
                for (int x = Math.min(from.dx(), to.dx()); x <= Math.max(from.dx(), to.dx()); x++) {
                    for (int y = Math.min(from.dy(), to.dy()); y <= Math.max(from.dy(), to.dy()); y++) {
                        for (int z = Math.min(from.dz(), to.dz()); z <= Math.max(from.dz(), to.dz()) && cells.size() < MAX_CELLS; z++) {
                            cells.add(new Offset(x, y, z));
                        }
                    }
                }
            }
        }
        return cells.size() > MAX_CELLS ? cells.subList(0, MAX_CELLS) : cells;
    }
}
