package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.GlyphCirclePainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
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
    private static long frame;

    private TransientVisuals() {}

    public static void clear() {
        EFFECTS.clear();
    }

    public static void handle(VisualCuePayload payload) {
        VisualProfile profile = VisualProfiles.byIndex(payload.skillIndex());
        ProfileCues.TrailSpec trail = profile.trail();
        switch (payload.cue()) {
            case VisualCuePayload.CUE_CAST_WINDUP -> {
                int windup = Math.max(4, profile.windup().windupTicks());
                add(new Effect(Kind.WINDUP, profile, payload, windup + profile.tier().lingerTicks(), windup));
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
                add(new Effect(Kind.RELEASE, profile, payload, 8, 0));
                ProfileCues.ReleaseCue release = profile.release();
                SpellParticles.burst(trail.active() ? trail.kind() : profile.impact().matterKind(), payload.pos(), payload.dir(), Math.max(4, release.muzzleParticleBurst()), 0.35F, 0.16F, 14, profile.color(ColorRole.BRIGHT), 1.0F, 26);
            }
            case VisualCuePayload.CUE_IMPACT -> {
                ProfileCues.ImpactSpec impact = profile.impact();
                add(new Effect(Kind.IMPACT, profile, payload, Math.max(10, impact.markTicks()), 0));
                SpellParticles.burst(impact.matterKind(), payload.pos(), payload.dir(), Math.round(impact.matterCount() * payload.scale()), impact.matterSpeed(), 0.14F + profile.tier().tier() * 0.03F, 18, profile.color(ColorRole.BASE), 1.0F, impact.matterKind().dark() ? 6 : 26);
            }
            case VisualCuePayload.CUE_DECAL -> add(new Effect(Kind.DECAL, profile, payload, Math.max(10, profile.linger().decalTicks()), 0));
            case VisualCuePayload.CUE_BARRIER_HIT -> add(new Effect(Kind.BARRIER_HIT, profile, payload, 10, 0));
            case VisualCuePayload.CUE_ZONE_TICK -> add(new Effect(Kind.ZONE_TICK, profile, payload, 14, 0));
            case VisualCuePayload.CUE_PARTICLE_BURST -> SpellParticles.burst(trail.active() ? trail.kind() : profile.impact().matterKind(), payload.pos(), payload.dir(), Math.round(12 * payload.scale()), 0.3F, 0.15F, 16, profile.color(ColorRole.BASE), 1.0F, 24);
            default -> { }
        }
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
                    orientCircle(pose, ctx, profile.anchor(), payload.dir());
                    GlyphCirclePainter.paint(script, profile.palette(), r, ctx.age, life, ctx.detail, pose, ctx.buffers, seed, through, opacity, 0.0F);
                    if (!through && post <= 0.0F) {
                        pose.pushPose();
                        pose.translate(0.0F, 0.0F, 0.02F);
                        OrbPainter.billboard(ctx, profile.windup().handOrbKind(), profile.windup().handOrbRadius() * (0.4F + 0.6F * ease), profile.color(ColorRole.HOT), 0.8F, ease, 8, 12);
                        pose.popPose();
                    }
                }
                case RELEASE -> {
                    float p = ctx.age / (float) life;
                    OrbPainter.billboard(ctx, profile.release().muzzleFlashKind(), 0.5F + profile.tier().tier() * 0.15F, profile.color(ColorRole.HOT), 1.0F, p, 8, 10);
                    if (profile.release().mode() == ReleaseMode.SLAM) {
                        pose.pushPose();
                        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                        MarkPainter.mark(ctx, FxKinds.Mark.SIGIL_SLAM_FLASH, radius * 1.3F, profile.color(ColorRole.BRIGHT), 1.0F, p, 8, 6);
                        pose.popPose();
                    }
                }
                case IMPACT -> {
                    ProfileCues.ImpactSpec impact = profile.impact();
                    float p = ctx.age / (float) life;
                    float flashLife = Math.min(1.0F, ctx.age / 8.0F);
                    if (ctx.age < 10.0F) {
                        OrbPainter.billboard(ctx, impact.flashKind(), impact.flashSize() * 1.6F * payload.scale(), profile.color(ColorRole.HOT), 1.0F, flashLife, 8, 12);
                    }
                    pose.pushPose();
                    orientToNormal(pose, payload.dir());
                    pose.translate(0.0F, 0.0F, 0.03F);
                    MarkPainter.mark(ctx, impact.markKind(), (0.8F + 0.35F * payload.scale()) * (1.0F + profile.tier().tier() * 0.25F), profile.color(ColorRole.BASE), 1.0F - p * 0.6F, p, 8, 6);
                    if (impact.stampDeliveryCircle() && ctx.age < 16.0F) {
                        // the delivery circle blinks in and un-draws backward: reversed lifecycle over 16 ticks
                        float stampAge = 16.0F - ctx.age;
                        pose.translate(0.0F, 0.0F, 0.02F);
                        GlyphCirclePainter.paint(profile.deliveryCircle(), profile.palette(), radius * 0.7F, stampAge, 0.0F, Math.min(ctx.detail, 1), pose, ctx.buffers, seed, false, 1.0F - ctx.age / 16.0F, 0.0F);
                    }
                    pose.popPose();
                }
                case DECAL -> {
                    float p = ctx.age / (float) life;
                    pose.pushPose();
                    orientToNormal(pose, payload.dir());
                    pose.translate(0.0F, 0.0F, 0.02F);
                    MarkPainter.mark(ctx, profile.linger().decalKind(), radius * 0.8F * payload.scale(), profile.color(ColorRole.DIM), 1.0F, p, 8, 8);
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
                    MarkPainter.mark(ctx, FxKinds.Mark.RIPPLES, radius * payload.scale(), profile.color(ColorRole.BASE), 0.8F - p * 0.8F, p, 3, 6);
                    pose.popPose();
                }
            }
        }

        /** Place the circle per anchor: flat on the ground, or perpendicular to the look vector at the hand. */
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
