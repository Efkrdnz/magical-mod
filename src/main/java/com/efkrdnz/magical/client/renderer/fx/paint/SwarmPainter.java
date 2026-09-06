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
import net.minecraft.util.Mth;

/**
 * Cloud bodies drawn as N smoke_veil billboards from the seed, no particle engine involved: CLOUD
 * (sphere of radius sizeA), COLUMN (cylinder radius sizeA, height sizeB rising), POOL (flat disc
 * radius sizeA, thickness sizeB), FIGURE (a humanoid silhouette of puffs, height sizeB), SPHERE.
 * Positions animate deterministically from seed + age so the cloud rolls without state.
 */
public final class SwarmPainter {
    private SwarmPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        FxKinds.Smoke kind = FxKinds.Smoke.values()[Math.floorMod(s.kind(), FxKinds.Smoke.values().length)];
        boolean dark = kind.dark();
        int rgb = profile.color(s.role());
        float opacity = ctx.fade() * s.opacity();
        int count = Math.max(1, Math.round(s.count() * Math.max(0.15F, ctx.lod)));
        VertexConsumer consumer = ctx.buffers.getBuffer(dark ? MagicalFxRenderTypes.smokeVeil() : MagicalFxRenderTypes.smokeAdd());
        float radius = s.sizeA();
        float height = s.sizeB() > 0.0F ? s.sizeB() : radius;
        for (int i = 0; i < count; i++) {
            float h1 = hash(i * 3 + ctx.seed * 7);
            float h2 = hash(i * 5 + ctx.seed * 11 + 1);
            float h3 = hash(i * 7 + ctx.seed * 13 + 2);
            float life = Mth.positiveModulo(ctx.age * (0.01F + h3 * 0.02F) + h1, 1.0F);
            float x, y, z, size;
            switch (s.form()) {
                case COLUMN -> {
                    float r = radius * (0.3F + 0.7F * h1) * (0.4F + 0.6F * life);
                    float a = h2 * Mth.TWO_PI + ctx.age * 0.02F;
                    x = Mth.cos(a) * r;
                    z = Mth.sin(a) * r;
                    y = life * height;
                    size = radius * (0.35F + 0.4F * life);
                }
                case POOL -> {
                    float r = radius * Mth.sqrt(h1);
                    float a = h2 * Mth.TWO_PI - ctx.age * 0.01F;
                    x = Mth.cos(a) * r;
                    z = Mth.sin(a) * r;
                    y = h3 * height * 0.5F + 0.1F;
                    size = radius * 0.35F;
                }
                case FIGURE -> {
                    // torso column + head + shoulders: puffs distributed along a body profile
                    float t = h1;
                    float bodyR = t > 0.82F ? 0.16F : t > 0.6F ? 0.32F : 0.24F;
                    float a = h2 * Mth.TWO_PI + ctx.age * 0.05F;
                    x = Mth.cos(a) * bodyR * height * 0.5F * h3;
                    z = Mth.sin(a) * bodyR * height * 0.5F * h3;
                    y = t * height;
                    size = height * 0.14F;
                }
                default -> {
                    float r = radius * Mth.sqrt(h1);
                    float a = h2 * Mth.TWO_PI + ctx.age * 0.03F;
                    float b = h3 * Mth.PI;
                    x = Mth.cos(a) * Mth.sin(b) * r;
                    y = Mth.cos(b) * r + (s.form() == Silhouette.Form.SPHERE ? radius : 0.0F);
                    z = Mth.sin(a) * Mth.sin(b) * r;
                    size = radius * 0.3F;
                }
            }
            int packed = MagicVertex.pack(kind.id(), 40, dark ? 6 : 26, life, (ctx.seed + i) & 63, dark ? 1 : 0);
            ctx.pose.pushPose();
            ctx.pose.translate(x, y, z);
            if (ctx.cameraOrientation != null) {
                ctx.pose.mulPose(ctx.cameraOrientation);
            }
            ctx.pose.mulPose(Axis.ZP.rotationDegrees(h2 * 360.0F + ctx.age * (h3 - 0.5F) * 3.0F));
            FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.square(), size, size, 1.0F, rgb, opacity, packed);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(count);
    }

    static float hash(int n) {
        n = (n ^ 61) ^ (n >>> 16);
        n *= 9;
        n = n ^ (n >>> 4);
        n *= 0x27d4eb2d;
        n = n ^ (n >>> 15);
        return (n & 0xFFFF) / (float) 0xFFFF;
    }
}
