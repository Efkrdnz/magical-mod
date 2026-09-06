package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.MagicOpponentEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class MagicOpponentRenderer extends HumanoidMobRenderer<MagicOpponentEntity, PlayerRenderState, PlayerModel> {
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;
    private ResourceLocation texture = DefaultSkinHolder.TEXTURE;

    public MagicOpponentRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public PlayerRenderState createRenderState() {
        return new PlayerRenderState();
    }

    @Override
    public void extractRenderState(MagicOpponentEntity entity, PlayerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        PlayerSkin skin = skin(entity);
        this.texture = skin.texture();
        this.model = skin.model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState state) {
        return texture;
    }

    private static PlayerSkin skin(MagicOpponentEntity entity) {
        GameProfile profile = profile(entity);
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
    }

    private static GameProfile profile(MagicOpponentEntity entity) {
        if (entity.copiedPlayerUuid() != null) {
            return new GameProfile(entity.copiedPlayerUuid(), stripCloneSuffix(entity.copiedPlayerName()));
        }
        return new GameProfile(entity.getUUID(), entity.getName().getString());
    }

    private static String stripCloneSuffix(String name) {
        if (name == null || name.isBlank()) {
            return "Mage Clone";
        }
        String suffix = "'s Clone";
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    private static final class DefaultSkinHolder {
        private static final ResourceLocation TEXTURE = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
    }
}
