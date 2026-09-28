package com.efkrdnz.magical.magic.visual.sigil;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Where a mark's sigils appear and which way they go: pure geometry, a list of spawns, so the
 * shapes are tested here and {@link Sigils} only sends them. Bodies are given by their feet,
 * width and height rather than as entities, which keeps this free of the level.
 */
public final class SigilPlacement {

    /** Blocks above the head a pop is written. */
    public static final double POP_GAP = 0.35D;
    /** A pop's upward speed; a rising sigil keeps nine tenths a tick, so it travels ten times this. */
    public static final double POP_LIFT = 0.03D;
    /** Blocks out from the body's side a halo stands. */
    public static final double HALO_GAP = 0.35D;
    /** Where on the body a halo circles, as a fraction of its height: the chest. */
    public static final double HALO_HEIGHT = 0.6D;
    /** How fast a rise leaves the ground. */
    public static final double RISE_SPEED = 0.05D;

    /** One sigil: where it is written and the velocity it is sent with. */
    public record Spawn(double x, double y, double z, double vx, double vy, double vz) {
    }

    private SigilPlacement() {
    }

    /**
     * A level ring just over a body's head, {@code slots} evenly spaced from due east and the first
     * {@code filled} of them written, standing still. The radius clears the widest part of the body.
     */
    public static List<Spawn> crown(double x, double feetY, double z, double width, double height, int slots, int filled) {
        int written = Math.max(0, Math.min(filled, slots));
        double ring = Math.max(0.45D, width * 0.5D) + 0.1D;
        double above = feetY + height + 0.25D;
        List<Spawn> out = new ArrayList<>(written);
        for (int i = 0; i < written; i++) {
            double angle = Math.PI * 2.0D * i / slots;
            out.add(new Spawn(x + Math.cos(angle) * ring, above, z + Math.sin(angle) * ring, 0.0D, 0.0D, 0.0D));
        }
        return out;
    }

    /** One sigil just over the head, drifting up: something just happened to this body. */
    public static Spawn pop(double x, double feetY, double z, double height) {
        return new Spawn(x, feetY + height + POP_GAP, z, 0.0D, POP_LIFT, 0.0D);
    }

    /** A ring round the body at chest height, {@code count} evenly spaced from {@code phase}. */
    public static List<Spawn> halo(double x, double feetY, double z, double width, double height, int count, double phase) {
        double ring = width * 0.5D + HALO_GAP;
        double y = feetY + height * HALO_HEIGHT;
        List<Spawn> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = phase + Math.PI * 2.0D * i / count;
            out.add(new Spawn(x + Math.cos(angle) * ring, y, z + Math.sin(angle) * ring, 0.0D, 0.0D, 0.0D));
        }
        return out;
    }

    /** Out from a point in {@code count} directions spread evenly over the sphere (a Fibonacci lattice). */
    public static List<Spawn> burst(double x, double y, double z, int count, double speed) {
        List<Spawn> out = new ArrayList<>(count);
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < count; i++) {
            double dy = 1.0D - 2.0D * (i + 0.5D) / count;
            double r = Math.sqrt(Math.max(0.0D, 1.0D - dy * dy));
            double angle = golden * i;
            out.add(new Spawn(x, y, z, Math.cos(angle) * r * speed, dy * speed, Math.sin(angle) * r * speed));
        }
        return out;
    }

    /** Up out of the ground at {@code count} points spread evenly over a disc. */
    public static List<Spawn> rise(double x, double groundY, double z, double radius, int count, RandomGenerator random) {
        List<Spawn> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double r = radius * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.PI * 2.0D;
            out.add(new Spawn(x + Math.cos(angle) * r, groundY, z + Math.sin(angle) * r, 0.0D, RISE_SPEED, 0.0D));
        }
        return out;
    }

    /**
     * A grid standing square to a horizontal look direction and centred on a point, row by row from
     * the viewer's top left, still: the preview wall.
     */
    public static List<Spawn> wall(double cx, double cy, double cz, double lookX, double lookZ, int columns, int rows, double spacing) {
        double length = Math.hypot(lookX, lookZ);
        // the viewer's right is a quarter turn of the look, (-z, x): yaw turns clockwise seen from
        // above, so a viewer facing +Z has +X on their left
        double rightX = length == 0.0D ? 1.0D : -lookZ / length;
        double rightZ = length == 0.0D ? 0.0D : lookX / length;
        List<Spawn> out = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            double up = ((rows - 1) * 0.5D - row) * spacing;
            for (int column = 0; column < columns; column++) {
                double across = (column - (columns - 1) * 0.5D) * spacing;
                out.add(new Spawn(cx + rightX * across, cy + up, cz + rightZ * across, 0.0D, 0.0D, 0.0D));
            }
        }
        return out;
    }
}
