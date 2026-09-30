package com.efkrdnz.magical.entity.sound;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.sound.Family;
import com.efkrdnz.magical.magic.sound.Instrument;
import com.efkrdnz.magical.magic.sound.RiffNote;
import com.efkrdnz.magical.magic.sound.RiffService;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A note of the Riff in flight: the low notes, the bells and the strings. It is drawn as the note it
 * is ({@code SoundNoteRenderer}) in its family colour and sheds smaller ones behind it.
 *
 * <p>A low note is a wave: it goes through walls and through every body it meets, and weighs each one
 * down. A bell and a string stop at the first body or block; a string bends toward the nearest enemy
 * in front of it as it goes. Only the server moves it and decides what it hits; a client draws it
 * where the server last put it. Never saved: a note is a moment of music, not a thing in the world.
 */
public final class SoundNoteEntity extends Entity {

    private static final EntityDataAccessor<Integer> INSTRUMENT = SynchedEntityData.defineId(SoundNoteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PITCH = SynchedEntityData.defineId(SoundNoteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMPLITUDE = SynchedEntityData.defineId(SoundNoteEntity.class, EntityDataSerializers.INT);

    /** How far a string looks for something to bend toward. */
    public static final double HOMING_REACH = 10.0D;
    /** How much of its heading a string gives to its target each tick. */
    public static final double HOMING_TURN = 0.22D;
    /** A string only bends toward what is in front of it: the cosine of the cone it looks in. */
    public static final double HOMING_CONE = 0.35D;

    private final Set<Integer> struck = new HashSet<>();
    private UUID ownerId;
    private RiffNote note;
    private int life;

    public SoundNoteEntity(EntityType<? extends SoundNoteEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Throws one note from the hand along the wielder's aim. */
    public static SoundNoteEntity shoot(ServerPlayer owner, RiffNote note) {
        SoundNoteEntity entity = new SoundNoteEntity(MagicalEntities.SOUND_NOTE.get(), owner.level());
        Vec3 look = owner.getLookAngle();
        Vec3 at = owner.getEyePosition().add(look.scale(0.9D)).add(0.0D, -0.3D, 0.0D);
        entity.setPos(at.x, at.y, at.z);
        entity.setDeltaMovement(look.scale(note.speed()));
        entity.ownerId = owner.getUUID();
        entity.note = note;
        entity.life = note.lifeTicks();
        entity.entityData.set(INSTRUMENT, note.instrument().ordinal());
        entity.entityData.set(PITCH, note.pitch());
        entity.entityData.set(AMPLITUDE, note.amplitude());
        owner.level().addFreshEntity(entity);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(INSTRUMENT, Instrument.BELL.ordinal());
        builder.define(PITCH, 12);
        builder.define(AMPLITUDE, 1);
    }

    public Instrument instrument() {
        Instrument instrument = Instrument.byOrdinal(entityData.get(INSTRUMENT));
        return instrument == null ? Instrument.BELL : instrument;
    }

    public Family family() {
        return instrument().family();
    }

    public int pitch() {
        return entityData.get(PITCH);
    }

    public int amplitude() {
        return entityData.get(AMPLITUDE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (tickCount % 2 == 0) {
                trail();
            }
            return;
        }
        if (note == null) {
            discard();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        Entity caster = owner != null ? owner : this;
        if (note.family() == Family.STRINGS) {
            steer(level, caster);
        }
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        boolean ends = false;
        if (note.family() != Family.LOW) {
            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS) {
                to = wall.getLocation();
                ends = true;
            }
        }
        for (LivingEntity target : SkillTargets.hostilesIn(level, caster, new AABB(from, to).inflate(note.radius() + 0.6D))) {
            if (struck.contains(target.getId())) {
                continue;
            }
            AABB box = target.getBoundingBox().inflate(note.radius());
            if (!box.contains(from) && box.clip(from, to).isEmpty()) {
                continue;
            }
            struck.add(target.getId());
            RiffService.strike(level, owner, target, note, getDeltaMovement());
            if (note.family() == Family.LOW) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, true));
            } else {
                to = target.getBoundingBox().getCenter();
                ends = true;
                break;
            }
        }
        setPos(to.x, to.y, to.z);
        if (ends || tickCount >= life) {
            pop(level);
            discard();
        }
    }

    /** A string bends toward the nearest enemy in front of it, keeping its speed. */
    private void steer(ServerLevel level, Entity caster) {
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-4D) {
            return;
        }
        Vec3 heading = velocity.scale(1.0D / speed);
        LivingEntity best = null;
        double bestScore = 0.0D;
        for (LivingEntity candidate : SkillTargets.hostilesWithin(level, caster, position(), HOMING_REACH)) {
            if (struck.contains(candidate.getId())) {
                continue;
            }
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(position());
            double distance = to.length();
            if (distance < 1.0E-4D) {
                continue;
            }
            double dot = to.scale(1.0D / distance).dot(heading);
            double score = dot / (1.0D + distance);
            if (dot >= HOMING_CONE && score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null) {
            Vec3 want = best.getBoundingBox().getCenter().subtract(position()).normalize();
            setDeltaMovement(heading.lerp(want, HOMING_TURN).normalize().scale(speed));
        }
    }

    private void pop(ServerLevel level) {
        TintedParticleOptions options = new TintedParticleOptions(MagicalParticles.NOTE.get(), note.family().rgb(), 1.3F);
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0D + level.random.nextDouble();
            level.sendParticles(options, getX(), getY(), getZ(), 0, Math.cos(angle) * 0.12D, 0.08D, Math.sin(angle) * 0.12D, 1.0D);
        }
    }

    private void trail() {
        TintedParticleOptions options = new TintedParticleOptions(MagicalParticles.NOTE.get(), family().rgb(), 0.8F);
        double jitter = 0.08D;
        level().addParticle(options, getX(), getY(), getZ(),
                (random.nextDouble() - 0.5D) * jitter, 0.03D + random.nextDouble() * 0.03D, (random.nextDouble() - 0.5D) * jitter);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
