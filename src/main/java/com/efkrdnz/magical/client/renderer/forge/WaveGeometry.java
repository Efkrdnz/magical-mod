package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.fx.ForgeMatterEmitter;
import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * WAVE: a slash let go of. The same blade a swing draws - the same arc, the same smear, the same
 * lens cross-section tapering to both horns - thrown down the aim with its belly leading and its
 * horns swept back behind it.
 *
 * <p>It has been three things before this. A ring band hanging a radius off the flight path, rolled
 * forty degrees, which read as a moon tipped up and to the right and claimed four times the width
 * it catches in. Then a crescent sized to the hit, but bowed belly-down - its bend pointed somewhere
 * that was not where it was going. Then a dome about the flight line, which had nothing on it to
 * point wrong because it had nothing on it at all: seen from the thrower, a circle. And once, for
 * a day, the horns were swept down behind it, which stood the crescent up as an arch facing the
 * sky. A thrown slash lies in the plane of the swing that threw it - level, rolled as a slash is
 * rolled - and bends toward exactly one thing, the flight: the belly leads, the horns trail.
 *
 * <p>{@code WaveSilhouetteTest} holds the drawn blade inside the volume {@code WaveShape} actually
 * catches with, and holds the crescent to its bend.
 */
public final class WaveGeometry {

    /**
     * Half the crescent's span horn to horn, as a fraction of the strike's half-width.
     *
     * <p>Under {@code WaveShape.LATERAL_FRACTION} (0.48), which is what the wave actually catches
     * either side of its flight line. There is no ceiling on it: the dome this replaced was as tall
     * as it was wide and had to stop at {@code WaveShape}'s flat 0.9 of vertical reach, but a
     * crescent lying in its swing rises only by its roll, so REACH, which widens a wave, is free to
     * make it a larger slash.
     */
    private static final float RADIUS = 0.46f;

    /** How far round its own circle the crescent runs, horn to horn. */
    private static final float SPAN = 150.0f;

    /**
     * How far the crescent is rolled about its flight: the slash's own tilt ({@code SlashGeometry}),
     * so what leaves the hand is the cut that was just swung. Rolled rather than pitched, so it
     * never stops lying along the flight.
     */
    private static final float ROLL = -22.0f;

    /** The blade's width at its middle, as a fraction of its circle's radius. */
    private static final float THICKNESS = 0.42f;

    /** How big the crescent is when it leaves the hand, as a fraction of its full size. */
    private static final float FROM = 0.55f;

    /** How far the wake strings out behind the head, and how much each copy back has shrunk. */
    private static final float TRAIL_GAP = 0.26f;
    private static final float TRAIL_SHRINK = 0.20f;

    private WaveGeometry() {}

    /** Half the crescent's span, horn to horn, at full size. */
    public static float radius(float halfWidth) {
        return halfWidth * RADIUS;
    }

    /** How much of its full size the crescent has opened out to at this point in its flight. */
    public static float grown(float progress) {
        return ForgeMotion.reached(FROM, progress);
    }

    /** The blade as a sweep round its own circle, belly at angle zero, before it is framed. */
    public static Sweep blade(float halfWidth, float scale) {
        float circle = radius(halfWidth) / Mth.sin(SPAN * 0.5f * Mth.DEG_TO_RAD) * scale;
        return new Sweep(Plane.GROUND, circle, circle * THICKNESS, -SPAN * 0.5f, SPAN * 0.5f);
    }

    /**
     * Takes a blade from its own circle to the strike: the belly onto the flight line and pointing
     * down it, the whole rolled by {@link #ROLL} about that line, and centred on the strike's
     * position so it straddles the thing that hits rather than standing ahead of it.
     */
    public static Matrix4f frame(Sweep blade) {
        float trail = blade.radius() * (1.0f - Mth.cos(SPAN * 0.5f * Mth.DEG_TO_RAD));
        return new Matrix4f()
                .translate(0.0f, 0.0f, trail * 0.5f)
                .rotateZ(ROLL * Mth.DEG_TO_RAD)
                .translate(0.0f, 0.0f, -blade.radius());
    }

    /**
     * A point on the drawn blade, in the strike's frame: {@code t} from horn to horn, {@code out}
     * from the inner edge to the lip. Pure, so the silhouette is measured without a frame drawn.
     */
    public static float[] point(Sweep blade, float t, float out) {
        float[] local = blade.at(t, out);
        Vector3f p = frame(blade).transformPosition(new Vector3f(local[0], local[1], local[2]));
        return new float[] {p.x, p.y, p.z};
    }

    public static void render(PoseStack poseStack, ForgeStroke stroke, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        Sweep blade = blade(state.halfWidth, grown(state.progress));
        Matrix4f frame = frame(blade);
        ForgeStroke thrown = stroke.thrown();
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -lag * TRAIL_GAP);
            poseStack.mulPose(frame);
            ForgeRibbon.arc(thrown.at(lag), poseStack.last().pose(), blade.scaled(1.0f - lag * TRAIL_SHRINK),
                    palette, alpha);
            poseStack.popPose();
        });
        poseStack.pushPose();
        poseStack.mulPose(frame);
        Matrix4f pose = poseStack.last().pose();
        float[] eye = ForgeView.eye(pose);
        ForgeRibbon.sheath(thrown, pose, blade, palette, state.alpha, eye[0], eye[1], eye[2]);
        ForgeModifierLook.adorn(thrown, pose, blade, palette, state.alpha, state.mods, eye);
        ForgeElementAccent.draw(thrown, pose, blade, palette, state.alpha, state.accent);
        // shed along the whole blade and left behind it, rather than off a head a swing would have
        ForgeMatterEmitter.rim(state, pose, blade);
        poseStack.popPose();
    }
}
