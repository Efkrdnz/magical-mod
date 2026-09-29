package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.passive.ArcanePassives;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.network.DreamStatePayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Dream: a level of plots, one per wielder, where a mind that believed completely is kept until it
 * finds the Flaw. This class owns the level, the plots, the sessions and Lull.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class DreamService {
    public static final ResourceKey<Level> DREAM = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream"));
    /** Every mob a wielder dreams into their plot wears this. */
    public static final String DREAM_TAG = "magical_dream";

    /**
     * Game tests only. The vanilla test server discards datapack dimensions, so a test lays the plots
     * out in its own level. They start a million blocks out, and {@link #isDream} only counts the plot
     * grid there, so nothing near a test template is ever a dream.
     */
    static ServerLevel testLevel;

    private DreamService() {}

    public static ServerLevel dreamLevel(MinecraftServer server) {
        if (testLevel != null && testLevel.getServer() == server) {
            return testLevel;
        }
        return server.getLevel(DREAM);
    }

    public static boolean isDream(Level level, double x, double z) {
        if (level.dimension().equals(DREAM)) {
            return true;
        }
        return level == testLevel && DreamRules.plotAt(x, z) >= 0;
    }

    public static boolean isDream(Level level, BlockPos pos) {
        return isDream(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean isDream(Entity entity) {
        return isDream(entity.level(), entity.getX(), entity.getZ());
    }

    /** The owner's Dreamscape, claimed and floored the first time anybody asks for it. */
    static Dreamscape dreamscape(ServerLevel dream, UUID owner) {
        DreamPlots plots = DreamPlots.of(dream);
        Dreamscape existing = plots.get(owner);
        if (existing != null) {
            return existing;
        }
        Dreamscape claimed = plots.claim(owner);
        floor(dream, claimed.plot());
        return claimed;
    }

    static BlockPos at(int plot, Offset offset) {
        Offset origin = DreamRules.origin(plot);
        return new BlockPos(origin.dx() + offset.dx(), origin.dy() + offset.dy(), origin.dz() + offset.dz());
    }

    static Offset offsetIn(int plot, BlockPos pos) {
        Offset origin = DreamRules.origin(plot);
        return new Offset(pos.getX() - origin.dx(), pos.getY() - origin.dy(), pos.getZ() - origin.dz());
    }

    private static final Map<UUID, DreamSession> SESSIONS = new LinkedHashMap<>();
    /**
     * How long a blow on the body waits for its dreamer to finish crossing back into it. A player
     * changing dimension is invulnerable to everything until their client acknowledges the move, so
     * a blow landed in the same call as the wake would be refused without a word.
     */
    static final int HURT_WAIT_TICKS = 100;
    private static final Map<UUID, PendingHurt> PENDING_HURTS = new LinkedHashMap<>();

    /** A blow the body took, owed to its dreamer once they can take it. */
    record PendingHurt(DamageSource source, float amount, long expiresAt) {}

    static PendingHurt pendingHurt(UUID dreamer) {
        return PENDING_HURTS.get(dreamer);
    }

    static DreamSession session(UUID dreamer) {
        return SESSIONS.get(dreamer);
    }

    public static boolean dreaming(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static boolean dreamingOwn(UUID player) {
        DreamSession session = SESSIONS.get(player);
        return session != null && session.own;
    }

    /** Puts a player to sleep: the body stays as a Sleeper where they stand, the mind goes to the owner's plot. */
    static boolean enter(ServerPlayer dreamer, UUID owner, boolean own) {
        if (SESSIONS.containsKey(dreamer.getUUID())) {
            return false;
        }
        ServerLevel dream = dreamLevel(dreamer.server);
        if (dream == null) {
            return false;
        }
        Dreamscape scape = dreamscape(dream, owner);
        dreamer.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.of(dreamer));
        SleeperEntity body = SleeperEntity.of(dreamer);
        dreamer.serverLevel().addFreshEntity(body);
        DreamSession session = new DreamSession(dreamer.getUUID(), owner, own, scape.plot());
        session.sleeperId = body.getId();
        session.sleeperLevel = dreamer.serverLevel().dimension();
        session.sleeperChunk = body.chunkPosition();
        MagicalChunkTickets.DREAM_SLEEPERS.forceChunk(dreamer.serverLevel(), dreamer.getUUID(),
                session.sleeperChunk.x, session.sleeperChunk.z, true, true);
        session.wakesAt = own ? Long.MAX_VALUE : dreamer.server.getTickCount() + DreamRules.DREAM_TICKS;
        SESSIONS.put(dreamer.getUUID(), session);
        BlockPos arrival = at(scape.plot(), scape.arrival());
        if (!move(dreamer, dream, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, scape.arrivalYaw(), 0.0F)) {
            // Something else refused the teleport: nothing of the dream is left behind, and nobody moved.
            SESSIONS.remove(dreamer.getUUID());
            releaseBody(dreamer.server, session);
            dreamer.removeData(MagicalAttachments.DREAM_RETURN);
            return false;
        }
        dream.playSound(null, arrival, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.8F, 0.6F);
        dreamer.displayClientMessage(Component.translatable("message.magical.dream_enter"), true);
        if (own) {
            sendState(dreamer);
        }
        return true;
    }

    /**
     * Back into the body: where it lay, the body gone, any blow it took landed on the dreamer. Works
     * from anywhere, so a dreamer who left the dream by some other route wakes the same way.
     */
    static void wake(ServerPlayer dreamer) {
        DreamSession session = SESSIONS.get(dreamer.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel dream = dreamLevel(dreamer.server);
        if (session.own && dream != null && dreamer.level() == dream
                && DreamRules.inside(session.plot, dreamer.getX(), dreamer.getY(), dreamer.getZ())) {
            dreamscape(dream, session.owner).setArrival(offsetIn(session.plot, dreamer.blockPosition()), dreamer.getYRot());
            DreamPlots.of(dream).changed();
        }
        calm(dreamer);
        if (!sendHome(dreamer)) {
            // The move was refused. Nothing is let go - not the session, not the return point, not the
            // body - so the next server tick tries again rather than leaving them in the dream with neither.
            session.wakeNow = true;
            return;
        }
        SESSIONS.remove(dreamer.getUUID());
        dreamer.removeData(MagicalAttachments.DREAM_RETURN);
        releaseBody(dreamer.server, session);
        if (session.own) {
            sendState(dreamer);
        }
        dreamer.serverLevel().playSound(null, dreamer.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.7F);
        dreamer.displayClientMessage(Component.translatable("message.magical.dream_woke"), true);
        if (session.hurt != null) {
            PENDING_HURTS.put(dreamer.getUUID(),
                    new PendingHurt(session.hurt, session.hurtAmount, dreamer.server.getTickCount() + HURT_WAIT_TICKS));
        }
    }

    /**
     * What a dream did to a body stops when the dreamer wakes. The dream let them down to one heart
     * and no further, so anything still running on them - fire, frost, a harmful effect, Levitation,
     * a status of the mod's (none of which says whether it harms, so all of them), a fall in progress -
     * would otherwise finish in the waking world what the dream was not allowed to.
     */
    static void calm(ServerPlayer dreamer) {
        dreamer.clearFire();
        dreamer.setTicksFrozen(0);
        for (MobEffectInstance effect : List.copyOf(dreamer.getActiveEffects())) {
            Holder<MobEffect> kind = effect.getEffect();
            if (kind.value().getCategory() == MobEffectCategory.HARMFUL || kind.value() == MobEffects.LEVITATION.value()) {
                dreamer.removeEffect(kind);
            }
        }
        for (MagicStatus status : MagicStatus.values()) {
            if (MagicStatusService.has(dreamer, status)) {
                MagicStatusService.clear(dreamer, status);
            }
        }
        dreamer.fallDistance = 0.0F;
    }

    /** A player the server has no session for but who is still owed a way home: sent back to where they lay. */
    static void recover(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID()) || !player.hasData(MagicalAttachments.DREAM_RETURN)) {
            return;
        }
        if (sendHome(player)) {
            player.removeData(MagicalAttachments.DREAM_RETURN);
        }
    }

    /**
     * Back to where they lay, or to the overworld's shared spawn when nothing usable remembers it: no
     * return point, a level that is gone, coordinates that would not load, or a point that is itself in
     * the dream. True once they are there; the return point is left for the caller to let go.
     */
    private static boolean sendHome(ServerPlayer player) {
        // Every road home ends what the dream was doing to the body, a login's recovery and a rescue as much as a wake.
        calm(player);
        if (player.hasData(MagicalAttachments.DREAM_RETURN)) {
            DreamReturn back = player.getData(MagicalAttachments.DREAM_RETURN);
            ServerLevel level = back.level(player.server);
            if (level != null && !isDream(level, back.x(), back.z())) {
                return move(player, level, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
            }
        }
        ServerLevel home = player.server.overworld();
        BlockPos spawn = home.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getSharedSpawnPos());
        return move(player, home, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, home.getSharedSpawnAngle(), 0.0F);
    }

    /** How often the dream level is searched for players in it with no dream to be in. */
    static final int RESCUE_INTERVAL_TICKS = 20;

    /**
     * Anybody in the dream the server has no session for is sent home: a wake whose move failed and
     * was then lost, a move this class did not make, anything. Nobody is ever kept in a dream that
     * nothing is running, where a death would cost them everything they carry.
     */
    static void rescueStranded(MinecraftServer server) {
        ServerLevel dream = dreamLevel(server);
        if (dream == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(dream.players())) {
            if (!player.isAlive() || visitor(player) || SESSIONS.containsKey(player.getUUID()) || !isDream(player)) {
                continue;
            }
            if (sendHome(player)) {
                player.removeData(MagicalAttachments.DREAM_RETURN);
            }
        }
    }

    /**
     * Whether moving this player to there would put them in a dream nobody sent them into. Only a
     * dreamer, who already has a session, may be moved to a place in the dream by anything else.
     */
    public static boolean refusesEntry(ServerPlayer mover, Level level, double x, double z) {
        return isDream(level, x, z) && !SESSIONS.containsKey(mover.getUUID());
    }

    /**
     * A player in creative or spectator is a visitor, not a dreamer: an operator may look round the
     * dream level by command, is never sent home from it, and stops being one on leaving the mode.
     */
    static boolean visitor(ServerPlayer player) {
        return player.isCreative() || player.isSpectator();
    }

    /**
     * Whether the teleport into the dream would be allowed, asked before anything is billed: the same
     * event the teleport itself posts, put to every listener with the door held open as a real move holds it.
     */
    static boolean canEnter(ServerPlayer dreamer, ServerLevel dream) {
        if (UnwakingCapabilities.refuseTravel(dreamer, dream.dimension())) {
            return false;
        }
        EntityTravelToDimensionEvent ask = new EntityTravelToDimensionEvent(dreamer, dream.dimension());
        entering = true;
        try {
            NeoForge.EVENT_BUS.post(ask);
        } finally {
            entering = false;
        }
        return !ask.isCanceled();
    }

    /** Whether this body is the one a live dream left lying; any other body is an orphan. */
    public static boolean isLiveBody(SleeperEntity body) {
        DreamSession session = body.dreamer().map(SESSIONS::get).orElse(null);
        return session != null && session.sleeperId == body.getId();
    }

    /** Set only while this class moves a player, so the one door into the dream level is its own. */
    private static boolean entering;

    /**
     * Nothing enters the dream level but by this class: a command, a portal, another mod's teleport
     * are all refused. NeoForge posts this for every teleport, a move within one level included, so a
     * move that starts in the dream level is left alone - it is not an entry.
     */
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getDimension().equals(DREAM) && !entering && !event.getEntity().level().dimension().equals(DREAM)
                && !(event.getEntity() instanceof ServerPlayer player && visitor(player))) {
            event.setCanceled(true);
        }
    }

    /** A blow on a body. With a dreamer to wake, it wakes them; a body with no dreamer is only a shape, and goes. */
    public static void sleeperStruck(SleeperEntity body, ServerLevel level, DamageSource source, float amount) {
        DreamSession session = body.dreamer().map(SESSIONS::get).orElse(null);
        if (session == null || session.sleeperId != body.getId()) {
            body.discard();
            return;
        }
        if (!source.is(DamageTypes.GENERIC_KILL)) {
            session.hurt = source;
            session.hurtAmount = amount;
        }
        session.wakeNow = true;
    }

    /** A click on a block in a dream: the Flaw wakes whoever found it. */
    static void touched(ServerPlayer player, BlockPos pos) {
        DreamSession session = SESSIONS.get(player.getUUID());
        ServerLevel dream = dreamLevel(player.server);
        if (session == null || session.own || dream == null) {
            return;
        }
        Dreamscape.Flaw flaw = dreamscape(dream, session.owner).flaw();
        if (flaw != null && flaw.block() != null && at(session.plot, flaw.block()).equals(pos)) {
            session.wakeNow = true;
        }
    }

    /** A blow on, or a hand laid on, a creature in a dream: the Flaw wakes whoever found it. */
    static void touched(ServerPlayer player, Entity target) {
        DreamSession session = SESSIONS.get(player.getUUID());
        ServerLevel dream = dreamLevel(player.server);
        if (session == null || session.own || dream == null) {
            return;
        }
        Dreamscape.Flaw flaw = dreamscape(dream, session.owner).flaw();
        if (flaw != null && target.getUUID().equals(flaw.figment())) {
            session.wakeNow = true;
        }
    }

    /** A dreamed creature died: if it was its plot's Flaw, the plot has none now. */
    static void figmentGone(ServerLevel dream, Entity figment) {
        int plot = DreamRules.plotAt(figment.getX(), figment.getZ());
        UUID owner = plot < 0 ? null : DreamPlots.of(dream).ownerOf(plot);
        if (owner != null && dreamscape(dream, owner).clearIfFlaw(figment.getUUID())) {
            DreamPlots.of(dream).changed();
            ServerPlayer online = dream.getServer().getPlayerList().getPlayer(owner);
            if (online != null) {
                sendState(online);
            }
        }
    }

    /** To the wielder: whether they are in their own dream, and its Flaw, so Daydream can show it. */
    static void sendState(ServerPlayer player) {
        DreamSession session = SESSIONS.get(player.getUUID());
        ServerLevel dream = dreamLevel(player.server);
        if (session == null || !session.own || dream == null) {
            MagicalNetwork.sendDreamState(player, new DreamStatePayload(false, Optional.empty(), -1));
            return;
        }
        Dreamscape.Flaw flaw = dreamscape(dream, session.owner).flaw();
        Optional<BlockPos> block = flaw != null && flaw.block() != null ? Optional.of(at(session.plot, flaw.block())) : Optional.empty();
        int entity = -1;
        if (flaw != null && flaw.figment() != null) {
            Entity figment = dream.getEntity(flaw.figment());
            entity = figment == null ? -1 : figment.getId();
        }
        MagicalNetwork.sendDreamState(player, new DreamStatePayload(true, block, entity));
    }

    /** How often the dream level is swept for creatures that have wandered out of their plots. */
    static final int SWEEP_INTERVAL_TICKS = 20;

    /**
     * A dreamed creature belongs to its plot: one that has wandered out of the bounds is set back at the
     * plot's arrival, and one that has left the grid altogether is unmade. Without this a Flaw could
     * walk out of any dreamer's reach, and a figment outside the box would escape the cap.
     */
    static void sweepFigments(ServerLevel dream) {
        for (Entity entity : List.copyOf(iterate(dream))) {
            if (!(entity instanceof Mob mob) || mob.isRemoved() || !mob.getTags().contains(DREAM_TAG)) {
                continue;
            }
            int plot = DreamRules.plotAt(mob.getX(), mob.getZ());
            UUID owner = plot < 0 ? null : DreamPlots.of(dream).ownerOf(plot);
            if (owner == null) {
                mob.discard();
                continue;
            }
            if (!DreamRules.inside(plot, mob.getX(), mob.getY(), mob.getZ())) {
                Dreamscape scape = dreamscape(dream, owner);
                BlockPos arrival = at(plot, scape.arrival());
                mob.moveTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, mob.getYRot(), 0.0F);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.getNavigation().stop();
            }
        }
    }

    private static List<Entity> iterate(ServerLevel dream) {
        List<Entity> all = new java.util.ArrayList<>();
        dream.getAllEntities().forEach(all::add);
        return all;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel swept = dreamLevel(event.getServer());
        if (swept != null && event.getServer().getTickCount() % SWEEP_INTERVAL_TICKS == 0) {
            sweepFigments(swept);
        }
        if (event.getServer().getTickCount() % RESCUE_INTERVAL_TICKS == 0) {
            rescueStranded(event.getServer());
        }
        if (SESSIONS.isEmpty() && PENDING_HURTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dream = dreamLevel(server);
        for (DreamSession session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.dreamer);
            if (player == null) {
                SESSIONS.remove(session.dreamer);
                releaseBody(server, session);
                continue;
            }
            // Left the dream level by some other route (a skill, a command): the dream is simply over.
            if (dream == null || player.level() != dream || session.wakeNow || server.getTickCount() >= session.wakesAt) {
                wake(player);
                continue;
            }
            Dreamscape scape = dreamscape(dream, session.owner);
            if (!DreamRules.inside(session.plot, player.getX(), player.getY(), player.getZ())) {
                BlockPos arrival = at(scape.plot(), scape.arrival());
                move(player, dream, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, scape.arrivalYaw(), 0.0F);
                continue;
            }
            if (!session.own && touchesFlaw(dream, player, scape)) {
                wake(player);
            }
        }
        landPendingHurts(server);
    }

    /** Lands each blow owed to a woken dreamer once they have finished crossing back; drops it if they never do. */
    private static void landPendingHurts(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingHurt>> owed = PENDING_HURTS.entrySet().iterator();
        while (owed.hasNext()) {
            Map.Entry<UUID, PendingHurt> entry = owed.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || server.getTickCount() >= entry.getValue().expiresAt()) {
                owed.remove();
                continue;
            }
            if (player.isChangingDimension() || !player.hasClientLoaded()) {
                continue;
            }
            owed.remove();
            player.hurtServer(player.serverLevel(), entry.getValue().source(), entry.getValue().amount());
        }
    }

    /** A dream cannot kill: a blow that would leave less than a heart leaves exactly one, and wakes them. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DreamSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        float health = player.getHealth();
        float incoming = event.getNewDamage();
        if (DreamRules.wakes(health, incoming)) {
            event.setNewDamage(DreamRules.dealt(health, incoming));
            session.wakeNow = true;
        }
    }

    /** What gets past the damage event (a /kill, the void) still does not kill a dreamer. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            event.setCanceled(true);
            player.setHealth(DreamRules.ONE_HEART);
            SESSIONS.get(player.getUUID()).wakeNow = true;
        }
    }

    /** A blow owed to a body is owed to that life: a death, and the respawn after it, write it off. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDied(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_HURTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PENDING_HURTS.remove(event.getEntity().getUUID());
    }

    /**
     * A dreamed creature that turns into another (a tadpole grown, a villager struck) is still the
     * same thing in the dream: if it was the Flaw, the Flaw is what it became. Vanilla carries its
     * tags across, so the new one is dreamed too.
     */
    @SubscribeEvent
    public static void onConverted(LivingConversionEvent.Post event) {
        LivingEntity before = event.getEntity();
        if (!(before.level() instanceof ServerLevel level) || level != dreamLevel(level.getServer()) || !isDream(before)) {
            return;
        }
        int plot = DreamRules.plotAt(before.getX(), before.getZ());
        UUID owner = plot < 0 ? null : DreamPlots.of(level).ownerOf(plot);
        if (owner == null) {
            return;
        }
        Dreamscape scape = dreamscape(level, owner);
        if (!scape.clearIfFlaw(before.getUUID())) {
            return;
        }
        scape.markFigment(event.getOutcome().getUUID());
        DreamPlots.of(level).changed();
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null) {
            sendState(online);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSession session = SESSIONS.remove(player.getUUID());
            if (session != null) {
                releaseBody(player.server, session);
            }
            PENDING_HURTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recover(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (DreamSession session : SESSIONS.values()) {
            releaseBody(event.getServer(), session);
        }
        SESSIONS.clear();
        PENDING_HURTS.clear();
    }

    private static boolean touchesFlaw(ServerLevel dream, ServerPlayer player, Dreamscape scape) {
        Dreamscape.Flaw flaw = scape.flaw();
        if (flaw == null) {
            return false;
        }
        AABB reach = player.getBoundingBox().inflate(0.05);
        if (flaw.block() != null) {
            BlockPos pos = at(scape.plot(), flaw.block());
            return !dream.getBlockState(pos).isAir() && reach.intersects(new AABB(pos));
        }
        Entity figment = dream.getEntity(flaw.figment());
        return figment != null && reach.intersects(figment.getBoundingBox());
    }

    /** The body goes and its chunk is let go: every way a session ends comes through here. */
    private static void releaseBody(MinecraftServer server, DreamSession session) {
        ServerLevel level = session.sleeperLevel == null ? null : server.getLevel(session.sleeperLevel);
        if (level == null) {
            return;
        }
        if (level.getEntity(session.sleeperId) instanceof SleeperEntity body) {
            body.discard();
        }
        ChunkPos chunk = session.sleeperChunk;
        if (chunk != null) {
            MagicalChunkTickets.DREAM_SLEEPERS.forceChunk(level, session.dreamer, chunk.x, chunk.z, false, true);
        }
    }

    /** Every move this class makes; false when the teleport was refused and the player has not moved. */
    private static boolean move(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        boolean moved;
        entering = true;
        try {
            moved = player.teleportTo(level, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, true);
        } finally {
            entering = false;
        }
        if (moved) {
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
        }
        return moved;
    }

    private static void floor(ServerLevel dream, int plot) {
        for (int x = -DreamRules.PLATFORM_HALF; x <= DreamRules.PLATFORM_HALF; x++) {
            for (int z = -DreamRules.PLATFORM_HALF; z <= DreamRules.PLATFORM_HALF; z++) {
                dream.setBlock(at(plot, new Offset(x, -1, z)), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** How far off the crosshair a body may stand and still be the one Lull means. */
    static final double LULL_TOLERANCE = 0.5;

    /**
     * The Lull press. In your own dream, it wakes you. Sneaking, it takes you into your own dream, free.
     * Aimed at a mob that is sure of your scene, the mob sleeps; at a player who is, they fall into your
     * Dreamscape, if it has a Flaw. Billed only once there is something to do.
     */
    public static boolean lull(ServerPlayer wielder, PlayerMagicState state) {
        return lull(wielder, state, wielder.isShiftKeyDown());
    }

    /** As {@link #lull(ServerPlayer, PlayerMagicState)}, with the sneak the cast pipeline saw. */
    public static boolean lull(ServerPlayer wielder, PlayerMagicState state, boolean sneak) {
        DreamSession mine = SESSIONS.get(wielder.getUUID());
        if (mine != null) {
            if (mine.own) {
                wake(wielder);
                return true;
            }
            return false;
        }
        if (isDream(wielder)) {
            return false;
        }
        if (sneak) {
            if (!wielder.onGround()) {
                wielder.displayClientMessage(Component.translatable("message.magical.lull_not_grounded"), true);
                return false;
            }
            return enterOwn(wielder);
        }
        ServerLevel level = wielder.serverLevel();
        AimResolver.Result aim = AimResolver.resolve(level, wielder, MindService.UNVEIL_REACH, LULL_TOLERANCE, false);
        LivingEntity target = aim.living();
        if (!(target instanceof Mob) && !(target instanceof ServerPlayer)) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_nobody"), true);
            return false;
        }
        if (!sure(wielder.getUUID(), target)) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_unsure"), true);
            return false;
        }
        if (target instanceof Mob mob) {
            // A boss is too great a mind to be put to sleep by a lie; refused before anything is billed.
            if (mob.getType().is(Tags.EntityTypes.BOSSES)) {
                wielder.displayClientMessage(Component.translatable("message.magical.lull_boss"), true);
                return false;
            }
            // Refuse for nothing what ASLEEP would not take: a boss that resists control, a Null Field, a sleeper.
            if (UnwakingCapabilities.refuseControl(wielder, mob) || ArcanePassives.blocksStatus(mob)) {
                return false;
            }
            if (MagicStatusService.has(mob, MagicStatus.ASLEEP)) {
                wielder.displayClientMessage(Component.translatable("message.magical.lull_asleep"), true);
                return false;
            }
            if (!MindService.payFor(wielder, state, MagicContent.LULL, DreamRules.LULL_MANA)) {
                return false;
            }
            MagicStatusService.apply(mob, MagicStatus.ASLEEP, DreamRules.MOB_SLEEP_TICKS, MagicContent.LULL.id(), wielder);
            level.sendParticles(new DustParticleOptions(0xBDA4FF, 1.2F), mob.getX(), mob.getEyeY() + 0.4, mob.getZ(),
                    12, 0.3, 0.2, 0.3, 0.0);
            state.sync(wielder);
            return true;
        }
        ServerPlayer dreamer = (ServerPlayer) target;
        if (SESSIONS.containsKey(dreamer.getUUID())) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_dreaming"), true);
            return false;
        }
        ServerLevel dream = dreamLevel(wielder.server);
        if (dream == null) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_no_dream"), true);
            return false;
        }
        if (!flawStands(dream, dreamscape(dream, wielder.getUUID()))) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_no_flaw"), true);
            return false;
        }
        if (!canEnter(dreamer, dream)) {
            wielder.displayClientMessage(Component.translatable("message.magical.dream_refused"), true);
            return false;
        }
        int manaBefore = state.mana();
        if (!MindService.payFor(wielder, state, MagicContent.LULL, DreamRules.LULL_MANA)) {
            return false;
        }
        if (!enter(dreamer, wielder.getUUID(), false)) {
            // Refused after it was asked and allowed: the bill is taken back, never paid out as more than it took.
            state.setMana(Math.max(state.mana(), manaBefore));
            state.setSkillCooldown(MagicContent.LULL.id(), 0);
            wielder.displayClientMessage(Component.translatable("message.magical.dream_refused"), true);
            state.sync(wielder);
            return false;
        }
        state.sync(wielder);
        return true;
    }

    /** Into your own dream, to build it: free, no clock, and no Flaw needed yet. */
    static boolean enterOwn(ServerPlayer wielder) {
        if (isDream(wielder)) {
            // Already in a dream: the return point would be taken inside it, and the way out lost.
            return false;
        }
        if (dreamLevel(wielder.server) == null) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_no_dream"), true);
            return false;
        }
        if (!canEnter(wielder, dreamLevel(wielder.server)) || !enter(wielder, wielder.getUUID(), true)) {
            wielder.displayClientMessage(Component.translatable("message.magical.dream_refused"), true);
            return false;
        }
        return true;
    }

    /** Whether the viewer believes some live element of the wielder's, in its own level, at 0.8 or more. */
    static boolean sure(UUID owner, LivingEntity viewer) {
        for (LiveScene scene : MindService.scenesOf(owner)) {
            if (!scene.dimension().equals(viewer.level().dimension())) {
                continue;
            }
            for (LiveScene.Element element : scene.elements()) {
                if (scene.belief().get(viewer.getId(), element.index()) >= DreamRules.LULL_BELIEF) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A Flaw that is still there: a block not yet air, or a figment not yet dead (a dead one clears itself). */
    static boolean flawStands(ServerLevel dream, Dreamscape scape) {
        Dreamscape.Flaw flaw = scape.flaw();
        if (flaw == null) {
            return false;
        }
        return flaw.figment() != null || !dream.getBlockState(at(scape.plot(), flaw.block())).isAir();
    }
}
