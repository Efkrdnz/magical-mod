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
 * rift_cut sheets. VERTICAL_PANE / HORIZONTAL_SLIT_EYE face the camera (billboard, yaw only for
 * upright panes); GROUND_SEAM / GROUND_STAR lie flat; STACK draws three slightly offset sheets like
 * the old guillotine renderer. sizeA = half-width, sizeB = half-height, paramB = interior style.
 */
public final class RiftPainter {
    private RiftPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        if (!FxBudget.claimRift()) {
            return;
        }
        FxKinds.Rift kind = FxKinds.Rift.values()[Math.floorMod(s.kind(), FxKinds.Rift.values().length)];
        int rgb = profile.color(s.role());
        float opacity = ctx.fade() * s.opacity();
        int packed = MagicVertex.pack(kind.id(), s.count(), s.paramB(), ctx.phase, ctx.seed, 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.riftCut());
        ctx.pose.pushPose();
        switch (s.form()) {
            case GROUND_SEAM, GROUND_STAR, DISC -> ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            case VERTICAL_PANE -> {
                if (ctx.cameraOrientation != null) {
                    // yaw-only billboard: keep the pane upright
                    float yaw = (float) Math.toDegrees(Math.atan2(ctx.cameraPos.x, ctx.cameraPos.z));
                    ctx.pose.mulPose(Axis.YP.rotationDegrees(yaw));
                }
            }
            case HORIZONTAL_SLIT_EYE, BILLBOARD -> {
                if (ctx.cameraOrientation != null) {
                    ctx.pose.mulPose(ctx.cameraOrientation);
                }
            }
            default -> { }
        }
        int sheets = s.form() == Silhouette.Form.STACK ? 3 : 1;
        for (int i = 0; i < sheets; i++) {
            ctx.pose.pushPose();
            ctx.pose.translate(0.0F, 0.0F, (i - 1) * 0.04F);
            FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.square(), s.sizeA(), s.sizeB(), 1.0F, rgb, opacity * (i == 1 || sheets == 1 ? 1.0F : 0.55F), packed);
            ctx.pose.popPose();
        }
        ctx.pose.popPose();
        FxBudget.countQuads(sheets);
    }
}
