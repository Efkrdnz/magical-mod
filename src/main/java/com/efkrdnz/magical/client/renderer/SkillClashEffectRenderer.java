package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.fx.SpellParticles;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A parry, drawn as three beats: it stopped, it held, it was thrown back.
 *
 * <p>The old version was crossed flat quads on vanilla's lightning render type - two ribbons and a
 * two-plane "spark" standing in fixed world orientations. Seen from the side they were cards and
 * edge-on they were a line, which is the worst way to draw the single most satisfying moment in the
 * fight. Every layer here goes through {@link OrbPainter#billboard} instead, so it is oriented to
 * the camera and cannot be caught at a bad angle, and it uses the mod's own plasma-orb shader
 * rather than borrowing vanilla's.
 *
 * <p>The two colours are doing work and should not be swapped: the counter colour is the thing that
 * held, the incoming colour is the attack, and the attack is what gets scattered.
 */
public final class SkillClashEffectRenderer extends EntityRenderer<SkillClashEffectEntity, SkillClashEffectRenderer.State> {

    /** How long the white core burns. Short: this is the clang, not the aftermath. */
    private static final float FLASH_TICKS = 7.0F;

    /** How long the guard plane stays up before it lets go. */
    private static final float LENS_TICKS = 13.0F;

    /** And how long the fragments of the attack keep travelling. */
    private static final float SHARD_TICKS = 18.0F;

    private static final int SHARDS = 7;

    /**
     * Under this many blocks the bright layers pull back.
     *
     * <p>A clash is planted at the defender's own eye, so for the player who parried it is inside
     * their head, and a camera-facing flash there is a white screen rather than an effect. They are
     * not missing anything: the first-person shock ring is already doing that job for them, and it
     * was authored for the purpose. This is only about the world copy, which is for everyone else.
     */
    private static final float NEAR_FADE_BLOCKS = 2.6F;

    public SkillClashEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SkillClashEffectEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.incomingColor = entity.incomingColor();
        state.counterColor = entity.counterColor();
        state.life = entity.life();
        state.seed = entity.getId();
        state.cameraDistance = (float) entity.position()
                .distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (entity.takeClientBurst()) {
            // One shot, on the first frame this effect is drawn. Grit the shaders cannot give: real
            // particles that outlive the entity and fall.
            Vec3 at = entity.position();
            SpellParticles.burst(FxKinds.Smoke.GLASS_SPLINTER, at, Vec3.ZERO, 26, 0.42F, 0.16F, 26,
                    state.incomingColor, 1.0F, 26);
            SpellParticles.burst(FxKinds.Smoke.SPARK_STREAK, at, Vec3.ZERO, 18, 0.55F, 0.12F, 18,
                    state.counterColor, 1.0F, 26);
        }
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float life = Math.max(1.0F, state.life);
        float age = Math.min(state.ageInTicks, life);
        FxContext ctx = new FxContext(poseStack, buffer, 0.0F,
                entityRenderDispatcher.cameraOrientation(), Vec3.ZERO)
                .timing(age, life, state.seed);
        float progress = age / life;
        float near = Mth.clamp(state.cameraDistance / NEAR_FADE_BLOCKS, 0.0F, 1.0F);

        // 1. The clang. Full size on the first frame and gone in a third of a second - an impact
        //    that eases in has already stopped reading as an impact.
        if (age < FLASH_TICKS) {
            float t = age / FLASH_TICKS;
            OrbPainter.billboard(ctx, FxKinds.Orb.BLOOM_FLASH, 0.9F + 1.9F * easeOut(t),
                    0xFFFFFF, (1.0F - t * t) * near, t, 8, 12);
        }

        // 2. The guard. Held flat against the viewer so it is always the shape that stopped
        //    something, never a sliver seen edge-on.
        if (age < LENS_TICKS) {
            float t = age / LENS_TICKS;
            OrbPainter.billboard(ctx, FxKinds.Orb.HEX_LENS, 1.4F + 1.3F * easeOut(t),
                    state.counterColor, (1.0F - t) * (1.0F - t) * near, t, 6, 10);
        }

        // 3. Two waves leaving the point of contact, the guard's ahead of the attack's.
        OrbPainter.billboard(ctx, FxKinds.Orb.THIN_HALO, 0.9F + 5.6F * easeOut(progress),
                state.counterColor, fadeOut(progress, 2.0F), progress, 4, 8);
        OrbPainter.billboard(ctx, FxKinds.Orb.THIN_HALO, 0.5F + 3.6F * easeOut(progress),
                state.incomingColor, fadeOut(progress, 1.6F) * 0.75F, progress, 4, 6);

        // 4. The attack coming apart, in its own colour so it is legible as the thing that lost.
        if (age < SHARD_TICKS) {
            float t = age / SHARD_TICKS;
            float distance = 0.6F + 4.0F * easeOut(t);
            for (int i = 0; i < SHARDS; i++) {
                float yaw = Mth.TWO_PI * i / SHARDS + state.seed * 0.7F;
                float pitch = (hash(state.seed + i) - 0.5F) * 1.5F;
                poseStack.pushPose();
                poseStack.translate(
                        Mth.cos(yaw) * distance * Mth.cos(pitch),
                        Mth.sin(pitch) * distance,
                        Mth.sin(yaw) * distance * Mth.cos(pitch));
                OrbPainter.billboard(ctx, FxKinds.Orb.SHARD_DIAMOND, 0.62F * (1.0F - t),
                        state.incomingColor, 1.0F - t, t, 3, 4);
                poseStack.popPose();
            }
        }

        super.render(state, poseStack, buffer, packedLight);
    }

    /** Fast at the start, slow at the end - how something that was struck actually moves. */
    private static float easeOut(float t) {
        float inverted = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - inverted * inverted;
    }

    private static float fadeOut(float t, float power) {
        float remaining = Mth.clamp(1.0F - t, 0.0F, 1.0F);
        return (float) Math.pow(remaining, power);
    }

    /** Stable 0..1 scatter, so a given clash always throws its fragments the same way. */
    private static float hash(int value) {
        int x = value * 374761393 + 668265263;
        x = (x ^ (x >> 13)) * 1274126177;
        return ((x ^ (x >> 16)) & 0xFFFF) / 65535.0F;
    }

    public static final class State extends EntityRenderState {
        private int incomingColor = 0xF8FCFF;
        private int counterColor = 0xA57DFF;
        private int life = 24;
        private int seed;
        private float cameraDistance = 16.0F;
    }
}
