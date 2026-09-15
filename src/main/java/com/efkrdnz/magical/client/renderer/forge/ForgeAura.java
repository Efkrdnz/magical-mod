package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * What every forged strike gets on top of its steel: the glow around the blade and the sparks off
 * it.
 *
 * <p>The two exist for the two halves of the same complaint. A blade is a blade, so seen down the
 * line of its own swing it is close to a line however thick it is - the sheath is the light it is
 * giving off, and light faces everyone. And a picture made only of smooth ribbons reads as fog
 * however bright it is, because nothing in it is small - the sparks are the small thing.
 *
 * <p>One call, because eight forms had to get both and eight copies of this would drift. Each form
 * passes the pose it is drawing in and nothing else: the viewer's position is read back off that
 * matrix, so a form that lifts, tilts or fans its arc is accounted for without saying so.
 */
public final class ForgeAura {

    private ForgeAura() {}

    /** The glow and the shower for an arc of blade, in the frame {@code pose} is drawing into. */
    public static void arc(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette,
            ForgeStrikeRenderer.State state, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        float[] eye = ForgeView.eye(pose);
        ForgeRibbon.sheath(edge, pose, sweep, palette, alpha, eye[0], eye[1], eye[2]);
        ForgeSparks.strike(edge, pose, sweep, palette, alpha, state.seed, state.grade, state.progress,
                eye[0], eye[1], eye[2]);
    }

    /**
     * The glow for a straight lance. A thrust throws no shower: it goes in rather than through, and
     * debris off a lunge would be telling the player about a cut that did not happen.
     */
    public static void lance(VertexConsumer edge, Matrix4f pose, float length, float halfWidth,
            ForgePalette palette, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        float[] eye = ForgeView.eye(pose);
        ForgeRibbon.lanceSheath(edge, pose, length, halfWidth, palette, alpha, eye[0], eye[1], eye[2]);
    }
}
