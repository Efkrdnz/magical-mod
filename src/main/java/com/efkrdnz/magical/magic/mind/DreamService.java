package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        session.wakesAt = own ? Long.MAX_VALUE : dreamer.server.getTickCount() + DreamRules.DREAM_TICKS;
        SESSIONS.put(dreamer.getUUID(), session);
        BlockPos arrival = at(scape.plot(), scape.arrival());
        move(dreamer, dream, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, scape.arrivalYaw(), 0.0F);
        dream.playSound(null, arrival, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.8F, 0.6F);
        dreamer.displayClientMessage(Component.translatable("message.magical.dream_enter"), true);
        return true;
    }

    /**
     * Back into the body: where it lay, the body gone, any blow it took landed on the dreamer. Works
     * from anywhere, so a dreamer who left the dream by some other route wakes the same way.
     */
    static void wake(ServerPlayer dreamer) {
        DreamSession session = SESSIONS.remove(dreamer.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel dream = dreamLevel(dreamer.server);
        if (session.own && dream != null && dreamer.level() == dream
                && DreamRules.inside(session.plot, dreamer.getX(), dreamer.getY(), dreamer.getZ())) {
            dreamscape(dream, session.owner).setArrival(offsetIn(session.plot, dreamer.blockPosition()), dreamer.getYRot());
            DreamPlots.of(dream).changed();
        }
        if (!dreamer.hasData(MagicalAttachments.DREAM_RETURN)) {
            return;
        }
        DreamReturn back = dreamer.getData(MagicalAttachments.DREAM_RETURN);
        dreamer.removeData(MagicalAttachments.DREAM_RETURN);
        ServerLevel home = back.level(dreamer.server);
        discardBody(home, session.sleeperId);
        move(dreamer, home, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
        home.playSound(null, dreamer.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.7F);
        dreamer.displayClientMessage(Component.translatable("message.magical.dream_woke"), true);
        if (session.hurt != null) {
            dreamer.hurtServer(home, session.hurt, session.hurtAmount);
        }
    }

    /** A player the server has no session for but who is still owed a way home: sent back to where they lay. */
    static void recover(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID()) || !player.hasData(MagicalAttachments.DREAM_RETURN)) {
            return;
        }
        DreamReturn back = player.getData(MagicalAttachments.DREAM_RETURN);
        player.removeData(MagicalAttachments.DREAM_RETURN);
        move(player, back.level(player.server), back.x(), back.y(), back.z(), back.yaw(), back.pitch());
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

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dream = dreamLevel(server);
        for (DreamSession session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.dreamer);
            if (player == null) {
                SESSIONS.remove(session.dreamer);
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
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            event.setCanceled(true);
            player.setHealth(DreamRules.ONE_HEART);
            SESSIONS.get(player.getUUID()).wakeNow = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSession session = SESSIONS.remove(player.getUUID());
            if (session != null && player.hasData(MagicalAttachments.DREAM_RETURN)) {
                discardBody(player.getData(MagicalAttachments.DREAM_RETURN).level(player.server), session.sleeperId);
            }
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
        SESSIONS.clear();
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

    private static void discardBody(ServerLevel level, int id) {
        if (level.getEntity(id) instanceof SleeperEntity body) {
            body.discard();
        }
    }

    private static void move(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private static void floor(ServerLevel dream, int plot) {
        for (int x = -DreamRules.PLATFORM_HALF; x <= DreamRules.PLATFORM_HALF; x++) {
            for (int z = -DreamRules.PLATFORM_HALF; z <= DreamRules.PLATFORM_HALF; z++) {
                dream.setBlock(at(plot, new Offset(x, -1, z)), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
