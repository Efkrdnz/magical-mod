package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * surface_field bodies: DOME, SPHERE, CYLINDER (radius sizeA, height sizeB), WALL/PANE (half-width
 * sizeA, height sizeB, facing local +Z), SLAB (half-extent sizeA, height sizeB), CAGE (hairline box).
 * DISC delegates to the ground_mark painter. Fresnel is baked into vertex opacity.
 */
public final class FieldPainter {
    private FieldPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        if (s.form() == Silhouette.Form.DISC) {
            MarkPainter.paint(ctx, profile, s);
            return;
        }
        FxKinds.Field kind = FxKinds.Field.values()[Math.floorMod(s.kind(), FxKinds.Field.values().length)];
        int rgb = profile.color(s.role());
        float opacity = ctx.fade() * s.opacity();
        int packed = MagicVertex.pack(kind.id(), s.count(), s.paramB(), ctx.phase, ctx.seed, 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.surfaceField());
        PoseStack pose = ctx.pose;
        float radius = s.sizeA();
        float height = s.sizeB() > 0.0F ? s.sizeB() : radius;
        switch (s.form()) {
            case DOME -> {
                float[] mesh = FxMesh.dome(24, 8);
                FxMesh.emitShaded(consumer, pose.last().pose(), mesh, radius, radius, radius, rgb, opacity, packed, fresnel(ctx, radius));
                FxBudget.countQuads(mesh.length / 20);
            }
            case SPHERE -> {
                float[] mesh = FxMesh.sphere(24, 12);
                FxMesh.emitShaded(consumer, pose.last().pose(), mesh, radius, radius, radius, rgb, opacity, packed, fresnel(ctx, radius));
                FxBudget.countQuads(mesh.length / 20);
            }
            case CYLINDER -> {
                float[] mesh = FxMesh.cylinderWall(24);
                FxMesh.emitShaded(consumer, pose.last().pose(), mesh, radius, height, radius, rgb, opacity, packed, fresnel(ctx, radius));
                FxBudget.countQuads(24);
            }
            case WALL, PANE, VERTICAL_PANE -> {
                pose.pushPose();
                // a thin box: front/back faces carry the material, thickness 0.05
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.slab(), radius, height, Math.max(0.05F, s.paramB() * 0.0F + 0.06F), rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(6);
            }
            case SLAB, BASIN -> {
                pose.pushPose();
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.slab(), radius, height, radius, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(6);
            }
            case LANE -> {
                // a flat strip along local +Z: half-width sizeA, length sizeB, a hand-span thick
                pose.pushPose();
                pose.translate(0.0F, 0.0F, height * 0.5F);
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.slab(), radius, 0.08F, height * 0.5F, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(6);
            }
            case CAGE -> {
                pose.pushPose();
                pose.translate(0.0F, height * 0.5F, 0.0F);
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.cage(), radius, height * 0.5F, radius, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(24);
            }
            case CONE_SPOT -> {
                // a searchlight cone: cylinder wall scaled to a frustum is approximated by a tapered column
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(180.0F));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.cylinderWall(20), radius, height, radius, rgb, opacity * 0.6F, packed);
                pose.popPose();
                FxBudget.countQuads(20);
            }
            default -> {
                float[] mesh = FxMesh.dome(16, 6);
                FxMesh.emitShaded(consumer, pose.last().pose(), mesh, radius, radius, radius, rgb, opacity, packed, fresnel(ctx, radius));
                FxBudget.countQuads(mesh.length / 20);
            }
        }
    }

    /** CPU-baked fresnel: shells glow at their silhouette for zero shader cost. */
    private static FxMesh.VertexShade fresnel(FxContext ctx, float radius) {
        return (x, y, z, u, v) -> {
            float len = Mth.sqrt(x * x + y * y + z * z);
            if (len < 1.0E-4F) {
                return 1.0F;
            }
            // approximate view vector as the camera direction from the pose origin
            float dot = (float) ((x * ctx.cameraPos.x + y * ctx.cameraPos.y + z * ctx.cameraPos.z) / (len * Math.max(ctx.cameraPos.length(), 1.0E-3D)));
            float f = 1.0F - Math.abs(dot);
            return 0.35F + 0.65F * f * f;
        };
    }
}
