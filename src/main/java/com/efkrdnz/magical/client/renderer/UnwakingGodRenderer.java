package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.boss.unwaking.UnwakingGodEntity;
import com.efkrdnz.magical.boss.unwaking.UnwakingPhase;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;

/** A standard wide-arm player biped, using a completely opaque white skin. */
public final class UnwakingGodRenderer extends HumanoidMobRenderer<UnwakingGodEntity, PlayerRenderState, PlayerModel> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/entity/unwaking_god.png");

    public UnwakingGodRenderer(EntityRendererProvider.Context context) {
        super(context, new WhiteBipedModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override public PlayerRenderState createRenderState() { return new GodRenderState(); }
    @Override public ResourceLocation getTextureLocation(PlayerRenderState state) { return WHITE; }

    @Override public void extractRenderState(UnwakingGodEntity entity, PlayerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity.phase() == UnwakingPhase.DORMANT || entity.phase() == UnwakingPhase.SLEEPING) state.xRot = 25;
        state.walkAnimationSpeed = 0;
        ((GodRenderState) state).phase = entity.phase();
        ((GodRenderState) state).pose = com.efkrdnz.magical.client.ClientUnwakingEncounter.bodyPose(entity.getId());
    }

    private static final class GodRenderState extends PlayerRenderState {
        UnwakingPhase phase = UnwakingPhase.DORMANT;
        int pose;
    }
    /** Poses only: geometry, proportions and the opaque white texture stay player-standard. */
    private static final class WhiteBipedModel extends PlayerModel {
        WhiteBipedModel(net.minecraft.client.model.geom.ModelPart root) { super(root, false); }
        @Override public void setupAnim(PlayerRenderState state) {
            super.setupAnim(state);
            if (!(state instanceof GodRenderState god)) return;
            if (god.phase == UnwakingPhase.UNBODYING || god.phase == UnwakingPhase.REFORMING) {
                leftArm.zRot = -1.15F; rightArm.zRot = 1.15F; head.xRot = -0.15F;
            } else if (god.phase == UnwakingPhase.DEATH) {
                head.xRot = 0.25F; rightArm.xRot = -1.1F; leftArm.xRot = 0.1F;
            } else if (god.pose == 2) {
                rightArm.xRot = -1.6F; rightArm.yRot = -0.25F;
            } else if (god.pose == 1) {
                rightArm.xRot = -1.4F; leftArm.xRot = -0.7F; leftArm.zRot = -0.25F;
            } else if (god.pose == 3) {
                rightArm.zRot = 0.55F; leftArm.zRot = -0.55F; head.xRot = 0.1F;
            }
        }
    }
}
