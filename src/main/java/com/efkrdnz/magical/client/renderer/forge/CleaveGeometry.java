package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * CLEAVE: the overhead chop. A tall, narrow ribbon standing in front of the wielder, running from
 * above their head down past their feet, with the wake hanging above it because the blade fell.
 */
public final class CleaveGeometry {

    // A chop reaches as far as the form says it does. It used to draw at 1.5 blocks whatever the
    // weapon, on a ribbon a quarter of a block across, which is why it read as a scratch in the air
    // rather than an overhead blow.
    // Sized to the hit shape rather than to the reach: CleaveShape is a vertical slab running from
    // a body below the wielder to a body and a half above, and narrow across. An arc any taller
    // than that puts its own tip out of the picture and leaves the fat of the blade behind the
    // player's head, which is exactly where it used to draw.
    private static final float MIN_RADIUS = 1.6f;
    private static final float RADIUS_SCALE = 0.64f;
    private static final float SPAN = 66.0f;
    private static final float THICKNESS = 0.95f;
    private static final float LIFT_TOP = 1.0f;
    private static final float DROP = 0.5f;
    // The same reason as the rising cut's drop: copies this far apart stack into ribs instead
    // of blurring into a wake.
    private static final float TRAIL_RISE = 0.30f;
    private static final float OPEN_BY = 0.55f;

    private CleaveGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float radius = Math.max(MIN_RADIUS, state.reach * RADIUS_SCALE);
        // From the top down: the arc opens from where the blade was raised, so the chop falls
        // through its span. Opened the other way round it was an uppercut wearing a chop's name.
        Sweep full = new Sweep(Plane.FORWARD, radius, state.halfWidth * THICKNESS, SPAN, -SPAN);
        Sweep sweep = ForgeMotion.opening(full, state.progress / OPEN_BY);
        // The blade falls while it opens, so the chop travels down through its own arc.
        float lift = LIFT_TOP - ForgeMotion.swept(state.progress) * DROP;
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, lift + lag * TRAIL_RISE, 0.0f);
            ForgeRibbon.arc(edge, poseStack.last().pose(), sweep, palette, alpha);
            poseStack.popPose();
        });
        poseStack.pushPose();
        poseStack.translate(0.0f, lift, 0.0f);
        ForgeElementAccent.draw(edge, poseStack.last().pose(), sweep, palette, state.alpha, state.accent);
        ForgeAura.arc(edge, poseStack.last().pose(), sweep, palette, state, state.alpha);
        poseStack.popPose();
    }
}
