package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.forge.ForgeElementKind;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * What makes a FROST cut read as frost and not merely blue. The palette carries the hue; this
 * carries the shape, so the eight elements stay apart even where the colours are close: one switch
 * on the kind decides how bright the body and its wake are, and which ornament goes over the edge.
 */
public final class ForgeElementAccent {

    /** The ornament laid over a finished ribbon, one per element that has one. */
    public enum Ornament { NONE, SHARDS, SPARKS, BLOOM, DRIPS, SEGMENTS, TWIN }

    /** How an element bends a strike: body brightness, wake direction, and the ornament on top. */
    public record Accent(float bodyAlpha, boolean invertTrail, Ornament ornament) {}

    private static final int SHARD_COUNT = 3;
    private static final int SPARK_STEPS = 9;
    private static final int SEGMENT_COUNT = 5;
    private static final float SHARD_SPAN = 0.09f;
    private static final float SHARD_OUT = 1.30f;
    private static final float SPARK_OUT = 1.28f;
    private static final float SPARK_WIDTH = 0.045f;
    private static final float DRIP_LENGTH = 0.55f;
    private static final float DRIP_WIDTH = 0.07f;
    private static final int DRIP_SAMPLES = 24;
    private static final float DRIP_LEVEL = 0.05f;
    private static final float DRIP_AT_LOW = 0.28f;
    private static final float DRIP_AT_HIGH = 0.72f;
    private static final float SEGMENT_FILL = 0.62f;
    private static final float TWIN_SHIFT = 9.0f;
    private static final float TWIN_SCALE = 1.12f;
    private static final float TWIN_ALPHA = 0.45f;
    private static final float BLOOM_SPREAD = 2.2f;
    private static final float BLOOM_ALPHA = 0.5f;

    private ForgeElementAccent() {}

    /** The single element table. Everything else in this class reads from what it returns. */
    public static Accent of(ForgeElementKind kind) {
        return switch (kind) {
            case FIRE -> new Accent(1.0f, false, Ornament.NONE);
            case FROST -> new Accent(1.0f, false, Ornament.SHARDS);
            case STORM -> new Accent(1.0f, false, Ornament.SPARKS);
            case VOID -> new Accent(0.55f, true, Ornament.NONE);
            case RADIANT -> new Accent(1.0f, false, Ornament.BLOOM);
            case VENOM -> new Accent(1.0f, false, Ornament.DRIPS);
            case TERRA -> new Accent(0.85f, false, Ornament.SEGMENTS);
            case GALE -> new Accent(1.0f, false, Ornament.TWIN);
            // Compounds read as the parent they most look like, darkened or brightened.
            case BLACK_FLAME -> new Accent(0.45f, true, Ornament.NONE);
            case EXPLOSION -> new Accent(1.0f, false, Ornament.BLOOM);
            case RIME_GALE -> new Accent(1.0f, false, Ornament.SHARDS);
            case PLASMA -> new Accent(1.0f, false, Ornament.SPARKS);
            case MAGMA -> new Accent(0.9f, false, Ornament.SEGMENTS);
            case HAILSTORM -> new Accent(1.0f, false, Ornament.SHARDS);
            case ECLIPSE -> new Accent(0.6f, true, Ornament.BLOOM);
            case BLIGHT -> new Accent(0.85f, false, Ornament.DRIPS);
            case VERDIGRIS -> new Accent(1.0f, false, Ornament.DRIPS);
        };
    }

    /** Lays the element ornament over a ribbon that has already been drawn. */
    public static void draw(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha,
            Accent accent) {
        if (alpha <= 0.0f) {
            return;
        }
        switch (accent.ornament()) {
            case SHARDS -> shards(edge, pose, sweep, palette, alpha);
            case SPARKS -> sparks(edge, pose, sweep, palette, alpha);
            case BLOOM -> bloom(edge, pose, sweep, palette, alpha);
            case DRIPS -> drips(edge, pose, sweep, palette, alpha);
            case SEGMENTS -> segments(edge, pose, sweep, palette, alpha);
            case TWIN -> ForgeRibbon.arc(edge, pose, sweep.scaled(TWIN_SCALE).shifted(TWIN_SHIFT), palette,
                    alpha * TWIN_ALPHA);
            case NONE -> { }
        }
    }

    /** FROST: the leading edge breaks into three crystal shards standing proud of the arc. */
    private static void shards(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        int color = palette.bloom();
        int value = ForgeRibbon.alpha(255.0f, alpha);
        for (int i = 0; i < SHARD_COUNT; i++) {
            float centre = (i + 1) / (float) (SHARD_COUNT + 1);
            ForgeSliver.spike(edge, pose, sweep.plane(), sweep.uv(centre - SHARD_SPAN, 1.0f),
                    sweep.uv(centre, SHARD_OUT), sweep.uv(centre + SHARD_SPAN, 1.0f), color, value);
        }
    }

    /** STORM: a jagged spark polyline hopping in and out along the whole cutting edge. */
    private static void sparks(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        int color = palette.bloom();
        int value = ForgeRibbon.alpha(240.0f, alpha);
        float[] previous = sweep.uv(0.0f, 1.0f);
        for (int i = 1; i <= SPARK_STEPS; i++) {
            float[] next = sweep.uv(i / (float) SPARK_STEPS, i % 2 == 0 ? 1.0f : SPARK_OUT);
            ForgeSliver.link(edge, pose, sweep.plane(), previous, next, SPARK_WIDTH, color, value);
            previous = next;
        }
    }

    /** RADIANT: a wide soft ribbon sitting behind the edge so the cut trails light. */
    private static void bloom(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        Sweep wide = new Sweep(sweep.plane(), sweep.radius(), sweep.thickness() * BLOOM_SPREAD,
                sweep.fromDegrees(), sweep.toDegrees());
        ForgeRibbon.arc(edge, pose, wide, new ForgePalette(palette.bloom(), palette.secondary(), palette.bloom()),
                alpha * BLOOM_ALPHA);
    }

    /**
     * VENOM: two tails hanging off the arc, falling along local down rather than along the sweep
     * plane's own second axis — that axis is −Z for a GROUND or a FORWARD sweep, so a slash or a
     * cleave used to throw its drips horizontally backwards and read as gale streaks.
     */
    private static void drips(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        int color = palette.secondary();
        int value = ForgeRibbon.alpha(215.0f, alpha);
        for (float t : dripAnchors(sweep)) {
            ForgeSliver.tail(edge, pose, sweep.at(t, 1.0f), DRIP_LENGTH, DRIP_WIDTH, color, value);
        }
    }

    /**
     * Where the arc actually hangs lowest, one point per half, so a tilted sweep drips from its real
     * bottom instead of a fixed pair of parameters. A level arc has no low point, so it keeps the
     * defaults rather than pinning both drips to whichever sample happened to come first.
     */
    private static float[] dripAnchors(Sweep sweep) {
        float[] found = {DRIP_AT_LOW, DRIP_AT_HIGH};
        float[] lowest = {Float.MAX_VALUE, Float.MAX_VALUE};
        float highest = -Float.MAX_VALUE;
        for (int i = 0; i <= DRIP_SAMPLES; i++) {
            float t = i / (float) DRIP_SAMPLES;
            float y = sweep.at(t, 1.0f)[1];
            int half = t < 0.5f ? 0 : 1;
            highest = Math.max(highest, y);
            if (y < lowest[half]) {
                lowest[half] = y;
                found[half] = t;
            }
        }
        return highest - Math.min(lowest[0], lowest[1]) < DRIP_LEVEL
                ? new float[] {DRIP_AT_LOW, DRIP_AT_HIGH} : found;
    }

    /** TERRA: the edge breaks into chunky slabs with bare gaps between them. */
    private static void segments(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        int value = ForgeRibbon.alpha(255.0f, alpha);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float t0 = i / (float) SEGMENT_COUNT;
            ForgeRibbon.band(edge, pose, sweep, t0, t0 + SEGMENT_FILL / SEGMENT_COUNT, 0.55f, 1.35f,
                    palette.edge(), value);
        }
    }
}
