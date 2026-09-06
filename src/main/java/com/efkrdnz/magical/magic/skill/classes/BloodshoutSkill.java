package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
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
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * BERSERKER - TAUNT_SELF / SELF / AGGRO_SPHERE. A roar forces every hostile mob nearby onto the
 * caster for the duration (their hits land softer, and their first landed hit sears them); enemy
 * players get a lock-on cue. Sneak = aim the shout as a cone.
 */
public final class BloodshoutSkill implements SkillModule {
    private static final int WINDUP = 6;
    private static final double RADIUS = 12.0D;
    private static final double CONE_RANGE = 20.0D;
    private static final double CONE_COS = Math.cos(Math.toRadians(50.0D));

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BLOODSHOUT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity banner = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.0D, 0.0D), WINDUP + Math.max(40, ctx.duration()), 1.6F, ctx.look().scale(-1.0D));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.defence();
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            Vec3 look = owner.getLookAngle();
            Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
            back = back.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, -1.0D) : back.normalize();
            entity.setPos(owner.getX(), owner.getY() + 1.0D, owner.getZ());
            entity.setDirection(back);
            if (entity.tickCount != WINDUP) {
                return;
            }
            int taunt = Math.max(40, entity.duration());
            double range = entity.sneakMode() ? CONE_RANGE : RADIUS;
            for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, owner.position(), range)) {
                if (entity.sneakMode()) {
                    Vec3 to = hostile.position().subtract(owner.position());
                    to = new Vec3(to.x, 0.0D, to.z);
                    if (to.lengthSqr() > 0.01D && to.normalize().dot(new Vec3(look.x, 0.0D, look.z).normalize()) < CONE_COS) {
                        continue;
                    }
                }
                if (hostile instanceof ServerPlayer target) {
                    SpellFx.overlay(target, entity.definition(), FxKinds.Overlay.HEARTBEAT, 40, 0.5F, ColorRole.HOT);
                    continue;
                }
                MagicStatusService.apply(hostile, MagicStatus.TAUNTED, taunt, 0, 0.0F, entity.definition().id(), owner);
            }
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            SpellFx.impact(level, entity.definition(), owner.getEyePosition(), look, null, owner, 1.8F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.WAR_HORN).frame(9).band(GlyphKind.TOOTH_BAND, 27).band(GlyphKind.WAVE_BAND, 9).stamps(StampId.CHEVRON, 9).core(CoreKind.EMBER_PIT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("bloodshout", 1.6F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.HEARTBEAT)
                .bounds(3.0F, 4.0F, 1.0F);
    }
}
