package com.efkrdnz.magical.magic.skill.primordial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.ThrownSpellEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.primordial.Ballistics;
import com.efkrdnz.magical.magic.primordial.PrimordialScars;
import com.efkrdnz.magical.magic.primordial.PrimordialService;
import com.efkrdnz.magical.magic.primordial.Shapes;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import com.efkrdnz.magical.magic.service.Bodies;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.visual.Accent;
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
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * PRIMORDIAL T-4 - raise a volcano.
 *
 * <p>The ground where the caster aims heaves up into a cone of basalt and blackstone with a magma
 * vent on top, rumbles, then erupts: every so often it lobs a lava bomb in a high arc at a hostile
 * within reach (up, when there is none), which bursts where it lands. Anything touching the flanks
 * burns. When it is done the cone sinks back into the ground it came from. Wellspring: heat, which
 * sets how often it fires. The bombs are the owner's spells in flight, so a Cyclone that meets one
 * becomes Ember.
 */
public final class CalderaSkill implements SkillModule {
    public static final double AIM_RANGE = 24.0D;
    public static final int RISE = 20;
    public static final int RUMBLE = 20;
    public static final int BASE_RADIUS = 2;
    public static final int HEIGHT = 4;
    public static final double BOMB_RANGE = 16.0D;
    public static final double BOMB_GRAVITY = 0.05D;
    public static final double BOMB_BURST = 2.5D;
    private static final int SCORCH_EVERY = 10;
    private static final float SCORCH_DAMAGE = 2.0F;
    private static final double IDLE_REACH = 5.0D;
    private static final double IDLE_REACH_STEP = 2.5D;
    public static final int FASTEST = 12;
    public static final int SLOWEST = 28;
    /** Draw mode of a lava bomb; the cone itself is draw mode 0. */
    public static final int MODE_BOMB = 1;
    private static final String KEY_BX = "BX";
    private static final String KEY_BY = "BY";
    private static final String KEY_BZ = "BZ";
    private static final String KEY_R = "R";
    private static final String KEY_H = "H";
    private static final String KEY_LAYERS = "Layers";
    private static final String KEY_NEXT = "Next";
    private static final String KEY_SHOTS = "Shots";
    private static final String KEY_HEAT = "Heat";
    private static final String KEY_EDIT = "Edit";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CALDERA;
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
                float heat = PrimordialService.heat(level, ground);
                PrimordialService.say(ctx.caster(), "heat", heat);
                int radius = Math.max(1, Math.round(BASE_RADIUS * ctx.size()));
                int height = HEIGHT + (ctx.size() >= 1.25F ? 1 : 0);
                Vec3 top = Vec3.atBottomCenterOf(ground.above(height + 1));
                SpellEffectEntity caldera = SpellEffectEntity.spawn(ctx, top, RISE + RUMBLE + ctx.duration(), radius, new Vec3(0.0D, 1.0D, 0.0D));
                CompoundTag data = caldera.serverData();
                data.putInt(KEY_BX, ground.getX());
                data.putInt(KEY_BY, ground.getY());
                data.putInt(KEY_BZ, ground.getZ());
                data.putInt(KEY_R, radius);
                data.putInt(KEY_H, height);
                data.putInt(KEY_LAYERS, 0);
                data.putInt(KEY_NEXT, RISE + RUMBLE);
                data.putFloat(KEY_HEAT, heat);
                data.putUUID(KEY_EDIT, ConjuredTerrainService.begin(level).id());
                caldera.setPhase(SpellEffectEntity.PHASE_WINDUP);
                level.playSound(null, top.x, ground.getY(), top.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.0F, 0.4F);
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

    /** How often a caldera fires at a heat reading. */
    public static int interval(float heat) {
        float f = Math.max(0.0F, Math.min(1.0F, heat - 0.5F));
        return Math.round(SLOWEST - (SLOWEST - FASTEST) * f);
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity fx) {
                if (fx instanceof ThrownSpellEntity bomb) {
                    bomb(bomb);
                    return;
                }
                CompoundTag data = fx.serverData();
                if (!data.contains(KEY_H)) {
                    return;
                }
                int t = fx.tickCount;
                if (t <= RISE) {
                    raise(fx, t);
                }
                ServerLevel level = fx.serverLevel();
                if (t > RISE && t < RISE + RUMBLE && t % 4 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, fx.getX(), fx.getY(), fx.getZ(), 4, 0.3D, 0.2D, 0.3D, 0.03D);
                    level.playSound(null, fx.getX(), fx.getY(), fx.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F);
                }
                if (t == RISE + RUMBLE) {
                    fx.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    level.playSound(null, fx.getX(), fx.getY(), fx.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 0.5F);
                    level.sendParticles(ParticleTypes.LAVA, fx.getX(), fx.getY(), fx.getZ(), 20, 0.4D, 0.4D, 0.4D, 0.3D);
                }
                if (fx.phase() == SpellEffectEntity.PHASE_ACTIVE) {
                    if (t >= data.getInt(KEY_NEXT)) {
                        fire(fx);
                        data.putInt(KEY_NEXT, t + interval(data.getFloat(KEY_HEAT)));
                    }
                    if (t % 3 == 0) {
                        level.sendParticles(ParticleTypes.LAVA, fx.getX(), fx.getY() - 0.2D, fx.getZ(), 1, 0.15D, 0.05D, 0.15D, 0.1D);
                    }
                }
                if (t > RISE && t % SCORCH_EVERY == 0) {
                    scorch(fx);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity fx) {
                if (fx instanceof ThrownSpellEntity) {
                    return;
                }
                CompoundTag data = fx.serverData();
                ServerLevel level = fx.serverLevel();
                if (data.hasUUID(KEY_EDIT)) {
                    PrimordialService.giveBack(level, ConjuredTerrainService.lookup(level, data.getUUID(KEY_EDIT)), PrimordialScars::ours);
                    data.remove(KEY_EDIT);
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE, fx.getX(), fx.getY() - 2.0D, fx.getZ(), 30, 1.5D, 1.2D, 1.5D, 0.04D);
                level.playSound(null, fx.getX(), fx.getY(), fx.getZ(), SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
            }
        };
    }

    private static CompoundTag data(SpellEffectEntity fx) {
        return fx.serverData();
    }

    private static BlockPos base(CompoundTag data) {
        return new BlockPos(data.getInt(KEY_BX), data.getInt(KEY_BY), data.getInt(KEY_BZ));
    }

    /** The cone climbs out of the ground a layer at a time. */
    private static void raise(SpellEffectEntity fx, int t) {
        ServerLevel level = fx.serverLevel();
        CompoundTag data = fx.serverData();
        int radius = data.getInt(KEY_R);
        int height = data.getInt(KEY_H);
        int want = Math.min(height, (int) Math.ceil(t * height / (double) RISE));
        int done = data.getInt(KEY_LAYERS);
        if (want <= done || !data.hasUUID(KEY_EDIT)) {
            return;
        }
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.lookup(level, data.getUUID(KEY_EDIT));
        if (edit == null) {
            return;
        }
        BlockPos base = base(data);
        Shapes.Cone cone = Shapes.cone(radius, height);
        Entity owner = fx.owner();
        for (int layer = done; layer < want; layer++) {
            for (int[] c : cone.body()) {
                if (c[1] == layer) {
                    place(level, edit, base.offset(c[0], 1 + c[1], c[2]), flank(c), base, owner);
                }
            }
            if (cone.vent()[1] == layer) {
                place(level, edit, base.above(1 + layer), Blocks.MAGMA_BLOCK.defaultBlockState(), base, owner);
            }
            BlockPos ring = base.above(1 + layer);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), ring.getX() + 0.5D, ring.getY() + 0.5D, ring.getZ() + 0.5D, 16, radius * 0.6D, 0.3D, radius * 0.6D, 0.2D);
            level.playSound(null, ring, SoundEvents.BASALT_PLACE, SoundSource.PLAYERS, 1.6F, 0.5F);
        }
        data.putInt(KEY_LAYERS, want);
    }

    private static BlockState flank(int[] c) {
        return Math.floorMod(c[0] + c[1] * 2 + c[2] * 3, 3) == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
    }

    /** A block goes in only where it would not bury anybody; whoever stands there is thrown off the slope. */
    private static void place(ServerLevel level, ConjuredTerrainService.Edit edit, BlockPos pos, BlockState state, BlockPos base, Entity owner) {
        if (!PrimordialService.raisable(level, pos)) {
            return;
        }
        List<LivingEntity> standing = Bodies.of(level, LivingEntity.class, new AABB(pos), LivingEntity::isAlive);
        if (!standing.isEmpty()) {
            for (LivingEntity body : standing) {
                SkillTargets.shove(body, Vec3.atCenterOf(base), 0.7D, 0.55D);
            }
            return;
        }
        PrimordialService.place(level, edit, pos, state);
    }

    private static void fire(SpellEffectEntity fx) {
        ServerLevel level = fx.serverLevel();
        Entity owner = fx.owner();
        Vec3 from = fx.position().add(0.0D, 0.3D, 0.0D);
        List<LivingEntity> hostiles = SkillTargets.hostilesWithin(level, owner, from, BOMB_RANGE);
        int shots = fx.serverData().getInt(KEY_SHOTS);
        fx.serverData().putInt(KEY_SHOTS, shots + 1);
        Vec3 to;
        if (!hostiles.isEmpty()) {
            LivingEntity target = hostiles.get(shots % hostiles.size());
            to = target.position();
        } else {
            // nothing to aim at: the mountain still erupts, round itself in a spiral of landings
            double angle = shots * 2.4D;
            double reach = IDLE_REACH + IDLE_REACH_STEP * (shots % 3);
            to = from.add(Math.cos(angle) * reach, -(data(fx).getInt(KEY_H) + 1.0D), Math.sin(angle) * reach);
        }
        Vec3 d = to.subtract(from);
        int flight = Ballistics.flightTicks(Math.sqrt(d.x * d.x + d.z * d.z));
        double[] v = Ballistics.lob(d.x, d.y, d.z, BOMB_GRAVITY, flight);
        ThrownSpellEntity bomb = ThrownSpellEntity.create(level, fx, from, new Vec3(v[0], v[1], v[2]), (float) BOMB_GRAVITY, 1.0F, 0, flight + 20);
        bomb.setMode((byte) (MODE_BOMB << 1));
        bomb.setLife(flight + 40);
        bomb.setRadius(0.5F);
        bomb.setPhase(SpellEffectEntity.PHASE_WINDUP);
        bomb.serverData().putFloat(KEY_HEAT, fx.serverData().getFloat(KEY_HEAT));
        level.addFreshEntity(bomb);
        level.sendParticles(ParticleTypes.LAVA, from.x, from.y, from.z, 6, 0.2D, 0.2D, 0.2D, 0.2D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 6, 0.2D, 0.3D, 0.2D, 0.05D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.55F);
    }

    private static void bomb(ThrownSpellEntity bomb) {
        if (bomb.phase() == SpellEffectEntity.PHASE_DONE) {
            return;
        }
        ServerLevel level = bomb.serverLevel();
        boolean hit = !SkillTargets.hostilesWithin(level, bomb.owner(), bomb.position(), 0.9D).isEmpty();
        if (!bomb.landed() && !hit) {
            return;
        }
        Vec3 at = bomb.position();
        float heat = bomb.serverData().contains(KEY_HEAT) ? bomb.serverData().getFloat(KEY_HEAT) : 1.0F;
        for (LivingEntity body : SkillTargets.hostilesWithin(level, bomb.owner(), at, BOMB_BURST)) {
            SkillTargets.hurt(level, bomb.owner(), body, bomb.damage() * heat, bomb.skillId());
            body.igniteForSeconds(4.0F);
            SkillTargets.shove(body, at, bomb.knockback(), 0.3D);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3D, at.z, 20, 0.8D, 0.3D, 0.8D, 0.08D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3D, at.z, 8, 0.5D, 0.2D, 0.5D, 0.2D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5D, at.z, 8, 0.6D, 0.3D, 0.6D, 0.03D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
        bomb.setPhase(SpellEffectEntity.PHASE_DONE);
        bomb.finish();
    }

    /** The flanks are hot: anything touching them burns. */
    private static void scorch(SpellEffectEntity fx) {
        ServerLevel level = fx.serverLevel();
        CompoundTag data = fx.serverData();
        BlockPos base = base(data);
        int radius = data.getInt(KEY_R);
        int height = data.getInt(KEY_H);
        AABB flanks = new AABB(base.getX() - radius - 1.2D, base.getY(), base.getZ() - radius - 1.2D,
                base.getX() + radius + 2.2D, base.getY() + height + 2.0D, base.getZ() + radius + 2.2D);
        for (LivingEntity body : Bodies.of(level, LivingEntity.class, flanks, b -> SkillTargets.isHostile(fx.owner(), b))) {
            body.igniteForSeconds(3.0F);
            SkillTargets.hurt(level, fx.owner(), body, SCORCH_DAMAGE, fx.skillId());
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.PRIMORDIAL)
                .accent(Accent.EMBER)
                .circle(CircleScript.of(SchoolMaterial.PRIMORDIAL).emblem(EmblemId.CALDERA).frame(6)
                        .band(GlyphKind.PETAL_BAND, 10, ColorRole.BRIGHT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.FLAME, 6).core(CoreKind.EMBER_PIT, ColorRole.HOT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.EMBER_CLUSTER, 0.45F).forModes(MODE_BOMB))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.PLASMA, 0.5F).withRole(ColorRole.HOT).withOpacity(0.6F).forModes(0))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.EMBER_FIELD, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.HEAT_SHIMMER)
                .budget(3)
                .bounds(3.0F, 2.0F, 6.0F);
    }
}
