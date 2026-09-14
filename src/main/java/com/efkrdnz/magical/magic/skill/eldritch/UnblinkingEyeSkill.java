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
import com.efkrdnz.magical.magic.cast.AimResolver;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - a lidless eye hangs where you looked and watches.
 *
 * <p>What it sees is revealed to its owner, takes a quarter more of their spells (read by
 * {@code EldritchPassives.outgoingSpellDamage}) and feels the stare itself every two seconds. It
 * never blinks: the body rolls toward its mark and the pupil contracts on it, and that is all the
 * motion it has.
 */
public final class UnblinkingEyeSkill implements SkillModule {
    public static final double BASE_SIGHT = 12.0D;
    public static final double AIM_RANGE = 12.0D;
    public static final float SEEN_AMP = 1.25F;
    private static final int LOOK_INTERVAL = 5;
    private static final int STING_INTERVAL = 40;
    private static final int REVEAL_TICKS = 12;
    /** How far back from a wall the eye hangs. */
    public static final double WALL_GAP = 0.7D;
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.UNBLINKING_EYE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                AimResolver.Result aim = ctx.aim();
                Vec3 at;
                if (aim != null && aim.hitEntity()) {
                    // Just off what it looks at, on the side of the caster.
                    at = aim.point().add(aim.normal().scale(aim.entity().getBbWidth() / 2.0D + WALL_GAP));
                } else if (aim != null && aim.hitBlock()) {
                    at = aim.point().add(aim.normal().scale(WALL_GAP));
                } else {
                    at = ctx.eye().add(ctx.look().scale(AIM_RANGE));
                }
                float potency = EldritchService.potency(ctx.state());
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity eye = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_EYE,
                        EldritchConstructEntity.ANCHOR_GROUND, at, ctx.duration(), (float) (BASE_SIGHT * ctx.size() * potency), ctx.size() * potency, ctx.look());
                eye.serverData().putFloat(KEY_POTENCY, potency);
                eye.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, eye.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8F, 0.5F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED.labels("screen.magical.tuning.sting", null,
                        "screen.magical.tuning.sight", "screen.magical.tuning.watch", "screen.magical.tuning.thrift");
            }
        };
    }

    /** True when one of the owner's eyes has this thing in its stare. */
    public static boolean watching(ServerPlayer owner, LivingEntity target) {
        for (EldritchConstructEntity eye : EldritchConstructEntity.ownedBy(owner.serverLevel(), owner, MagicContent.UNBLINKING_EYE.id(), 64.0D)) {
            if (eye.target() == target) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity eye) || eye.tickCount % LOOK_INTERVAL != 0) {
                    return;
                }
                ServerLevel level = eye.serverLevel();
                LivingEntity seen = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity candidate : SkillTargets.hostilesWithin(level, eye.owner(), eye.position(), eye.radius())) {
                    double distance = candidate.distanceToSqr(eye);
                    if (distance < best && level.clip(new ClipContext(eye.position(), candidate.getEyePosition(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, eye)).getType() == HitResult.Type.MISS) {
                        best = distance;
                        seen = candidate;
                    }
                }
                eye.setTarget(seen);
                if (seen == null) {
                    return;
                }
                MagicStatusService.apply(seen, MagicStatus.REVEALED, REVEAL_TICKS, eye.skillId(), eye.owner());
                if (eye.tickCount % STING_INTERVAL == 0) {
                    float potency = eye.serverData().contains(KEY_POTENCY) ? eye.serverData().getFloat(KEY_POTENCY) : 1.0F;
                    SkillTargets.hurt(level, eye.owner(), seen, eye.damage() * potency, eye.skillId());
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.LIDLESS_EYE).frame(5)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.BRIGHT)
                        .band(GlyphKind.TICK_BAND, 24, ColorRole.INK)
                        .stamps(StampId.EYE, 5).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.THIN_HALO, 0.9F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.IRIS_CLOSE)
                .budget(2)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
