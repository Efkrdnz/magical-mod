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

    public int quads() {
        return quads;
    }
}
