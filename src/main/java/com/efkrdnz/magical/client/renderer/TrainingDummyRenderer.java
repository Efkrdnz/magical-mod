package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.TrainingDummyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A pale standing figure with its numbers above its head.
 *
 * <p>The model is the baked player biped left in its rest pose - no {@code setupAnim} call, so it
 * never flinches, leans or walks. A dummy that animates is a dummy you are reading instead of the
 * meter, and the meter is the entire point of it.
 */
public final class TrainingDummyRenderer extends EntityRenderer<TrainingDummyEntity, TrainingDummyRenderer.State> {

    /** Canvas over straw: clearly a practice target rather than a person standing very still. */
    private static final int SAND = 0xFFD9C9A8;
    private static final int HEADER_INK = 0xFFF7D774;
    private static final int LINE_INK = 0xFFE6EEF8;
    private static final ResourceLocation SKIN = DefaultPlayerSkin.getDefaultTexture();

    private final PlayerModel model;

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
    }

    public static final class State extends EntityRenderState {
        public float yaw;
        public String readout = "";
        public boolean parry;
        public boolean qte;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrainingDummyEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.readout = entity.readout();
        state.parry = entity.parryIncoming();
        state.qte = entity.alwaysQte();
    }

    @Override
    public void render(State state, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        // The same frame LivingEntityRenderer sets up: face the body yaw, flip into model space,
        // then drop the origin from the model's head anchor to the feet.
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(pose, buffer.getBuffer(model.renderType(SKIN)),
                packedLight, OverlayTexture.NO_OVERLAY, SAND);
        pose.popPose();
        lines(state, pose, buffer, packedLight);
        super.render(state, pose, buffer, packedLight);
    }

    /** The readout, billboarded above the head, one row per line the server formatted. */
    private void lines(State state, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        String[] rows = header(state, state.readout.isEmpty() ? new String[0] : state.readout.split("\n"));
        Font font = getFont();
        pose.pushPose();
        pose.translate(0.0F, 2.4F, 0.0F);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(-0.023F, -0.023F, 0.023F);
        Matrix4f matrix = pose.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.3F) * 255.0F) << 24;
        // Grows downward from a fixed top, so the block does not jump as lines come and go.
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            font.drawInBatch(row, -font.width(row) / 2.0F, i * 10.0F, i == 0 ? HEADER_INK : LINE_INK,
                    false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, background, packedLight);
        }
        pose.popPose();
    }

    /**
     * The title row, which says what the dummy is set to do.
     *
     * <p>Always drawn, meter or no meter: the two toggles change what happens when you swing at it,
     * and finding that out by swinging is a worse way to learn it than reading one line.
     */
    private static String[] header(State state, String[] readout) {
        StringBuilder title = new StringBuilder(Component.translatable("entity.magical.training_dummy").getString());
        if (state.parry) {
            title.append("  [").append(Component.translatable("gui.magical.training_dummy.parry_tag").getString()).append(']');
        }
        if (state.qte) {
            title.append("  [").append(Component.translatable("gui.magical.training_dummy.qte_tag").getString()).append(']');
        }
        String[] rows = new String[readout.length + 1];
        rows[0] = title.toString();
        System.arraycopy(readout, 0, rows, 1, readout.length);
        return rows;
    }
}
