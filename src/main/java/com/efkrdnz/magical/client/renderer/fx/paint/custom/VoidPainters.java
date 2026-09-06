package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.magic.skill.voidschool.GravemoonsSkill;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Bespoke painters for the VOID school. */
public final class VoidPainters {
    private VoidPainters() {}

    /** Three tumbling obsidian shards on the orbit (alive mask in ctx.extra as an int) plus a faint dark rope ring. */
    public static void gravemoons(FxContext ctx, VisualProfile profile, Silhouette s) {
        int alive = ctx.data != null && ctx.data.contains("Alive") ? ctx.data.getInt("Alive") : 0b111;
        boolean reversed = ctx.data != null && ctx.data.getBoolean("Rev");
        float radius = reversed ? s.sizeA() * 0.64F : s.sizeA();
        int rgb = profile.color(ColorRole.BASE);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.OBSIDIAN.id(), 5, 14, 0.2F, ctx.seed, 0);
        int quads = 0;
        for (int i = 0; i < GravemoonsSkill.SHARDS; i++) {
            if ((alive & (1 << i)) == 0) {
                continue;
            }
            Vec3 off = GravemoonsSkill.shardOffset(i, ctx.age, radius, reversed, 1.0F);
            ctx.pose.pushPose();
            ctx.pose.translate(off.x, off.y, off.z);
            ctx.pose.mulPose(Axis.YP.rotationDegrees(ctx.age * 7.0F + i * 120.0F));
            ctx.pose.mulPose(Axis.XP.rotationDegrees(ctx.age * 4.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.tube(), 0.16F, 0.16F, 0.55F, rgb, 1.0F, packed);
            ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.tube(), 0.16F, 0.16F, 0.55F, rgb, 1.0F, packed);
            ctx.pose.popPose();
            quads += 4;
        }
        // orbit path: a dark rope ring, nearly invisible
        VertexConsumer rope = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
        ctx.pose.pushPose();
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        FxMesh.emit(rope, ctx.pose.last().pose(), FxMesh.ring(40, 0.02F), radius, radius, 1.0F, profile.color(ColorRole.INK), 0.35F, MagicVertex.pack(FxKinds.Filament.ROPE.id(), 3, 0, 0.5F, ctx.seed, 1));
        ctx.pose.popPose();
        FxBudget.countQuads(quads + 40);
    }

    /** The descending ink ceiling: a huge dark disc, swaying tendrils to the ground, and its shadow. */
    public static void firmament(FxContext ctx, VisualProfile profile, Silhouette s) {
        float radius = s.sizeA();
        float height = Math.max(0.5F, ctx.extra);
        int ink = profile.color(ColorRole.INK);
        int base = profile.color(ColorRole.BASE);
        float form = Mth.clamp((ctx.age - 20.0F) / 10.0F, 0.0F, 1.0F);
        if (ctx.life > 0.0F) {
            form *= Mth.clamp((ctx.life - ctx.age) / 20.0F, 0.0F, 1.0F);
        }
        if (form <= 0.0F) {
            return;
        }
        // the disc itself (dark, flat)
        ctx.pose.pushPose();
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.INK_STAIN, radius * form, ink, 0.95F, 0.5F, 8, 2);
        ctx.pose.popPose();
        // shadow on the ground
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, -height + 0.05F, 0.0F);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.VORTEX_SPIRAL, radius * 0.95F * form, base, 0.6F, 0.5F, 5, 8);
        ctx.pose.popPose();
        // 24 tendrils from the rim down to the ground
        VertexConsumer tendril = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
        int packed = MagicVertex.pack(FxKinds.Filament.INK_TENDRIL.id(), 3, 20, 0.5F, ctx.seed, 1);
        int count = Math.max(6, Math.round(24 * Math.max(0.3F, ctx.lod)));
        for (int i = 0; i < count; i++) {
            float a = i / (float) count * Mth.TWO_PI;
            float sway = Mth.sin(ctx.age * 0.06F + i * 1.7F) * 0.6F;
            ctx.pose.pushPose();
            ctx.pose.translate(Mth.cos(a) * radius * 0.92F * form, 0.0F, Mth.sin(a) * radius * 0.92F * form);
            ctx.pose.mulPose(Axis.YP.rotationDegrees(-a * Mth.RAD_TO_DEG));
            ctx.pose.mulPose(Axis.ZP.rotationDegrees(sway * 8.0F));
            ctx.pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            FxMesh.emit(tendril, ctx.pose.last().pose(), FxMesh.column(), 0.22F, height, 0.22F, ink, 0.85F * form, packed);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(2 + count * 2);
    }

    public static void register() {
        CustomPainters.register("gravemoons", VoidPainters::gravemoons);
        CustomPainters.register("firmament", VoidPainters::firmament);
    }
}
