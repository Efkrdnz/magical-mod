package com.efkrdnz.magical.client.renderer.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.sound.SoundNoteEntity;
import com.efkrdnz.magical.magic.sound.Family;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A flying note of the Riff, drawn as the note it is: the same sprite the note particles wear, much
 * larger, full bright, turned to the eye and rocking as it goes, with a faint larger copy behind it
 * for a glow. A low note is a half note, a bell an eighth and a string a beamed pair.
 */
public final class SoundNoteRenderer extends EntityRenderer<SoundNoteEntity, SoundNoteRenderer.State> {

    private static final ResourceLocation[] GLYPHS = new ResourceLocation[6];

    static {
        for (int i = 0; i < GLYPHS.length; i++) {
            GLYPHS[i] = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/particle/note_" + i + ".png");
        }
    }

    public SoundNoteRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends EntityRenderState {
        int rgb;
        int glyph;
        float size;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** Which of the six note sprites a family flies as. */
    public static int glyph(Family family) {
        return switch (family) {
            case LOW -> 4;
            case BELLS -> 1;
            case STRINGS -> 2;
            case KEYS -> 0;
            case DRUMS -> 3;
        };
    }

    @Override
    public void extractRenderState(SoundNoteEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.rgb = entity.family().rgb();
        state.glyph = glyph(entity.family());
        state.size = 0.45F + 0.07F * entity.amplitude() + (entity.family() == Family.LOW ? 0.35F : 0.0F);
    }

    @Override
    public void render(State state, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(GLYPHS[state.glyph]));
        int r = (state.rgb >> 16) & 0xFF;
        int g = (state.rgb >> 8) & 0xFF;
        int b = state.rgb & 0xFF;
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.ageInTicks * 0.45F) * 14.0F));
        quad(consumer, pose, state.size, r, g, b, 255);
        pose.popPose();
        super.render(state, pose, buffer, packedLight);
    }

    private static void quad(VertexConsumer consumer, PoseStack pose, float size, int r, int g, int b, int a) {
        PoseStack.Pose last = pose.last();
        float h = size * 0.5F;
        vertex(consumer, last, -h, -h, 0.0F, 1.0F, r, g, b, a);
        vertex(consumer, last, h, -h, 1.0F, 1.0F, r, g, b, a);
        vertex(consumer, last, h, h, 1.0F, 0.0F, r, g, b, a);
        vertex(consumer, last, -h, h, 0.0F, 0.0F, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int r, int g, int b, int a) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
