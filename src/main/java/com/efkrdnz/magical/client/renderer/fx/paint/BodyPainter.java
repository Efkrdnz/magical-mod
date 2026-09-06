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

/**
 * shard_body solids (depth-writing). CROSSED_BLADES (two crossed tapered blades along +Z), PLATE_FAN
 * (n stacked plates, radius sizeA, height sizeB), SPIKE_CLUSTER (n spikes of length sizeA), SLAB
 * (half-extent sizeA, height sizeB), PRISM (n-sided, radius sizeA, height sizeB), CAGE, BOULDER
 * (jittered sphere, radius sizeA), RING (a solid wheel), CHAIN (link ring stack), SPHERE, RIG.
 * ctx.phase is the INTEGRITY channel (spawn-in reversed, death forward).
 */
public final class BodyPainter {
    private BodyPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        FxKinds.Body kind = FxKinds.Body.values()[Math.floorMod(s.kind(), FxKinds.Body.values().length)];
        int rgb = profile.color(s.role());
        float opacity = s.opacity() * (ctx.opacity <= 0.0F ? 1.0F : ctx.opacity);
        int packed = MagicVertex.pack(kind.id(), Math.max(1, s.count()), s.paramB(), ctx.phase, ctx.seed, 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        PoseStack pose = ctx.pose;
        float size = s.sizeA();
        float height = s.sizeB() > 0.0F ? s.sizeB() : size;
        pose.pushPose();
        switch (s.form()) {
            case CROSSED_BLADES -> {
                pose.mulPose(Axis.ZP.rotationDegrees(ctx.age * 4.0F + ctx.seed * 5.6F));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.tube(), size * 0.22F, size * 0.22F, height, rgb, opacity, packed);
                FxBudget.countQuads(2);
            }
            case PLATE_FAN -> {
                float[] mesh = FxMesh.plateFan(Math.max(2, s.count()));
                FxMesh.emit(consumer, pose.last().pose(), mesh, size, height, size, rgb, opacity, packed);
                FxBudget.countQuads(mesh.length / 20);
            }
            case SPIKE_CLUSTER -> {
                pose.mulPose(Axis.YP.rotationDegrees(ctx.seed * 5.6F));
                float[] mesh = FxMesh.spikeCluster(Math.max(3, s.count()));
                FxMesh.emit(consumer, pose.last().pose(), mesh, size, size, size, rgb, opacity, packed);
                FxBudget.countQuads(mesh.length / 20);
            }
            case SLAB -> {
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.slab(), size, height, size * (s.paramB() > 20 ? 0.12F : 1.0F), rgb, opacity, packed);
                FxBudget.countQuads(6);
            }
            case PRISM -> {
                pose.mulPose(Axis.YP.rotationDegrees(ctx.seed * 5.6F));
                float[] mesh = FxMesh.prism(Math.max(3, s.count()));
                FxMesh.emit(consumer, pose.last().pose(), mesh, size, height, size, rgb, opacity, packed);
                FxBudget.countQuads(mesh.length / 20);
            }
            case CAGE -> {
                pose.translate(0.0F, height * 0.5F, 0.0F);
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.cage(), size, height * 0.5F, size, rgb, opacity, packed);
                FxBudget.countQuads(24);
            }
            case BOULDER, SPHERE -> {
                if (s.form() == Silhouette.Form.BOULDER) {
                    pose.mulPose(Axis.XP.rotationDegrees(ctx.extra));
                }
                float[] mesh = s.form() == Silhouette.Form.BOULDER ? FxMesh.boulder(ctx.seed) : FxMesh.sphere(16, 8);
                if (s.form() == Silhouette.Form.SPHERE) {
                    pose.translate(0.0F, size, 0.0F);
                }
                FxMesh.emit(consumer, pose.last().pose(), mesh, size, size, size, rgb, opacity, packed);
                FxBudget.countQuads(mesh.length / 20);
            }
            case RING -> {
                // a solid wheel standing on edge: ring band in XY (callers orient), thickness paramB
                pose.mulPose(Axis.ZP.rotationDegrees(ctx.extra));
                float[] mesh = FxMesh.ring(24, 0.18F);
                FxMesh.emit(consumer, pose.last().pose(), mesh, size, size, 1.0F, rgb, opacity, packed);
                // spokes
                float[] spokes = FxMesh.spikeCluster(Math.max(4, s.count()));
                FxMesh.emit(consumer, pose.last().pose(), spokes, size * 0.9F, size * 0.9F, 0.15F, rgb, opacity, packed);
                FxBudget.countQuads(24 + spokes.length / 20);
            }
            case CHAIN -> {
                // alternating ring links along +Z from 0..height
                int links = Math.max(2, Math.round(height / (size * 1.6F)));
                float[] ring = FxMesh.ring(12, 0.28F);
                for (int i = 0; i < links; i++) {
                    pose.pushPose();
                    pose.translate(0.0F, 0.0F, i * (height / links) + size * 0.5F);
                    pose.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? 0.0F : 90.0F));
                    pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                    FxMesh.emit(consumer, pose.last().pose(), ring, size, size, 1.0F, rgb, opacity, packed);
                    pose.popPose();
                }
                FxBudget.countQuads(links * 12);
            }
            case RIG -> {
                // a simple articulated rig: torso prism + head sphere + four leg spikes (placeholder for bespoke painters)
                float[] torso = FxMesh.prism(6);
                pose.pushPose();
                pose.translate(0.0F, size * 0.4F, 0.0F);
                FxMesh.emit(consumer, pose.last().pose(), torso, size * 0.5F, size * 0.6F, size * 0.35F, rgb, opacity, packed);
                pose.popPose();
                pose.pushPose();
                pose.translate(0.0F, size * 1.0F, size * 0.3F);
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.sphere(10, 5), size * 0.3F, size * 0.3F, size * 0.3F, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(torso.length / 20 + 50);
            }
            default -> {
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.prism(6), size, height, size, rgb, opacity, packed);
                FxBudget.countQuads(18);
            }
        }
        pose.popPose();
    }
}
