package com.efkrdnz.magical.magic.skill.primordial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.primordial.Ballistics;
import com.efkrdnz.magical.magic.primordial.MassCodec;
import com.efkrdnz.magical.magic.primordial.PrimordialScars;
import com.efkrdnz.magical.magic.primordial.PrimordialService;
import com.efkrdnz.magical.magic.primordial.Shapes;
import com.efkrdnz.magical.magic.primordial.Wellspring;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import com.efkrdnz.magical.magic.service.Bodies;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * PRIMORDIAL T-4 - tear up the ground and throw it.
 *
 * <p>A disc of the ground where the caster aims is torn loose and rises as one slab of the blocks
 * it was, throwing whatever stood on it into the air. It hangs for a heartbeat, then is hurled at
 * wherever the caster is looking by then and bursts on landing or on the first body it meets. The
 * hole fills back in later. Wellspring: mass - how much solid ground the plate carried, which is
 * the weight of the blow. A plate with nothing solid in it is refused.
 */
public final class UpheavalSkill implements SkillModule {
    public static final double AIM_RANGE = 20.0D;
    public static final int RADIUS = 2;
    public static final int DEPTH = 2;
    public static final int RISE = 20;
    public static final double LIFT = 6.0D;
    public static final double THROW_RANGE = 30.0D;
    public static final double GRAVITY = 0.06D;
    public static final int MAX_FLIGHT = 60;
    public static final double BURST_RADIUS = 3.5D;
    /** Blocks carried for the base hit; the hit scales with the ground, half to double. */
    public static final int MASS_PAR = 20;
    public static final int SCAR_TICKS = 600;
    private static final float HANG_SPIN = 2.0F;
    private static final float FLIGHT_SPIN = 14.0F;
    private static final String KEY_BASE_Y = "BaseY";
    private static final String KEY_MASS = "Mass";
    private static final String KEY_HURL = "Hurl";
    private static final String KEY_VX = "VX";
    private static final String KEY_VY = "VY";
    private static final String KEY_VZ = "VZ";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.UPHEAVAL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 aim = ctx.aim() != null ? ctx.aim().point() : ctx.feet().add(ctx.look().scale(6.0D));
                BlockPos ground = PrimordialService.surface(level, aim, 3, 10);
                if (ground == null) {
                    PrimordialService.refuse(ctx.caster(), "message.magical.primordial.no_ground");
                    return CastResult.FAILED;
                }
                List<int[]> plate = new ArrayList<>(Shapes.plate(RADIUS, DEPTH));
                // top down, so what rests on a lower cell is already gone when it is cut
                plate.sort((a, b) -> Integer.compare(b[1], a[1]));
                int solid = 0;
                for (int[] c : plate) {
                    BlockPos pos = ground.offset(c[0], c[1], c[2]);
                    if (PrimordialService.carvable(level, pos) && PrimordialService.solid(level, pos)) {
                        solid++;
                    }
                }
                if (solid == 0) {
                    PrimordialService.refuse(ctx.caster(), "message.magical.upheaval.nothing");
                    return CastResult.FAILED;
                }
                Entity owner = ctx.caster();
                AABB standing = new AABB(ground.getX() - RADIUS, ground.getY() + 0.5D, ground.getZ() - RADIUS,
                        ground.getX() + RADIUS + 1.0D, ground.getY() + 3.0D, ground.getZ() + RADIUS + 1.0D);
                for (LivingEntity body : Bodies.of(level, LivingEntity.class, standing, b -> SkillTargets.isHostile(owner, b))) {
                    body.setDeltaMovement(body.getDeltaMovement().x, 1.05D, body.getDeltaMovement().z);
                    body.hurtMarked = true;
                }
                ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
                List<BlockState> states = new ArrayList<>();
                List<int[]> offsets = new ArrayList<>();
                for (int[] c : plate) {
                    BlockPos pos = ground.offset(c[0], c[1], c[2]);
                    if (!PrimordialService.solid(level, pos)) {
                        continue;
                    }
                    BlockState was = PrimordialService.carve(level, edit, pos, Blocks.AIR.defaultBlockState());
                    if (was == null) {
                        continue;
                    }
                    states.add(was);
                    offsets.add(c);
                    if (c[1] == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.4D, 0.4D, 0.4D, 0.15D);
                    }
                }
                if (states.isEmpty()) {
                    ConjuredTerrainService.restore(level, edit);
                    PrimordialService.refuse(ctx.caster(), "message.magical.upheaval.nothing");
                    return CastResult.FAILED;
                }
                PrimordialService.say(ctx.caster(), "mass", Wellspring.mass(states.size(), plate.size()));
                int hang = Math.max(5, ctx.duration() - RISE);
                PrimordialScars.giveBackLater(level, edit, SCAR_TICKS + RISE + hang);
                Vec3 centre = new Vec3(ground.getX() + 0.5D, ground.getY() + 0.5D, ground.getZ() + 0.5D);
                SpellEffectEntity slab = SpellEffectEntity.spawn(ctx, centre, RISE + hang + MAX_FLIGHT + 5, (float) BURST_RADIUS, ctx.look());
                slab.serverData().putDouble(KEY_BASE_Y, centre.y);
                slab.serverData().putInt(KEY_MASS, states.size());
                slab.serverData().putInt(KEY_HURL, RISE + hang);
                PrimordialService.carry(slab, states, offsets, 1.0F, 0.0F, false);
                slab.setPhase(SpellEffectEntity.PHASE_WINDUP);
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 0.45F);
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity slab) {
                CompoundTag data = slab.serverData();
                if (slab.phase() == SpellEffectEntity.PHASE_DONE || !data.contains(KEY_HURL)) {
                    return;
                }
                int t = slab.tickCount;
                int hurl = data.getInt(KEY_HURL);
                double baseY = data.getDouble(KEY_BASE_Y);
                if (t <= RISE) {
                    double f = t / (double) RISE;
                    double ease = 1.0D - (1.0D - f) * (1.0D - f);
                    slab.setPos(slab.getX(), baseY + LIFT * ease, slab.getZ());
                    if (t == RISE) {
                        PrimordialService.spin(slab, HANG_SPIN);
                    }
                } else if (t < hurl) {
                    slab.setPos(slab.getX(), baseY + LIFT + Math.sin(t * 0.3D) * 0.1D, slab.getZ());
                } else if (t == hurl) {
                    throwIt(slab);
                } else {
                    fly(slab, t - hurl);
                }
            }
        };
    }

    private static void throwIt(SpellEffectEntity slab) {
        ServerLevel level = slab.serverLevel();
        Entity owner = slab.owner();
        Vec3 from = slab.position();
        Vec3 to;
        if (owner instanceof LivingEntity living && living.isAlive() && living.level() == level) {
            Vec3 eye = living.getEyePosition();
            Vec3 end = eye.add(living.getLookAngle().scale(THROW_RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, living));
            to = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
        } else {
            to = from.add(slab.direction().scale(12.0D)).add(0.0D, -LIFT, 0.0D);
        }
        Vec3 d = to.subtract(from);
        int flight = Ballistics.flightTicks(Math.sqrt(d.x * d.x + d.z * d.z));
        double[] v = Ballistics.lob(d.x, d.y, d.z, GRAVITY, Math.min(MAX_FLIGHT, flight));
        CompoundTag data = slab.serverData();
        data.putDouble(KEY_VX, v[0]);
        data.putDouble(KEY_VY, v[1]);
        data.putDouble(KEY_VZ, v[2]);
        slab.setDirection(new Vec3(v[0], v[1], v[2]));
        slab.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        PrimordialService.spin(slab, FLIGHT_SPIN);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0F, 0.4F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    private static void fly(SpellEffectEntity slab, int flown) {
        ServerLevel level = slab.serverLevel();
        CompoundTag data = slab.serverData();
        double vx = data.getDouble(KEY_VX);
        double vy = data.getDouble(KEY_VY);
        double vz = data.getDouble(KEY_VZ);
        Vec3 at = slab.position().add(vx, vy, vz);
        data.putDouble(KEY_VY, vy - GRAVITY);
        slab.setPos(at.x, at.y, at.z);
        BlockPos cell = BlockPos.containing(at);
        boolean ground = level.isLoaded(cell) && PrimordialService.solid(level, cell);
        boolean body = !SkillTargets.hostilesWithin(level, slab.owner(), at, 1.6D).isEmpty();
        if (ground || body || flown >= MAX_FLIGHT) {
            burst(slab, at);
        }
    }

    private static void burst(SpellEffectEntity slab, Vec3 at) {
        ServerLevel level = slab.serverLevel();
        Entity owner = slab.owner();
        int mass = slab.serverData().getInt(KEY_MASS);
        float weight = Math.max(0.5F, Math.min(2.0F, mass / (float) MASS_PAR));
        for (LivingEntity body : SkillTargets.hostilesWithin(level, owner, at, BURST_RADIUS)) {
            double d = body.position().distanceTo(at);
            double fall = 1.0D - 0.5D * Math.min(1.0D, d / BURST_RADIUS);
            SkillTargets.hurt(level, owner, body, (float) (slab.damage() * weight * fall), slab.definition(), true);
            SkillTargets.shove(body, at, slab.knockback() * weight * fall, 0.45D);
        }
        Set<BlockState> kinds = new LinkedHashSet<>();
        for (int id : slab.syncedData().getIntArray(MassCodec.STATES)) {
            kinds.add(Block.stateById(id));
        }
        int n = 0;
        for (BlockState state : kinds) {
            if (n++ >= 8) {
                break;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.5D, at.z, 24, 1.2D, 0.6D, 1.2D, 0.35D);
            level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.5F, 0.6F);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5D, at.z, 2, 0.6D, 0.3D, 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.DUST_PLUME, at.x, at.y + 0.3D, at.z, 20, 1.5D, 0.2D, 1.5D, 0.05D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.7F);
        PrimordialService.drop(slab);
        slab.setPhase(SpellEffectEntity.PHASE_DONE);
        slab.setLife(slab.tickCount + 2);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.PRIMORDIAL)
                .circle(CircleScript.of(SchoolMaterial.PRIMORDIAL).emblem(EmblemId.HEAVE).frame(4)
                        .band(GlyphKind.FACET_BAND, 8, ColorRole.BRIGHT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.SQUARE, 6).core(CoreKind.HEX_LENS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("primordial_mass", 2.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DUST, FxKinds.Overlay.SHOCK_RING)
                .budget(3)
                .bounds(3.0F, 3.0F, 3.0F);
    }
}
