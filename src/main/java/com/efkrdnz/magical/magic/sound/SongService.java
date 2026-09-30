package com.efkrdnz.magical.magic.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Song, performed: one loop per wielder, played where they stand for as long as they can pay.
 *
 * <p>The server never plays a note. It keeps the clock - the tick step 0 fell on - and hands the
 * score and that tick to every player close enough to hear, and each client plays the loop itself
 * against its own copy of the game time. That is what makes the music exact on every screen at once
 * and costs one packet per listener per performance rather than one per note.
 *
 * <p>What the server does do is judge. The wielder acts in time with what they hear, their client
 * names the note it acted on, and {@link SongRun#claim} believes it only if that note is really there
 * and really now. An action on the beat gives what its track gives ({@link SongEffect}); a run of
 * eight makes everything in earshot dance for a bar. Upkeep is billed a bar at a time, and a bar that
 * cannot be paid for ends the Song.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class SongService {

    /** How far the Song is heard, and so how far its swings reach. */
    public static final double EARSHOT = 16.0D;
    /** Mana a bar, before the wielder's cost scale. */
    public static final int UPKEEP_PER_BAR = 4;
    /** Ticks between the press and step 0, so every client holds the score before the first note. */
    public static final int LEAD_IN = 4;

    /** Players this near are sent the score: past earshot, so a walk toward it never cuts into a bar. */
    private static final double AUDIENCE = EARSHOT + 24.0D;
    private static final ResourceLocation ROOTED = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "song_rooted");
    private static final Map<UUID, Performance> LIVE = new HashMap<>();

    static final class Performance {
        final int ownerId;
        final ResourceKey<Level> dimension;
        final Set<UUID> audience = new HashSet<>();
        final Map<Integer, Long> dancers = new HashMap<>();
        SongRun run;
        long nextUpkeep;
        long rootedUntil = Long.MIN_VALUE;

        Performance(int ownerId, ResourceKey<Level> dimension, SongRun run) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.run = run;
        }
    }

    private SongService() {}

    public static boolean holds(PlayerMagicState state) {
        return state.hasAuthority(AuthorityContent.SOUND);
    }

    public static boolean playing(ServerPlayer player) {
        return LIVE.containsKey(player.getUUID());
    }

    /** The performance a wielder is giving, or null. */
    public static SongRun run(ServerPlayer player) {
        Performance performance = LIVE.get(player.getUUID());
        return performance == null ? null : performance.run;
    }

    public static void openScore(ServerPlayer player, PlayerMagicState state) {
        if (!holds(state)) {
            say(player, "message.magical.sound_required");
            return;
        }
        MagicalNetwork.sendOpenScore(player);
    }

    /** The Song key: start the loop, or end it. Ending is free and never refused. */
    public static void toggle(ServerPlayer player, PlayerMagicState state) {
        if (playing(player)) {
            stop(player, "message.magical.song_stopped");
            return;
        }
        start(player, state);
    }

    public static boolean start(ServerPlayer player, PlayerMagicState state) {
        if (!holds(state) || !state.hasUnlocked(MagicContent.SONG.id())) {
            say(player, "message.magical.sound_required");
            return false;
        }
        Score score = state.sound().song();
        if (score.isEmpty()) {
            say(player, "message.magical.song_empty");
            return false;
        }
        if (!payFor(player, state, MagicContent.SONG)) {
            return false;
        }
        begin(player, score);
        state.sync(player);
        return true;
    }

    /** A new performance of this score from the top, sent to everyone in hearing again. */
    private static void begin(ServerPlayer player, Score score) {
        long start = player.level().getGameTime() + LEAD_IN;
        Performance performance = new Performance(player.getId(), player.level().dimension(), new SongRun(score, start));
        performance.nextUpkeep = start + score.tempo().barTicks();
        Performance before = LIVE.put(player.getUUID(), performance);
        if (before != null) {
            performance.audience.addAll(before.audience);
            performance.rootedUntil = before.rootedUntil;
        }
        refreshAudience(player, performance, true);
    }

    public static void stop(ServerPlayer player, String reason) {
        Performance performance = LIVE.remove(player.getUUID());
        if (performance == null) {
            return;
        }
        unroot(player);
        silence(player.server, performance);
        if (reason != null) {
            say(player, reason);
        }
    }

    private static void silence(MinecraftServer server, Performance performance) {
        for (UUID id : performance.audience) {
            ServerPlayer listener = server.getPlayerList().getPlayer(id);
            if (listener != null) {
                MagicalNetwork.sendSongStop(listener, performance.ownerId);
            }
        }
    }

    /**
     * The Score screen's save. Both halves are read back through the same repair a save gets, so a
     * forged packet cannot write a grid the editor would refuse. Rewriting the Song while it plays
     * starts it again from the top without a second bill: the performance was already paid for.
     */
    public static void write(ServerPlayer player, CompoundTag data) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!holds(state) || data == null) {
            return;
        }
        if (data.contains("song")) {
            Score score = SoundState.loadSong(data.getCompound("song"));
            state.sound().setSong(score);
            if (playing(player)) {
                if (score.isEmpty()) {
                    stop(player, "message.magical.song_stopped");
                } else {
                    begin(player, score);
                }
            }
        }
        if (data.contains("riff")) {
            state.sound().setRiff(SoundState.loadRiff(data.getCompound("riff")));
        }
        state.sync(player);
    }

    /** A client names the note it acted on, or -1 for an action on no note at all. */
    public static SongRun.Judgement claim(ServerPlayer player, SongAction action, long index) {
        Performance performance = LIVE.get(player.getUUID());
        if (performance == null || action == null) {
            return null;
        }
        long now = player.level().getGameTime();
        SongRun.Judgement judgement = index < 0 ? performance.run.miss() : performance.run.claim(action, index, now);
        answer(player, performance, action, judgement);
        return judgement;
    }

    /** Judged on the server clock alone: what an action seen at this very tick lands on. */
    public static SongRun.Judgement act(ServerPlayer player, SongAction action) {
        Performance performance = LIVE.get(player.getUUID());
        if (performance == null || action == null) {
            return null;
        }
        SongRun.Judgement judgement = performance.run.act(action, player.level().getGameTime());
        answer(player, performance, action, judgement);
        return judgement;
    }

    /** The note nearest now, as a well-timed client would name it; for commands and tests. */
    public static long nearestNote(ServerPlayer player) {
        Performance performance = LIVE.get(player.getUUID());
        if (performance == null) {
            return -1L;
        }
        long now = player.level().getGameTime();
        SongRun run = performance.run;
        long around = SongRun.indexAt(run.start(), run.score().tempo(), now);
        long best = -1L;
        long bestDistance = Long.MAX_VALUE;
        for (long index = Math.max(0L, around - Score.BAR); index <= around + Score.BAR; index++) {
            long distance = Math.abs(SongRun.tickOf(run.start(), run.score().tempo(), index) - now);
            if (run.score().sounds((int) Math.floorMod(index, (long) Score.STEPS)) && distance < bestDistance) {
                best = index;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void answer(ServerPlayer player, Performance performance, SongAction action, SongRun.Judgement judgement) {
        switch (judgement.kind()) {
            case ON_BEAT -> {
                for (Track track : judgement.tracks()) {
                    give(player, performance, SongEffect.of(track, action), judgement.streak());
                    burst(player, track);
                }
                if (judgement.dance()) {
                    dance(player, performance);
                }
                player.displayClientMessage(Component.translatable(
                        judgement.dance() ? "message.magical.song_dance" : "message.magical.song_streak",
                        judgement.streak(), SongRun.MAX_STREAK), true);
            }
            case OFF_BEAT -> player.displayClientMessage(Component.translatable("message.magical.song_off_beat"), true);
            default -> {
            }
        }
    }

    static void give(ServerPlayer player, Performance performance, SongEffect effect, int streak) {
        ServerLevel level = player.serverLevel();
        int bar = performance.run.score().tempo().barTicks();
        int amplifier = SongEffect.amplifier(streak);
        switch (effect) {
            case BULWARK -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, bar, amplifier, false, false, true));
            case ROOTED -> {
                root(player);
                performance.rootedUntil = level.getGameTime() + bar;
            }
            case MEND -> player.heal(SongEffect.mend(streak));
            case STAGGER -> {
                for (LivingEntity enemy : enemies(player)) {
                    SkillTargets.shove(enemy, player.position(), SongEffect.stagger(streak), 0.15D);
                    enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 3, false, false, true));
                    mark(level, enemy, Track.PERCUSSION);
                }
            }
            case WEIGHT -> {
                for (LivingEntity enemy : enemies(player)) {
                    enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, bar, 1 + amplifier, false, false, true));
                    mark(level, enemy, Track.BASS);
                }
            }
            case DISSONANCE -> {
                for (LivingEntity enemy : enemies(player)) {
                    enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, bar, amplifier, false, false, true));
                    mark(level, enemy, Track.MELODY);
                }
            }
        }
    }

    /** Everything in earshot, rooted in place and unable to land a blow for a bar, turning to the Song. */
    static void dance(ServerPlayer player, Performance performance) {
        int bar = performance.run.score().tempo().barTicks();
        long until = player.level().getGameTime() + bar;
        for (LivingEntity enemy : enemies(player)) {
            performance.dancers.put(enemy.getId(), until);
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, bar, 6, false, false, true));
            enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, bar, 4, false, false, true));
            if (enemy instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
    }

    /** For tests: make everything in earshot dance now, as a full streak would. */
    static void danceNow(ServerPlayer player) {
        Performance performance = LIVE.get(player.getUUID());
        if (performance != null) {
            dance(player, performance);
        }
    }

    static boolean dancing(ServerPlayer player, Entity entity) {
        Performance performance = LIVE.get(player.getUUID());
        return performance != null && performance.dancers.containsKey(entity.getId());
    }

    private static List<LivingEntity> enemies(ServerPlayer player) {
        return SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), EARSHOT);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (UUID id : List.copyOf(LIVE.keySet())) {
            Performance performance = LIVE.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                LIVE.remove(id);
                silence(server, performance);
                continue;
            }
            tick(player, performance);
        }
    }

    private static void tick(ServerPlayer player, Performance performance) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!player.isAlive() || !holds(state) || player.level().dimension() != performance.dimension) {
            stop(player, null);
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now >= performance.nextUpkeep) {
            if (!spend(player, state, MagicContent.SONG, UPKEEP_PER_BAR)) {
                stop(player, "message.magical.song_out_of_breath");
                return;
            }
            performance.nextUpkeep += performance.run.score().tempo().barTicks();
            state.sync(player);
        }
        if (performance.rootedUntil != Long.MIN_VALUE && now >= performance.rootedUntil) {
            unroot(player);
            performance.rootedUntil = Long.MIN_VALUE;
        }
        danceStep(level, performance, now);
        if (now % 10 == 0) {
            refreshAudience(player, performance, false);
        }
    }

    /** A dancer turns a little every tick and hops on every beat; a player only keeps the effects. */
    private static void danceStep(ServerLevel level, Performance performance, long now) {
        Iterator<Map.Entry<Integer, Long>> dancers = performance.dancers.entrySet().iterator();
        int beat = performance.run.score().tempo().ticksPerStep() * 4;
        while (dancers.hasNext()) {
            Map.Entry<Integer, Long> entry = dancers.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity dancer) || !dancer.isAlive() || now > entry.getValue()) {
                dancers.remove();
                continue;
            }
            if (now % 4 == 0) {
                mark(level, dancer, Track.values()[(int) Math.floorMod(now / 4, (long) Track.values().length)]);
            }
            if (dancer instanceof Player) {
                continue;
            }
            if (dancer instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            float turn = dancer.getYRot() + 24.0F;
            dancer.setYRot(turn);
            dancer.setYHeadRot(turn);
            dancer.setYBodyRot(turn);
            if (dancer.onGround() && Math.floorMod(now - performance.run.start(), (long) beat) == 0) {
                dancer.setDeltaMovement(dancer.getDeltaMovement().x, 0.32D, dancer.getDeltaMovement().z);
                dancer.hurtMarked = true;
            }
        }
    }

    /** Sends the score to anyone who has come into hearing, and silence to anyone who has left it. */
    private static void refreshAudience(ServerPlayer owner, Performance performance, boolean everyone) {
        CompoundTag song = SoundState.saveSong(performance.run.score());
        Set<UUID> hearing = new HashSet<>();
        for (ServerPlayer listener : owner.serverLevel().players()) {
            if (listener == owner || listener.distanceToSqr(owner) <= AUDIENCE * AUDIENCE) {
                hearing.add(listener.getUUID());
                if (everyone || !performance.audience.contains(listener.getUUID())) {
                    MagicalNetwork.sendSongPlay(listener, owner.getId(), performance.run.start(), song);
                }
            }
        }
        for (UUID gone : performance.audience) {
            if (!hearing.contains(gone)) {
                ServerPlayer listener = owner.server.getPlayerList().getPlayer(gone);
                if (listener != null) {
                    MagicalNetwork.sendSongStop(listener, owner.getId());
                }
            }
        }
        performance.audience.clear();
        performance.audience.addAll(hearing);
    }

    private static void root(ServerPlayer player) {
        AttributeInstance resistance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            resistance.addOrUpdateTransientModifier(new AttributeModifier(ROOTED, 1.0D, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void unroot(ServerPlayer player) {
        AttributeInstance resistance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            resistance.removeModifier(ROOTED);
        }
    }

    /** A ring of the track's notes thrown out from the wielder at chest height, clear of their own eyes. */
    private static void burst(ServerPlayer player, Track track) {
        ServerLevel level = player.serverLevel();
        TintedParticleOptions note = new TintedParticleOptions(MagicalParticles.NOTE.get(), track.rgb(), 1.4F);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < 5; i++) {
            double angle = offset + i * Math.PI * 2.0D / 5.0D;
            double x = Math.cos(angle);
            double z = Math.sin(angle);
            level.sendParticles(note, player.getX() + x * 1.6D, player.getY() + 1.0D, player.getZ() + z * 1.6D,
                    0, x * 0.06D, 0.07D, z * 0.06D, 1.0D);
        }
    }

    private static void mark(ServerLevel level, LivingEntity entity, Track track) {
        TintedParticleOptions note = new TintedParticleOptions(MagicalParticles.NOTE.get(), track.rgb(), 1.2F);
        level.sendParticles(note, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3D, entity.getZ(), 0, 0.0D, 0.06D, 0.0D, 1.0D);
    }

    /** Bills a self-managed press by hand: the cast pipeline returns before it charges anything. */
    static boolean payFor(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill) {
        if (state.isSkillOnCooldown(skill.id())) {
            say(player, "message.magical.skill_cooling");
            return false;
        }
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            say(player, "message.magical.not_enough_mana");
            return false;
        }
        state.setSkillCooldown(skill.id(), stats.cooldownTicks());
        return true;
    }

    /** A base price scaled the way the cast would scale it, for bills the pipeline never sees. */
    static boolean spend(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int baseMana) {
        float scale = skill.resolve(state.tuningFor(skill.id())).costScale();
        return MagicSinService.spendManaForSkill(player, state, Math.max(1, Math.round(baseMana * scale)));
    }

    static void say(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, null);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, null);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LIVE.clear();
    }
}
