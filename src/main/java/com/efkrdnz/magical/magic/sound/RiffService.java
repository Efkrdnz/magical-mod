package com.efkrdnz.magical.magic.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.sound.SoundNoteEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalParticles;
import com.efkrdnz.magical.registry.MagicalSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Riff, played: while the key is held, one note of the written figure every
 * {@link Riff#STEP_TICKS} ticks, round and round.
 *
 * <p>Every note is billed as it sounds, at {@link RiffNote#mana()}, and the fractions are carried
 * so a quiet riff pays a point every few notes rather than rounding its price away. A note that
 * cannot be paid for is not played, and the next one is tried on its beat: a wielder running dry
 * hears their riff falter rather than stop.
 *
 * <p>What a note does is its family: the kit strikes a cone where the wielder stands, the keys are a
 * ray that lands at once, and the low notes, the bells and the strings fly as {@link SoundNoteEntity}.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class RiffService {

    /** How many bodies a ray of the keys passes through before it stops. */
    public static final int KEY_PIERCE = 2;

    private static final Map<UUID, Play> PLAYING = new HashMap<>();

    static final class Play {
        long note;
        long nextTick;
        float owed;

        Play(long now) {
            this.nextTick = now;
        }
    }

    private RiffService() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!HoldService.isHeldSkill(player, MagicContent.RIFF.id())) {
                PLAYING.remove(player.getUUID());
                continue;
            }
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (player.isAlive() && SongService.holds(state) && state.hasUnlocked(MagicContent.RIFF.id())) {
                tick(player, state);
            } else {
                PLAYING.remove(player.getUUID());
            }
        }
    }

    /** One tick of a held riff: plays the next note when its beat has come. */
    public static void tick(ServerPlayer player, PlayerMagicState state) {
        long now = player.level().getGameTime();
        Play play = PLAYING.computeIfAbsent(player.getUUID(), id -> new Play(now));
        if (now < play.nextTick) {
            return;
        }
        play.nextTick = now + Riff.STEP_TICKS;
        Riff riff = state.sound().riff();
        int slot = riff.slotAt(play.note++);
        Instrument instrument = riff.instrument(slot);
        if (instrument == null) {
            return;
        }
        RiffNote note = RiffNote.resolve(instrument, riff.pitch(slot), riff.amplitude());
        if (!pay(player, state, play, note)) {
            return;
        }
        play(player, note);
    }

    private static boolean pay(ServerPlayer player, PlayerMagicState state, Play play, RiffNote note) {
        float scale = MagicContent.RIFF.resolve(state.tuningFor(MagicContent.RIFF.id())).costScale();
        float due = play.owed + note.mana() * scale;
        int whole = (int) Math.floor(due);
        if (whole > 0 && !MagicSinService.spendManaForSkill(player, state, whole)) {
            if (player.level().getGameTime() % 20 < Riff.STEP_TICKS) {
                SongService.say(player, "message.magical.not_enough_mana");
            }
            return false;
        }
        play.owed = due - whole;
        if (whole > 0) {
            state.sync(player);
        }
        return true;
    }

    /** Sounds one note and lets it do what its family does. */
    public static void play(ServerPlayer player, RiffNote note) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), MagicalSounds.note(note.instrument()).get(),
                SoundSource.PLAYERS, 0.55F + 0.1F * note.amplitude(), Riff.pitchRate(note.pitch()));
        switch (note.family()) {
            case DRUMS -> drum(player, note);
            case KEYS -> ray(player, note);
            default -> SoundNoteEntity.shoot(player, note);
        }
    }

    /** The kit: a cone of force out of the wielder, as wide as the note is low. */
    static void drum(ServerPlayer player, RiffNote note) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double halfAngle = Math.toRadians(12.0D + 22.0D * note.radius());
        double cos = Math.cos(halfAngle);
        for (LivingEntity target : SkillTargets.hostilesWithin(level, player, eye, note.range())) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSqr() < 1.0E-6D || to.normalize().dot(look) >= cos) {
                strike(level, player, target, note, look);
            }
        }
        Vec3 right = side(look);
        Vec3 up = right.cross(look).normalize();
        TintedParticleOptions options = new TintedParticleOptions(MagicalParticles.NOTE.get(), Family.DRUMS.rgb(), 1.5F);
        int count = 6 + note.amplitude() * 2;
        double spread = Math.sin(halfAngle);
        double speed = 0.3D + note.range() * 0.07D;
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double reach = spread * Math.sqrt(level.random.nextDouble());
            Vec3 dir = look.add(right.scale(Math.cos(angle) * reach)).add(up.scale(Math.sin(angle) * reach)).normalize();
            Vec3 at = eye.add(dir.scale(1.5D));
            level.sendParticles(options, at.x, at.y - 0.2D, at.z, 0, dir.x * speed, dir.y * speed, dir.z * speed, 1.0D);
        }
    }

    /** The keys: a ray from the hand that lands the moment it is played, through the first body into the next. */
    static void ray(ServerPlayer player, RiffNote note) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = eye.add(look.scale(note.range()));
        BlockHitResult wall = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation();
        List<LivingEntity> struck = new ArrayList<>();
        for (LivingEntity target : SkillTargets.hostilesIn(level, player, new AABB(eye, end).inflate(note.radius() + 0.5D))) {
            if (target.getBoundingBox().inflate(note.radius()).clip(eye, end).isPresent()) {
                struck.add(target);
            }
        }
        struck.sort(Comparator.comparingDouble(target -> target.distanceToSqr(eye)));
        for (int i = 0; i < Math.min(KEY_PIERCE, struck.size()); i++) {
            strike(level, player, struck.get(i), note, look);
        }
        // drawn from the hand rather than the eye, or the caster sees the whole ray end-on as one note
        Vec3 hand = eye.add(side(look).scale(0.35D)).add(0.0D, -0.3D, 0.0D);
        Vec3 path = end.subtract(hand);
        double length = path.length();
        TintedParticleOptions options = new TintedParticleOptions(MagicalParticles.NOTE.get(), Family.KEYS.rgb(), 1.0F);
        for (double d = 1.2D; d < length; d += 0.85D) {
            Vec3 at = hand.add(path.scale(d / length));
            level.sendParticles(options, at.x, at.y, at.z, 0, look.x * 0.04D, 0.02D, look.z * 0.04D, 1.0D);
        }
    }

    /**
     * One note landing on one body. The hurt cooldown is cleared first, or a riff - a note every four
     * ticks - would land its first note and have every other one refused as no stronger. A bell rings
     * through armour and every enchantment on it; the rest land as magic.
     */
    public static void strike(ServerLevel level, Entity owner, LivingEntity target, RiffNote note, Vec3 along) {
        target.invulnerableTime = 0;
        Entity source = owner != null ? owner : target;
        if (note.family() == Family.BELLS) {
            MagicDamageService.hurt(target, level.damageSources().sonicBoom(source), note.damage(), MagicContent.RIFF.id());
        } else {
            SkillTargets.hurt(level, owner, target, note.damage(), MagicContent.RIFF.id());
        }
        if (note.knockback() > 0.0F) {
            Vec3 from = target.position().subtract(new Vec3(along.x, 0.0D, along.z).normalize());
            SkillTargets.shove(target, from, note.knockback(), 0.08D);
        }
        TintedParticleOptions options = new TintedParticleOptions(MagicalParticles.NOTE.get(), note.family().rgb(), 1.2F);
        Vec3 centre = target.getBoundingBox().getCenter();
        for (int i = 0; i < 3; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            level.sendParticles(options, centre.x, centre.y + 0.3D, centre.z, 0, Math.cos(angle) * 0.1D, 0.1D, Math.sin(angle) * 0.1D, 1.0D);
        }
    }

    private static Vec3 side(Vec3 look) {
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PLAYING.clear();
    }
}
