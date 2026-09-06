package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.CircleLayer;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Interprets a {@link CircleScript} into glyph_ink quads in the local XY plane (callers orient the
 * pose: pitch 90 for ground circles, camera/look orientation for muzzle rings). One cached annulus
 * per band layer, one quad per planar layer, orbit children instanced by the CPU.
 */
public final class GlyphCirclePainter {
    private static final int SEGMENTS = 40;

    private GlyphCirclePainter() {}

    /** Silhouette entry point: a GLYPH family silhouette draws the profile's cast circle. */
    public static void paintSilhouette(FxContext ctx, VisualProfile profile, Silhouette silhouette) {
        paint(profile.castCircle(), profile.palette(), silhouette.sizeA(), ctx.age, ctx.life, ctx.detail, ctx.pose, ctx.buffers, ctx.seed, false, ctx.fade(), ctx.extra);
    }

    public static void paint(CircleScript script, Palette palette, float radius, float age, float life, int detail, PoseStack pose, MultiBufferSource buffers, int seed, boolean throughTerrain, float opacity, float sweepBearing) {
        if (opacity <= 0.002F) {
            return;
        }
        RenderType additive = throughTerrain ? MagicalFxRenderTypes.glyphInkThrough() : MagicalFxRenderTypes.glyphInk();
        RenderType ink = MagicalFxRenderTypes.glyphVoid();
        int stacks = Math.max(1, script.stackCount());
        for (int s = 0; s < stacks; s++) {
            pose.pushPose();
            pose.translate(0.0F, 0.0F, s * script.stackSpacing());
            float stackScale = 1.0F - s * 0.12F;
            for (CircleLayer layer : script.layers()) {
                if (layer.importance() > detail) {
                    continue;
                }
                if (layer.orbitCount() > 0) {
                    paintOrbit(layer, script, palette, radius * stackScale, age, life, pose, buffers, seed, additive, ink, opacity);
                    continue;
                }
                paintLayer(layer, script, palette, radius * stackScale, age, life, pose, buffers, seed, additive, ink, opacity, sweepBearing);
            }
            pose.popPose();
        }
    }

    private static void paintOrbit(CircleLayer layer, CircleScript script, Palette palette, float radius, float age, float life, PoseStack pose, MultiBufferSource buffers, int seed, RenderType additive, RenderType ink, float opacity) {
        int count = layer.orbitCount();
        float orbitAngle = age * layer.orbitSpeed() * 1.4F * Mth.DEG_TO_RAD;
        for (int i = 0; i < count; i++) {
            float a = orbitAngle + i * Mth.TWO_PI / count;
            pose.pushPose();
            pose.translate(Mth.cos(a) * layer.orbitRadius() * radius, Mth.sin(a) * layer.orbitRadius() * radius, 0.002F);
            paintLayer(layer, script, palette, radius, age, life, pose, buffers, seed + i, additive, ink, opacity, 0.0F);
            pose.popPose();
        }
    }

    private static void paintLayer(CircleLayer layer, CircleScript script, Palette palette, float radius, float age, float life, PoseStack pose, MultiBufferSource buffers, int seed, RenderType additive, RenderType ink, float opacity, float sweepBearing) {
        float phase = layerPhase(layer, age, life);
        if (phase <= 0.0F) {
            return;
        }
        boolean dark = layer.role() == ColorRole.INK;
        int rgb = palette.of(layer.role());
        int mode = dark ? 1 : 0;
        float spinDeg = layer.spinDegPerTick() * age + seed * 5.6F + layer.arcStartDeg();
        if (layer.kind() == GlyphKind.SPOKES && sweepBearing != 0.0F && layer.count() == 1) {
            spinDeg = sweepBearing;
        }
        // the charge meter maps the caller's extra scalar to the fill fraction
        float shaderPhase = layer.kind() == GlyphKind.ARC_SWEEP ? Mth.clamp(sweepBearing, 0.0F, 1.0F) : phase;
        int packed = MagicVertex.pack(layer.kind().id(), layer.count(), layer.paramB(), shaderPhase, seed, mode);
        float layerOpacity = opacity * layer.intensity();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(spinDeg));
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(dark ? ink : additive);
        if (layer.kind().isBand()) {
            float outer = layer.r1() * radius;
            float innerRatio = layer.r0() / Math.max(layer.r1(), 0.001F);
            float[] mesh = layer.isPartialArc()
                    ? FxMesh.arc(Math.max(6, Math.round(SEGMENTS * layer.arcSweepDeg() / 360.0F)), innerRatio, 0.0F, layer.arcSweepDeg())
                    : FxMesh.annulus(SEGMENTS, innerRatio);
            int copies = layer.isPartialArc() && layer.mirrorAxes() > 0 ? layer.mirrorAxes() : 1;
            for (int c = 0; c < copies; c++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(c * 360.0F / copies));
                FxMesh.emit(consumer, pose.last().pose(), mesh, outer, outer, 1.0F, rgb, layerOpacity, packed);
                pose.popPose();
            }
            FxBudget.countQuads(mesh.length / 20 * copies);
        } else {
            float half = layer.r1() * radius;
            float[] mesh = layer.kind() == GlyphKind.FRAME ? FxMesh.trapezoidAnnulus(0.72F) : FxMesh.square();
            FxMesh.emit(consumer, matrix, mesh, half, half, 1.0F, rgb, layerOpacity, packed);
            FxBudget.countQuads(mesh.length / 20);
        }
        pose.popPose();
    }

    /**
     * Layer lifecycle: 0..0.25 ink-on over inkTicks after inkDelay, sustain, and 0.75..1 dissolve
     * over the last 28% of life. Following/state circles pass life <= 0 and loop the sustain.
     */
    private static float layerPhase(CircleLayer layer, float age, float life) {
        float local = age - layer.inkDelay();
        if (local < 0.0F) {
            return 0.0F;
        }
        float inkOn = Mth.clamp(local / Math.max(1.0F, layer.inkTicks()), 0.0F, 1.0F);
        if (life <= 0.0F) {
            // following/state circles: ink in, then hold mid-sustain until the effect ends
            return inkOn < 1.0F ? inkOn * 0.25F : 0.5F;
        }
        float dissolveStart = life * 0.72F;
        if (age > dissolveStart) {
            return 0.75F + Mth.clamp((age - dissolveStart) / Math.max(1.0F, life - dissolveStart), 0.0F, 0.999F) * 0.25F;
        }
        return inkOn * 0.25F + (inkOn >= 1.0F ? 0.25F : 0.0F);
    }
}
