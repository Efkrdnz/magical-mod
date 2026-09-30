package com.efkrdnz.magical.magic.skill.primordial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
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
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.visual.SpellFx;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * PRIMORDIAL T-4 - call down a star.
 *
 * <p>Refused, for nothing, where the target cannot see the sky. Otherwise a ring of embers closes
 * on the target for the whole warning while a burning rock comes down a slant out of the sky behind
 * the caster, and lands exactly as the ring closes: a blast falling off from its heart, fire, a
 * shove, the ground it hit thrown up, and a crater lined with magma that fills itself back later. A
 * star at night is a quarter larger. Wellspring: the open sky round the target.
 */
public final class SkyfallSkill implements SkillModule {
    public static final double AIM_RANGE = 48.0D;
    public static final double BLAST_RADIUS = 5.0D;
    public static final double FULL_WITHIN = 2.0D;
    public static final double SLANT_BACK = 28.0D;
    public static final double SLANT_UP = 42.0D;
    public static final float NIGHT = 1.25F;
    public static final int SCAR_TICKS = 600;
    private static final float ROCK_SCALE = 0.7F;
    private static final float ROCK_SPIN = 9.0F;
    public static final String KEY_IX = "IX";
    public static final String KEY_IY = "IY";
    public static final String KEY_IZ = "IZ";
    public static final String KEY_WARN = "W";
    public static final String KEY_RADIUS = "Rb";
    private static final String KEY_SX = "SX";
    private static final String KEY_SY = "SY";
    private static final String KEY_SZ = "SZ";
    private static final String KEY_WELL = "Well";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SKYFALL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 aim = ctx.aim() != null ? ctx.aim().point() : ctx.eye().add(ctx.look().scale(AIM_RANGE));
                BlockPos ground = PrimordialService.surface(level, aim, 3, 12);
                if (ground == null) {
                    PrimordialService.refuse(ctx.caster(), "message.magical.primordial.no_ground");
                    return CastResult.FAILED;
                }
                if (!PrimordialService.opensToSky(level, ground.above())) {
                    PrimordialService.refuse(ctx.caster(), "message.magical.skyfall.roofed");
                    return CastResult.FAILED;
                }
                float sky = PrimordialService.sky(level, ground);
                PrimordialService.say(ctx.caster(), "sky", sky);
                float scale = ctx.size() * (level.isDay() ? 1.0F : NIGHT);
                Vec3 impact = Vec3.atBottomCenterOf(ground.above());
                Vec3 back = new Vec3(ctx.feet().x - impact.x, 0.0D, ctx.feet().z - impact.z);
                if (back.lengthSqr() < 1.0E-4D) {
                    back = new Vec3(-ctx.look().x, 0.0D, -ctx.look().z);
                }
                back = back.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : back.normalize();
                Vec3 start = impact.add(back.scale(SLANT_BACK)).add(0.0D, SLANT_UP, 0.0D);
                int warn = Math.max(20, ctx.duration());
                float radius = (float) (BLAST_RADIUS * scale);
                SpellEffectEntity star = SpellEffectEntity.spawn(ctx, start, warn + 6, radius, impact.subtract(start).normalize());
                CompoundTag data = star.serverData();
                data.putDouble(KEY_SX, start.x);
                data.putDouble(KEY_SY, start.y);
                data.putDouble(KEY_SZ, start.z);
                data.putFloat(KEY_WELL, sky);
                List<BlockState> states = new ArrayList<>();
                List<int[]> offsets = new ArrayList<>();
                rock(scale, states, offsets);
                PrimordialService.carry(star, states, offsets, ROCK_SCALE * scale, ROCK_SPIN, true);
                CompoundTag synced = star.syncedData().copy();
                synced.putDouble(KEY_IX, impact.x);
                synced.putDouble(KEY_IY, impact.y);
                synced.putDouble(KEY_IZ, impact.z);
                synced.putInt(KEY_WARN, warn);
                synced.putFloat(KEY_RADIUS, radius);
                star.setSyncedData(synced);
                level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
                level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0F, 0.4F);
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

    /** Magma at the heart, blackstone and basalt about it; a larger star grows corners. */
    private static void rock(float scale, List<BlockState> states, List<int[]> offsets) {
        states.add(Blocks.MAGMA_BLOCK.defaultBlockState());
        offsets.add(new int[] {0, 0, 0});
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < faces.length; i++) {
            states.add(i % 3 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : (i % 3 == 1 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState()));
            offsets.add(faces[i]);
        }
        if (scale >= 1.2F) {
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    for (int dz = -1; dz <= 1; dz += 2) {
                        states.add((dx + dy + dz) > 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                        offsets.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity star) {
                if (star.phase() == SpellEffectEntity.PHASE_DONE) {
                    return;
                }
                CompoundTag synced = star.syncedData();
                CompoundTag data = star.serverData();
                if (!synced.contains(KEY_WARN) || !data.contains(KEY_SX)) {
                    return;
                }
                int warn = synced.getInt(KEY_WARN);
                Vec3 start = new Vec3(data.getDouble(KEY_SX), data.getDouble(KEY_SY), data.getDouble(KEY_SZ));
                Vec3 impact = new Vec3(synced.getDouble(KEY_IX), synced.getDouble(KEY_IY), synced.getDouble(KEY_IZ));
                int t = star.tickCount;
                double p = Ballistics.fall(t / (double) warn);
                Vec3 at = start.add(impact.subtract(start).scale(p));
                star.setPos(at.x, at.y, at.z);
                if (t == warn / 2) {
                    star.serverLevel().playSound(null, impact.x, impact.y + 8.0D, impact.z, SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 3.0F, 0.3F);
                }
                if (t >= warn) {
                    strike(star, impact);
                }
            }
        };
    }

    private static void strike(SpellEffectEntity star, Vec3 at) {
        ServerLevel level = star.serverLevel();
        Entity owner = star.owner();
        float radius = star.radius();
        float well = star.serverData().contains(KEY_WELL) ? star.serverData().getFloat(KEY_WELL) : 1.0F;
        float scale = (float) (radius / BLAST_RADIUS);
        for (LivingEntity body : SkillTargets.hostilesWithin(level, owner, at, radius)) {
            double d = body.position().distanceTo(at);
            double fall = d <= FULL_WITHIN ? 1.0D : 1.0D - 0.7D * Math.min(1.0D, (d - FULL_WITHIN) / Math.max(0.5D, radius - FULL_WITHIN));
            SkillTargets.hurt(level, owner, body, (float) (star.damage() * well * fall), star.definition(), false);
            body.igniteForSeconds(5.0F);
            SkillTargets.shove(body, at, star.knockback() * fall * 1.2D, 0.5D + 0.4D * fall);
        }
        BlockPos ground = BlockPos.containing(at).below();
        int r = Math.max(2, Math.round(3 * scale));
        int depth = Math.max(1, Math.round(2 * scale));
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        Set<BlockState> ejecta = new LinkedHashSet<>();
        for (int[] c : Shapes.crater(r, depth)) {
            BlockPos pos = ground.offset(c[0], c[1], c[2]);
            BlockState was = PrimordialService.carve(level, edit, pos, Blocks.AIR.defaultBlockState());
            if (was != null) {
                ejecta.add(was);
            }
        }
        for (int[] c : Shapes.craterLining(r, depth)) {
            BlockPos pos = ground.offset(c[0], c[1], c[2]);
            // scorched rather than molten: magma would burn the caster and their allies for the
            // whole life of the scar
            int h = Math.floorMod(c[0] * 31 + c[2] * 17, 5);
            BlockState lining = h == 0 ? Blocks.SMOOTH_BASALT.defaultBlockState() : (h < 3 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState());
            PrimordialService.carve(level, edit, pos, lining);
        }
        PrimordialScars.giveBackLater(level, edit, SCAR_TICKS);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5D, at.z, 30, r * 0.5D, 0.5D, r * 0.5D, 0.2D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0D, at.z, 40, r * 0.6D, 1.0D, r * 0.6D, 0.05D);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3D, at.z, 40, r * 0.5D, 0.2D, r * 0.5D, 0.12D);
        int kinds = 0;
        for (BlockState state : ejecta) {
            if (kinds++ >= 10) {
                break;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.5D, at.z, 14, r * 0.4D, 0.4D, r * 0.4D, 0.4D);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
        SpellFx.impact(level, star.definition(), at, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
        PrimordialService.drop(star);
        star.setPhase(SpellEffectEntity.PHASE_DONE);
        star.setLife(star.tickCount + 3);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.PRIMORDIAL)
                .accent(Accent.EMBER)
                .circle(CircleScript.of(SchoolMaterial.PRIMORDIAL).emblem(EmblemId.SKYFALL).frame(7)
                        .band(GlyphKind.RUNE_BAND, 12, ColorRole.BRIGHT)
                        .band(GlyphKind.TICK_BAND, 24, ColorRole.INK)
                        .stamps(StampId.STAR4, 5).core(CoreKind.SUNBURST, ColorRole.HOT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.SKY)
                .silhouette(Silhouette.custom("primordial_mass", 1.5F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.FLASH)
                .budget(3)
                .bounds(3.0F, 3.0F, 3.0F);
    }
}
