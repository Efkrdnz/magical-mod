package com.efkrdnz.magical.magic;

import net.minecraft.world.phys.Vec3;

/** Shared sky-cut proportions for the command preview and the physical boss wound. */
public final class ChronosSkyCutGeometry {
    public static final double HEIGHT = .16, DISTANCE = .26;
    public static final double HALF_WIDTH = .75, HALF_HEIGHT = .80, ROLL = Math.toRadians(35);
    private ChronosSkyCutGeometry() {}

    public record Frame(Vec3 center, Vec3 right, Vec3 up) {}

    public static Frame frame(Vec3 anchor, Vec3 forward, double scale) {
        Vec3 right = new Vec3(-forward.z, 0, forward.x), up = new Vec3(0, 1, 0);
        return new Frame(anchor.add(forward.scale(scale * DISTANCE)).add(0, scale * HEIGHT, 0),
                right.scale(Math.cos(ROLL)).add(up.scale(Math.sin(ROLL))).scale(scale * HALF_WIDTH),
                up.scale(Math.cos(ROLL)).subtract(right.scale(Math.sin(ROLL))).scale(scale * HALF_HEIGHT));
    }
}
