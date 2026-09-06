package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/**
 * ground_mark: one flat polar quad in the local XY plane (callers orient it to the surface normal,
 * pitch 90 for floors). Dark kinds go through the translucent twin, bright kinds add.
 */
public final class MarkPainter {
    private MarkPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        FxKinds.Mark kind = FxKinds.Mark.values()[Math.floorMod(s.kind(), FxKinds.Mark.values().length)];
        mark(ctx, kind, s.sizeA(), profile.color(s.role()), ctx.fade() * s.opacity(), ctx.phase, s.count(), s.paramB());
    }

    public static void mark(FxContext ctx, FxKinds.Mark kind, float radius, int rgb, float opacity, float phase, int count, int paramB) {
        boolean dark = kind.dark();
        int packed = MagicVertex.pack(kind.id(), count, paramB, phase, ctx.seed, dark ? 1 : 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(dark ? MagicalFxRenderTypes.groundMark() : MagicalFxRenderTypes.groundMarkAdd());
        ctx.pose.pushPose();
        ctx.pose.mulPose(Axis.ZP.rotationDegrees(ctx.seed * 5.6F));
        FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.square(), radius, radius, 1.0F, rgb, opacity, packed);
        ctx.pose.popPose();
        FxBudget.countQuads(1);
    }
}
