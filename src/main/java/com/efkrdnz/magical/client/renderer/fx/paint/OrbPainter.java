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

/** plasma_orb billboards: BILLBOARD (one camera-facing disc) or STACK (n discs up the local Y axis). */
public final class OrbPainter {
    private OrbPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        FxKinds.Orb kind = FxKinds.Orb.values()[Math.floorMod(s.kind(), FxKinds.Orb.values().length)];
        boolean dark = kind.dark();
        int rgb = profile.color(s.role());
        float opacity = ctx.fade() * s.opacity();
        int packed = MagicVertex.pack(kind.id(), s.count(), s.paramB(), ctx.phase, ctx.seed, dark ? 1 : 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(dark ? MagicalFxRenderTypes.plasmaOrbDark() : MagicalFxRenderTypes.plasmaOrb());
        int copies = s.form() == Silhouette.Form.STACK ? Math.max(2, s.count()) : 1;
        float spacing = s.sizeB() > 0.0F && copies > 1 ? s.sizeB() / (copies - 1) : 0.0F;
        for (int i = 0; i < copies; i++) {
            ctx.pose.pushPose();
            if (copies > 1) {
                ctx.pose.translate(0.0F, i * spacing, 0.0F);
            }
            if (ctx.cameraOrientation != null) {
                ctx.pose.mulPose(ctx.cameraOrientation);
            }
            ctx.pose.mulPose(Axis.ZP.rotationDegrees(ctx.seed * 5.6F + ctx.age * 0.4F));
            float r = s.sizeA() * (copies > 1 ? 1.0F - i * 0.08F : 1.0F);
            FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.square(), r, r, 1.0F, rgb, opacity, packed);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(copies);
    }

    /** One free-standing orb at the current pose (used by muzzle flashes and impact blooms). */
    public static void billboard(FxContext ctx, FxKinds.Orb kind, float radius, int rgb, float opacity, float phase, int count, int paramB) {
        int packed = MagicVertex.pack(kind.id(), count, paramB, phase, ctx.seed, kind.dark() ? 1 : 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(kind.dark() ? MagicalFxRenderTypes.plasmaOrbDark() : MagicalFxRenderTypes.plasmaOrb());
        ctx.pose.pushPose();
        if (ctx.cameraOrientation != null) {
            ctx.pose.mulPose(ctx.cameraOrientation);
        }
        FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.square(), radius, radius, 1.0F, rgb, opacity, packed);
        ctx.pose.popPose();
        FxBudget.countQuads(1);
    }
}
