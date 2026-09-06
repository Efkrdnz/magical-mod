package com.efkrdnz.magical.magic.skill.classes;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * TRANSMUTER - PLANT_BOMB / AIM_TARGET_HITSCAN / CARRIED_POINT_BURST. After a windup a splash of
 * reagent plants on the first living creature along the look (else the struck face, else the ray
 * end) and rides it for a fixed fuse, then detonates wherever the carrier is; nothing hurries or
 * delays it. Sneak = stick it to the surface under the aim point as a timed mine.
 */
public final class UnstableCompoundSkill implements SkillModule {
    private static final int WINDUP = 10;
    private static final byte MODE_EGG = 2;
    private static final double RANGE = 12.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.UNSTABLE_COMPOUND;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity hand = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.8D)), WINDUP, 0.35F, ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return RANGE;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 12.0F);
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
                ServerLevel level = entity.serverLevel();
                if ((entity.mode() & MODE_EGG) != 0) {
                    Entity carrier = entity.target();
                    if (carrier != null && carrier.isAlive()) {
                        entity.setPos(carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.6D, carrier.getZ());
                    }
                    entity.setValue(entity.tickCount / (float) Math.max(1, entity.life()));
                    return;
                }
                LivingEntity owner = entity.livingOwner();
                if (owner == null || !owner.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.8D)).add(0.0D, -0.2D, 0.0D));
                if (entity.tickCount != WINDUP - 1) {
                    return;
                }
                boolean mine = entity.sneakMode();
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), RANGE, mine ? 0.0D : 0.8D, mine, 8, e -> SkillTargets.isHostile(owner, e));
                int fuse = Math.max(20, entity.duration());
                SpellEffectEntity egg = SpellEffectEntity.create(level, entity.definition(), null, owner, aim.point(), fuse, 3.5F, aim.normal(), entity.seed());
                egg.setMode((byte) ((mine ? 1 : 0) | MODE_EGG));
                egg.copyStatsFrom(entity);
                LivingEntity carrier = mine ? null : aim.living();
                if (carrier != null && SkillTargets.isHostile(owner, carrier)) {
                    egg.setTarget(carrier);
                    egg.setPos(carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.6D, carrier.getZ());
                }
                level.addFreshEntity(egg);
                SpellFx.impact(level, entity.definition(), egg.position(), aim.normal(), carrier, owner, 0.7F);
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if ((entity.mode() & MODE_EGG) == 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                Entity owner = entity.owner();
                float damage = entity.damage();
                float knockback = entity.knockback();
                Vec3 centre = entity.position();
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(entity.radius()), e -> e.isAlive() && e != owner && !SkillTargets.isAlly(owner, e))) {
                    if (victim.getBoundingBox().getCenter().distanceTo(centre) > entity.radius()) {
                        continue;
                    }
                    SkillTargets.hurt(level, owner, victim, damage, entity.definition(), true);
                    SkillTargets.shove(victim, centre, 0.6D * Math.max(0.5D, knockback), 0.25D);
                }
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
                SpellFx.decal(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), 2.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.ALEMBIC).frame(7).band(GlyphKind.TICK_BAND, 60).band(GlyphKind.CHAIN_BAND, 14).stamps(StampId.HOURGLASS, 7).orbit(3, 0.84F, 3).core(CoreKind.DISC_GLOW).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.orb(Silhouette.Form.STACK, FxKinds.Orb.CHARGE_SPHERE, 0.9F, 3, 10).withSize(0.9F, 0.5F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 0.3F, 2, 4).forModes(0))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.FLASH)
                .bounds(4.0F, 2.0F, 2.0F);
    }
}
