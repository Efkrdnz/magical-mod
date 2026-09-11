package com.efkrdnz.magical.magic.blood.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a drawn shape into geometry: clipped to the caster's reach, resampled, and projected into
 * the world.
 *
 * <p>This is the one place a shape becomes positions, and both sides call it. The server tests hits
 * against the spine it produces; the client grows its voxels toward targets expanded from the same
 * spine through the same transform. Anything that drifted between the two would show up as blood
 * that damages somewhere other than where it visibly is, so there is deliberately no second
 * implementation to drift from.
 *
 * <p><b>Frames.</b> The canvas is a map seen from above. {@code u} is the caster's right and
 * {@code v} is forward - up on the canvas is forward, which is why capture flips the sign of screen
 * y exactly once. Height is never drawn: it comes from the height slider (where the plane sits) and
 * the wall extrusion (how far the blood stands up off it).
 *
 * <p><b>Order.</b> Everything is ranked by distance from the caster in the canvas plane, because
 * the blood forms from the middle outwards. The server advances a radius rather than walking the
 * drawn path, so the damage arrives exactly where the blood has reached.
 */
public final class BloodShapeGeometry {

    /**
     * One point of the spine, as an offset from the caster in blocks.
     *
     * @param rank distance from the caster in the canvas plane - what formation order is keyed on,
     *             kept here so the client and the server sort identically
     */
    public record Node(double x, double y, double z, double rank) {
    }

    private BloodShapeGeometry() {
    }

    // ---------------------------------------------------------------- clipping

    /**
     * Every stroke of {@code shape}, clipped to the square canvas of the given half extent.
     *
     * <p>Clipping is per segment, never per point. Dropping the points that fall outside would take
     * the segment that crosses the boundary with them, so trimming a shape by one block could
     * delete a whole lobe; cutting the segment at the edge leaves exactly the part that was inside.
     * A stroke that leaves and re-enters comes back as two polylines rather than one with a chord
     * drawn across the gap.
     *
     * @return polylines as {@code u, v} pairs in blocks; never null, possibly empty
     */
    public static List<double[]> clip(BloodShape shape, double halfExtent) {
        List<double[]> out = new ArrayList<>();
        if (shape == null || shape.isEmpty() || !(halfExtent > 0.0D)) {
            return out;
        }
        for (int stroke = 0; stroke < shape.strokeCount(); stroke++) {
            clipStroke(shape, stroke, halfExtent, out);
        }
        return out;
    }

    private static void clipStroke(BloodShape shape, int stroke, double halfExtent, List<double[]> out) {
        int start = shape.strokeStart(stroke);
        int end = shape.strokeEnd(stroke);
        List<Double> current = new ArrayList<>();
        for (int i = start; i + 1 < end; i++) {
            double ax = shape.u(i);
            double ay = shape.v(i);
            double bx = shape.u(i + 1);
            double by = shape.v(i + 1);
            double[] span = segmentSpan(ax, ay, bx, by, halfExtent);
            if (span == null) {
                flush(current, out);
                continue;
            }
            double t0 = span[0];
            double t1 = span[1];
            if (t0 > 0.0D) {
                // The segment entered partway, so whatever came before does not join onto it.
                flush(current, out);
            }
            if (current.isEmpty()) {
                current.add(ax + (bx - ax) * t0);
                current.add(ay + (by - ay) * t0);
            }
            current.add(ax + (bx - ax) * t1);
            current.add(ay + (by - ay) * t1);
            if (t1 < 1.0D) {
                // It left before the end, so the path is broken here.
                flush(current, out);
            }
        }
        flush(current, out);
    }

    private static void flush(List<Double> current, List<double[]> out) {
        if (current.size() >= 4) {
            double[] line = new double[current.size()];
            for (int i = 0; i < line.length; i++) {
                line[i] = current.get(i);
            }
            out.add(line);
        }
        current.clear();
    }

    /**
     * The portion of a segment lying inside the square, as a pair of parameters along it.
     * Liang-Barsky against four half planes.
     *
     * @return {@code {t0, t1}}, or null when the segment misses the square entirely
     */
    private static double[] segmentSpan(double ax, double ay, double bx, double by, double h) {
        double dx = bx - ax;
        double dy = by - ay;
        double[] span = {0.0D, 1.0D};
        if (!clipEdge(-dx, ax + h, span)
                || !clipEdge(dx, h - ax, span)
                || !clipEdge(-dy, ay + h, span)
                || !clipEdge(dy, h - ay, span)) {
            return null;
        }
        return span[0] <= span[1] ? span : null;
    }

    private static boolean clipEdge(double p, double q, double[] span) {
        if (p == 0.0D) {
            return q >= 0.0D;
        }
        double r = q / p;
        if (p < 0.0D) {
            if (r > span[1]) {
                return false;
            }
            if (r > span[0]) {
                span[0] = r;
            }
        } else {
            if (r < span[0]) {
                return false;
            }
            if (r < span[1]) {
                span[1] = r;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- measuring

    /** Total clipped path length in blocks. What the cost and the voxel count are both drawn from. */
    public static double arcLength(BloodShape shape, double halfExtent) {
        double total = 0.0D;
        for (double[] line : clip(shape, halfExtent)) {
            total += polylineLength(line);
        }
        return total;
    }

    private static double polylineLength(double[] line) {
        double total = 0.0D;
        for (int i = 0; i + 3 < line.length; i += 2) {
            double dx = line[i + 2] - line[i];
            double dy = line[i + 3] - line[i + 1];
            total += Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }

    // ---------------------------------------------------------------- resampling

    /**
     * Resamples a polyline to points spaced evenly along its length, endpoints always included.
     *
     * <p>Written here rather than through {@code GlyphNormalizer.resampleStroke}, which resamples to
     * a fixed <em>count</em> of normalized canvas points. This needs a fixed <em>spacing</em> in
     * blocks, and running block coordinates through a type documented as normalized would be the
     * kind of reuse that reads as correct right until someone believes the doc.
     */
    public static double[] resample(double[] line, double spacing) {
        if (line == null || line.length < 4 || !(spacing > 0.0D)) {
            return line == null ? new double[0] : line.clone();
        }
        double length = polylineLength(line);
        int count = Math.max(2, (int) Math.round(length / spacing) + 1);
        double step = length / (count - 1);
        double[] out = new double[count * 2];
        out[0] = line[0];
        out[1] = line[1];
        out[out.length - 2] = line[line.length - 2];
        out[out.length - 1] = line[line.length - 1];

        int segment = 0;
        double walked = 0.0D;
        double segmentLength = segmentLength(line, 0);
        for (int i = 1; i < count - 1; i++) {
            double target = i * step;
            while (segment + 4 < line.length && walked + segmentLength < target) {
                walked += segmentLength;
                segment += 2;
                segmentLength = segmentLength(line, segment);
            }
            double t = segmentLength <= 0.0D ? 0.0D : (target - walked) / segmentLength;
            t = Math.max(0.0D, Math.min(1.0D, t));
            out[i * 2] = line[segment] + (line[segment + 2] - line[segment]) * t;
            out[i * 2 + 1] = line[segment + 1] + (line[segment + 3] - line[segment + 1]) * t;
        }
        return out;
    }

    private static double segmentLength(double[] line, int index) {
        if (index + 3 >= line.length) {
            return 0.0D;
        }
        double dx = line[index + 2] - line[index];
        double dy = line[index + 3] - line[index + 1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ---------------------------------------------------------------- projection

    /**
     * The yaw the canvas is read at: the caster's own when horizontal tracking is on, due north
     * otherwise.
     *
     * <p>Reading a world-locked canvas as "facing north" is not a special case dressed up. At 180
     * degrees the general transform collapses to canvas-right is east and canvas-up is north, which
     * is exactly what the N/S/W/E letters promise. One formula, two behaviours.
     */
    public static float readingYaw(BloodShape shape, float casterYaw) {
        return shape != null && shape.trackYaw() ? casterYaw : BloodShapeRules.COMPASS_YAW_DEGREES;
    }

    /** The pitch the plane is tilted by: the caster's when vertical tracking is on, else flat. */
    public static float readingPitch(BloodShape shape, float casterPitch) {
        return shape != null && shape.trackPitch() ? casterPitch : 0.0F;
    }

    /**
     * Canvas-local to an offset from the caster, in blocks. The single transform both sides use.
     *
     * @param u            canvas right
     * @param w            height above the plane - the wall extrusion, not the drawing
     * @param v            canvas forward
     * @param heightOffset where the plane sits above the caster's feet
     */
    public static Node toWorld(double u, double w, double v, double heightOffset,
            float yawDegrees, float pitchDegrees) {
        double[] out = new double[3];
        project(u, w, v, heightOffset, basis(yawDegrees, pitchDegrees), out);
        return new Node(out[0], out[1], out[2], Math.sqrt(u * u + v * v));
    }

    /**
     * The four trig terms the transform needs, computed once for a whole field.
     *
     * <p>The painter runs this once a frame and {@link #project} a thousand times, which is the only
     * reason the hot path can share an implementation with the server instead of growing an inlined
     * copy that drifts.
     *
     * @return {@code {sinPitch, cosPitch, sinYaw, cosYaw}}
     */
    public static double[] basis(float yawDegrees, float pitchDegrees) {
        // Looking up should tip the far edge up, and Minecraft pitch is negative upward, so the tilt
        // runs against it.
        double phi = Math.toRadians(-pitchDegrees);
        double theta = Math.toRadians(yawDegrees);
        return new double[] {Math.sin(phi), Math.cos(phi), Math.sin(theta), Math.cos(theta)};
    }

    /** {@link #toWorld} without the allocation: writes x, y, z into {@code out}. */
    public static void project(double u, double w, double v, double heightOffset,
            double[] basis, double[] out) {
        double vTilted = v * basis[1] - w * basis[0];
        double wTilted = v * basis[0] + w * basis[1];
        out[0] = -(u * basis[3] + vTilted * basis[2]);
        out[1] = heightOffset + wTilted;
        out[2] = -u * basis[2] + vTilted * basis[3];
    }

    /**
     * The unit direction the wall stands up in: straight up while the plane is flat, swinging
     * toward the caster's look as it tilts.
     */
    public static Node columnAxis(float yawDegrees, float pitchDegrees) {
        Node top = toWorld(0.0D, 1.0D, 0.0D, 0.0D, yawDegrees, pitchDegrees);
        return new Node(top.x(), top.y(), top.z(), 0.0D);
    }

    /**
     * The spine: the clipped path resampled to {@code spacing} and projected into caster-relative
     * offsets, ordered as drawn but ranked by distance from the middle.
     */
    public static List<Node> spine(BloodShape shape, double halfExtent, double spacing,
            double casterHeight, float yawDegrees, float pitchDegrees) {
        List<Node> nodes = new ArrayList<>();
        if (shape == null || shape.isEmpty()) {
            return nodes;
        }
        double heightOffset = BloodShapeRules.heightOffsetBlocks(shape.heightPercent(), casterHeight);
        for (double[] line : clip(shape, halfExtent)) {
            double[] even = resample(line, spacing);
            for (int i = 0; i + 1 < even.length; i += 2) {
                nodes.add(toWorld(even[i], 0.0D, even[i + 1], heightOffset, yawDegrees, pitchDegrees));
            }
        }
        return nodes;
    }

    /** The furthest any part of the shape reaches from the middle. Zero for an empty shape. */
    public static double maxRank(List<Node> spine) {
        double max = 0.0D;
        for (Node node : spine) {
            max = Math.max(max, node.rank());
        }
        return max;
    }

    // ---------------------------------------------------------------- voxel targets

    /**
     * Expands a resampled canvas polyline into voxel targets, in canvas-local {@code u, w, v}.
     *
     * <p>Left in canvas-local space on purpose: the painter re-applies {@link #toWorld} every frame,
     * so a shape set to keep rotating can follow the caster's view smoothly - which it could not do
     * if the rotation had been baked in here.
     *
     * <p>Each point of the spine gets a filled block of cubes: a lane for every pitch across the
     * path's normal, and a row for every pitch up (or down) the wall. Filling the across axis rather
     * than scattering one cube somewhere inside it is what makes the blood read as liquid instead of
     * as a spray of specks - a single jittered cube per column covers a fraction of a band many
     * times its own width, and the eye reads the rest as holes.
     *
     * <p>Jitter is kept well under one pitch, so it breaks coplanarity - which removes
     * coincident-face z-fighting as a whole class - without opening the grid back up.
     *
     * <p>{@code wallHeight} is signed: positive extrudes up from the drawn plane, negative down.
     *
     * @param out receives {@code u, w, v} per voxel; expansion stops when it is full
     * @return how many voxels were written
     */
    public static int expand(double[] spine, double wallHeight, double thickness, double pitch,
            int seed, int cap, float[] out) {
        if (spine == null || spine.length < 4 || !(pitch > 0.0D) || out == null) {
            return 0;
        }
        int columns = spine.length / 2;
        int rows = Math.max(1, (int) Math.round(Math.abs(wallHeight) / pitch));
        double rowStep = wallHeight / rows;
        double span = Math.max(0.0D, thickness) * 2.0D;
        int lanes = Math.max(1, (int) Math.round(span / pitch));
        double laneStep = lanes > 1 ? span / (lanes - 1) : 0.0D;
        // Fraying eats the top of the wall, which is right for a tall slab and wrong for a sheet
        // three cubes high - there it just punches holes in the thing it was meant to soften.
        boolean fray = rows >= 6;
        int limit = Math.min(cap, out.length / 3);
        int written = 0;

        for (int c = 0; c < columns && written < limit; c++) {
            double su = spine[c * 2];
            double sv = spine[c * 2 + 1];
            int prev = Math.max(0, c - 1);
            int next = Math.min(columns - 1, c + 1);
            double tu = spine[next * 2] - spine[prev * 2];
            double tv = spine[next * 2 + 1] - spine[prev * 2 + 1];
            double tangent = Math.sqrt(tu * tu + tv * tv);
            double nu = tangent > 1.0E-6D ? -tv / tangent : 1.0D;
            double nv = tangent > 1.0E-6D ? tu / tangent : 0.0D;

            for (int l = 0; l < lanes && written < limit; l++) {
                double offset = lanes > 1 ? -thickness + l * laneStep : 0.0D;
                for (int r = 0; r < rows && written < limit; r++) {
                    int salt = c * 7919 + r * 104729 + l * 33391 + seed;
                    float wobble = hash01(salt);
                    float along = hash01(salt + 1);
                    if (fray) {
                        double top = r / (double) rows;
                        // The last fifth of a tall wall ends ragged rather than in a ruled line.
                        if (top > 0.8D && along < (top - 0.8D) * 5.0D) {
                            continue;
                        }
                    }
                    double drift = (wobble - 0.5D) * pitch * 0.3D;
                    out[written * 3] = (float) (su + nu * (offset + drift));
                    out[written * 3 + 1] =
                            (float) (r * rowStep + (along - 0.5D) * pitch * 0.2D);
                    out[written * 3 + 2] = (float) (sv + nv * (offset + drift));
                    written++;
                }
            }
        }
        return written;
    }

    /**
     * How many voxels an expansion of this size wants before any cap. Callers use it to pick a
     * coarser pitch rather than to drop rows - thinning a wall by dropping rows leaves a comb.
     */
    public static int voxelDemand(int spinePoints, double wallHeight, double thickness,
            double pitch) {
        if (spinePoints <= 0 || !(pitch > 0.0D)) {
            return 0;
        }
        int rows = Math.max(1, (int) Math.round(Math.abs(wallHeight) / pitch));
        int lanes = Math.max(1, (int) Math.round(Math.max(0.0D, thickness) * 2.0D / pitch));
        return spinePoints * rows * lanes;
    }

    /** A stable value in {@code [0, 1)} from an integer. No allocation, no shared state. */
    public static float hash01(int value) {
        int h = value * 0x27D4EB2D;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h >>> 8) / (float) (1 << 24);
    }
}
