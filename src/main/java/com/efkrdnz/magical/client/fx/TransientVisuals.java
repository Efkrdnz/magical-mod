package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.GlyphCirclePainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.network.VisualCuePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-only transient effects created from {@link VisualCuePayload}: windup circles, release
 * beats, impact flashes/marks/stamps, decals, barrier-hit ripples. Rendered in the AFTER_PARTICLES
 * pass (through-terrain windups in AFTER_CUTOUT_BLOCKS) with one endBatch per type.
 */
public final class TransientVisuals {
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final int MAX = 96;

    /**
     * How long a hit on a creature is worth drawing.
     *
     * <p>A hit on a wall leaves a mark that can sit there for seconds, because a wall has a surface
     * and the mark lies on it. A creature has neither: it moves, and the only orientation the mark
     * ever had was the direction the attacker happened to be standing in at the instant of the hit.
     * So a hit on a creature is now just the flash, which is camera-facing, and the flash is over in
     * half a second - there is nothing left to keep alive after it.
     */
    private static final int CREATURE_IMPACT_TICKS = 10;
    private static long frame;

    private TransientVisuals() {}

    public static void clear() {
        EFFECTS.clear();
    }

    public static void handle(VisualCuePayload payload) {
        VisualProfile profile = VisualProfiles.byIndex(payload.skillIndex());
        ProfileCues.TrailSpec trail = profile.trail();
        // An accented profile hands its matter to SpellAccents and keeps only its light here, drawn
        // lighter: see AccentPlan for every factor and why. A NONE profile is drawn as it always was.
        boolean accented = profile.accent().active();
        switch (payload.cue()) {
            case VisualCuePayload.CUE_CAST_WINDUP -> {
                int windup = Math.max(4, profile.windup().windupTicks());
                Effect effect = new Effect(Kind.WINDUP, profile, payload, windup + profile.tier().lingerTicks(), windup);
                CircleAnchor own = accented ? ownAnchor(profile, payload) : null;
                if (own != null) {
                    float radius = profile.tier().radius();
                    effect.own = own;
                    effect.ownScale = AccentPlan.ownCircleRadius(own, radius, profile.tier().tier()) / radius;
                    effect.ownOpacity = AccentPlan.ownCircleOpacity(own);
                }
                add(effect);
                if (accented) {
                    boolean ownNow = own != null && firstPerson();
                    // runes lifting off a sigil held at the eyes would lift straight through the view
                    if (!ownNow || own != CircleAnchor.EYE_FORWARD) {
                        SpellAccents.windup(profile, payload.pos(), profile.tier().radius() * (ownNow ? effect.ownScale : 1.0F), windup);
                    }
                    return;
                }
                // rune motes lift off the band into the hand
                int motes = Math.round(profile.windup().runeMoteCount() * FxBudget.lodMultiplier());
                for (int i = 0; i < motes; i++) {
                    float a = i / (float) Math.max(1, motes) * Mth.TWO_PI;
                    double r = profile.tier().radius();
                    Vec3 p = payload.pos().add(Math.cos(a) * r, 0.1D, Math.sin(a) * r);
                    Vec3 v = payload.pos().add(0.0D, 1.3D, 0.0D).subtract(p).scale(1.0D / Math.max(6, windup));
                    SpellParticles.spawn(FxKinds.Smoke.RUNE_MOTE, p.x, p.y, p.z, v.x, v.y + 0.02D, v.z, 0.18F, windup + 4, profile.color(ColorRole.BRIGHT), 1.0F, profile.castCircle().emblem().atlasCell(), 1.0F, 0.0F);
                }
            }
            case VisualCuePayload.CUE_RELEASE, VisualCuePayload.CUE_MUZZLE -> {
                Effect effect = new Effect(Kind.RELEASE, profile, payload, accented ? 6 : 8, 0);
                effect.ownView = accented && nearEyes(payload.pos());
                add(effect);
                // the release is sent from the hand, 0.8 of a block in front of the eyes: whatever it
                // throws there lands on the caster's own crosshair, so the one who cast it sees the
                // accent's matter thrown from further along the aim and no shader burst, and
                // everybody else sees the whole mouthful
                if (effect.ownView && firstPerson()) {
                    SpellAccents.release(profile, payload.pos(), payload.dir(), true);
                    return;
                }
                ProfileCues.ReleaseCue release = profile.release();
                int motes = Math.max(4, release.muzzleParticleBurst());
                FxKinds.Smoke kind = trail.active() ? trail.kind() : profile.impact().matterKind();
                SpellParticles.burst(kind, payload.pos(), payload.dir(), accented ? shaderShare(kind, motes) : motes, 0.35F, 0.16F, 14, profile.color(ColorRole.BRIGHT), 1.0F, 26);
                if (accented) {
                    SpellAccents.release(profile, payload.pos(), payload.dir());
                }
            }
            case VisualCuePayload.CUE_IMPACT -> {
                ProfileCues.ImpactSpec impact = profile.impact();
                boolean onBody = payload.victimId() >= 0;
                int markTicks = accented ? Math.min(AccentPlan.MARK_TICKS_CAP, impact.markTicks()) : impact.markTicks();
                int life = onBody ? CREATURE_IMPACT_TICKS : Math.max(10, markTicks);
                Effect effect = new Effect(Kind.IMPACT, profile, payload, life, 0);
                effect.onSurface = !accented || onBody || surfaceBehind(payload.pos(), payload.dir());
                add(effect);
                int matter = Math.round(impact.matterCount() * payload.scale());
                SpellParticles.burst(impact.matterKind(), payload.pos(), payload.dir(), accented ? shaderShare(impact.matterKind(), matter) : matter, impact.matterSpeed(), 0.14F + profile.tier().tier() * 0.03F, 18, profile.color(ColorRole.BASE), 1.0F, impact.matterKind().dark() ? 6 : 26);
                if (accented) {
                    Vec3[] face = viewerFace(payload);
                    SpellAccents.impact(profile, face[0], face[1], payload.scale(), onBody);
                }
            }
            case VisualCuePayload.CUE_DECAL -> add(new Effect(Kind.DECAL, profile, payload, Math.max(10, profile.linger().decalTicks()), 0));
            case VisualCuePayload.CUE_BARRIER_HIT -> {
                add(new Effect(Kind.BARRIER_HIT, profile, payload, 10, 0));
                if (accented) {
                    SpellAccents.barrierHit(profile, payload.pos(), payload.dir());
                }
            }
            case VisualCuePayload.CUE_ZONE_TICK -> {
                add(new Effect(Kind.ZONE_TICK, profile, payload, 14, 0));
                if (accented) {
                    SpellAccents.zone(profile, payload.pos(), profile.tier().radius() * Math.max(0.2F, payload.scale()));
                }
            }
            case VisualCuePayload.CUE_PARTICLE_BURST -> {
                int motes = Math.round(12 * payload.scale());
                FxKinds.Smoke kind = trail.active() ? trail.kind() : profile.impact().matterKind();
                SpellParticles.burst(kind, payload.pos(), payload.dir(), accented ? shaderShare(kind, motes) : motes, 0.3F, 0.15F, 16, profile.color(ColorRole.BASE), 1.0F, 24);
                if (accented) {
                    Vec3[] face = viewerFace(payload);
                    SpellAccents.impact(profile, face[0], face[1], payload.scale() * 0.6F, true);
                }
            }
            default -> { }
        }
    }

    /**
     * Where a hit's matter is thrown from and which way: for a hit on a body, off the side of it the
     * viewer sees, at its middle height, outward; anything else where and as the cue says. Thrown
     * from the middle of a golem the spray was inside it and every school's hit read as the same
     * lone flash on its chest.
     */
    private static Vec3[] viewerFace(VisualCuePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity victim = payload.victimId() >= 0 && minecraft.level != null ? minecraft.level.getEntity(payload.victimId()) : null;
        if (victim == null) {
            return new Vec3[] {payload.pos(), payload.dir()};
        }
        Vec3 centre = victim.getBoundingBox().getCenter();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 out = new Vec3(camera.x - centre.x, 0.0D, camera.z - centre.z);
        if (out.lengthSqr() < 1.0E-4D) {
            return new Vec3[] {payload.pos(), payload.dir()};
        }
        out = out.normalize();
        Vec3 at = centre.add(out.scale(victim.getBbWidth() * 0.5D + AccentPlan.BODY_FACE_GAP));
        return new Vec3[] {at, out.add(0.0D, 0.35D, 0.0D).normalize()};
    }

    /**
     * What is left of a legacy additive burst once the matter layer throws the rest: a share of
     * it when it is light (sparks, glints, embers), none of it when it is stuff. Smoke, ash and ink
     * drawn additively are black cards over daylight, and fragments, glyphs, mist and droplets are
     * white dice; the accent already throws all of them as real particles.
     */
    private static int shaderShare(FxKinds.Smoke kind, int count) {
        return kind.glint() ? Math.max(2, Math.round(count * AccentPlan.SHADER_MATTER_SHARE)) : 0;
    }

    /**
     * True when a cue is within reach of the viewer's own eyes: their own cast, whichever camera
     * they are looking through. What that changes is applied only while {@link #firstPerson} holds,
     * and that is asked every frame, because the camera can change while a circle is still up.
     */
    private static boolean nearEyes(Vec3 pos) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        double reach = AccentPlan.OWN_CIRCLE_REACH;
        return viewer != null && viewer.getEyePosition().distanceToSqr(pos) < reach * reach;
    }

    private static boolean firstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /**
     * True when there is something solid behind a hit for its mark to lie on. The aim point on a
     * living target is the middle of its box, so a hit that did not name its victim used to lay
     * its ground mark flat through the target's chest, in mid-air.
     */
    private static boolean surfaceBehind(Vec3 pos, Vec3 normal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        double length = normal.length();
        Vec3 n = length < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : normal.scale(1.0D / length);
        return !minecraft.level.getBlockState(net.minecraft.core.BlockPos.containing(pos.subtract(n.scale(0.5D)))).isAir();
    }

    /**
     * The anchor a windup is drawn by when it is the viewer's own circle, seen from inside their
     * own head, or null when it is not.
     *
     * <p>A circle a block in front of the eyes, or under the feet, is drawn for everybody else at
     * the distance they stand from it. Seen from the caster's own camera it is the whole of the
     * frame, which is the single largest thing the capture of the roster showed. Someone standing
     * right beside the caster gets the caster's view of it too, which is the price of deciding it
     * from a position rather than an entity id the cue does not carry.
     */
    private static CircleAnchor ownAnchor(VisualProfile profile, VisualCuePayload payload) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer == null || !nearEyes(payload.pos())) {
            return null;
        }
        return switch (profile.anchor()) {
            case EYE_FORWARD -> CircleAnchor.EYE_FORWARD;
            // SpellFx.windup lays a following circle at the caster's feet until it has a host
            case GROUND, TARGET_FOLLOW -> CircleAnchor.GROUND;
            // BOTH sends two cues, one at the feet and one at the eyes, told apart by height
            case BOTH -> payload.pos().y < viewer.getEyeY() - 1.0D ? CircleAnchor.GROUND : CircleAnchor.EYE_FORWARD;
            default -> null;
        };
    }

    private static void add(Effect effect) {
        if (EFFECTS.size() >= MAX) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(effect);
    }

    public static void tick() {
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext();) {
            Effect e = it.next();
            if (++e.age >= e.life) {
                it.remove();
            }
        }
    }

    /** AFTER_PARTICLES: everything except through-terrain windups. */
    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        frame++;
        FxBudget.beginFrame(frame);
        if (EFFECTS.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        for (Effect e : EFFECTS) {
            if (e.kind == Kind.WINDUP && e.profile.windupThroughTerrain()) {
                continue;
            }
            Vec3 p = e.position(minecraft, partial);
            if (!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(p.x - 4.0D, p.y - 2.0D, p.z - 4.0D, p.x + 4.0D, p.y + 4.0D, p.z + 4.0D))) {
                continue;
            }
            pose.pushPose();
            pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            FxContext ctx = new FxContext(pose, buffers, partial, event.getCamera().rotation(), cam.subtract(p)).timing(e.age + partial, e.life, e.seed);
            ctx.detail = FxBudget.detailForDistance(3, p.distanceToSqr(cam));
            e.paint(ctx, false);
            pose.popPose();
        }
        for (RenderType type : MagicalFxRenderTypes.flushOrder()) {
            buffers.endBatch(type);
        }
    }

    /** AFTER_CUTOUT_BLOCKS: through-terrain T4 windups via glyphInkThrough. */
    public static void renderThroughTerrain(RenderLevelStageEvent event, Minecraft minecraft) {
        boolean any = false;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        for (Effect e : EFFECTS) {
            if (e.kind != Kind.WINDUP || !e.profile.windupThroughTerrain()) {
                continue;
            }
            Vec3 p = e.position(minecraft, partial);
            pose.pushPose();
            pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            FxContext ctx = new FxContext(pose, buffers, partial, event.getCamera().rotation(), cam.subtract(p)).timing(e.age + partial, e.life, e.seed);
            e.paint(ctx, true);
            pose.popPose();
            any = true;
        }
        if (any) {
            buffers.endBatch(MagicalFxRenderTypes.glyphInkThrough());
        }
    }

    private enum Kind {
        WINDUP, RELEASE, IMPACT, DECAL, BARRIER_HIT, ZONE_TICK
    }

    private static final class Effect {
        private final Kind kind;
        private final VisualProfile profile;
        private final VisualCuePayload payload;
        private final int life;
        private final int windup;
        private final int seed;
        private int age;
        /**
         * The anchor this windup is drawn by as the viewer's own circle - smaller, fainter, and a
         * sigil at the eyes moved to the hand's corner of the frame - or null when it is somebody
         * else's; see {@link #ownAnchor}. Applied only while the camera is in first person, asked
         * every frame: decided once, a circle cast in third person kept its full size after the
         * camera went back, and covered the whole view.
         */
        private CircleAnchor own;
        private float ownScale = 1.0F;
        private float ownOpacity = 1.0F;
        /** A release by the viewer: while they see it first person, the flash is a spark at the hand. */
        private boolean ownView;
        /** A hit with a surface behind it, for its mark to lie on. */
        private boolean onSurface = true;

        private Effect(Kind kind, VisualProfile profile, VisualCuePayload payload, int life, int windup) {
            this.kind = kind;
            this.profile = profile;
            this.payload = payload;
            this.life = life;
            this.windup = windup;
            this.seed = payload.seed();
        }

        private Vec3 position(Minecraft minecraft, float partial) {
            if (payload.victimId() >= 0 && minecraft.level != null) {
                Entity victim = minecraft.level.getEntity(payload.victimId());
                if (victim != null) {
                    return victim.getPosition(partial).add(0.0D, kind == Kind.IMPACT ? victim.getBbHeight() * 0.5D : 0.05D, 0.0D);
                }
            }
            return payload.pos();
        }

        private void paint(FxContext ctx, boolean through) {
            PoseStack pose = ctx.pose;
            CircleScript script = payload.sneak() ? profile.castCircle().mirroredSpin() : profile.castCircle();
            float radius = profile.tier().radius() * Math.max(0.2F, payload.scale());
            boolean accented = profile.accent().active();
            switch (kind) {
                case WINDUP -> {
                    // radius eases 0.6R -> R over the windup, release collapse after
                    float t = Mth.clamp(ctx.age / Math.max(1.0F, windup), 0.0F, 1.0F);
                    float ease = t * t * (3.0F - 2.0F * t);
                    float r = radius * (0.6F + 0.4F * ease);
                    float post = Math.max(0.0F, ctx.age - windup);
                    float opacity = 1.0F;
                    if (post > 0.0F) {
                        ReleaseMode mode = profile.release().mode();
                        float k = Mth.clamp(post / 6.0F, 0.0F, 1.0F);
                        if (mode == ReleaseMode.SLAM) {
                            r *= 1.0F + 0.4F * k;
                        } else if (mode == ReleaseMode.FUNNEL) {
                            r *= 1.0F - 0.75F * k;
                        } else {
                            pose.translate(0.0F, 1.2F * k, 0.0F);
                        }
                        opacity = 1.0F - Mth.clamp((post - 6.0F) / Math.max(1.0F, life - windup - 6.0F), 0.0F, 1.0F);
                    }
                    boolean ownNow = own != null && firstPerson();
                    boolean atHand = ownNow && own == CircleAnchor.EYE_FORWARD;
                    if (atHand) {
                        toTheHand(pose, ctx);
                    }
                    orientCircle(pose, ctx, profile.anchor(), payload.dir());
                    GlyphCirclePainter.paint(script, profile.palette(), r * (ownNow ? ownScale : 1.0F), ctx.age, life, ctx.detail, pose, ctx.buffers, seed, through, opacity * (ownNow ? ownOpacity : 1.0F), 0.0F);
                    // a circle laid on the aimed ground or hung in the sky has no hand at its middle
                    boolean hand = !accented || (profile.anchor() != CircleAnchor.AIM_SURFACE && profile.anchor() != CircleAnchor.SKY);
                    if (!through && post <= 0.0F && hand) {
                        pose.pushPose();
                        pose.translate(0.0F, 0.0F, 0.02F);
                        float orb = profile.windup().handOrbRadius() * (0.4F + 0.6F * ease) * (accented ? AccentPlan.HAND_ORB_SCALE : 1.0F);
                        float orbOpacity = atHand ? AccentPlan.OWN_HAND_ORB_OPACITY : accented ? 0.55F : 0.8F;
                        OrbPainter.billboard(ctx, profile.windup().handOrbKind(), orb, profile.color(ColorRole.HOT), orbOpacity, ease, 8, 12);
                        pose.popPose();
                    }
                }
                case RELEASE -> {
                    float p = ctx.age / (float) life;
                    boolean mine = ownView && firstPerson();
                    float muzzle = mine
                            ? AccentPlan.ownMuzzleRadius(profile.tier().tier())
                            : (0.5F + profile.tier().tier() * 0.15F) * (accented ? AccentPlan.MUZZLE_SCALE : 1.0F);
                    float muzzleOpacity = mine ? AccentPlan.OWN_MUZZLE_OPACITY : accented ? 0.7F : 1.0F;
                    OrbPainter.billboard(ctx, profile.release().muzzleFlashKind(), muzzle, profile.color(accented ? ColorRole.BRIGHT : ColorRole.HOT), muzzleOpacity, p, 8, 10);
                    if (profile.release().mode() == ReleaseMode.SLAM) {
                        pose.pushPose();
                        float slam = radius * 1.3F;
                        float slamOpacity = 1.0F;
                        if (accented) {
                            // the release is sent from the hand; a slam is on the floor under it
                            Vec3 d = payload.dir().lengthSqr() < 1.0E-6D ? Vec3.ZERO : payload.dir().normalize();
                            pose.translate(-d.x * AccentPlan.HAND_REACH,
                                    AccentPlan.HAND_DROP - AccentPlan.EYE_HEIGHT - d.y * AccentPlan.HAND_REACH + 0.05D,
                                    -d.z * AccentPlan.HAND_REACH);
                            if (mine) {
                                slam *= AccentPlan.OWN_SLAM_SCALE;
                                slamOpacity = AccentPlan.OWN_SLAM_OPACITY;
                            }
                        }
                        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                        MarkPainter.mark(ctx, FxKinds.Mark.SIGIL_SLAM_FLASH, slam, profile.color(ColorRole.BRIGHT), slamOpacity, p, 8, 6);
                        pose.popPose();
                    }
                }
                case IMPACT -> {
                    ProfileCues.ImpactSpec impact = profile.impact();
                    float p = ctx.age / (float) life;
                    if (accented) {
                        // a spark at the point, not a sun: the matter layer carries the rest of the hit
                        float flashTicks = AccentPlan.FLASH_TICKS;
                        if (ctx.age < flashTicks) {
                            // BASE, not BRIGHT: over daylight a pale school's BRIGHT clips to white and the hit loses its hue
                            OrbPainter.billboard(ctx, impact.flashKind(), impact.flashSize() * AccentPlan.FLASH_SCALE * payload.scale(), profile.color(ColorRole.BASE), AccentPlan.FLASH_OPACITY, ctx.age / flashTicks, 8, 12);
                        }
                    } else {
                        float flashLife = Math.min(1.0F, ctx.age / 8.0F);
                        if (ctx.age < 10.0F) {
                            OrbPainter.billboard(ctx, impact.flashKind(), impact.flashSize() * AccentPlan.LEGACY_FLASH_SCALE * payload.scale(), profile.color(ColorRole.HOT), 1.0F, flashLife, 8, 12);
                        }
                    }
                    // Nothing flat on a living target. The mark and the delivery stamp are single
                    // quads held at the hit normal, while position() drags them along with the
                    // victim every frame - so the moment anyone moves they are being viewed from an
                    // angle they were never placed for, and edge-on they are a line. Under a boss
                    // spamming spells they also pile up, several seconds each. A surface keeps them,
                    // because a surface has a normal and does not walk away. The flash above is
                    // camera-facing and the matter burst already went out when the cue arrived, so a
                    // hit on a creature still reads without either of them.
                    if (payload.victimId() < 0 && onSurface) {
                        pose.pushPose();
                        orientToNormal(pose, payload.dir());
                        pose.translate(0.0F, 0.0F, 0.03F);
                        float markSize = (0.8F + 0.35F * payload.scale()) * (1.0F + profile.tier().tier() * 0.25F) * (accented ? AccentPlan.MARK_SCALE : 1.0F);
                        MarkPainter.mark(ctx, impact.markKind(), markSize, profile.color(ColorRole.BASE), (1.0F - p * 0.6F) * (accented ? AccentPlan.MARK_OPACITY : 1.0F), p, 8, 6);
                        // an accented hit does not stamp a second glyph circle over its own mark
                        if (impact.stampDeliveryCircle() && !accented && ctx.age < 16.0F) {
                            // the delivery circle blinks in and un-draws backward: reversed lifecycle over 16 ticks
                            float stampAge = 16.0F - ctx.age;
                            pose.translate(0.0F, 0.0F, 0.02F);
                            GlyphCirclePainter.paint(profile.deliveryCircle(), profile.palette(), radius * 0.7F, stampAge, 0.0F, Math.min(ctx.detail, 1), pose, ctx.buffers, seed, false, 1.0F - ctx.age / 16.0F, 0.0F);
                        }
                        pose.popPose();
                    }
                }
                case DECAL -> {
                    float p = ctx.age / (float) life;
                    pose.pushPose();
                    orientToNormal(pose, payload.dir());
                    pose.translate(0.0F, 0.0F, 0.02F);
                    // A stain a skill leaves is a hit's mark that lingers, so an accented one is cut the
                    // same way; radius already carries the cue's scale, which the legacy look took twice.
                    // A spirit wolf dissolving at its owner's side left a dark smear two blocks wide
                    // across the bottom of their view for as long as the stain lasted.
                    float decal = accented ? radius * AccentPlan.MARK_SCALE : radius * payload.scale();
                    MarkPainter.mark(ctx, profile.linger().decalKind(), decal * 0.8F, profile.color(ColorRole.DIM), accented ? AccentPlan.MARK_OPACITY : 1.0F, p, 8, 8);
                    pose.popPose();
                }
                case BARRIER_HIT -> {
                    float p = ctx.age / (float) life;
                    pose.pushPose();
                    orientToNormal(pose, payload.dir());
                    MarkPainter.mark(ctx, FxKinds.Mark.SHOCK_RING, 0.9F * payload.scale(), profile.color(ColorRole.BRIGHT), 1.0F - p, p, 8, 4);
                    pose.popPose();
                }
                case ZONE_TICK -> {
                    float p = ctx.age / (float) life;
                    pose.pushPose();
                    pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                    pose.translate(0.0F, 0.0F, 0.02F);
                    // radius already carries the cue's scale; the legacy look multiplied it in twice
                    float ripple = accented ? radius : radius * payload.scale();
                    MarkPainter.mark(ctx, FxKinds.Mark.RIPPLES, ripple, profile.color(ColorRole.BASE), (0.8F - p * 0.8F) * (accented ? 0.7F : 1.0F), p, 3, 6);
                    pose.popPose();
                }
            }
        }

        /** Place the circle per anchor: flat on the ground, or perpendicular to the look vector at the hand. */
        /**
         * Moves the pose from the line of sight to the hand's corner of the frame, by a share of the
         * sigil's own distance from the eye (AccentPlan.OWN_SIGIL_RIGHT, OWN_SIGIL_DOWN).
         */
        private static void toTheHand(PoseStack pose, FxContext ctx) {
            net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            org.joml.Vector3f left = camera.getLeftVector();
            org.joml.Vector3f up = camera.getUpVector();
            float distance = (float) ctx.cameraPos.length();
            float right = AccentPlan.OWN_SIGIL_RIGHT * distance;
            float down = AccentPlan.OWN_SIGIL_DOWN * distance;
            pose.translate(-left.x() * right - up.x() * down, -left.y() * right - up.y() * down, -left.z() * right - up.z() * down);
        }

        private static void orientCircle(PoseStack pose, FxContext ctx, CircleAnchor anchor, Vec3 dir) {
            switch (anchor) {
                case EYE_FORWARD -> FilamentPainter.orientAlong(pose, dir);
                case AIM_SURFACE -> orientToNormal(pose, dir);
                case SKY -> {
                    pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                }
                default -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
        }

        static void orientToNormal(PoseStack pose, Vec3 normal) {
            FilamentPainter.orientToNormal(pose, normal);
        }
    }
}
