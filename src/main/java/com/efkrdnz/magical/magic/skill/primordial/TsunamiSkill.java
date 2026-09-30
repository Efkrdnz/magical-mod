package com.efkrdnz.magical.magic.skill.primordial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.primordial.PrimordialService;
import com.efkrdnz.magical.magic.primordial.Wellspring;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * PRIMORDIAL T-4 - a wall of the sea.
 *
 * <p>A wave stands up in front of the caster and rolls along their gaze, riding the terrain.
 * Everything hostile in its face is swept along with it, out of breath and put out; where it breaks
 * it slams them down for the full damage and leaves them slowed. Fire it passes is put out.
 * Wellspring: the water round the caster, which is the wave's width and height - a trickle in a
 * desert, a wall on the coast.
 */
public final class TsunamiSkill implements SkillModule {
    public static final double SPEED = 0.45D;
    public static final double START = 2.0D;
    public static final float MIN_WIDTH = 5.0F;
    public static final float MAX_WIDTH = 11.0F;
    public static final float MIN_HEIGHT = 2.0F;
    public static final float MAX_HEIGHT = 5.0F;
    private static final double FACE_BEHIND = 1.8D;
    private static final double FACE_AHEAD = 1.2D;
    private static final float WALL_HIT = 0.3F;
    public static final String KEY_WIDTH = "W";
    public static final String KEY_HEIGHT = "H";
    private static final String KEY_OX = "OX";
    private static final String KEY_OZ = "OZ";
    private static final String KEY_CAUGHT = "Caught";
    private static final String KEY_WELL = "Well";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TSUNAMI;
    }

    /** How wide and how tall the sea stands at a water reading. */
    public static float width(float water, float size) {
        return (MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * (water - Wellspring.MIN)) * size;
    }

    public static float height(float water, float size) {
        return (MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * (water - Wellspring.MIN)) * Math.min(size, 1.3F);
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 look = ctx.look();
                Vec3 dir = new Vec3(look.x, 0.0D, look.z);
                dir = dir.lengthSqr() > 1.0E-6D ? dir.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
                float water = PrimordialService.water(level, ctx.caster().blockPosition());
                PrimordialService.say(ctx.caster(), "water", water);
                float w = width(water, ctx.size());
                float h = height(water, ctx.size());
                Vec3 origin = ctx.feet().add(dir.scale(START));
                SpellEffectEntity wave = SpellEffectEntity.spawn(ctx, origin, Math.max(10, ctx.duration()), w * 0.5F, dir);
                CompoundTag synced = wave.syncedData().copy();
                synced.putFloat(KEY_WIDTH, w);
                synced.putFloat(KEY_HEIGHT, h);
                wave.setSyncedData(synced);
                wave.serverData().putDouble(KEY_OX, origin.x);
                wave.serverData().putDouble(KEY_OZ, origin.z);
                wave.serverData().putFloat(KEY_WELL, water);
                wave.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 2.0F, 0.5F);
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.0F, 0.4F);
                return CastResult.SUCCESS;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity wave) {
                if (wave.phase() == SpellEffectEntity.PHASE_DONE || !wave.serverData().contains(KEY_OX)) {
                    return;
                }
                ServerLevel level = wave.serverLevel();
                Vec3 dir = wave.direction();
                double speed = SPEED * Math.max(0.35F, wave.speed());
                double ox = wave.serverData().getDouble(KEY_OX);
                double oz = wave.serverData().getDouble(KEY_OZ);
                double x = ox + dir.x * speed * wave.tickCount;
                double z = oz + dir.z * speed * wave.tickCount;
                BlockPos floor = PrimordialService.floor(level, new Vec3(x, wave.getY(), z), 3, 8);
                double want = floor != null ? floor.getY() + 1.0D : wave.getY();
                double y = wave.getY() + Math.max(-0.6D, Math.min(0.6D, want - wave.getY()));
                wave.setPos(x, y, z);
                sweep(level, wave, dir, speed);
                if (wave.tickCount % 2 == 0) {
                    douse(level, wave, dir);
                }
                if (wave.tickCount % 8 == 0) {
                    level.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2F, 0.5F);
                }
                if (wave.tickCount >= wave.life() - 2) {
                    crash(level, wave, dir);
                }
            }
        };
    }

    private static float width(SpellEffectEntity wave) {
        return wave.syncedData().contains(KEY_WIDTH) ? wave.syncedData().getFloat(KEY_WIDTH) : MIN_WIDTH;
    }

    private static float height(SpellEffectEntity wave) {
        return wave.syncedData().contains(KEY_HEIGHT) ? wave.syncedData().getFloat(KEY_HEIGHT) : MIN_HEIGHT;
    }

    /** Is a body in the face of the wave? */
    private static boolean inFace(SpellEffectEntity wave, Vec3 dir, LivingEntity body, double halfWidth, double h, double slack) {
        double rx = body.getX() - wave.getX();
        double rz = body.getZ() - wave.getZ();
        double along = rx * dir.x + rz * dir.z;
        double lateral = -rx * dir.z + rz * dir.x;
        double dy = body.getY() - wave.getY();
        return along >= -FACE_BEHIND - slack && along <= FACE_AHEAD + slack && Math.abs(lateral) <= halfWidth + 0.5D + slack
                && dy >= -1.0D && dy <= h + 0.5D + slack;
    }

    private static void sweep(ServerLevel level, SpellEffectEntity wave, Vec3 dir, double speed) {
        double half = width(wave) * 0.5D;
        double h = height(wave);
        Entity owner = wave.owner();
        AABB box = new AABB(wave.getX() - half - 2.0D, wave.getY() - 1.0D, wave.getZ() - half - 2.0D, wave.getX() + half + 2.0D, wave.getY() + h + 1.0D, wave.getZ() + half + 2.0D);
        int[] caught = wave.serverData().getIntArray(KEY_CAUGHT);
        for (LivingEntity body : Bodies.of(level, LivingEntity.class, box, b -> SkillTargets.isHostile(owner, b))) {
            if (!inFace(wave, dir, body, half, h, 0.0D)) {
                continue;
            }
            if (!contains(caught, body.getId())) {
                caught = append(caught, body.getId());
                SkillTargets.hurt(level, owner, body, wave.damage() * WALL_HIT, wave.skillId());
                level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
            double lift = body.getY() - wave.getY() < h * 0.6D ? 0.12D : 0.0D;
            body.setDeltaMovement(dir.x * speed * 1.1D, Math.max(lift, body.getDeltaMovement().y * 0.5D), dir.z * speed * 1.1D);
            body.hurtMarked = true;
            body.clearFire();
            body.setAirSupply(Math.max(-19, body.getAirSupply() - 10));
            body.fallDistance = 0.0F;
        }
        wave.serverData().putIntArray(KEY_CAUGHT, caught);
    }

    private static boolean contains(int[] ids, int id) {
        for (int i : ids) {
            if (i == id) {
                return true;
            }
        }
        return false;
    }

    private static int[] append(int[] ids, int id) {
        int[] next = java.util.Arrays.copyOf(ids, ids.length + 1);
        next[ids.length] = id;
        return next;
    }

    /** Put out the fire along the front. */
    private static void douse(ServerLevel level, SpellEffectEntity wave, Vec3 dir) {
        double half = width(wave) * 0.5D;
        for (double u = -half; u <= half; u += 1.0D) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = BlockPos.containing(wave.getX() - dir.z * u, wave.getY() + dy, wave.getZ() + dir.x * u);
                if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.FIRE)) {
                    level.removeBlock(pos, false);
                }
            }
        }
    }

    /** The wave breaks: everything it carried is slammed down. */
    private static void crash(ServerLevel level, SpellEffectEntity wave, Vec3 dir) {
        double half = width(wave) * 0.5D;
        double h = height(wave);
        float well = wave.serverData().contains(KEY_WELL) ? wave.serverData().getFloat(KEY_WELL) : 1.0F;
        Entity owner = wave.owner();
        for (int id : wave.serverData().getIntArray(KEY_CAUGHT)) {
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity body) || !body.isAlive() || !inFace(wave, dir, body, half, h, 3.0D)) {
                continue;
            }
            SkillTargets.hurt(level, owner, body, wave.damage() * well, wave.definition(), true);
            Vec3 v = body.getDeltaMovement();
            body.setDeltaMovement(v.x * 0.3D, -0.8D, v.z * 0.3D);
            body.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            body.hurtMarked = true;
        }
        Vec3 crest = wave.position().add(dir.scale(1.0D));
        level.sendParticles(ParticleTypes.SPLASH, crest.x, crest.y + 0.5D, crest.z, 120, half, 0.5D, half, 0.3D);
        level.sendParticles(ParticleTypes.CLOUD, crest.x, crest.y + 1.0D, crest.z, 30, half * 0.8D, 0.6D, half * 0.8D, 0.05D);
        level.sendParticles(ParticleTypes.FALLING_WATER, crest.x, crest.y + h, crest.z, 60, half, 0.4D, half, 0.0D);
        level.playSound(null, crest.x, crest.y, crest.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 3.0F, 0.55F);
        level.playSound(null, crest.x, crest.y, crest.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 2.5F, 0.6F);
        wave.setPhase(SpellEffectEntity.PHASE_DONE);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.PRIMORDIAL)
                .accent(Accent.SPLASH)
                .circle(CircleScript.of(SchoolMaterial.PRIMORDIAL).emblem(EmblemId.TSUNAMI).frame(8)
                        .band(GlyphKind.WAVE_BAND, 16, ColorRole.BRIGHT)
                        .band(GlyphKind.BRAID_BAND, 8, ColorRole.INK)
                        .stamps(StampId.WAVE, 8).core(CoreKind.RIPPLE, ColorRole.HOT).spin(SpinSignature.SWEEP))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.field(Silhouette.Form.VERTICAL_PANE, FxKinds.Field.RIPPLE_WATER, 2.5F, 2.0F).withOpacity(0.5F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.WATER_DROPLETS)
                .budget(3)
                .bounds(6.0F, 5.0F, 1.0F);
    }
}
