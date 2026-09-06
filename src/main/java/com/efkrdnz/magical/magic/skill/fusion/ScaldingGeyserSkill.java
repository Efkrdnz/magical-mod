package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SPELL CREATOR (magma_vent + deluge_jet) - LAUNCH_PERIODIC / AIM_SURFACE / VERTICAL_JET_PULSE. A
 * geyser bore cracks open and erupts on a timer: anything above it is launched (allies land softly,
 * enemies are scalded); between eruptions it hisses a slowing steam ring. Sneak = bore under your
 * own feet.
 */
public final class ScaldingGeyserSkill implements SkillModule {
    private static final int PERIOD = 30;
    private static final int ERUPTION = 10;
    private static final double JET_HEIGHT = 8.0D;
    private static final double BORE = 1.2D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SCALDING_GEYSER;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity bore = SpellEffectEntity.spawn(ctx, pos, Math.max(60, ctx.duration()), 2.0F * Math.max(0.6F, ctx.size() / 1.2F), new Vec3(0.0D, 1.0D, 0.0D));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 16.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            int t = entity.tickCount % PERIOD;
            boolean erupting = t < ERUPTION;
            entity.setValue(erupting ? 1.0F - t / (float) ERUPTION : 0.0F);
            if (t == 0) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.impact(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.4F);
            }
            if (erupting) {
                double launch = Math.max(0.8D, entity.speed());
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(BORE, JET_HEIGHT, BORE), l -> l.isAlive())) {
                    double dx = e.getX() - entity.getX();
                    double dz = e.getZ() - entity.getZ();
                    if (dx * dx + dz * dz > BORE * BORE || e.getY() < entity.getY() - 0.5D || e.getY() > entity.getY() + JET_HEIGHT) {
                        continue;
                    }
                    Vec3 v = e.getDeltaMovement();
                    e.setDeltaMovement(v.x * 0.5D, Math.max(v.y, launch), v.z * 0.5D);
                    e.hurtMarked = true;
                    if (SkillTargets.isAlly(entity.owner(), e)) {
                        e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
                    } else if (SkillTargets.isHostile(entity.owner(), e) && t == 1) {
                        SkillTargets.hurt(level, entity.owner(), e, entity.damage(), entity.definition(), true);
                        e.clearFire();
                        e.igniteForSeconds(3.0F);
                    }
                }
                return;
            }
            if (t % 5 == 0) {
                for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, entity.owner(), entity.position(), entity.radius(), 2.0D)) {
                    hostile.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false));
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.GEYSER).frame(12).band(GlyphKind.TICK_BAND, 30, ColorRole.HOT).band(GlyphKind.WAVE_BAND, 12).band(GlyphKind.TOOTH_BAND, 18).stamps(StampId.FLAME, 6).orbit(3, 0.84F, 3).core(CoreKind.EMBER_PIT).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("scalding_geyser", 8.0F))
                .silhouette(Silhouette.body(Silhouette.Form.PLATE_FAN, FxKinds.Body.MAGMA_ROCK, 6, 1.3F, 0.35F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.MIST_WISP, FxKinds.Overlay.HEAT_SHIMMER)
                .budget(2)
                .bounds(4.0F, 10.0F, 1.0F);
    }
}
