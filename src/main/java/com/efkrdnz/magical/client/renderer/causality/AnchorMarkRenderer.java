package com.efkrdnz.magical.client.renderer.causality;

import com.efkrdnz.magical.client.CausalAnchorOverlay;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A ring round every body the Causal Anchor hold is offering, drawn in the world rather than on the
 * screen.
 *
 * <p>Two decisions carry this. It is drawn <b>through terrain</b>, on the same glyph ink the
 * through-terrain windups use, because a Mark reaches 28 blocks and the body worth marking is
 * regularly the one behind the pillar - a chooser that hides its own candidates behind cover is
 * asking the wielder to guess. And focus is carried by the <b>shape</b> of the ring, solid against
 * dashed, not only by how bright it is: at twenty blocks through a wall, in additive ink over
 * whatever colour the sky happens to be, brightness is the one channel that cannot be trusted.
 *
 * <p>There is no projection arithmetic here at all. The ring is a quad at the body position with
 * the camera rotation applied, so the game does the projecting and the mark cannot drift off its
 * body when the field of view moves - which it does, every time the wielder sprints.
 */
public final class AnchorMarkRenderer {

    private static final int ACCENT = 0xE8A33D;
    private static final int MARKED = 0xFFC85C;
    private static final int RESTING = 0x8FA2B5;

    private static final int SEGMENTS = 36;
    private static final int DASHES = 14;
    /** How far outside the body the ring sits, so it frames rather than covers. */
    private static final float MARGIN = 0.38F;

    /**
     * Where the glyph shader is actually asked for a ring, and why neither number is obvious.
     *
     * <p>{@code PHASE} is a position in a glyph lifetime, not a reveal: the shader reads 0 to 0.25
     * as inking on and 0.75 to 1 as burning away, so the innocent-looking 1.0 means <em>finished</em>
     * and draws precisely nothing. A mark that has to sit still for as long as a key is held lives
     * in the middle of its own life and never moves.
     *
     * <p>{@code BAND_INNER} and {@code WEIGHT} together decide how heavy the line is, because the
     * shader strokes the <em>middle</em> of whatever annulus it is handed: the geometry is the
     * gutter and the stroke is a fraction of it. A hairline band therefore yields a hairline no
     * matter how thick the weight, which at thirteen blocks is the same as nothing.
     */
    private static final float PHASE = 0.5F;
    private static final float BAND_INNER = 0.55F;
    private static final int WEIGHT = 3;
    /** The stroke lands at the middle of the band, so the band has to run wider than the ring. */
    private static final float BAND_MIDDLE = (1.0F + BAND_INNER) * 0.5F;

    private AnchorMarkRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        // F1 hides the rail and the reading with the rest of the modded HUD, so the rings go with
        // them: half a chooser left standing in an otherwise clean shot is worse than none of it.
        if (!CausalAnchorOverlay.active() || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        var offered = CausalAnchorOverlay.offered();
        if (offered.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.glyphInkThrough());
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        int focus = CausalAnchorOverlay.focusId();
        long now = minecraft.level.getGameTime();

        for (int id : offered) {
            if (!(minecraft.level.getEntity(id) instanceof LivingEntity body) || !body.isAlive()) {
                continue;
            }
            boolean focused = id == focus;
            boolean worn = wearsTheMark(id, now);
            Vec3 at = centre(body, partial);
            float radius = Math.max(body.getBbWidth(), body.getBbHeight()) * 0.5F + MARGIN;

            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(event.getCamera().rotation());
            var matrix = pose.last().pose();
            int rgb = focused ? ACCENT : worn ? MARKED : RESTING;
            if (focused) {
                // Solid, and a second ring just inside it: the body about to be marked reads as a
                // different object from the ones merely on offer, not as a brighter one.
                band(consumer, matrix, GlyphKind.SOLID_RING, 1, radius, WEIGHT, rgb, 0.95F);
                band(consumer, matrix, GlyphKind.SOLID_RING, 1, radius * 0.72F, WEIGHT - 1, rgb, 0.5F);
            } else {
                band(consumer, matrix, GlyphKind.DASHED_RING, DASHES, radius, WEIGHT - 1, rgb,
                        worn ? 0.75F : 0.55F);
            }
            pose.popPose();
        }
        buffers.endBatch(MagicalFxRenderTypes.glyphInkThrough());
    }

    private static void band(VertexConsumer consumer, org.joml.Matrix4f matrix, GlyphKind kind,
            int count, float radius, int weight, int rgb, float opacity) {
        float[] mesh = FxMesh.annulus(SEGMENTS, BAND_INNER);
        int packed = MagicVertex.pack(kind.id(), count, Math.max(0, weight), PHASE, 0, 0);
        float outer = radius / BAND_MIDDLE;
        FxMesh.emit(consumer, matrix, mesh, outer, outer, 1.0F, rgb, opacity, packed);
    }

    private static Vec3 centre(Entity body, float partial) {
        return body.getPosition(partial).add(0.0D, body.getBbHeight() * 0.5D, 0.0D);
    }

    private static boolean wearsTheMark(int entityId, long now) {
        var state = ClientMagicState.get();
        return state != null && state.anchor().set(now) && state.anchor().entityId() == entityId;
    }
}
