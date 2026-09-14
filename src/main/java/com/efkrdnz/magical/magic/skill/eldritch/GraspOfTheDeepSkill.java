package com.efkrdnz.magical.magic.skill.eldritch;

import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - a tentacle erupts where you looked and takes hold of the nearest thing.
 *
 * <p>Rooted for as long as it holds, crushed at every squeeze; with nothing to hold it sways and
 * takes whatever walks into reach for as long as it stands. The same eruption is what the deep
 * uses on a mage it has Noticed, and what Call of the Deep rains on a crowd.
 */
public final class GraspOfTheDeepSkill implements SkillModule {
    /** How far a grasp reaches for something to hold at one point of Reach, in blocks. */
    public static final double BASE_REACH = 2.5D;
    public static final double AIM_RANGE = 20.0D;
    /** Ticks from eruption to the first squeeze. */
    private static final int FORM_TICKS = 12;
    private static final int BASE_CRUSH_INTERVAL = 20;
    private static final int ROOT_TOP_UP = 15;
    private static final int REACH_HOLD_TICKS = 40;
    /** How far past the edge of a victim the tentacle erupts, toward the caster. */
    public static final double BESIDE_GAP = 0.75D;
    private static final String KEY_CRUSH = "crush";
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GRASP_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                Vec3 at = ctx.aim() != null ? ctx.aim().point() : ctx.feet().add(ctx.look().scale(4.0D));
                if (ctx.aim() != null && ctx.aim().entity() != null) {
                    at = beside(ctx.aim().entity(), ctx.caster().position());
                }
                float potency = EldritchService.potency(ctx.state());
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                grasp(ctx.level(), null, ctx, at, null, ctx.duration() + FORM_TICKS, potency);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.crush", "screen.magical.tuning.rise",
                        "screen.magical.tuning.reach", "screen.magical.tuning.hold", "screen.magical.tuning.thrift");
            }
        };
    }

    /**
     * The eruption itself. Either a cast (ctx) or a running effect (parent) supplies the skill,
     * owner and stats; a target given here is held from the first tick, else the grasp looks for
     * one within reach every tick.
     */
    public static EldritchConstructEntity grasp(ServerLevel level, SpellEffectEntity parent, CastContext ctx, Vec3 at,
            LivingEntity target, int life, float potency) {
        float size = ctx != null ? ctx.size() : 1.0F;
        float reach = (float) (BASE_REACH * size * potency);
        Vec3 dir = ctx != null ? ctx.look() : parent.direction();
        EldritchConstructEntity tentacle = ctx != null
                ? EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_TENTACLE, EldritchConstructEntity.ANCHOR_GROUND, at, life, reach, size * potency, dir)
                : EldritchConstructEntity.spawnChild(parent, EldritchConstructEntity.MODEL_TENTACLE, EldritchConstructEntity.ANCHOR_GROUND, at, life, reach, 0.8F * potency, dir);
        tentacle.serverData().putFloat(KEY_POTENCY, potency);
        if (target != null) {
            take(tentacle, target);
        }
        level.playSound(null, tentacle.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.0F, 0.6F);
        return tentacle;
    }

    /**
     * A point a step past the edge of a victim toward whoever is casting, on the ground, so the
     * tentacle wraps the victim rather than standing inside it.
     */
    public static Vec3 beside(Entity victim, Vec3 from) {
        Vec3 toward = new Vec3(from.x - victim.getX(), 0.0D, from.z - victim.getZ());
        toward = toward.lengthSqr() > 1.0E-6D ? toward.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        return victim.position().add(toward.scale(victim.getBbWidth() / 2.0D + BESIDE_GAP));
    }

    /** The deep reaches for the mage it has Noticed: a grasp erupting just ahead of them, on them. */
    public static void reachFor(ServerPlayer player, PlayerMagicState state) {
        MagicSkillResolvedStats stats = MagicContent.GRASP_OF_THE_DEEP.resolve(MagicSkillTuning.DEFAULT);
        Vec3 at = beside(player, player.position().add(player.getLookAngle().scale(4.0D)));
        SpellEffectEntity template = SpellEffectEntity.create(player.serverLevel(), MagicContent.GRASP_OF_THE_DEEP, stats, null,
                at, REACH_HOLD_TICKS, 1.0F, player.getLookAngle(), player.tickCount & 63);
        EldritchConstructEntity tentacle = EldritchConstructEntity.spawnChild(template, EldritchConstructEntity.MODEL_TENTACLE,
                EldritchConstructEntity.ANCHOR_GROUND, at, REACH_HOLD_TICKS + FORM_TICKS, 1.0F, 1.2F, player.getLookAngle());
        tentacle.serverData().putFloat(KEY_POTENCY, EldritchService.potency(state));
        take(tentacle, player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.2F, 0.4F);
    }

    private static void take(EldritchConstructEntity tentacle, LivingEntity target) {
        tentacle.setTarget(target);
        tentacle.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        MagicStatusService.apply(target, MagicStatus.ROOTED, Math.max(ROOT_TOP_UP, tentacle.life() - tentacle.tickCount), tentacle.skillId(), tentacle.owner());
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity tentacle) || tentacle.tickCount < FORM_TICKS) {
                    return;
                }
                LivingEntity held = tentacle.livingTarget();
                if (held == null || !held.isAlive() || held.distanceTo(tentacle) > tentacle.radius() * 2.0D + 1.0D) {
                    tentacle.setTarget(null);
                    tentacle.setPhase(SpellEffectEntity.PHASE_WINDUP);
                    if (tentacle.owner() == null) {
                        return;
                    }
                    LivingEntity nearest = null;
                    double best = Double.MAX_VALUE;
                    for (LivingEntity candidate : SkillTargets.hostilesWithin(tentacle.serverLevel(), tentacle.owner(), tentacle.position(), tentacle.radius())) {
                        double distance = candidate.distanceToSqr(tentacle);
                        if (distance < best) {
                            best = distance;
                            nearest = candidate;
                        }
                    }
                    if (nearest == null) {
                        return;
                    }
                    take(tentacle, nearest);
                    held = nearest;
                }
                CompoundTag scratch = tentacle.serverData();
                int interval = Math.max(6, Math.round(BASE_CRUSH_INTERVAL / Math.max(0.35F, tentacle.speed())));
                if (tentacle.tickCount >= scratch.getInt(KEY_CRUSH)) {
                    scratch.putInt(KEY_CRUSH, tentacle.tickCount + interval);
                    float potency = scratch.contains(KEY_POTENCY) ? scratch.getFloat(KEY_POTENCY) : 1.0F;
                    SkillTargets.hurt(tentacle.serverLevel(), tentacle.owner(), held, tentacle.damage() * potency, tentacle.skillId());
                    MagicStatusService.apply(held, MagicStatus.ROOTED, Math.max(ROOT_TOP_UP, tentacle.life() - tentacle.tickCount), tentacle.skillId(), tentacle.owner());
                }
            }

            // No clear on expiry: every hold is applied for exactly the ticks the grasp has left, so
            // the root lapses with the tentacle, and a second grasp on the same victim (a pulse of
            // the Call) is not wiped by the first one ending on the same tick.
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.TENDRIL).frame(3)
                        .band(GlyphKind.FACET_BAND, 12, ColorRole.BRIGHT)
                        .band(GlyphKind.WAVE_BAND, 8, ColorRole.INK)
                        .stamps(StampId.SPIRAL, 6).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.mark(FxKinds.Mark.INK_STAIN, 1.2F).withRole(ColorRole.DIM))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.TUNNEL)
                .budget(3)
                .bounds(3.0F, 3.0F, 1.0F);
    }
}
