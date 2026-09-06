package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Wildfire: one flame-tongue column per burning frontier cell over an ember-crust carpet that
 * cools to black scorch; cells come from the entity's synced data (world block positions).
 */
public final class WildfirePainter {
    private static final int BURN_TICKS = 60;

    private WildfirePainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag data = ctx.data;
        if (data == null) {
            return;
        }
        ListTag cells = data.getList("Cells", Tag.TAG_COMPOUND);
        if (cells.isEmpty()) {
            return;
        }
        int hot = profile.color(ColorRole.HOT);
        int base = profile.color(ColorRole.BASE);
        int dim = profile.color(ColorRole.DIM);
        VertexConsumer flames = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        VertexConsumer crust = ctx.buffers.getBuffer(MagicalFxRenderTypes.surfaceField());
        int quads = 0;
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag c = cells.getCompound(i);
            BlockPos p = BlockPos.of(c.getLong("Pos"));
            float age = ctx.age - c.getInt("Tick");
            float burn = Mth.clamp(age / BURN_TICKS, 0.0F, 1.0F);
            Vec3 rel = Vec3.atBottomCenterOf(p).subtract(ctx.origin);
            int seed = (ctx.seed + i * 7) & 63;
            ctx.pose.pushPose();
            ctx.pose.translate(rel.x, rel.y + 0.02D, rel.z);
            // ember-crust carpet: flat slab that erodes to black as the cell burns out
            ctx.pose.pushPose();
            ctx.pose.translate(-0.5F, 0.0F, -0.5F);
            int crustPacked = MagicVertex.pack(FxKinds.Field.EMBER_CRUST.id(), 4, 3, 0.25F + burn * 0.7F, seed, 0);
            FxMesh.emit(crust, ctx.pose.last().pose(), FxMesh.slab(), 0.5F, 0.05F, 0.5F, com.efkrdnz.magical.magic.visual.Palette.mix(base, dim, burn), 0.9F, crustPacked);
            ctx.pose.translate(0.5F, 0.0F, 0.5F);
            ctx.pose.popPose();
            // flame tongues while burning
            if (burn < 1.0F) {
                float height = 0.9F * (1.0F - burn * 0.6F);
                int packed = MagicVertex.pack(FxKinds.Filament.FLAME_TONGUE.id(), 3, 4, 0.5F, seed, 0);
                ctx.pose.pushPose();
                ctx.pose.mulPose(Axis.YP.rotationDegrees(seed * 5.6F));
                FxMesh.emit(flames, ctx.pose.last().pose(), FxMesh.column(), 0.5F, height, 0.5F, hot, 1.0F - burn * 0.5F, packed);
                ctx.pose.popPose();
                quads += 2;
            }
            ctx.pose.popPose();
            quads += 6;
        }
        FxBudget.countQuads(quads);
    }
}
