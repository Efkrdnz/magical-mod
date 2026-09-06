package com.efkrdnz.magical.client.renderer.fx;

import com.efkrdnz.magical.entity.fx.WrenchedItemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The profile shell plus the wrenched item itself, spinning slowly inside the sigil. */
public class WrenchedItemRenderer extends ProfileRendererShell<WrenchedItemEntity> {
    public WrenchedItemRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new ItemState();
    }

    @Override
    public void extractRenderState(WrenchedItemEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (state instanceof ItemState item) {
            item.stack = entity.stack();
        }
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(state, poseStack, buffer, packedLight);
        if (!(state instanceof ItemState item) || item.stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.15F + Mth.sin(state.age * 0.1F) * 0.05F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.age * 3.0F));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        Minecraft.getInstance().getItemRenderer().renderStatic(item.stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, Minecraft.getInstance().level, state.seed);
        poseStack.popPose();
    }

    public static class ItemState extends State {
        public ItemStack stack = ItemStack.EMPTY;
    }
}
