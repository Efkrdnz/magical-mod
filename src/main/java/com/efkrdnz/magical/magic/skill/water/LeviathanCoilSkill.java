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
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
    /** Ticks between the spurts of water welling up round the rim while the sea is called. */
    private static final int WELL_INTERVAL = 2;
    /** Splashes in each spurt on the rim. */
    private static final int WELL_SPLASH = 3;
    /** How far round the rim each spurt steps from the last: the golden angle, so the ring fills evenly. */
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));
    /** The mist a spurt lifts off the rim: a sprite size, and how fast it leans in over the basin. */
    private static final float WELL_MIST_SCALE = 2.8F;
    private static final double WELL_MIST_SPEED = 0.03D;
    /** Spray thrown out from the middle as the sea bursts up. */
    private static final int SURGE_SPOKES = 16;
    /** The crest of foam that rolls out over the ground with the surge: about four blocks before it thins away. */
    private static final float FOAM_SCALE = 3.2F;
    private static final double FOAM_SPEED = 0.35D;
    /** Puffs of spray thrown straight up out of the middle as the sea bursts, and how hard. */
    private static final int SPOUT_PLUMES = 3;
    private static final double SPOUT_SPEED = 0.22D;
    /**
     * A caster standing within this of the middle (a sneak cast, or one aimed at their own feet) is
     * inside the burst: the spout would rise straight through their eyes and the spokes be born
     * under their nose, so the ring starts past them and the spout is left out.
     */
    private static final double HEART_CLEARANCE = 2.5D;
    /** How far toward white the palette's bright is taken for foam. */
    private static final float FOAM_WHITENING = 0.55F;
    /** Ticks between the bubbles the undertow drags toward the middle. */
    private static final int UNDERTOW_INTERVAL = 4;
    /** Drops the sea's body leaves in the air as it drains away. */
    private static final int DRAIN_DROPS = 28;

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
                if (t < WINDUP && t % WELL_INTERVAL == 0) {
                    well(level, centre, radius, t, foam(entity, WELL_MIST_SCALE));
                }
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
                    surge(level, centre, radius, foam(entity, FOAM_SCALE), casterAtHeart(entity, centre));
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
                        SpellFx.zoneTickWithin(level, entity.definition(), centre.add(0.0D, LAYERS, 0.0D), radius);
                    }
                    if (since % UNDERTOW_INTERVAL == 0) {
                        undertow(level, centre, radius);
                    }
                    return;
                }
                if (t == WINDUP + FILL + stand) {
                    entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                    restore(entity);
                }
                if (t == WINDUP + FILL + stand + 1) {
                    // a tick after the blocks go, so the drops are not born inside water that is still there
                    drain(level, centre, radius);
                }
            }

            /**
             * Water welling up out of the ground on the rim: the telegraph, in matter. One point a
             * spurt, each a golden angle round from the last, so over the windup the rim fills in
             * evenly as a ring of sea-mist gathering where the water will stand, instead of splash
             * specks at random bearings, which from across a basin read as dust on the horizon.
             */
            private void well(ServerLevel level, Vec3 centre, double radius, int t, ParticleOptions foam) {
                double a = (t / WELL_INTERVAL) * GOLDEN_ANGLE;
                double c = Math.cos(a);
                double s = Math.sin(a);
                double x = centre.x + c * radius;
                double z = centre.z + s * radius;
                level.sendParticles(ParticleTypes.SPLASH, x, centre.y + 0.1D, z, WELL_SPLASH, 0.2D, 0.0D, 0.2D, 0.0D);
                // the mist leans in over the basin as it lifts
                level.sendParticles(foam, x, centre.y + 0.3D, z, 0, -c, 0.6D, -s, WELL_MIST_SPEED);
            }

            /**
             * The sea bursting out of the middle: a ring of spray thrown across the ground toward
             * the rim, a crest of foam rolling out with every other spoke, and a plume of spray
             * thrown straight up out of the centre. Vanilla's splash is a few pixels from across a
             * basin, so the foam is what carries a tier-four sea arriving.
             */
            private void surge(ServerLevel level, Vec3 centre, double radius, ParticleOptions foam, boolean casterInside) {
                double speed = Math.min(1.0D, radius * 0.12D);
                double sprayFrom = casterInside ? HEART_CLEARANCE : 0.6D;
                double foamFrom = casterInside ? HEART_CLEARANCE : 0.8D;
                for (int i = 0; i < SURGE_SPOKES; i++) {
                    double a = i * Math.PI * 2.0D / SURGE_SPOKES;
                    double c = Math.cos(a);
                    double s = Math.sin(a);
                    // a level push: vanilla's splash keeps a horizontal speed only when it is handed no vertical one
                    level.sendParticles(ParticleTypes.SPLASH, centre.x + c * sprayFrom, centre.y + 1.1D, centre.z + s * sprayFrom, 0, c, 0.0D, s, speed);
                    if (i % 2 == 0) {
                        level.sendParticles(foam, centre.x + c * foamFrom, centre.y + 0.4D, centre.z + s * foamFrom, 0, c, 0.12D, s, FOAM_SPEED);
                    }
                }
                if (casterInside) {
                    return;
                }
                for (int i = 0; i < SPOUT_PLUMES; i++) {
                    double jx = (level.random.nextDouble() - 0.5D) * 0.12D;
                    double jz = (level.random.nextDouble() - 0.5D) * 0.12D;
                    level.sendParticles(foam, centre.x, centre.y + 0.8D + i * 0.5D, centre.z, 0, jx, 1.0D, jz, SPOUT_SPEED);
                }
            }

            /** Whether the caster stands in the middle of their own sea, where the burst would be thrown into their own view. */
            private boolean casterAtHeart(SpellEffectEntity entity, Vec3 centre) {
                Entity owner = entity.owner();
                if (owner == null) {
                    return false;
                }
                double dx = owner.getX() - centre.x;
                double dz = owner.getZ() - centre.z;
                return dx * dx + dz * dz < HEART_CLEARANCE * HEART_CLEARANCE;
            }

            /** Sea foam: the palette's bright whitened, so it reads as spray over any floor. */
            private ParticleOptions foam(SpellEffectEntity entity, float scale) {
                int bright = VisualProfiles.of(entity.definition()).color(ColorRole.BRIGHT);
                return new TintedParticleOptions(MagicalParticles.WISP.get(), Palette.mix(bright, 0xFFFFFF, FOAM_WHITENING), scale);
            }

            /** The undertow made visible: a few bubbles in the standing water, dragged toward the middle. */
            private void undertow(ServerLevel level, Vec3 centre, double radius) {
                double floor = Math.floor(centre.y);
                for (int i = 0; i < 3; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2.0D;
                    double d = radius * (0.3D + 0.6D * level.random.nextDouble());
                    double c = Math.cos(a);
                    double s = Math.sin(a);
                    double y = floor + 0.3D + level.random.nextDouble() * (LAYERS - 1.0D);
                    level.sendParticles(ParticleTypes.BUBBLE, centre.x + c * d, y, centre.z + s * d, 0, -c, 0.15D, -s, 1.2D);
                }
            }

            /** The sea letting go: its body falls out of the air it stood in and splashes over the floor. */
            private void drain(ServerLevel level, Vec3 centre, double radius) {
                double floor = Math.floor(centre.y);
                level.sendParticles(ParticleTypes.FALLING_WATER, centre.x, floor + LAYERS - 0.5D, centre.z, DRAIN_DROPS, radius * 0.45D, 0.5D, radius * 0.45D, 0.0D);
                level.sendParticles(ParticleTypes.SPLASH, centre.x, floor + 0.2D, centre.z, DRAIN_DROPS / 2, radius * 0.45D, 0.0D, radius * 0.45D, 0.0D);
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
                // no glowing drop hung over the basin: the sea is real water and its spray real particles
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.WATER_DROPLETS)
                .budget(3)
                .bounds(12.0F, 5.0F, 1.0F);
    }
}
