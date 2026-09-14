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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - a tentacle grows from your shoulder and whips through everything ahead.
 *
 * <p>The whip takes {@code BASE_WHIP / speed} ticks; at its middle everything hostile in a
 * forward arc of Length takes the sting, is shoved out of the arc sideways and is harried for the
 * duration. Then the tendril withdraws. The construct rides its owner (anchor OWNER) and the
 * renderer sweeps the base through the arc over the synced whip ticks.
 */
public final class TendrilLashSkill implements SkillModule {
    public static final double BASE_LENGTH = 5.0D;
    public static final int BASE_WHIP = 12;
    public static final double ARC_DEGREES = 60.0D;
    private static final int WITHDRAW_TICKS = 8;
    private static final String KEY_WHIP = "whip";
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TENDRIL_LASH;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                float potency = EldritchService.potency(ctx.state());
                int whip = Math.max(4, Math.round(BASE_WHIP / Math.max(0.35F, ctx.stats().speed())));
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity tendril = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_TENTACLE,
                        EldritchConstructEntity.ANCHOR_OWNER, ctx.feet(), whip + WITHDRAW_TICKS,
                        (float) (BASE_LENGTH * ctx.size() * potency), 0.6F * ctx.size() * potency, ctx.look());
                CompoundTag synced = new CompoundTag();
                synced.putInt(KEY_WHIP, whip);
                tendril.setSyncedData(synced);
                tendril.serverData().putFloat(KEY_POTENCY, potency);
                tendril.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 0.9F, 1.1F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.sting", "screen.magical.tuning.snap",
                        "screen.magical.tuning.length", "screen.magical.tuning.stagger", "screen.magical.tuning.thrift");
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity tendril) || !(tendril.owner() instanceof LivingEntity owner) || !owner.isAlive()) {
                    effect.finish();
                    return;
                }
                tendril.setPos(owner.getX(), owner.getY(), owner.getZ());
                int whip = tendril.syncedData().getInt(KEY_WHIP);
                if (tendril.tickCount != Math.max(1, whip / 2)) {
                    return;
                }
                Vec3 look = tendril.direction();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() > 1.0E-6D ? flat.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
                double cos = Math.cos(Math.toRadians(ARC_DEGREES));
                float potency = tendril.serverData().contains(KEY_POTENCY) ? tendril.serverData().getFloat(KEY_POTENCY) : 1.0F;
                ServerLevel level = tendril.serverLevel();
                for (LivingEntity hit : SkillTargets.hostilesWithin(level, owner, owner.position(), tendril.radius())) {
                    Vec3 toward = hit.position().subtract(owner.position());
                    Vec3 towardFlat = new Vec3(toward.x, 0.0D, toward.z);
                    if (towardFlat.lengthSqr() < 1.0E-6D || towardFlat.normalize().dot(flat) < cos) {
                        continue;
                    }
                    SkillTargets.hurt(level, owner, hit, tendril.damage() * potency, tendril.skillId());
                    Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
                    double sign = towardFlat.dot(side) >= 0.0D ? 1.0D : -1.0D;
                    hit.setDeltaMovement(hit.getDeltaMovement().add(side.scale(sign * tendril.knockback()).add(0.0D, 0.15D, 0.0D)));
                    hit.hurtMarked = true;
                    MagicStatusService.apply(hit, MagicStatus.HARRIED, Math.max(20, tendril.duration()), tendril.skillId(), owner);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.LASH).frame(7)
                        .band(GlyphKind.ARC_SWEEP, 3, ColorRole.BRIGHT)
                        .band(GlyphKind.DASHED_RING, 18, ColorRole.INK)
                        .stamps(StampId.WAVE, 7).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.TRAIL, FxKinds.Filament.INK_TENDRIL, 3, 0.05F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.INK_BLEED)
                .budget(2)
                .bounds(6.0F, 2.5F, 1.0F);
    }
}
