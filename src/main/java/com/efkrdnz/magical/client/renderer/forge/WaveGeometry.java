package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * WAVE: the thrown front. A sheet of force standing across the flight, bulging toward where it is
 * going, with its wake strung out behind it and shrinking as it goes.
 *
 * <p>It has been wrong twice, both times about which way it leans. First it was a ring band hanging
 * a radius off the flight path and rolled forty degrees, so it read as a moon tipped up and to the
 * right; the picture also claimed nearly four times the width the wave catches in, which is the
 * kind of lie that reads as the game not registering a hit. Then it was a crescent sized off
 * {@code WaveShape} and sitting on the strike, but bowed belly-down - still a direction, still
 * arbitrary, and still not the direction the thing was travelling.
 *
 * <p>The fix is to stop drawing an arc. An arc has a belly and a belly points somewhere; a surface
 * of revolution about the aim does not, so there is nothing left to get wrong. {@link
 * ForgeWaveFront} is that surface and {@code WaveSilhouetteTest} holds it to both halves of this:
 * the same shape at every roll angle, and never drawn wider or deeper than {@code HitShapes} says
 * the wave actually catches.
 */
public final class WaveGeometry {

    /**
     * The front's radius, as a fraction of the strike's half-width.
     *
     * <p>Under {@code WaveShape.LATERAL_FRACTION} (0.48), which is what the wave actually catches
     * either side of its flight line. The margin is what the rim glow spends.
     */
    private static final float RADIUS = 0.46f;

    /**
     * The widest the front may ever be drawn, whatever the weapon.
     *
     * <p>{@code WaveShape.VERTICAL_INFLATION} is a flat 0.9 blocks and does not grow with the
     * half-width, so a heavy divine blade would otherwise be drawn reaching a block and a half up
     * and down while still only catching within 0.9.
     */
    private static final float CEILING = 0.9f;

    /**
     * How far the middle of the front stands ahead of its rim, as a fraction of half-width.
     *
     * <p>This is the whole of "facing forward", and it is the only direction on the shape. Also
     * under {@code LATERAL_FRACTION}, because the wave's forward pad is the same 0.48.
     */
    private static final float DEPTH = 0.40f;

    /** How broad the rim reads to the things that decorate an edge, as a fraction of half-width. */
    private static final float THICKNESS = 0.42f;

    /** How big the front is when it leaves the hand, as a fraction of its full size. */
    private static final float FROM = 0.55f;

    /**
     * How far the wake strings out behind the head, and how much each copy back has shrunk.
     *
     * <p>The thrower stands behind a wave, so its wake is the part nearest the camera and every
     * copy of it is drawn larger than the head by perspective alone. Left at a swing's numbers the
     * oldest copy framed the whole strike in a wide soft ring, and what the eye took for the size
     * of the wave was the faintest thing in it. Short and tapering, the wake sits inside the edge.
     */
    private static final float TRAIL_GAP = 0.26f;
    private static final float TRAIL_SHRINK = 0.20f;

    private WaveGeometry() {}

    /**
     * How far out from the flight line the front reaches at full size.
     *
     * <p>Public and pure so the silhouette can be measured against the hit volume without a frame
     * being drawn.
     */
    public static float radius(float halfWidth) {
        return Math.min(halfWidth * RADIUS, CEILING);
    }

    /** How far the middle of the front leads its rim at full size. */
    public static float depth(float halfWidth) {
        return halfWidth * DEPTH;
    }

    /** How much of its full size the front has opened out to at this point in its flight. */
    public static float grown(float progress) {
        return ForgeMotion.reached(FROM, progress);
    }

    /**
     * The rim as an arc, for the two things that want an edge to work along: the element ornament
     * and the shower of debris. A closed circle, because the rim is one - the seam where such a
     * sweep pinches shut falls between ornaments and is invisible, which is why the rim glow is
     * drawn by {@link ForgeWaveFront#halo} instead of by the tapering sheath.
     */
    public static Sweep rim(float halfWidth, float scale) {
        return new Sweep(Plane.UPRIGHT, radius(halfWidth) * scale, halfWidth * THICKNESS * scale, 0.0f, 360.0f);
    }

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float scale = grown(state.progress);
        float radius = radius(state.halfWidth) * scale;
        float depth = depth(state.halfWidth) * scale;
        poseStack.pushPose();
        // Pulled back half its own bulge, so the sheet straddles the thing that hits rather than
        // standing in front of it: the middle leads by half, the rim trails by half.
        poseStack.translate(0.0f, 0.0f, -depth * 0.5f);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -lag * TRAIL_GAP);
            float shrink = 1.0f - lag * TRAIL_SHRINK;
            ForgeWaveFront.draw(edge, poseStack.last().pose(), radius * shrink, depth * shrink, palette, alpha);
            poseStack.popPose();
        });
        Matrix4f pose = poseStack.last().pose();
        float[] eye = ForgeView.eye(pose);
        ForgeWaveFront.halo(edge, pose, radius, depth, palette, state.alpha, eye[0], eye[1], eye[2]);
        Sweep rim = rim(state.halfWidth, scale);
        ForgeElementAccent.draw(edge, pose, rim, palette, state.alpha, state.accent);
        // The shower rather than the whole of ForgeAura: the glow a swing gets from the sheath is
        // the halo here, and asking for both would light the rim twice.
        ForgeSparks.strike(edge, pose, rim, palette, state.alpha, state.seed, state.grade, state.progress,
                eye[0], eye[1], eye[2]);
        poseStack.popPose();
    }
}
