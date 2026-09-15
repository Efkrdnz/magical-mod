package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

/**
 * WAVE: the thrown crescent. A shallow blade lying across the flight, bowed so its belly leads and
 * its two horns trail, with the wake strung out behind it and shrinking as it goes.
 *
 * <p>It used to be a ring band sitting between one and a half and three blocks out from the strike,
 * rolled forty degrees about the aim. Two things were wrong with that and they compounded: nothing
 * at all was drawn on the thing that actually hits - the whole band hung a radius away from it -
 * and the roll tipped that band up and to one side, so what a player saw was a moon leaning
 * up-right rather than a blade thrown forward. The picture also claimed nearly four times the width
 * the wave catches in, which is the kind of lie that reads as the game not registering a hit.
 *
 * <p>So the crescent is sized off {@code WaveShape}'s own inflation and sits on the strike rather
 * than beside it, and {@code WaveSilhouetteTest} measures it against {@code HitShapes} so it cannot
 * drift back. What makes it read as thrown is no longer a tilt but the bow: the belly is further
 * along the flight than the horns, so the shape has a front from every angle.
 */
public final class WaveGeometry {

    /**
     * The crescent's radius, as a fraction of the strike's half-width.
     *
     * <p>Under {@code WaveShape.LATERAL_FRACTION} (0.48), which is what the wave actually catches
     * either side of its flight line, because the arc runs past ninety degrees and so reaches its
     * full radius sideways. The margin is what the blade's own thickness spends.
     */
    private static final float RADIUS = 0.44f;

    /** The blade's width across, as a fraction of half-width. A thrown edge is a thin thing. */
    private static final float THICKNESS = 0.42f;

    /** Half the crescent's span. Past ninety, so the horns turn back and the shape closes on itself. */
    private static final float HALF_ARC = 104.0f;

    /**
     * How far the horns trail behind the belly, as a fraction of half-width.
     *
     * <p>This is the whole of "facing forward". Flat, the crescent is the same shape whichever way
     * it travels and reads as a decal turned to face you; bowed, the middle leads and the shape
     * itself points down the flight.
     */
    private static final float BOW = 0.30f;

    /** Where the crescent sits in its own span: the belly at the bottom, horns rising either side. */
    private static final float BELLY_DOWN = 180.0f;

    /** How big the crescent is when it leaves the hand, as a fraction of its full size. */
    private static final float FROM = 0.70f;
    private static final float TRAIL_GAP = 0.45f;
    private static final float TRAIL_SHRINK = 0.10f;

    private WaveGeometry() {}

    /**
     * The wave's blade at full size, in the strike's own frame: belly down, horns up and trailing.
     *
     * <p>Public and pure so the silhouette can be measured against the hit volume without a frame
     * being drawn.
     */
    public static Sweep crescent(float halfWidth) {
        float radius = halfWidth * RADIUS;
        return new Sweep(Plane.UPRIGHT, radius, halfWidth * THICKNESS,
                BELLY_DOWN - HALF_ARC, BELLY_DOWN + HALF_ARC, halfWidth * BOW);
    }

    /** The blade at this point in its flight: it leaves the hand small and opens out as it goes. */
    public static Sweep head(float halfWidth, float progress) {
        return ForgeMotion.reaching(crescent(halfWidth), FROM, progress);
    }

    /**
     * How far up the crescent is moved so it straddles the strike instead of hanging under it.
     *
     * <p>An arc is measured from the centre of its own circle, so a crescent built at radius R has
     * every one of its pixels R blocks from that centre and none of them on it. This puts the
     * middle of the drawn shape back on the thing that hits.
     */
    public static float lift(float halfWidth) {
        float radius = halfWidth * RADIUS;
        // The belly bottoms out at -radius. The horns stand at 180 degrees either side of it, so
        // they sit at cos(180 - HALF_ARC) - above the centre, because the arc runs past ninety.
        float horns = -radius * Mth.cos(HALF_ARC * Mth.DEG_TO_RAD);
        return (radius - horns) * 0.5f;
    }

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        Sweep head = head(state.halfWidth, state.progress);
        poseStack.pushPose();
        poseStack.translate(0.0f, lift(state.halfWidth), 0.0f);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -lag * TRAIL_GAP);
            ForgeRibbon.arc(edge, poseStack.last().pose(), head.scaled(1.0f - lag * TRAIL_SHRINK), palette, alpha);
            poseStack.popPose();
        });
        ForgeElementAccent.draw(edge, poseStack.last().pose(), head, palette, state.alpha, state.accent);
        ForgeAura.arc(edge, poseStack.last().pose(), head, palette, state, state.alpha);
        poseStack.popPose();
    }
}
