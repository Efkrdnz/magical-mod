package com.efkrdnz.magical.magic.skill.fire;

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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * FIRE T1 - SPREAD / AIM_SURFACE / CREEPING_FRONT. A fire seed at the aimed surface catches one
 * new walkable neighbour every 5 ticks, biased along the caster's look (the wind), up to N cells;
 * each cell burns for a while, hurting and igniting what stands on it, then leaves scorch. It
 * walks around corners and up stairs. Sneak = reverse the wind.
 */
public final class WildfireSkill implements SkillModule {
    private static final int SPREAD_INTERVAL = 5;
    private static final int BURN_TICKS = 60;
    /**
     * The flame beat runs once per spread interval, two ticks after it, so it never lands on the
     * tick a new cell catches and throws its own burst.
     */
    private static final int FLAME_PHASE = 2;
    /**
     * A cell is the fire's front for this long after it catches and burns hardest (two tongues a
     * beat), then burns steadily (one) until {@link #EMBER_AGE}, when it is down to embers (a small
     * flame) and smokes. About four particles a tick at the peak, over a front thirteen cells long.
     */
    private static final int FRONT_AGE = 20;
    private static final int EMBER_AGE = 45;
    /** How fast a tongue licks up: a flame keeps 0.96 of its speed a tick, so it climbs about 25x this. */
    private static final double TONGUE_LIFT = 0.025D;
    private static final double TONGUE_LIFT_SPREAD = 0.015D;
    /** How far across its block a tongue may start: a burning cell is a patch of fire, not a candle. */
    private static final double CELL_SPREAD = 0.35D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.WILDFIRE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                BlockPos seed = walkable(level, BlockPos.containing(ctx.aim().point()));
                if (seed == null) {
                    seed = walkable(level, BlockPos.containing(ctx.feet().add(ctx.look().scale(1.5D))));
                }
                if (seed == null) {
                    seed = ctx.caster().blockPosition();
                }
                Vec3 look = ctx.look();
                Vec3 wind = new Vec3(look.x, 0.0D, look.z);
                wind = wind.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : wind.normalize();
                if (ctx.sneak()) {
                    wind = wind.scale(-1.0D);
                }
                int maxCells = Math.max(6, Math.round(ctx.size() * 5.0F));
                int life = SPREAD_INTERVAL * maxCells + BURN_TICKS + 20;
                Vec3 origin = Vec3.atBottomCenterOf(seed);
                SpellEffectEntity front = SpellEffectEntity.spawn(ctx, origin, life, 1.0F, wind);
                front.setExtra(maxCells);
                CompoundTag data = new CompoundTag();
                ListTag cells = new ListTag();
                cells.add(cell(seed, 0));
                data.put("Cells", cells);
                front.setSyncedData(data);
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
                return MobCastProfile.attack(2.0F, 14.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static CompoundTag cell(BlockPos pos, int tick) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        tag.putInt("Tick", tick);
        return tag;
    }

    /** The air cell standing on a solid block at or near this position (searching +-2 vertically). */
    private static BlockPos walkable(ServerLevel level, BlockPos around) {
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            BlockPos p = around.above(dy);
            if (isWalkable(level, p)) {
                return p;
            }
        }
        return null;
    }

    private static boolean isWalkable(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) {
            return false;
        }
        var below = level.getBlockState(p.below());
        var here = level.getBlockState(p);
        return !below.getCollisionShape(level, p.below()).isEmpty() && here.getCollisionShape(level, p).isEmpty()
                && here.getFluidState().isEmpty() && below.getFluidState().isEmpty();
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            CompoundTag data = entity.syncedData();
            ListTag cells = data.getList("Cells", Tag.TAG_COMPOUND);
            int maxCells = Math.max(6, entity.extra());
            // spread: catch one new neighbour weighted by the wind
            if (entity.tickCount % SPREAD_INTERVAL == 0 && cells.size() < maxCells) {
                Vec3 wind = entity.direction();
                List<BlockPos> owned = new ArrayList<>();
                for (int i = 0; i < cells.size(); i++) {
                    owned.add(BlockPos.of(cells.getCompound(i).getLong("Pos")));
                }
                BlockPos best = null;
                double bestWeight = -1.0D;
                for (BlockPos cellPos : owned) {
                    for (int side = 0; side < 4; side++) {
                        int dx = side == 0 ? 1 : side == 1 ? -1 : 0;
                        int dz = side == 2 ? 1 : side == 3 ? -1 : 0;
                        BlockPos n = walkable(level, cellPos.offset(dx, 0, dz));
                        if (n == null || owned.contains(n) || Math.abs(n.getY() - cellPos.getY()) > 1) {
                            continue;
                        }
                        double dot = dx * wind.x + dz * wind.z;
                        double weight = 1.0D + 2.5D * Math.max(0.0D, dot) + 0.3D * level.random.nextDouble();
                        if (weight > bestWeight) {
                            bestWeight = weight;
                            best = n;
                        }
                    }
                }
                if (best != null) {
                    ListTag copy = cells.copy();
                    copy.add(cell(best, entity.tickCount));
                    CompoundTag next = data.copy();
                    next.put("Cells", copy);
                    entity.setSyncedData(next);
                    cells = copy;
                    SpellFx.burst(level, entity.definition(), Vec3.atBottomCenterOf(best).add(0.0D, 0.3D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.5F);
                }
            }
            // burn: damage + ignite whoever stands on a burning cell
            if (entity.tickCount % 10 == 0) {
                for (int i = 0; i < cells.size(); i++) {
                    CompoundTag c = cells.getCompound(i);
                    if (entity.tickCount - c.getInt("Tick") > BURN_TICKS) {
                        continue;
                    }
                    BlockPos p = BlockPos.of(c.getLong("Pos"));
                    Vec3 centre = Vec3.atBottomCenterOf(p);
                    for (LivingEntity hit : SkillTargets.hostilesIn(level, entity.owner(), new net.minecraft.world.phys.AABB(p).inflate(0.1D, 0.9D, 0.1D))) {
                        SkillTargets.hurt(level, entity.owner(), hit, entity.damage(), entity.definition().id());
                        hit.igniteForSeconds(2.0F);
                    }
                    if (level.random.nextInt(3) == 0) {
                        SpellFx.zoneTick(level, entity.definition(), centre.add(0.0D, 0.05D, 0.0D), 0.35F);
                    }
                }
            }
            if (entity.tickCount % SPREAD_INTERVAL == FLAME_PHASE) {
                flicker(level, cells, entity.tickCount);
            }
        };
    }

    /**
     * The fire itself: real flames licking up off every burning cell - hardest at the front, down to
     * embers behind it - a thread of smoke off the cells burning down, and a breath of large smoke off
     * each one as it goes out, so the front reads as a grass fire and the painter's tongues as its
     * glow. One flame a cell sitting still on the ground read as a few candles, and by the time the
     * front had walked three cells there was next to nothing to see. The beat's cadence is the spread
     * interval, so a cell's burn-out window is met exactly once.
     */
    private static void flicker(ServerLevel level, ListTag cells, int now) {
        RandomSource random = level.random;
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag c = cells.getCompound(i);
            int age = now - c.getInt("Tick");
            BlockPos p = BlockPos.of(c.getLong("Pos"));
            double x = p.getX() + 0.5D;
            double y = p.getY();
            double z = p.getZ() + 0.5D;
            if (age <= BURN_TICKS) {
                if (age < EMBER_AGE) {
                    int tongues = age < FRONT_AGE ? 2 : 1;
                    for (int t = 0; t < tongues; t++) {
                        tongue(level, random, ParticleTypes.FLAME, x, y + 0.1D, z);
                    }
                } else {
                    // burning down: embers, and the smoke rising off the ground the front has left
                    tongue(level, random, ParticleTypes.SMALL_FLAME, x, y + 0.05D, z);
                    level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3D, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
                }
            } else if (age <= BURN_TICKS + SPREAD_INTERVAL) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.15D, z, 2, 0.2D, 0.05D, 0.2D, 0.01D);
            }
        }
    }

    /** One flame from somewhere across the cell, climbing: count 0 sends it at exactly this velocity. */
    private static void tongue(ServerLevel level, RandomSource random, SimpleParticleType flame, double x, double y, double z) {
        double ox = (random.nextDouble() - 0.5D) * 2.0D * CELL_SPREAD;
        double oz = (random.nextDouble() - 0.5D) * 2.0D * CELL_SPREAD;
        double lift = TONGUE_LIFT + random.nextDouble() * TONGUE_LIFT_SPREAD;
        level.sendParticles(flame, x + ox, y, z + oz, 0, (random.nextDouble() - 0.5D) * 0.01D, lift, (random.nextDouble() - 0.5D) * 0.01D, 1.0D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.SEED).frame(7, CircleScript.FrameStyle.THIN).band(GlyphKind.TICK_BAND, 20).band(GlyphKind.RUNE_BAND, 7).stamps(StampId.LEAF, 7).lattice(4).core(CoreKind.EMBER_PIT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("wildfire", 6.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.HEAT_SHIMMER)
                .bounds(12.0F, 3.0F, 3.0F);
    }
}
