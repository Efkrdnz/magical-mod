package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * The only writer of {@code hud_sigil} vertices. One instance is reused every frame; it holds the
 * consumer and pose for the duration of a {@code drawSpecial} block and counts what it emitted,
 * which is what the debug readout and the quad-budget test read.
 */
public final class HudBatch {
    // corner order: top-left, bottom-left, bottom-right, top-right; corner i takes TURN_U/V[(i + q) & 3]
    private static final float[] TURN_U = {0.0F, 0.0F, 1.0F, 1.0F};
    private static final float[] TURN_V = {0.0F, 1.0F, 1.0F, 0.0F};

    private VertexConsumer out;
    private Matrix4f pose;
    private int quads;

    public HudBatch begin(VertexConsumer out, Matrix4f pose) {
        this.out = out;
        this.pose = pose;
        this.quads = 0;
        return this;
    }

    /** A square quad centred on (cx, cy) with the given half-size. */
    public void square(float cx, float cy, float half, int rgb, float opacity, HudKind kind, int count, int paramB, float phase, int mode) {
        rect(cx - half, cy - half, cx + half, cy + half, rgb, opacity, kind, count, paramB, phase, 0, mode);
    }

    /** A square quad sized so a ring's outer edge lands exactly on {@code outerRadius}. */
    public void ring(float cx, float cy, float outerRadius, int rgb, float opacity, HudKind kind, int count, int paramB, float phase, int mode) {
        square(cx, cy, outerRadius / HudKind.RING_OUTER, rgb, opacity, kind, count, paramB, phase, mode);
    }

    public void rect(float x0, float y0, float x1, float y1, int rgb, float opacity, HudKind kind, int count, int paramB, float phase, int mode) {
        rect(x0, y0, x1, y1, rgb, opacity, kind, count, paramB, phase, 0, mode);
    }

    public void rect(float x0, float y0, float x1, float y1, int rgb, float opacity, HudKind kind, int count, int paramB, float phase, int seed, int mode) {
        int packed = MagicVertex.pack(kind.id(), count, paramB, phase, seed, mode);
        MagicVertex.quad(out, pose,
                x0, y0, 0.0F, x0, y1, 0.0F, x1, y1, 0.0F, x1, y0, 0.0F,
                0.0F, 0.0F, 1.0F, 1.0F, rgb, opacity, packed);
        quads++;
    }

    /**
     * A square whose drawing is turned by quarter turns clockwise, so a ring can start its fill at
     * nine o'clock (three turns) instead of twelve. Only the texture coordinates move.
     */
    public void squareTurned(float cx, float cy, float half, int quarterTurns, int rgb, float opacity, HudKind kind, int count, int paramB, float phase, int mode) {
        int packed = MagicVertex.pack(kind.id(), count, paramB, phase, 0, mode);
        int q = Math.floorMod(quarterTurns, 4);
        MagicVertex.emit(out, pose, cx - half, cy - half, 0.0F, TURN_U[q], TURN_V[q], rgb, opacity, packed);
        MagicVertex.emit(out, pose, cx - half, cy + half, 0.0F, TURN_U[(1 + q) & 3], TURN_V[(1 + q) & 3], rgb, opacity, packed);
        MagicVertex.emit(out, pose, cx + half, cy + half, 0.0F, TURN_U[(2 + q) & 3], TURN_V[(2 + q) & 3], rgb, opacity, packed);
        MagicVertex.emit(out, pose, cx + half, cy - half, 0.0F, TURN_U[(3 + q) & 3], TURN_V[(3 + q) & 3], rgb, opacity, packed);
        quads++;
    }

    /**
     * The upper half of a ring, turned so its fill runs from the left tip over the apex to the
     * right tip: the reflection below the waterline. Pass the fill as half the fraction.
     */
    public void halfDiscTop(float cx, float cy, float half, int rgb, float opacity, HudKind kind, int count, int paramB, float phase, int mode) {
        int packed = MagicVertex.pack(kind.id(), count, paramB, phase, 0, mode);
        // Turned three quarters: the shader's twelve o'clock is screen left, its right half is
        // the screen's upper half, so the quad covers only that half of the texture.
        MagicVertex.emit(out, pose, cx - half, cy - half, 0.0F, 1.0F, 0.0F, rgb, opacity, packed);
        MagicVertex.emit(out, pose, cx - half, cy, 0.0F, 0.5F, 0.0F, rgb, opacity, packed);
        MagicVertex.emit(out, pose, cx + half, cy, 0.0F, 0.5F, 1.0F, rgb, opacity, packed);
        MagicVertex.emit(out, pose, cx + half, cy - half, 0.0F, 1.0F, 1.0F, rgb, opacity, packed);
        quads++;
    }

    /** A LINE quad along a segment of any angle, {@code thickness} wide. */
    public void line(float x0, float y0, float x1, float y1, float thickness, int rgb, float opacity, int dashes, float phase, int mode) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001F) {
            return;
        }
        float nx = -dy / length * thickness * 0.5F;
        float ny = dx / length * thickness * 0.5F;
        int packed = MagicVertex.pack(HudKind.LINE.id(), dashes, 0, phase, 0, mode);
        MagicVertex.emit(out, pose, x0 + nx, y0 + ny, 0.0F, 0.0F, 0.0F, rgb, opacity, packed);
        MagicVertex.emit(out, pose, x0 - nx, y0 - ny, 0.0F, 0.0F, 1.0F, rgb, opacity, packed);
        MagicVertex.emit(out, pose, x1 - nx, y1 - ny, 0.0F, 1.0F, 1.0F, rgb, opacity, packed);
        MagicVertex.emit(out, pose, x1 + nx, y1 + ny, 0.0F, 1.0F, 0.0F, rgb, opacity, packed);
        quads++;
    }

    public int quads() {
        return quads;
    }
}
