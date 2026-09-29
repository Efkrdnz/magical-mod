package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * A dreamer's body: their skin, lying down the way they faced. The entity stands on the server, so
 * its box is big enough to hit; only the drawing lies down, with the bed turned to its yaw, because
 * with no bed the game would lay every sleeper due north.
 */
public final class SleeperRenderer extends LivingEntityRenderer<SleeperEntity, PlayerRenderState, PlayerModel> {
    /** Half of the model's 2.0-block length: how far back along the bed the body is shifted to sit centred on the entity. */
    private static final float BODY_CENTRE_SHIFT = 1.0F;
    private static final float LIFT = 0.125F;

    private final PlayerModel wide;
    private final PlayerModel slim;

    public SleeperRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public PlayerRenderState createRenderState() {
        return new PlayerRenderState();
    }

    @Override
    public void extractRenderState(SleeperEntity sleeper, PlayerRenderState state, float partialTick) {
        super.extractRenderState(sleeper, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(sleeper, state, partialTick, this.itemModelResolver);
        state.skin = skin(sleeper.dreamer().orElse(sleeper.getUUID()));
        state.pose = Pose.SLEEPING;
        state.bedOrientation = Direction.fromYRot(sleeper.getYRot());
        // The bed shifts the body back along its length by eyeHeight - 0.1. The unscaled model is 32 px
        // (2.0 blocks) foot to crown and runs from the origin forward, so 1.1 centres it on the entity.
        state.eyeHeight = BODY_CENTRE_SHIFT + 0.1F;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
    }

    @Override
    public void render(PlayerRenderState state, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = state.skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        // A laid-down model is centred vertically on the entity's y; vanilla seats a sleeper 0.125 above its mattress.
        pose.pushPose();
        pose.translate(0.0F, LIFT, 0.0F);
        super.render(state, pose, buffers, light);
        pose.popPose();
    }

    /** The body lies out to a block either side of the entity, past its hitbox, so the box the view is culled by is wider. */
    @Override
    protected AABB getBoundingBoxForCulling(SleeperEntity sleeper) {
        return sleeper.getBoundingBox().inflate(1.0, 0.0, 1.0);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState state) {
        return state.skin.texture();
    }

    @Override
    protected boolean shouldShowName(SleeperEntity sleeper, double distance) {
        return false;
    }

    /** The dreamer's own skin while they are online (they always are, or there is no body); the default for their UUID otherwise. */
    private static PlayerSkin skin(UUID dreamer) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(dreamer);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(dreamer);
    }
}
