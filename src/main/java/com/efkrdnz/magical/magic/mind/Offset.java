package com.efkrdnz.magical.magic.mind;

import java.util.List;

/** A cell relative to a reverie's anchor, in the reverie's own facing. */
public record Offset(int dx, int dy, int dz) {
    /** Clockwise quarter turns seen from above; east (1,0) becomes south (0,1). */
    public Offset rotate(int clockwiseTurns) {
        int x = dx;
        int z = dz;
        for (int i = 0; i < Math.floorMod(clockwiseTurns, 4); i++) {
            int turned = -z;
            z = x;
            x = turned;
        }
        return new Offset(x, dy, z);
    }

    public boolean within(int reach) {
        return Math.abs(dx) <= reach && Math.abs(dy) <= reach && Math.abs(dz) <= reach;
    }

    public List<Offset> neighbours() {
        return List.of(new Offset(dx + 1, dy, dz), new Offset(dx - 1, dy, dz),
                new Offset(dx, dy + 1, dz), new Offset(dx, dy - 1, dz),
                new Offset(dx, dy, dz + 1), new Offset(dx, dy, dz - 1));
    }
}
