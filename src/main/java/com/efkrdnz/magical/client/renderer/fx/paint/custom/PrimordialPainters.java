package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;
import com.efkrdnz.magical.magic.primordial.MassCodec;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The Primordial school's one bespoke painter: a mass of real blocks in flight - a falling star, a
 * torn-up plate - read off the effect's synced data ({@link MassCodec}) and drawn through the block
 * renderer at their offsets, turning at the effect's spin. A star glows; a plate takes the light of
 * where it is.
 */
public final class PrimordialPainters {
    private PrimordialPainters() {}

    public static void register() {
        CustomPainters.register("primordial_mass", PrimordialPainters::mass);
    }

    public static void mass(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag data = ctx.data;
        Level level = Minecraft.getInstance().level;
        if (data == null || level == null) {
            return;
        }
        int[] states = data.getIntArray(MassCodec.STATES);
        int[] offsets = data.getIntArray(MassCodec.OFFSETS);
        if (states.length == 0 || offsets.length != states.length) {
            return;
        }
        float scale = data.contains(MassCodec.SCALE) ? data.getFloat(MassCodec.SCALE) : 1.0F;
        float spin = data.getFloat(MassCodec.SPIN);
        int light = data.getBoolean(MassCodec.GLOW) ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(ctx.origin));
        ctx.pose.pushPose();
        if (spin != 0.0F) {
            ctx.pose.mulPose(Axis.YP.rotationDegrees(ctx.age * spin));
            ctx.pose.mulPose(Axis.XP.rotationDegrees(ctx.age * spin * 0.6F));
        }
        ctx.pose.scale(scale, scale, scale);
        for (int i = 0; i < states.length; i++) {
            BlockState state = Block.stateById(states[i]);
            if (state.isAir()) {
                continue;
            }
            int[] o = MassCodec.unpack(offsets[i]);
            ctx.pose.pushPose();
            ctx.pose.translate(o[0] - 0.5F, o[1] - 0.5F, o[2] - 0.5F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ctx.pose, ctx.buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ctx.pose.popPose();
        }
        ctx.pose.popPose();
        FxBudget.countQuads(states.length * 6);
    }
}
