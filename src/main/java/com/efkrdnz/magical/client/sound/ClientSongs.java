package com.efkrdnz.magical.client.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.sound.Riff;
import com.efkrdnz.magical.magic.sound.Score;
import com.efkrdnz.magical.magic.sound.SongAction;
import com.efkrdnz.magical.magic.sound.SongRun;
import com.efkrdnz.magical.magic.sound.SoundState;
import com.efkrdnz.magical.magic.sound.Track;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.SongPlayPayload;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import com.efkrdnz.magical.registry.MagicalSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Every Song this client can hear, played here against the local game time.
 *
 * <p>The server sends a score and the tick its step 0 falls on; from then on this class plays each
 * step as the clock reaches it - the kit, the bass and the melody from the Song kit samples, at the
 * performer, with a note of the track colour rising off them - and the server sends nothing more
 * until the Song changes or stops. If the clock jumps (a lag spike) the missed steps are skipped
 * rather than played in a heap.
 *
 * <p>For the performer it is also the ear the judgement starts in: a crouch or a swing is measured
 * against the music as this client is playing it, and the note it lands on is sent to the server to
 * be believed or not.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientSongs {

    /** Steps behind the clock past which the missed ones are skipped rather than played at once. */
    private static final int CATCH_UP = 4;
    private static final double PARTICLE_RANGE_SQR = 48.0D * 48.0D;

    private static final class Heard {
        final Score score;
        final long start;
        final SongRun judge;
        long lastIndex;

        Heard(Score score, long start, long now) {
            this.score = score;
            this.start = start;
            this.judge = new SongRun(score, start);
            this.lastIndex = SongRun.indexAt(start, score.tempo(), now) - 1;
        }
    }

    private static final Map<Integer, Heard> HEARD = new HashMap<>();
    private static boolean wasCrouching;

    private ClientSongs() {}

    public static void receive(SongPlayPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || payload.song() == null || payload.song().isEmpty()) {
            HEARD.remove(payload.owner());
            return;
        }
        HEARD.put(payload.owner(), new Heard(SoundState.loadSong(payload.song()), payload.start(), level.getGameTime()));
    }

    /** Whether this client is performing a Song it can hear. */
    public static boolean performing() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && HEARD.containsKey(minecraft.player.getId());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            HEARD.clear();
            return;
        }
        long now = level.getGameTime();
        for (Map.Entry<Integer, Heard> entry : HEARD.entrySet()) {
            Heard heard = entry.getValue();
            long current = SongRun.indexAt(heard.start, heard.score.tempo(), now);
            if (current - heard.lastIndex > CATCH_UP) {
                heard.lastIndex = current - 1;
            }
            Entity performer = level.getEntity(entry.getKey());
            for (long index = heard.lastIndex + 1; index <= current; index++) {
                if (index >= 0 && performer != null) {
                    playStep(level, performer.position(), heard.score, (int) Math.floorMod(index, (long) Score.STEPS), 1.0F, true);
                }
            }
            heard.lastIndex = current;
        }
        boolean crouching = minecraft.screen == null && minecraft.options.keyShift.isDown();
        if (crouching && !wasCrouching) {
            act(SongAction.CROUCH);
        }
        wasCrouching = crouching;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            act(SongAction.SWING);
        }
    }

    private static void act(SongAction action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Heard own = HEARD.get(minecraft.player.getId());
        if (own == null) {
            return;
        }
        double tick = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        MagicalNetwork.sendSongBeat(action.ordinal(), own.judge.noteNear(tick));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HEARD.clear();
    }

    /**
     * Plays one step of a score at a place: every note of the kit on it, the bass and the melody
     * at their pitch in the score scale, each with a note of its colour. The Score screen preview
     * plays through here too, at the wielder, with {@code volume} turned down and {@code notes} off:
     * a preview is heard, and notes thrown round a wielder reading a screen land on its lens.
     */
    public static void playStep(Level level, Vec3 at, Score score, int step, float volume, boolean notes) {
        for (int row = 0; row < Track.PERCUSSION.rows(); row++) {
            if (score.has(Track.PERCUSSION, row, step)) {
                sound(level, at, kit(row), 0.9F * volume, 1.0F);
                if (notes) {
                    note(level, at, Track.PERCUSSION);
                }
            }
        }
        int bass = score.voiceAt(Track.BASS, step);
        if (bass >= 0) {
            sound(level, at, MagicalSounds.SONG_BASS.get(), 0.85F * volume, Riff.pitchRate(Riff.CENTRE + score.scale().semitone(bass)));
            if (notes) {
                note(level, at, Track.BASS);
            }
        }
        int melody = score.voiceAt(Track.MELODY, step);
        if (melody >= 0) {
            sound(level, at, MagicalSounds.SONG_LEAD.get(), 0.6F * volume, Riff.pitchRate(Riff.CENTRE + score.scale().semitone(melody)));
            if (notes) {
                note(level, at, Track.MELODY);
            }
        }
    }

    /** The kit row, bottom up: kick, snare, hat. */
    public static SoundEvent kit(int row) {
        return switch (row) {
            case 0 -> MagicalSounds.SONG_KICK.get();
            case 1 -> MagicalSounds.SONG_SNARE.get();
            default -> MagicalSounds.SONG_HAT.get();
        };
    }

    private static void sound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y + 1.0D, at.z, sound, SoundSource.RECORDS, volume, pitch, false);
    }

    /** A note of the track colour rising off the performer, one to a sounding track. */
    private static void note(Level level, Vec3 at, Track track) {
        Minecraft minecraft = Minecraft.getInstance();
        ParticleStatus status = minecraft.options.particles().get();
        if (status == ParticleStatus.MINIMAL || minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) > PARTICLE_RANGE_SQR) {
            return;
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double reach = 0.9D + random.nextDouble() * 0.6D;
        double x = at.x + Math.cos(angle) * reach;
        double z = at.z + Math.sin(angle) * reach;
        double y = at.y + 1.2D + random.nextDouble() * 0.9D;
        TintedParticleOptions options = new TintedParticleOptions(MagicalParticles.NOTE.get(), track.rgb(), 1.25F);
        minecraft.particleEngine.createParticle(options, x, y, z, Math.cos(angle) * 0.03D, 0.05D, Math.sin(angle) * 0.03D);
    }
}
