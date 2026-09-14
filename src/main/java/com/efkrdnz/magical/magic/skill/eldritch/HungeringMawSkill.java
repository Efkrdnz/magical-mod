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
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - jaws open in the ground where you looked and wait.
 *
 * <p>They open over the windup, hang open for at most the duration, and the moment something
 * hostile stands in them - or when the wait runs out - they snap shut: the bite, an upward launch,
 * and every hostile projectile inside the bite eaten. The renderer reads the windup and the snap
 * tick off the synced tag and does the rest.
 */
public final class HungeringMawSkill implements SkillModule {
    public static final double BASE_BITE = 1.8D;
    public static final double AIM_RANGE = 16.0D;
    public static final int BASE_WINDUP = 24;
    private static final int SNAP_TICKS = 3;
    private static final int LINGER_AFTER_SNAP = 14;
    private static final double BITE_HEIGHT = 2.2D;
    private static final String KEY_WINDUP = "windup";
    private static final String KEY_SNAP = "snap";
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HUNGERING_MAW;
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
                if (ctx.aim() != null && ctx.aim().hitEntity()) {
                    // Under the feet of what the caster looks at, on whatever ground is below them.
                    Vec3 feet = ctx.aim().entity().position();
                    Vec3 ground = AimResolver.groundBelow(ctx.level(), feet, 8);
                    at = ground != null ? ground : feet;
                }
                float potency = EldritchService.potency(ctx.state());
                int windup = Math.max(6, Math.round(BASE_WINDUP / Math.max(0.35F, ctx.stats().speed())));
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity maw = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_MAW,
                        EldritchConstructEntity.ANCHOR_GROUND, at, windup + ctx.duration() + SNAP_TICKS + LINGER_AFTER_SNAP,
                        (float) (BASE_BITE * ctx.size() * potency), ctx.size() * potency, ctx.look());
                CompoundTag synced = new CompoundTag();
                synced.putInt(KEY_WINDUP, windup);
                synced.putInt(KEY_SNAP, 0);
                maw.setSyncedData(synced);
                maw.serverData().putFloat(KEY_POTENCY, potency);
                ctx.level().playSound(null, maw.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.5F);
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
                return TuningView.DEFAULT.labels("screen.magical.tuning.bite", "screen.magical.tuning.snap",
                        "screen.magical.tuning.gape", "screen.magical.tuning.patience", "screen.magical.tuning.thrift");
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity maw)) {
                    return;
                }
                CompoundTag synced = maw.syncedData();
                int windup = synced.getInt(KEY_WINDUP);
                int snap = synced.getInt(KEY_SNAP);
                if (snap > 0) {
                    if (maw.tickCount >= snap + SNAP_TICKS + LINGER_AFTER_SNAP) {
                        maw.finish();
                    }
                    return;
                }
                if (maw.tickCount < windup) {
                    return;
                }
                maw.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ServerLevel level = maw.serverLevel();
                List<LivingEntity> standing = SkillTargets.hostilesInCylinder(level, maw.owner(), maw.position(), maw.radius(), BITE_HEIGHT);
                boolean waited = maw.tickCount >= windup + maw.duration();
                if (standing.isEmpty() && !waited) {
                    return;
                }
                float potency = maw.serverData().contains(KEY_POTENCY) ? maw.serverData().getFloat(KEY_POTENCY) : 1.0F;
                for (LivingEntity bitten : standing) {
                    SkillTargets.hurt(level, maw.owner(), bitten, maw.damage() * potency, maw.skillId());
                    bitten.setDeltaMovement(bitten.getDeltaMovement().add(0.0D, maw.knockback(), 0.0D));
                    bitten.hurtMarked = true;
                }
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, maw.getBoundingBox().inflate(maw.radius(), BITE_HEIGHT, maw.radius()),
                        p -> p.getOwner() != maw.owner())) {
                    projectile.discard();
                }
                CompoundTag shut = synced.copy();
                shut.putInt(KEY_SNAP, maw.tickCount);
                maw.setSyncedData(shut);
                maw.setPhase(SpellEffectEntity.PHASE_CLOSING);
                level.playSound(null, maw.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.4F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.FANGED_MAW).frame(6)
                        .band(GlyphKind.TOOTH_BAND, 16, ColorRole.HOT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.TOOTH, 8).core(CoreKind.VOID_PIT, ColorRole.INK).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.rift(Silhouette.Form.DISC, FxKinds.Rift.IRIS_MOUTH, 1.0F, 1.0F, FxKinds.RiftInterior.VOID_BLACK))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.MAW, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.FLASH)
                .budget(3)
                .bounds(3.0F, 2.5F, 1.0F);
    }
}
