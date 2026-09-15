package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The debris a forged strike throws off its cutting lip.
 *
 * <p>Everything else in the school is a smooth ribbon, and a picture made only of smooth ribbons
 * reads as fog however bright it is: nothing in it is small, so nothing in it gives a sense of
 * scale or of force. These are the small thing. They are also the one place the grade shows as
 * something other than brightness - a divine blade throws a shower and a crude one throws a
 * handful - so a player can read the weapon off the strike twice over.
 *
 * <p>Drawn from a seed rather than simulated. A strike lives three ticks; a simulation would have
 * nowhere to keep its state, and a client that dropped a frame would see a different shower than
 * the one standing next to it.
 */
public final class ForgeSparks {

    /** The shower at the bottom of the grade ladder and at the top of it. */
    private static final int FEWEST = 3;
    private static final int MOST = 18;

    /** How far a spark may fly from the lip, as a fraction of the arc's own radius. */
    private static final float REACH = 0.34f;
    private static final float SLOWEST = 0.45f;
    private static final float FASTEST = 1.0f;

    /** The last fraction of a strike's life in which a new spark is still struck. */
    private static final float LAST_BIRTH = 0.45f;

    /** How far back in a spark's own flight its streak reaches. Long, so it reads as a line. */
    private static final float STREAK = 0.60f;
    /**
     * Half the width of a spark's head, as a fraction of the arc's radius.
     *
     * <p>Very small. At four times this a spark was a chunky arrowhead a third of a block across
     * and the shower read as a flight of darts rather than as grit: the whole reason these exist
     * is to put something small in a picture that otherwise has nothing small in it.
     */
    private static final float HEAD = 0.006f;
    private static final float SPARK_ALPHA = 255.0f;
    /** How far a spark sags over its flight. Enough to read as weight, not enough to read as rain. */
    private static final float DROOP = 0.35f;
    /** How far a spark wanders off the plane of the swing, as a fraction of how far it has flown. */
    private static final float SCATTER = 0.55f;

    /** Salts, so one seed gives every spark several independent numbers. */
    private static final int BIRTH = 0;
    private static final int ALONG = 1;
    private static final int SPREAD = 2;
    private static final int SPEED = 3;

    private ForgeSparks() {}

    /**
     * How many sparks a strike off a weapon of this grade throws.
     *
     * <p>An ordinal rather than the enum, for the same reason {@link ForgeWeaponLook#emission}
     * takes one: it arrives over the wire, and an ordinal this build does not recognise has to draw
     * something rather than nothing.
     */
    public static int count(int gradeOrdinal) {
        int grades = ForgeGrade.values().length;
        float t = gradeOrdinal < 0 || gradeOrdinal >= grades ? 0.5f : gradeOrdinal / (float) (grades - 1);
        return Math.round(Mth.lerp(t, FEWEST, MOST));
    }

    /** One of a spark's own numbers, in {@code [0, 1)} and the same on every client and frame. */
    public static float unit(int seed, int index, int salt) {
        int h = seed * 0x9E3779B9 + index * 0x85EBCA6B + salt * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    /**
     * How far through its flight a spark struck at {@code birth} is. Zero until the blade reaches
     * it, one when the strike ends: nothing is left hanging in the air after the cut is gone.
     */
    public static float life(float progress, float birth) {
        float struck = Mth.clamp(birth, 0.0f, 1.0f);
        float span = 1.0f - struck;
        if (span <= 1.0E-4f) {
            return Mth.clamp(progress, 0.0f, 1.0f) >= 1.0f ? 1.0f : 0.0f;
        }
        return Mth.clamp((progress - struck) / span, 0.0f, 1.0f);
    }

    /** How far out spark {@code index} has flown, as a fraction of the arc's radius. */
    public static float reach(int seed, int index, float progress) {
        return flown(life(progress, unit(seed, index, BIRTH) * LAST_BIRTH), unit(seed, index, SPEED));
    }

    /**
     * Strikes the whole shower off the arc's cutting lip.
     *
     * <p>Each spark is a short camera-facing streak running back along the way it came, so it reads
     * as a thing in motion from any angle rather than as a dot that happens to be lit.
     */
    public static void strike(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha,
            int seed, int gradeOrdinal, float progress, float camX, float camY, float camZ) {
        int shower = count(gradeOrdinal);
        float radius = Math.max(sweep.radius(), 1.0E-3f);
        for (int index = 0; index < shower; index++) {
            float life = life(progress, unit(seed, index, BIRTH) * LAST_BIRTH);
            if (life <= 0.0f || life >= 1.0f) {
                continue;
            }
            int glow = ForgeRibbon.alpha(SPARK_ALPHA, alpha * (1.0f - life));
            if (glow <= 0) {
                continue;
            }
            float speed = unit(seed, index, SPEED);
            float flown = flown(life, speed) * radius;
            float trailing = flown(Math.max(0.0f, life - STREAK), speed) * radius;

            // Off the lip, along the way the blade was going, with a little scatter square to it.
            float along = unit(seed, index, ALONG);
            float[] lip = sweep.at(along, 1.0f);
            float[] run = run(sweep, along);
            float[] scatter = ForgeRibbon.facing(sweep, along, camX, camY, camZ);
            float sideways = unit(seed, index, SPREAD) * 2.0f - 1.0f;

            float[] head = fly(lip, run, scatter, sideways, flown, radius);
            float[] tail = fly(lip, run, scatter, sideways, trailing, radius);
            streak(edge, pose, head, tail, radius * HEAD, palette.edge(), glow, camX, camY, camZ);
        }
    }

    /**
     * How far a spark of this speed has flown at this point in its life, as a fraction of radius.
     *
     * <p>Eased out: a spark leaves fast and slows, which is what makes it read as thrown rather
     * than as a line being extruded.
     */
    private static float flown(float life, float speed) {
        float t = Mth.clamp(life, 0.0f, 1.0f);
        return REACH * Mth.lerp(Mth.clamp(speed, 0.0f, 1.0f), SLOWEST, FASTEST) * (1.0f - (1.0f - t) * (1.0f - t));
    }

    /** Where a spark is after flying {@code out} blocks: outward along the swing, and sagging. */
    private static float[] fly(float[] lip, float[] run, float[] scatter, float sideways, float out, float radius) {
        float drift = out * SCATTER * sideways;
        float sag = DROOP * out * out / radius;
        return new float[] {
                lip[0] + run[0] * out + scatter[0] * drift,
                lip[1] + run[1] * out + scatter[1] * drift - sag,
                lip[2] + run[2] * out + scatter[2] * drift};
    }

    /** The unit direction the blade is travelling at {@code t}: where the debris is thrown. */
    private static float[] run(Sweep sweep, float t) {
        float step = 0.02f;
        float[] before = sweep.at(Math.max(0.0f, t - step), 1.0f);
        float[] after = sweep.at(Math.min(1.0f, t + step), 1.0f);
        return unitOr(after[0] - before[0], after[1] - before[1], after[2] - before[2],
                new float[] {0.0f, 0.0f, 1.0f});
    }

    /** One spark: a camera-facing sliver from its tail to its head, tapering to a point in front. */
    private static void streak(VertexConsumer edge, Matrix4f pose, float[] head, float[] tail, float half,
            int color, int alpha, float camX, float camY, float camZ) {
        float runX = head[0] - tail[0];
        float runY = head[1] - tail[1];
        float runZ = head[2] - tail[2];
        float[] across = unitOr(runY * (camZ - head[2]) - runZ * (camY - head[1]),
                runZ * (camX - head[0]) - runX * (camZ - head[2]),
                runX * (camY - head[1]) - runY * (camX - head[0]),
                new float[] {0.0f, 1.0f, 0.0f});
        // The two head corners coincide, so the quad is a triangle: a spark comes to a point at the
        // front and is widest where it was a moment ago. The v stays on the spine the whole way, so
        // the shader draws it as light rather than hanging a cutting lip off a piece of grit.
        FusionGeometry.quad(edge, pose,
                tail[0] + across[0] * half, tail[1] + across[1] * half, tail[2] + across[2] * half, 0.04f, 0.5f,
                tail[0] - across[0] * half, tail[1] - across[1] * half, tail[2] - across[2] * half, 0.04f, 0.5f,
                head[0], head[1], head[2], 0.96f, 0.5f,
                head[0], head[1], head[2], 0.96f, 0.5f,
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }

    /** The vector normalised, or {@code fallback} if it is too short to have a direction. */
    private static float[] unitOr(float x, float y, float z, float[] fallback) {
        float lengthSqr = x * x + y * y + z * z;
        if (lengthSqr <= 1.0E-10f) {
            return fallback;
        }
        float length = (float) Math.sqrt(lengthSqr);
        return new float[] {x / length, y / length, z / length};
    }
}
