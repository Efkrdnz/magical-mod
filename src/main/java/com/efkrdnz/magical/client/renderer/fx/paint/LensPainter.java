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

/** lens_warp billboards (radius sizeA, paramB = vertex warp strength). */
public final class LensPainter {
    private LensPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        if (!FxBudget.claimLens()) {
            return;
        }
        FxKinds.Lens kind = FxKinds.Lens.values()[Math.floorMod(s.kind(), FxKinds.Lens.values().length)];
        int rgb = profile.color(s.role());
        float opacity = ctx.fade() * s.opacity();
        int packed = MagicVertex.pack(kind.id(), s.count(), s.paramB(), ctx.phase, ctx.seed, 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.lensWarp());
        ctx.pose.pushPose();
        if (ctx.cameraOrientation != null) {
            ctx.pose.mulPose(ctx.cameraOrientation);
        }
        FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.square(), s.sizeA(), s.sizeA(), 1.0F, rgb, opacity, packed);
        ctx.pose.popPose();
        FxBudget.countQuads(1);
    }
}
