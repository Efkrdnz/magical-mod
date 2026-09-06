package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SolidConstructEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * MAGIC ORIGINATOR (crucible + cleansing_ray) - RADIATE_AND_FOCUS / AIM_SURFACE_PLACE /
 * SOLID_SUN_WITH_STEERED_BEAM. After a counterable windup a solid sun of molten glass drops onto the
 * ground and burns there: heat rings purge and ignite everything around it, allies are warmed, and
 * a focal beam the caster steers by looking melts what it touches.
 */
public final class FallenSunSkill implements SkillModule {
    private static final int WINDUP = 24;
    private static final byte MODE_BODY = 2;
    private static final double RING = 8.0D;
    private static final double BEAM_RANGE = 24.0D;
    private static final double BEAM_RADIUS = 0.6D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.FALLEN_SUN;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity controller = SpellEffectEntity.spawn(ctx, ctx.aim().point(), WINDUP + Math.max(60, ctx.duration()), (float) RING, new Vec3(0.0D, 1.0D, 0.0D));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 20.0D;
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
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(5.0F, 20.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                if ((entity.mode() & MODE_BODY) != 0) {
                    return; // the solid sun body has no logic of its own
                }
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                int t = entity.tickCount;
                if (t < WINDUP) {
                    return;
                }
                Vec3 centre = entity.position();
                if (t == WINDUP) {
                    SolidConstructEntity sun = SolidConstructEntity.create(level, entity, centre, 3.0F, 3.0F, 400.0F, 0);
                    sun.setMode(MODE_BODY);
                    sun.setLife(entity.life() - WINDUP);
                    level.addFreshEntity(sun);
                    entity.serverData().putUUID("Sun", sun.getUUID());
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.4F);
                    for (LivingEntity crushed : SkillTargets.hostilesWithin(level, owner, centre.add(0.0D, 1.5D, 0.0D), 2.2D)) {
                        SkillTargets.hurt(level, owner, crushed, entity.damage() * 2.0F, entity.definition(), true);
                        SkillTargets.shove(crushed, centre, 1.0D, 0.4D);
                    }
                }
                Vec3 core = centre.add(0.0D, 1.5D, 0.0D);
                if (t % 20 == 0) {
                    for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, core, RING)) {
                        SkillTargets.hurt(level, owner, hostile, 7.0F, entity.definition().id());
                        hostile.igniteForSeconds(3.0F);
                        List<MobEffectInstance> buffs = new ArrayList<>();
                        for (MobEffectInstance effect : hostile.getActiveEffects()) {
                            if (effect.getEffect().value().isBeneficial()) {
                                buffs.add(effect);
                            }
                        }
                        for (MobEffectInstance buff : buffs) {
                            hostile.removeEffect(buff.getEffect());
                        }
                    }
                    for (LivingEntity ally : SkillTargets.alliesWithin(level, owner, core, RING)) {
                        ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
                    }
                    SpellFx.zoneTick(level, entity.definition(), centre, (float) RING * 0.5F);
                }
                // the focal beam follows the caster's aim
                if (owner == null || !owner.isAlive()) {
                    return;
                }
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 40.0D, 0.0D, false, 0, null);
                Vec3 target = aim.point();
                Vec3 dir = target.subtract(core);
                double len = Math.min(BEAM_RANGE, dir.length());
                if (len < 1.0E-3D) {
                    return;
                }
                dir = dir.normalize();
                Vec3 end = core.add(dir.scale(len));
                CompoundTag synced = new CompoundTag();
                synced.putDouble("BX", end.x);
                synced.putDouble("BY", end.y);
                synced.putDouble("BZ", end.z);
                entity.setSyncedData(synced);
                if (t % 10 == 0) {
                    for (LivingEntity hostile : SkillTargets.hostilesIn(level, owner, new AABB(core, end).inflate(BEAM_RADIUS + 0.5D))) {
                        Vec3 p = hostile.getBoundingBox().getCenter();
                        double along = p.subtract(core).dot(dir);
                        if (along < 0.0D || along > len || p.distanceTo(core.add(dir.scale(along))) > BEAM_RADIUS + hostile.getBbWidth() * 0.5D) {
                            continue;
                        }
                        SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition(), true);
                        hostile.igniteForSeconds(2.0F);
                    }
                    if (aim.hitBlock()) {
                        SpellFx.decal(level, entity.definition(), end, aim.normal(), 1.2F);
                    }
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if ((entity.mode() & MODE_BODY) != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                if (entity.serverData().hasUUID("Sun") && level.getEntity(entity.serverData().getUUID("Sun")) instanceof SolidConstructEntity sun) {
                    sun.finish();
                }
                SpellFx.impact(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.SUN).frame(16).band(GlyphKind.PETAL_BAND, 24, ColorRole.BASE).band(GlyphKind.RUNE_BAND, 24, ColorRole.HOT).band(GlyphKind.TOOTH_BAND, 36).stamps(StampId.STAR4, 12).spokes(12, 0.3F, true).orbit(7, 0.86F, 4).core(CoreKind.SUNBURST).stack(3, 0.6F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .throughTerrain(true)
                .silhouette(Silhouette.body(Silhouette.Form.SPHERE, FxKinds.Body.GOLD, 16, 1.5F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.HEX_LENS, 2.6F, 6, 8).withOffset(1.5F).withOpacity(0.7F).forModes(1))
                .silhouette(Silhouette.custom("fallen_sun", 1.5F).forModes(0))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.EMBER_FIELD, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.BLOOM_RAYS)
                .budget(3)
                .bounds(26.0F, 6.0F, 2.0F);
    }
}
