package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.fx.ForgeMatterEmitter;
import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;

import org.joml.Matrix4f;

/**
 * What every forged strike gets on top of its steel: the glow around the blade and the matter
 * thrown off it.
 *
 * <p>The two exist for the two halves of the same complaint. A blade is a blade, so seen down the
 * line of its own swing it is close to a line however thick it is - the sheath is the light it is
 * giving off, and light faces everyone. And a picture made only of smooth ribbons reads as fog
 * however bright it is, because nothing in it is small - the matter is the small thing, and it is
 * real particles ({@link ForgeMatterEmitter}) that fly on, fall and land after the blade is gone.
 *
 * <p>One call, because eight forms had to get both and eight copies of this would drift. Each form
 * passes the pose it is drawing in and nothing else: the viewer's position is read back off that
 * matrix, so a form that lifts, tilts or fans its arc is accounted for without saying so.
 */
public final class ForgeAura {

    private ForgeAura() {}

    /** The glow and the shower for an arc of blade, in the frame {@code pose} is drawing into. */
    public static void arc(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette,
            ForgeStrikeRenderer.State state, float alpha) {
        arc(stroke, pose, sweep, palette, state, alpha, 0, state.progress);
    }

    /**
     * The same, for one of several arcs a strike draws in a frame: {@code part} tells them apart
     * and {@code progress} is how far through its own life that arc is.
     */
    public static void arc(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette,
            ForgeStrikeRenderer.State state, float alpha, int part, float progress) {
        if (alpha <= 0.0f) {
            return;
        }
        float[] eye = ForgeView.eye(pose);
        ForgeRibbon.sheath(stroke, pose, sweep, palette, alpha, eye[0], eye[1], eye[2]);
        ForgeModifierLook.adorn(stroke, pose, sweep, palette, alpha, state.mods, eye);
        ForgeMatterEmitter.arc(state, pose, sweep, part, progress);
    }

    /**
     * The glow and the shower for a straight lance. The shower comes only off its outer half: the
     * hilt of a thrust is at the wielder's eye, and matter born there is thrown into their face.
     */
    public static void lance(ForgeStroke stroke, Matrix4f pose, float length, float halfWidth,
            ForgePalette palette, ForgeStrikeRenderer.State state, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        float[] eye = ForgeView.eye(pose);
        ForgeRibbon.lanceSheath(stroke, pose, length, halfWidth, palette, alpha, eye[0], eye[1], eye[2]);
        ForgeModifierLook.adornLance(stroke, pose, length, halfWidth, palette, alpha, state.mods, eye);
        ForgeMatterEmitter.lance(state, pose, length, halfWidth);
    }
}
