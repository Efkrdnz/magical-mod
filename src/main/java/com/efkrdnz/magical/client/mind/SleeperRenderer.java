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

import java.util.UUID;

/**
 * A dreamer's body: their skin, lying down the way they faced. The entity stands on the server, so
 * its box is big enough to hit; only the drawing lies down, with the bed turned to its yaw, because
 * with no bed the game would lay every sleeper due north.
 */
public final class SleeperRenderer extends LivingEntityRenderer<SleeperEntity, PlayerRenderState, PlayerModel> {
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
        state.eyeHeight = 1.62F;
    }

    @Override
    public void render(PlayerRenderState state, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = state.skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(state, pose, buffers, light);
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
