package com.efkrdnz.magical.magic.skill.water;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T4 - FLOOD / AIM_SURFACE / BASIN_VOLUME. After a counterable windup, three layers of
 * temporary water fill a wide disc: the rising sea hits everyone once, then stands for the
 * duration (real swimming physics, an undertow toward the centre, periodic swells) before draining
 * and restoring every block. Sneak = centre the basin on yourself.
 */
public final class LeviathanCoilSkill implements SkillModule {
    private static final int WINDUP = 24;
    private static final int FILL = 12;
    private static final int LAYERS = 3;
    private static final int DRAIN = 30;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LEVIATHAN_COIL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                int stand = Math.max(100, ctx.duration());
                SpellEffectEntity basin = SpellEffectEntity.spawn(ctx, pos, WINDUP + FILL + stand + DRAIN, Math.max(5.0F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                basin.setExtra(stand);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
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
                return MobCastProfile.attack(4.0F, 20.0F);
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
                Vec3 centre = entity.position();
                double radius = entity.radius();
                int stand = Math.max(100, entity.extra());
                int t = entity.tickCount;
                if (t == WINDUP) {
                    ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
                    entity.serverData().putUUID("Edit", edit.id());
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    for (LivingEntity hit : SkillTargets.hostilesInCylinder(level, entity.owner(), centre, radius, 4.0D)) {
                        SkillTargets.hurt(level, entity.owner(), hit, entity.damage(), entity.definition(), true);
                        hit.setDeltaMovement(hit.getDeltaMovement().x, Math.max(0.4D, entity.knockback()), hit.getDeltaMovement().z);
                        hit.hurtMarked = true;
                    }
                    SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.5F);
                }
                if (t >= WINDUP && t < WINDUP + FILL) {
                    // fill one ring per tick, bottom layer first
                    ConjuredTerrainService.Edit edit = ConjuredTerrainService.lookup(level, entity.serverData().getUUID("Edit"));
                    if (edit != null) {
                        int step = t - WINDUP;
                        int layer = step * LAYERS / FILL;
                        fillLayer(level, edit, centre, radius, layer, step);
                    }
                    return;
                }
                if (t >= WINDUP + FILL && t < WINDUP + FILL + stand) {
                    int since = t - WINDUP - FILL;
                    for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, entity.owner(), centre, radius, 4.0D)) {
                        if (!hostile.isInWater()) {
                            continue;
                        }
                        Vec3 toCentre = centre.subtract(hostile.position());
                        toCentre = new Vec3(toCentre.x, 0.0D, toCentre.z);
                        if (toCentre.lengthSqr() > 1.0D) {
                            hostile.setDeltaMovement(hostile.getDeltaMovement().add(toCentre.normalize().scale(0.12D)));
                            hostile.hurtMarked = true;
                        }
                        if (since % 40 == 20) {
                            SkillTargets.hurt(level, entity.owner(), hostile, 6.0F, entity.definition().id());
                        }
                    }
                    if (since % 40 == 20) {
                        SpellFx.zoneTick(level, entity.definition(), centre.add(0.0D, LAYERS, 0.0D), (float) radius);
                    }
                    return;
                }
                if (t == WINDUP + FILL + stand) {
                    entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                    restore(entity);
                }
            }

            private void fillLayer(ServerLevel level, ConjuredTerrainService.Edit edit, Vec3 centre, double radius, int layer, int step) {
                int baseY = (int) Math.floor(centre.y);
                int r = (int) Math.ceil(radius);
                BlockState water = Blocks.WATER.defaultBlockState();
                // fill the whole layer over the ticks assigned to it (rings from the centre outward)
                int ticksPerLayer = Math.max(1, FILL / LAYERS);
                int ringStart = (step % ticksPerLayer) * r / ticksPerLayer;
                int ringEnd = ((step % ticksPerLayer) + 1) * r / ticksPerLayer;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d > radius || d < ringStart || d >= ringEnd + (ringEnd == r ? 1 : 0)) {
                            continue;
                        }
                        BlockPos pos = new BlockPos((int) Math.floor(centre.x) + dx, baseY + layer, (int) Math.floor(centre.z) + dz);
                        BlockState state = level.getBlockState(pos);
                        if (!(state.isAir() || state.canBeReplaced()) || state.getFluidState().isSource()) {
                            continue;
                        }
                        boolean nearLava = false;
                        for (Direction dir : Direction.values()) {
                            if (level.getBlockState(pos.relative(dir)).is(Blocks.LAVA)) {
                                nearLava = true;
                                break;
                            }
                        }
                        if (!nearLava) {
                            ConjuredTerrainService.replace(level, edit, pos, water);
                        }
                    }
                }
            }

            private void restore(SpellEffectEntity entity) {
                if (entity.serverData().hasUUID("Edit")) {
                    ConjuredTerrainService.Edit edit = ConjuredTerrainService.lookup(entity.serverLevel(), entity.serverData().getUUID("Edit"));
                    if (edit != null) {
                        ConjuredTerrainService.restore(entity.serverLevel(), edit);
                    }
                    entity.serverData().remove("Edit");
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                restore(entity);
            }

            @Override
            public void onLoad(SpellEffectEntity entity) {
                // the ledger restores orphans on level load; nothing else to re-acquire
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.LEVIATHAN).frame(11).band(GlyphKind.WAVE_BAND, 22).band(GlyphKind.CHAIN_BAND, 14, com.efkrdnz.magical.magic.visual.ColorRole.BASE).stamps(StampId.WAVE, 9).orbit(7, 0.86F, 4).core(CoreKind.RIPPLE).stack(3, 0.6F).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .throughTerrain(true)
                .silhouette(Silhouette.field(Silhouette.Form.CYLINDER, FxKinds.Field.RIPPLE_WATER, 10.0F, 3.2F, 10, 6).withOpacity(0.55F))
                .silhouette(Silhouette.mark(FxKinds.Mark.RIPPLES, 10.0F, 4).withOffset(3.1F).withOpacity(0.5F))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 1.2F, 6, 10).withOffset(3.6F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.WATER_DROPLETS)
                .budget(3)
                .bounds(12.0F, 5.0F, 1.0F);
    }
}
