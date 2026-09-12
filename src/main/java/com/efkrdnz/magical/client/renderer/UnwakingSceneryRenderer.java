package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultGeometry;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState;
import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The three enraged passages, which are scenery rather than projectiles.
 *
 * <p>Every attack these passages throw already existed and is unchanged - the vortex beam, the
 * converging suns, the falling fragment, the sweeping cut, the closing ring. What is new is what
 * the player is standing inside while it happens, and what tells them it is about to. All three set
 * pieces are drawn from the passage age alone, against the same tables the server fires from
 * ({@link UnwakingAssaultGeometry}), so nothing about an eye, a reflection or a gesture travels on
 * the wire and the telegraph can never disagree with the attack.
 *
 * <p>The Eyes and the Giant are drawn camera-relative: translated to the camera and then out along
 * a bearing, so they hold a fixed apparent size and distance no matter where the player runs. A
 * world-sized figure placed at true world coordinates would z-fight the terrain and swim as you
 * walk, which is the opposite of "far away and as big as the world".
 */
public final class UnwakingSceneryRenderer {
    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/entity/unwaking_god.png");
    /** One baked player model serves the reflections and the one world-sized figure alike. */
    private static PlayerModel figure;

    private UnwakingSceneryRenderer() {}

    private static PlayerModel figure() {
        if (figure == null) {
            figure = new PlayerModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return figure;
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, UnwakingAssaultState state,
            long now, float partial, Vec3 camera, Quaternionf facing, int ink, boolean reduced, boolean closing) {
        if (!state.active(now)) return;
        int age = state.passageAge(now);
        float effect = state.effect(now);
        if (effect <= 0.01F) return;
        FxContext ctx = new FxContext(pose, buffers, partial, facing, camera)
                .timing(age, state.passage(now).ticks(), MagicVertex.seedOf(state.start()));
        switch (state.passage(now)) {
            case EYES -> eyes(ctx, state, age, effect, ink, reduced);
            case MIRROR -> mirror(pose, buffers, age, effect, ink, reduced);
            case GIANT -> giant(pose, buffers, state, camera, age, effect, ink);
            case TUNNEL -> tunnel(pose, buffers, state, camera, age, effect, reduced);
            case CLOCK -> clock(pose, buffers, state, age, effect, ink, closing);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- the eyes

    /**
     * Lidless eyes across the sky dome, with nothing behind them.
     *
     * <p>An eye is two billboards: a dark iris the size of a building and a slit pupil inside it.
     * The ring drifts and blinks on its own phase so the sky is never still, but the only motion
     * that means anything is the contraction - {@link UnwakingAssaultGeometry#EYE_CHARGE} ticks
     * before its beam leaves, an eye snaps its pupil shut and brightens. That is the telegraph, and
     * it is the reason the passage is readable at all: the beams come from every bearing at once,
     * so there is no direction to face, only a sky to read.
     */
    private static void eyes(FxContext ctx, UnwakingAssaultState state, int age, float effect, int ink, boolean reduced) {
        PoseStack pose = ctx.pose;
        for (int eye = 0; eye < UnwakingAssaultGeometry.EYE_COUNT; eye++) {
            float charge = charge(age, eye);
            // A slow independent drift, so ten eyes never read as one rotating object.
            float drift = Mth.sin((age + eye * 37) * 0.014F) * 0.12F;
            Vec3 bearing = UnwakingAssaultGeometry.eyeBearing(eye, state.forward()).add(0, drift, 0).normalize();
            Vec3 at = ctx.cameraPos.add(bearing.scale(UnwakingAssaultGeometry.EYE_DISTANCE));
            // Lids: shut most of the time, wide for a beat before firing, all open for the finale.
            float open = Math.max(blink(age, eye), Math.max(charge, finale(age)));
            if (open <= 0.02F) continue;
            float radius = 26F * (0.35F + 0.65F * open) * effect;
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            ctx.origin = at;
            // Iris first, pupil over it: neither writes depth, so batch order is blend order.
            OrbPainter.billboard(ctx, FxKinds.Orb.IRIS, radius, ink, 0.55F * effect, eye * 0.13F, 3, 0);
            OrbPainter.billboard(ctx, FxKinds.Orb.EYE_SLIT, radius * (1.0F - 0.35F * charge), ink,
                    (0.7F + 0.3F * charge) * effect, eye * 0.13F, 2, 0);
            // The contraction is the tell, so it gets the one additive layer in the passage.
            if (charge > 0.01F && !reduced) {
                OrbPainter.billboard(ctx, FxKinds.Orb.HEX_LENS, radius * 0.5F * charge, ink,
                        0.85F * charge * effect, eye * 0.13F, 2, 0);
            }
            pose.popPose();
        }
    }

    /** How far into its contraction eye {@code eye} is, from the same table the server fires from. */
    private static float charge(int age, int eye) {
        float best = 0;
        for (int beat = 0; beat < UnwakingAssaultGeometry.EYE_BEATS.length; beat++) {
            int at = UnwakingAssaultGeometry.EYE_BEATS[beat][0], count = UnwakingAssaultGeometry.EYE_BEATS[beat][1];
            for (int slot = 0; slot < count; slot++) {
                if (UnwakingAssaultGeometry.eyeFor(beat, slot, count) != eye) continue;
                // Rise over the charge window, then snap back over the eight ticks after it fires.
                float t = age <= at
                        ? (age - (at - UnwakingAssaultGeometry.EYE_CHARGE)) / (float) UnwakingAssaultGeometry.EYE_CHARGE
                        : 1 - (age - at) / 8.0F;
                best = Math.max(best, Mth.clamp(t, 0, 1));
            }
        }
        return best;
    }

    /** An idle blink, so an eye that is not about to fire is still alive. */
    private static float blink(int age, int eye) {
        return Mth.clamp((Mth.sin((age + eye * 53) * 0.021F) - 0.25F) * 1.6F, 0, 1) * 0.6F;
    }

    /** Every eye open at once behind the converging suns, and staying open to the boundary. */
    private static float finale(int age) {
        return Mth.clamp((age - (UnwakingAssaultGeometry.EYE_BURIAL - 24)) / 24.0F, 0, 1);
    }

    // -------------------------------------------------------------- the mirror

    /**
     * The domain as a hall of reflections: the player, one to one, in every cell of a lattice.
     *
     * <p>Copies sit thirty-two blocks apart horizontally and twenty-four vertically, mirrored on
     * alternating cells so the field reads as reflections rather than a grid of clones, and each
     * carries the player own skin.
     *
     * <p>The noise is load-bearing rather than unfair: one reflection lights up
     * {@link UnwakingAssaultGeometry#MIRROR_TELL} ticks before it throws, and the fragment that
     * follows leaves from exactly that cell. The passage is hard because you have to find the lit
     * copy among its duplicates - not because the attack was hidden from you.
     */
    private static void mirror(PoseStack pose, MultiBufferSource.BufferSource buffers,
            int age, float effect, int ink, boolean reduced) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        // Seventy-odd extra player models is the one genuinely expensive thing in these passages.
        // The outer shells are pure decoration, so reduced detail drops them and keeps the lit one.
        int span = reduced ? 1 : 2, rise = reduced ? 0 : 1;
        float tell = tell(age);
        ResourceLocation skin = skin(player);
        RenderType type = RenderType.entityTranslucent(skin);
        VertexConsumer body = buffers.getBuffer(type);
        Vec3 centre = player.position();
        int copies = 0;
        for (int x = -span; x <= span; x++) {
            for (int z = -span; z <= span; z++) {
                for (int y = -rise; y <= rise; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Vec3 at = centre.add(x * UnwakingAssaultGeometry.MIRROR_SPAN,
                            y * UnwakingAssaultGeometry.MIRROR_RISE, z * UnwakingAssaultGeometry.MIRROR_SPAN);
                    copy(pose, body, at, player.getYRot(), ((x + z) & 1) == 0,
                            ARGB.color(Math.round(190 * effect), ink));
                    copies++;
                }
            }
        }
        // The lit reflection, drawn last and at full alpha so it survives everything blended over it.
        if (tell > 0.01F) {
            Vec3 at = centre.add(UnwakingAssaultGeometry.mirrorOffset(litCell(age)));
            copy(pose, body, at, player.getYRot(), false, ARGB.color(255, 0xFFFFFF));
            copies++;
            buffers.endBatch(type);
            wall(pose, buffers, at, age, ink, Math.min(1, tell) * effect);
        } else {
            buffers.endBatch(type);
        }
        FxBudget.countQuads(copies * 6);
    }

    /** One reflection: the player model at a lattice cell, mirrored on half of them. */
    private static void copy(PoseStack pose, VertexConsumer out, Vec3 at, float yaw, boolean flip, int color) {
        pose.pushPose();
        pose.translate(at.x, at.y + 1.5, at.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - yaw + (flip ? 180 : 0)));
        // The model is built around an origin above its own feet, the way every biped renderer draws it.
        pose.scale(-1, -1, 1);
        figure().renderToBuffer(pose, out, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        pose.popPose();
    }

    /** The mirror material on the lit cell: a pane of sheen the reflection is standing behind. */
    private static void wall(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 at, int age,
            int ink, float opacity) {
        int packed = MagicVertex.pack(FxKinds.Field.MIRROR_SHEEN.ordinal(), 3, 0, age * 0.02F, age, 0);
        VertexConsumer out = buffers.getBuffer(MagicalFxRenderTypes.surfaceField());
        pose.pushPose();
        pose.translate(at.x, at.y + 2.0, at.z);
        FxMesh.emit(out, pose.last().pose(), FxMesh.slab(), 6.0F, 6.0F, 0.06F, ink, opacity, packed);
        pose.popPose();
        buffers.endBatch(MagicalFxRenderTypes.surfaceField());
        FxBudget.countQuads(6);
    }

    /** Which reflection is lit right now, and how far into its tell it is. */
    private static int litCell(int age) {
        return UnwakingAssaultGeometry.mirrorCell(Math.max(0, volley(age)));
    }
    private static float tell(int age) {
        int v = volley(age);
        if (v < 0) return 0;
        int at = UnwakingAssaultGeometry.MIRROR_START + v * UnwakingAssaultGeometry.MIRROR_PERIOD;
        // Bright through the whole tell, then out over the eight ticks after the body leaves.
        return age <= at
                ? Mth.clamp((age - (at - UnwakingAssaultGeometry.MIRROR_TELL))
                        / (float) UnwakingAssaultGeometry.MIRROR_TELL, 0, 1)
                : Mth.clamp(1 - (age - at) / 8.0F, 0, 1);
    }
    private static int volley(int age) {
        int v = Math.floorDiv(age - UnwakingAssaultGeometry.MIRROR_START
                + UnwakingAssaultGeometry.MIRROR_TELL, UnwakingAssaultGeometry.MIRROR_PERIOD);
        return v < 0 || v >= UnwakingAssaultGeometry.MIRROR_VOLLEYS ? -1 : v;
    }

    // --------------------------------------------------------------- the giant

    /**
     * The boss at the edge of the world, as tall as it, gesturing.
     *
     * <p>Drawn camera-relative at a fixed bearing and distance, so it holds its apparent size and
     * never parallaxes, clips terrain or z-fights - the moon trick. It is the same baked model the
     * reflections use and the same opaque white the body wears, and the gestures are posed by hand
     * on its {@code ModelPart} angles, exactly the way {@link UnwakingGodRenderer} poses the real
     * body for its slumped and unbodying stances.
     *
     * <p>Each gesture winds up {@link UnwakingAssaultGeometry#GESTURE_WIND} ticks before its shape
     * exists, so the thing the player reads is the arm, not the HUD.
     */
    private static void giant(PoseStack pose, MultiBufferSource.BufferSource buffers,
            UnwakingAssaultState state, Vec3 camera, int age, float effect, int ink) {
        PlayerModel model = figure();
        pose(model, age);
        // Camera-relative, but along the passage own bearing - the same direction
        // UnwakingAssaultState.giant() throws from, so the arm the player watches is the arm that
        // threw. On a fixed bearing every gesture was a telegraph pointing at nothing.
        Vec3 at = camera.add(state.giantBearing().scale(UnwakingAssaultGeometry.GIANT_DISTANCE));
        RenderType type = RenderType.entityTranslucent(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        pose.pushPose();
        pose.translate(at.x, at.y - UnwakingAssaultGeometry.GIANT_SCALE * 0.6, at.z);
        float scale = (float) UnwakingAssaultGeometry.GIANT_SCALE * (0.6F + 0.4F * effect);
        // Turn to face back down its own bearing, so the gestures read front-on, not in profile.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                (float) Math.toDegrees(Math.atan2(state.giantBearing().x, state.giantBearing().z))));
        pose.scale(-scale, -scale, scale);
        model.renderToBuffer(pose, out, 0xF000F0, OverlayTexture.NO_OVERLAY,
                ARGB.color(Math.round(235 * effect), ink));
        pose.popPose();
        buffers.endBatch(type);
        FxBudget.countQuads(64);
    }

    /** The gestures. Each winds up before its shape exists, and eases off after it lands. */
    private static void pose(PlayerModel model, int age) {
        float sweep = gesture(age, UnwakingAssaultGeometry.GIANT_SWEEP);
        float palm = gesture(age, UnwakingAssaultGeometry.GIANT_PALM);
        float fists = gesture(age, UnwakingAssaultGeometry.GIANT_FISTS);
        float flick = flick(age);
        model.body.xRot = 0; model.body.yRot = 0; model.body.zRot = 0;
        model.rightLeg.xRot = 0; model.leftLeg.xRot = 0;
        // The arm lifts over the wind-up and comes down through the cut; both come forward for the
        // suns; both close across the chest for the ring. They add, so a gesture never snaps.
        model.rightArm.xRot = -2.6F * lift(sweep) - 0.5F * flick - 1.2F * fists;
        model.rightArm.zRot = 0.15F + 0.5F * flick - 0.55F * fists;
        model.leftArm.xRot = -1.5F * palm - 1.2F * fists;
        model.leftArm.zRot = -0.35F * palm + 0.55F * fists;
        model.head.xRot = 0.25F * Math.max(sweep, Math.max(palm, fists));
        model.head.yRot = 0.2F * Mth.sin(age * 0.008F);
    }

    /** Zero until the wind-up starts, one at the moment the shape leaves, easing off after. */
    private static float gesture(int age, int at) {
        if (age < at - UnwakingAssaultGeometry.GESTURE_WIND) return 0;
        if (age <= at) return (age - (at - UnwakingAssaultGeometry.GESTURE_WIND))
                / (float) UnwakingAssaultGeometry.GESTURE_WIND;
        return Mth.clamp(1 - (age - at) / 40.0F, 0, 1);
    }
    /** The arm rises for three quarters of the wind-up and falls through the last quarter. */
    private static float lift(float g) { return g <= 0 ? 0 : g < 0.75F ? g / 0.75F : (1 - g) / 0.25F; }
    /** A snap of the wrist on each thrown fragment, so the rain has a source you can watch. */
    private static float flick(int age) {
        int since = age - UnwakingAssaultGeometry.FLICK_START;
        if (since < 0 || since > UnwakingAssaultGeometry.FLICK_VOLLEYS * UnwakingAssaultGeometry.FLICK_PERIOD) return 0;
        return Mth.clamp(1 - Math.floorMod(since, UnwakingAssaultGeometry.FLICK_PERIOD) / 6.0F, 0, 1);
    }

    // -------------------------------------------------------------- the tunnel

    /**
     * A shaft of colour rushing at the player, and the illusion of falling down it.
     *
     * <p>Nothing moves the player. Rings of the tunnel wall are laid out down the axis at a fixed
     * spacing and then slid toward the camera by the passage age, so each ring is continuously
     * replaced by the one behind it - the wall rushes past and the standing player reads it as
     * their own motion. That is the whole trick, and it costs one scrolling offset.
     *
     * <p>Colour is the other half. The rest of the fight is bone-white and its inversion, which is
     * exactly why the hue walks with depth and time here: the tunnel is meant to be the moment the
     * domain stops looking like itself.
     */
    private static void tunnel(PoseStack pose, MultiBufferSource.BufferSource buffers, UnwakingAssaultState state,
            Vec3 camera, int age, float effect, boolean reduced) {
        Vec3 axis = state.forward().normalize();
        int rings = reduced ? 14 : 28;
        // The wall slides one whole spacing every SCROLL ticks, so a ring is always mid-flight and
        // the seam where it wraps never lands on screen.
        double slide = (age % SCROLL) / (double) SCROLL * RING_GAP;
        VertexConsumer out = buffers.getBuffer(MagicalFxRenderTypes.surfaceField());
        for (int i = 0; i < rings; i++) {
            double depth = i * RING_GAP - slide;
            Vec3 at = camera.add(axis.scale(depth));
            // Fade the far end rather than ending the tunnel on a visible last ring, and fade the
            // near one so a ring does not pop as it passes through the camera.
            float far = (float) Mth.clamp(1 - depth / (rings * RING_GAP), 0, 1);
            float near = (float) Mth.clamp(depth / (RING_GAP * 2), 0, 1);
            int packed = MagicVertex.pack(FxKinds.Field.STATIC_SCANLINES.ordinal(), 4, 0,
                    (float) (age * 0.05 + i), i, 0);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            FxMesh.emit(out, pose.last().pose(), FxMesh.ring(reduced ? 10 : 18, 0.06F),
                    (float) TUNNEL_BORE, (float) TUNNEL_BORE, (float) TUNNEL_BORE,
                    tunnelHue(i, age), 0.55F * far * near * effect, packed);
            pose.popPose();
        }
        buffers.endBatch(MagicalFxRenderTypes.surfaceField());
        FxBudget.countQuads(rings * (reduced ? 10 : 18));
        // No marker for the open lane. One was drawn here, relative to the camera - but the
        // obstacles are fixed in world space at the moment their volley leaves, so the instant the
        // player moved the marker slid off the real gap and started pointing at an occupied lane. A
        // telegraph that lies is worse than none, and the obstacles already carry an honest one:
        // twenty-two ticks of ring at their true collision radius, the same standard the converging
        // suns set and the rest of the fight follows.
    }

    /** How far apart the rings sit, how fast the wall slides, and how wide the shaft is. */
    private static final double RING_GAP = 6, TUNNEL_BORE = 16;
    private static final int SCROLL = 6;

    /**
     * The tunnel colour at a given depth and moment - and the colour of what comes down it.
     *
     * <p>Public because the obstacles are drawn by {@link UnwakingOpenRenderer} on the shared hazard
     * path, and a shard in the boss bone-white would read as the boss reaching into the tunnel
     * rather than as something the tunnel brought with it.
     */
    public static int tunnelHue(int depth, double age) {
        float hue = (float) (((depth * 0.055 + age * 0.006) % 1.0 + 1.0) % 1.0);
        return Mth.hsvToRgb(hue, 0.62F, 1.0F) & 0xFFFFFF;
    }

    // --------------------------------------------------------------- the clock

    /**
     * The figure that walks a circle around the player and stops where the next strike comes from.
     *
     * <p>The Clock passage pins the player and asks them to look at an incoming strike to counter
     * it. The strikes used to leave three fixed yaws off an axis set when the passage opened, with
     * twenty ticks of warning - findable only if you already knew where to look, which is not a
     * telegraph at all. Now {@link UnwakingAssaultGeometry#clockAim} walks this figure round the
     * circle and settles it on the exact bearing, twelve blocks out, ten ticks early. It stands on
     * the point the strike will occupy, which is the point the counter asks you to aim at.
     *
     * <p>Client-drawn rather than the real body, which stays dissolved for the whole assault: every
     * player in the run is pinned at their own anchor and needs their own figure, and one entity
     * cannot circle four people at once.
     */
    private static void clock(PoseStack pose, MultiBufferSource.BufferSource buffers, UnwakingAssaultState state,
            int age, float effect, int ink, boolean closing) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 aim = UnwakingAssaultGeometry.clockAim(age, closing, state.forward());
        Vec3 at = player.getEyePosition().add(aim.scale(UnwakingAssaultGeometry.CLOCK_ORBIT));
        int next = UnwakingAssaultGeometry.clockStrikeIndex(age);
        // Bright once it has settled, dimmer while it is still walking: the stillness is the cue.
        boolean settled = next < UnwakingAssaultGeometry.CLOCK_STRIKES.length
                && age >= UnwakingAssaultGeometry.CLOCK_STRIKES[next] - UnwakingAssaultGeometry.CLOCK_SETTLE;
        float alpha = (settled ? 1.0F : 0.55F) * effect;
        RenderType type = RenderType.entityTranslucent(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        // Turn to face the player, so a settled figure is unmistakably looking at them.
        float yaw = (float) Math.toDegrees(Math.atan2(aim.x, aim.z));
        pose.pushPose();
        pose.translate(at.x, at.y + 1.0, at.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.scale(-2.2F, -2.2F, 2.2F);
        figure().renderToBuffer(pose, out, 0xF000F0, OverlayTexture.NO_OVERLAY,
                ARGB.color(Math.round(255 * alpha), ink));
        pose.popPose();
        buffers.endBatch(type);
        FxBudget.countQuads(64);
        if (!settled) return;
        // A mark under a settled figure, so it reads at a glance from across the arena.
        VertexConsumer mark = buffers.getBuffer(MagicalFxRenderTypes.groundMarkAdd());
        pose.pushPose();
        pose.translate(at.x, at.y - 2.2, at.z);
        FxMesh.emit(mark, pose.last().pose(), FxMesh.annulus(20, 0.45F), 2.6F, 1, 2.6F, ink, 0.85F * effect,
                MagicVertex.pack(FxKinds.Mark.CLOCK_SPOKES.ordinal(), 4, 0, age * 0.08F, next, 0));
        pose.popPose();
        buffers.endBatch(MagicalFxRenderTypes.groundMarkAdd());
        FxBudget.countQuads(20);
    }

    private static ResourceLocation skin(AbstractClientPlayer player) {
        var s = player.getSkin();
        return s == null || s.texture() == null ? WHITE : s.texture();
    }
}
