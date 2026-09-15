package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * SPIN: the full turn. A closed halo at the strike's reach, spinning with the strike's age, kept
 * inside a shallow vertical band so it never becomes a sphere — a spin is a level sweep.
 */
public final class SpinGeometry {

    private static final float HALO_Y = 1.1f;
    private static final float SPIN_PER_TICK = 42.0f;
    // A spin is a band at the reach, not a filled disc. halfWidth for this form is the radius of
    // its hit circle, so taking half of it as the band width left an annulus running from 1.4
    // blocks out to 2.75 - most of the way to the middle - and the player stood inside a solid
    // plate of light.
    private static final float THICKNESS = 0.18f;
    private static final float TRAIL_SWEEP = 34.0f;

    private SpinGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float spin = state.ageInTicks * SPIN_PER_TICK;
        Sweep halo = new Sweep(Plane.GROUND, state.reach, state.halfWidth * THICKNESS, spin, spin + 360.0f);
        poseStack.pushPose();
        poseStack.translate(0.0f, HALO_Y, 0.0f);
        Matrix4f pose = poseStack.last().pose();
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha,
                (lag, alpha) -> ForgeRibbon.arc(edge, pose, halo.shifted(-lag * TRAIL_SWEEP), palette, alpha));
        ForgeElementAccent.draw(edge, pose, halo, palette, state.alpha, state.accent);
        ForgeAura.arc(edge, pose, halo, palette, state, state.alpha);
        // The two faint copies above and below the halo are gone: they were there to fake depth on
        // a flat ring, and the ring has real thickness now. They cost a third of the form's quads
        // and wrote every one of their pixels over the halo, which is most of why a spin came out
        // as a white cloud with the player somewhere inside it.
        poseStack.popPose();
    }
}
