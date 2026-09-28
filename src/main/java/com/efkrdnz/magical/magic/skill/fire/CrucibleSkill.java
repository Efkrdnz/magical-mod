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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * FIRE T4 - ENCLOSE / AIM_SURFACE / CYLINDER_CAGE. After a through-terrain windup, twelve slag
 * ribs and molten glass seal a kiln around the aim point: nothing living leaves or enters, panels
 * break after enough damage, heat ramps inside, and the kiln shatters outward at the end.
 * Sneak = seal it around yourself.
 */
public final class CrucibleSkill implements SkillModule {
    private static final int WINDUP = 24;
    private static final int PANELS = 12;
    private static final byte MODE_PANEL = 2;
    /** Where the smoke leaves the kiln: just under the rim of its five-block wall. */
    private static final double CHIMNEY_Y = 4.6D;
    /** Ticks between the chimney's smoke beats; one to three puffs a beat as the heat ramps. */
    private static final int SMOKE_INTERVAL = 4;
    /** Ticks between the pale plume puffs that stand over the kiln, readable from far off. */
    private static final int PLUME_INTERVAL = 10;
    /** Crumbs a panel drops when it is hit down, and when the whole kiln shatters (twelve at once). */
    private static final int BROKEN_CRUMBS = 8;
    private static final int SHATTER_CRUMBS = 2;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CRUCIBLE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                int hold = Math.max(100, ctx.duration());
                SpellEffectEntity kiln = SpellEffectEntity.spawn(ctx, pos, WINDUP + hold + 16, Math.max(3.0F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                kiln.setExtra(hold);
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
                return MobCastProfile.attack(4.0F, 18.0F);
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
                    return;
                }
                ServerLevel level = entity.serverLevel();
                double radius = entity.radius();
                int hold = Math.max(100, entity.extra());
                Vec3 centre = entity.position();
                if (entity.tickCount == WINDUP) {
                    ListTag panels = new ListTag();
                    for (int i = 0; i < PANELS; i++) {
                        double a = i * Math.PI * 2.0D / PANELS;
                        Vec3 p = centre.add(Math.cos(a) * radius, 0.0D, Math.sin(a) * radius);
                        SolidConstructEntity panel = SolidConstructEntity.create(level, entity, p, (float) (radius * 0.55D), 5.0F, 30.0F, i);
                        panel.setMode(MODE_PANEL);
                        panel.setLife(hold + 16);
                        panel.setDirection(new Vec3(Math.cos(a), 0.0D, Math.sin(a)));
                        level.addFreshEntity(panel);
                        CompoundTag rec = new CompoundTag();
                        rec.putUUID("Id", panel.getUUID());
                        panels.add(rec);
                    }
                    entity.serverData().put("Panels", panels);
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.0F);
                    return;
                }
                if (entity.tickCount < WINDUP) {
                    return;
                }
                int sinceSeal = entity.tickCount - WINDUP;
                if (sinceSeal >= hold) {
                    if (sinceSeal == hold) {
                        shatter(entity);
                    }
                    return;
                }
                // containment: nothing living crosses the wall in either direction (open panels excepted)
                for (LivingEntity living : SkillTargets.hostilesInCylinder(level, entity.owner(), centre, radius + 1.5D, 6.0D)) {
                    double dx = living.getX() - centre.x;
                    double dz = living.getZ() - centre.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    boolean gap = isGapAt(entity, Math.atan2(dz, dx));
                    if (gap) {
                        continue;
                    }
                    if (dist > radius - 0.9D && dist < radius) {
                        living.setDeltaMovement(living.getDeltaMovement().add(-dx / dist * 0.35D, 0.0D, -dz / dist * 0.35D));
                        living.hurtMarked = true;
                    } else if (dist >= radius && dist < radius + 1.5D) {
                        living.setDeltaMovement(living.getDeltaMovement().add(dx / dist * 0.35D, 0.0D, dz / dist * 0.35D));
                        living.hurtMarked = true;
                    }
                }
                // heat ramps inside every 20 ticks
                if (sinceSeal % 20 == 0) {
                    float seconds = sinceSeal / 20.0F;
                    float damage = 4.0F + 1.5F * seconds;
                    for (LivingEntity inside : SkillTargets.hostilesInCylinder(level, entity.owner(), centre, radius - 0.3D, 5.0D)) {
                        SkillTargets.hurt(level, entity.owner(), inside, damage, entity.definition().id());
                        inside.igniteForSeconds(2.0F);
                    }
                    SpellFx.zoneTickWithin(level, entity.definition(), centre, radius * 0.9D);
                }
                chimney(entity, sinceSeal, hold);
            }

            /**
             * The kiln from outside is a black wall, so what is going on inside it has to come out of
             * the top: smoke pouring over the rim, thickening as the heat ramps, under a tall pale
             * plume that says something is cooking in there to anyone who cannot see in.
             */
            private void chimney(SpellEffectEntity entity, int sinceSeal, int hold) {
                if (sinceSeal % SMOKE_INTERVAL != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                RandomSource random = level.random;
                Vec3 centre = entity.position();
                double reach = entity.radius() * 0.7D;
                int puffs = 1 + Math.min(2, sinceSeal * 3 / Math.max(1, hold));
                for (int i = 0; i < puffs; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0D;
                    double r = reach * Math.sqrt(random.nextDouble());
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x + Math.cos(a) * r, centre.y + CHIMNEY_Y, centre.z + Math.sin(a) * r, 1, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                if (sinceSeal % PLUME_INTERVAL == 0) {
                    double dx = (random.nextDouble() - 0.5D) * reach * 0.5D;
                    double dz = (random.nextDouble() - 0.5D) * reach * 0.5D;
                    // count 0: one puff, rising at exactly the speed a campfire gives its own
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, centre.x + dx, centre.y + CHIMNEY_Y + 0.4D, centre.z + dz, 0, 0.0D, 1.0D, 0.0D, 0.07D);
                }
            }

            /** A panel coming down, hit through or taken by the shatter: obsidian falling out of where it stood. */
            private void crumble(SolidConstructEntity panel) {
                double height = panel.height();
                double spread = panel.width() * 0.3D;
                int crumbs = panel.integrity() <= 0.0F ? BROKEN_CRUMBS : SHATTER_CRUMBS;
                panel.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                        panel.getX(), panel.getY() + height * 0.5D, panel.getZ(), crumbs, spread, height * 0.3D, spread, 0.15D);
            }

            private boolean isGapAt(SpellEffectEntity entity, double angle) {
                ListTag panels = entity.serverData().getList("Panels", Tag.TAG_COMPOUND);
                int idx = Math.floorMod((int) Math.round(angle / (Math.PI * 2.0D / PANELS)), PANELS);
                if (idx >= panels.size()) {
                    return false;
                }
                Entity panel = entity.serverLevel().getEntity(panels.getCompound(idx).getUUID("Id"));
                return panel == null || !panel.isAlive();
            }

            private void shatter(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                Vec3 centre = entity.position();
                for (LivingEntity inside : SkillTargets.hostilesInCylinder(level, entity.owner(), centre, entity.radius(), 5.0D)) {
                    SkillTargets.hurt(level, entity.owner(), inside, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(inside, centre, 0.9D * entity.knockback(), 0.4D);
                }
                ListTag panels = entity.serverData().getList("Panels", Tag.TAG_COMPOUND);
                for (int i = 0; i < panels.size(); i++) {
                    Entity panel = level.getEntity(panels.getCompound(i).getUUID("Id"));
                    if (panel instanceof SolidConstructEntity construct) {
                        construct.finish();
                    }
                }
                entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                SpellFx.impact(level, entity.definition(), centre.add(0.0D, 2.5D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.5F);
                SpellFx.decal(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), 2.5F);
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity instanceof SolidConstructEntity wall) {
                    crumble(wall);
                }
                ListTag panels = entity.serverData().getList("Panels", Tag.TAG_COMPOUND);
                for (int i = 0; i < panels.size(); i++) {
                    Entity panel = entity.serverLevel().getEntity(panels.getCompound(i).getUUID("Id"));
                    if (panel != null) {
                        panel.discard();
                    }
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.CRUCIBLE).frame(12).band(GlyphKind.TOOTH_BAND, 36).band(GlyphKind.RUNE_BAND, 12).stamps(StampId.FLAME, 12).orbit(7, 0.86F, 3).core(CoreKind.EMBER_PIT).stack(3, 0.5F).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .throughTerrain(true)
                .silhouette(Silhouette.field(Silhouette.Form.CYLINDER, FxKinds.Field.MAGMA_CRACKS, 6.0F, 5.0F, 6, 8).withOpacity(0.55F).forModes(0))
                .silhouette(Silhouette.mark(FxKinds.Mark.EMBER_FIELD, 6.0F, 12).forModes(0))
                .silhouette(new Silhouette(Silhouette.Family.BODY, Silhouette.Form.SLAB, FxKinds.Body.OBSIDIAN.id(), 1.6F, 5.0F, 1, 24, com.efkrdnz.magical.magic.visual.ColorRole.BASE, 1.0F, "").forModes(1))
                .silhouette(Silhouette.glyph(0.9F).withOffset(2.5F).forModes(1))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.TUNNEL)
                .budget(3)
                .bounds(8.0F, 6.0F, 1.0F);
    }
}
