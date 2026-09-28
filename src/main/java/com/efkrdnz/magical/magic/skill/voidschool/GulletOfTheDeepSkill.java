package com.efkrdnz.magical.magic.skill.voidschool;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T3 - SWALLOW / AIM_SURFACE_PIT / SUBTERRANEAN_CYLINDER. After a telegraph the ground opens
 * into an octagonal pit (blocks temporarily erased); everything standing there drops in and is
 * chewed, pinned down and unable to climb out; then the pit closes bottom-up, restoring the terrain
 * and spitting its victims upward with a final bite.
 */
public final class GulletOfTheDeepSkill implements SkillModule {
    private static final int TELEGRAPH = 20;
    private static final int DEPTH = 6;
    private static final int CLOSE = 12;
    /** Rim motes a telegraph tick: nineteen ticks of two is a ring of thirty-eight. */
    private static final int BREATH_PER_TICK = 2;
    /**
     * A reverse-portal mote lives 60 or 61 ticks and each tick moves by its step times its age over
     * its life, so the steps it takes add up to about 31 of the step it was handed.
     */
    private static final double BREATH_STEPS = 31.0D;
    /** How far under the floor the rim motes are drawn: into the throat once it is open. */
    private static final double BREATH_SINK = 1.2D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GULLET_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int chew = Math.max(20, ctx.duration());
                SpellEffectEntity pit = SpellEffectEntity.spawn(ctx, ctx.aim().point(), TELEGRAPH + chew + CLOSE + 10, 2.4F * Math.max(0.6F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                pit.setExtra(chew);
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
                return TELEGRAPH;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 20.0F);
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
                int chew = Math.max(20, entity.extra());
                int t = entity.tickCount;
                if (t > 0 && t < TELEGRAPH) {
                    // the telegraph: the throat draws breath round the rim it will open to, and
                    // the ground over it trembles, crumbs hopping on it
                    drawBreath(level, centre, radius);
                    if (t % 4 == 0) {
                        crumbs(level, centre, radius * 0.4D, 6, 0.12D);
                    }
                }
                if (t == TELEGRAPH) {
                    // the floor gives way: crumbs of it tumbling into the throat, sampled before it goes
                    BlockParticleOption floor = groundCrumb(level, centre);
                    ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
                    entity.serverData().putUUID("Edit", edit.id());
                    int baseY = (int) Math.floor(centre.y) - 1;
                    int r = (int) Math.ceil(radius);
                    for (int layer = 0; layer < DEPTH; layer++) {
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                // octagon: |x| + |z| <= 1.3 r and both within r
                                if (Math.abs(dx) + Math.abs(dz) > radius * 1.3D || Math.abs(dx) > radius || Math.abs(dz) > radius) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos((int) Math.floor(centre.x) + dx, baseY - layer, (int) Math.floor(centre.z) + dz);
                                BlockState state = level.getBlockState(pos);
                                if (state.isAir() || state.getBlock().defaultDestroyTime() < 0.0F || level.getBlockEntity(pos) != null) {
                                    continue;
                                }
                                ConjuredTerrainService.replace(level, edit, pos, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.0F);
                    if (floor != null) {
                        // 20, not more: the heavy impact above already throws its own crumbs, blast and puffs
                        level.sendParticles(floor, centre.x, centre.y - 0.1D, centre.z, 20, radius * 0.45D, 0.15D, radius * 0.45D, 0.05D);
                    }
                    return;
                }
                if (t > TELEGRAPH && t <= TELEGRAPH + chew) {
                    for (LivingEntity victim : SkillTargets.hostilesInCylinder(level, entity.owner(), centre.subtract(0.0D, DEPTH, 0.0D), radius + 0.3D, DEPTH + 0.5D)) {
                        Vec3 v = victim.getDeltaMovement();
                        victim.setDeltaMovement(0.0D, Math.min(v.y, -0.05D), 0.0D);
                        victim.hurtMarked = true;
                        if ((t - TELEGRAPH) % 10 == 0) {
                            SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition().id());
                        }
                    }
                    if ((t - TELEGRAPH) % 10 == 0) {
                        SpellFx.zoneTickWithin(level, entity.definition(), centre, radius);
                    }
                    return;
                }
                if (t > TELEGRAPH + chew && t <= TELEGRAPH + chew + CLOSE) {
                    // restore bottom-up, one layer every CLOSE/DEPTH ticks; spit at the last layer
                    int step = t - TELEGRAPH - chew;
                    int layer = DEPTH - 1 - Math.min(DEPTH - 1, (step - 1) * DEPTH / CLOSE);
                    ConjuredTerrainService.Edit edit = ConjuredTerrainService.lookup(level, entity.serverData().getUUID("Edit"));
                    if (edit != null) {
                        int baseY = (int) Math.floor(centre.y) - 1;
                        List<BlockPos> subset = new ArrayList<>();
                        for (BlockPos pos : edit.positions()) {
                            if (pos.getY() == baseY - layer) {
                                subset.add(pos);
                            }
                        }
                        // lift anyone standing in that layer before it fills
                        for (LivingEntity victim : SkillTargets.hostilesInCylinder(level, entity.owner(), new Vec3(centre.x, baseY - layer, centre.z), radius + 0.3D, 1.2D)) {
                            victim.setPos(victim.getX(), baseY - layer + 1.05D, victim.getZ());
                            victim.hurtMarked = true;
                        }
                        ConjuredTerrainService.restorePositions(level, edit, subset);
                    }
                    if (step == CLOSE) {
                        for (LivingEntity victim : SkillTargets.hostilesInCylinder(level, entity.owner(), centre.subtract(0.0D, 1.0D, 0.0D), radius + 0.5D, 3.0D)) {
                            SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                            victim.setDeltaMovement(victim.getDeltaMovement().x, Math.max(0.6D, entity.knockback()), victim.getDeltaMovement().z);
                            victim.hurtMarked = true;
                        }
                        entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                        restore(entity);
                        // the throat slams shut and spits: the restored ground thrown up off its lid
                        crumbs(level, centre, radius * 0.45D, 24, 0.3D);
                    }
                }
            }

            /**
             * Two reverse-portal motes a tick on the rim the pit will open to, each handed its
             * whole journey into the throat - to the middle and down past the floor - as one step
             * spread over its life. A reverse-portal mote's step grows with its age, so it hardly
             * moves for the first half of it: through the telegraph the motes hang on the rim as a
             * ring that says how wide the pit will be, and once it has opened they rush into it.
             */
            private void drawBreath(ServerLevel level, Vec3 centre, double radius) {
                for (int i = 0; i < BREATH_PER_TICK; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2.0D;
                    double r = radius * (0.9D + level.random.nextDouble() * 0.15D);
                    double lift = 0.3D + level.random.nextDouble() * 0.3D;
                    double ox = Math.cos(a) * r;
                    double oz = Math.sin(a) * r;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x + ox, centre.y + lift, centre.z + oz,
                            0, -ox, -(lift + BREATH_SINK), -oz, 1.0D / BREATH_STEPS);
                }
            }

            /** Crumbs of whatever the throat is cut into, thrown up off its surface. */
            private void crumbs(ServerLevel level, Vec3 centre, double spread, int count, double speed) {
                BlockParticleOption crumb = groundCrumb(level, centre);
                if (crumb != null) {
                    level.sendParticles(crumb, centre.x, centre.y + 0.1D, centre.z, count, spread, 0.05D, spread, speed);
                }
            }

            /** The block the throat opens in, as crumbs; null over air or anything with no surface to crumble. */
            private BlockParticleOption groundCrumb(ServerLevel level, Vec3 centre) {
                BlockState state = level.getBlockState(BlockPos.containing(centre.x, centre.y - 0.5D, centre.z));
                return state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE ? null : new BlockParticleOption(ParticleTypes.BLOCK, state);
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
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.GULLET).frame(8).band(GlyphKind.RUNE_BAND, 16, ColorRole.INK).band(GlyphKind.CHAIN_BAND, 12, ColorRole.HOT).stamps(StampId.NEEDLE, 16).spokes(16, 0.3F, true).core(CoreKind.VOID_PIT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.mark(FxKinds.Mark.MAW, 3.5F, 16))
                .silhouette(Silhouette.field(Silhouette.Form.CYLINDER, FxKinds.Field.VOID_INK, 3.3F, 6.0F, 4, 6).withOffset(-6.0F).withOpacity(0.9F))
                .silhouette(Silhouette.body(Silhouette.Form.SPIKE_CLUSTER, FxKinds.Body.OBSIDIAN, 16, 1.4F).withOffset(0.1F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.MAW, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.TUNNEL)
                .budget(3)
                .bounds(5.0F, 2.0F, 8.0F);
    }
}
