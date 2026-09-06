package com.efkrdnz.magical.magic.skill.fire;

import com.efkrdnz.magical.entity.fx.SolidConstructEntity;
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
 * FIRE T2 - RAISE / AIM_SURFACE / COLUMN. A telegraphed star crack, then a solid hexagonal magma
 * column heaves up: everything in its footprint is thrown skyward and burned, the rim shoves, and
 * the column stands as real terrain until it crumbles. Sneak = erupt under your own feet.
 */
public final class MagmaVentSkill implements SkillModule {
    private static final int TELEGRAPH = 10;
    private static final int RISE = 4;
    private static final byte MODE_COLUMN = 2;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.MAGMA_VENT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                int stand = Math.max(40, ctx.duration());
                SpellEffectEntity vent = SpellEffectEntity.spawn(ctx, pos, TELEGRAPH + RISE + stand + 12, Math.max(0.5F, ctx.size() * 0.5F), new Vec3(0.0D, 1.0D, 0.0D));
                vent.setExtra(stand);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
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
                return MobCastProfile.attack(2.0F, 14.0F);
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
                if (entity instanceof SolidConstructEntity) {
                    return; // the column itself is inert terrain
                }
                ServerLevel level = entity.serverLevel();
                float radius = entity.radius();
                if (entity.tickCount == TELEGRAPH) {
                    Vec3 centre = entity.position();
                    for (LivingEntity hit : SkillTargets.hostilesInCylinder(level, entity.owner(), centre, radius + 0.6D, 2.5D)) {
                        double dx = hit.getX() - centre.x;
                        double dz = hit.getZ() - centre.z;
                        boolean inFootprint = dx * dx + dz * dz <= radius * radius;
                        if (inFootprint) {
                            SkillTargets.hurt(level, entity.owner(), hit, entity.damage(), entity.definition(), true);
                            hit.setDeltaMovement(hit.getDeltaMovement().x * 0.3D, Math.max(0.4D, entity.speed()), hit.getDeltaMovement().z * 0.3D);
                            hit.hurtMarked = true;
                            hit.igniteForSeconds(4.0F);
                        } else {
                            SkillTargets.shove(hit, centre, 0.4D * entity.knockback() + 0.3D, 0.15D);
                        }
                    }
                    SolidConstructEntity column = SolidConstructEntity.create(level, entity, centre, radius * 2.0F, 4.0F, 60.0F, 0);
                    column.setMode(MODE_COLUMN);
                    column.setLife(RISE + Math.max(40, entity.extra()));
                    level.addFreshEntity(column);
                    entity.serverData().putUUID("Column", column.getUUID());
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.6F);
                } else if (entity.tickCount == TELEGRAPH + RISE + Math.max(40, entity.extra())) {
                    entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                    SpellFx.decal(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), 1.8F);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.serverData().hasUUID("Column")) {
                    Entity column = entity.serverLevel().getEntity(entity.serverData().getUUID("Column"));
                    if (column != null) {
                        column.discard();
                    }
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.MOUNTAIN).frame(6).band(GlyphKind.FACET_BAND, 12).band(GlyphKind.TOOTH_BAND, 18).stamps(StampId.NEEDLE, 6).orbit(3, 0.84F, 3).core(CoreKind.EMBER_PIT).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.body(Silhouette.Form.PRISM, FxKinds.Body.MAGMA_ROCK, 6, 0.8F, 4.0F).forModes(1))
                .silhouette(Silhouette.mark(FxKinds.Mark.CRACK_WEB, 2.4F, 8).forModes(0))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.EMBER_CLUSTER, 1.1F, 8, 20).withOffset(4.1F).forModes(1))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.SHOCK_RING)
                .bounds(3.0F, 5.0F, 1.0F);
    }
}
