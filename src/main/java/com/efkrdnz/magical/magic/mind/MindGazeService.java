package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Learning the world by looking at it: a wielder of Mind who stands still and stares studies. */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindGazeService {
    public static final double REACH = 16.0;
    public static final double AIM_TOLERANCE = 0.3;
    public static final double STILL_SQR = 0.0025;

    private static final Map<UUID, GazeTracker> TRACKERS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    private MindGazeService() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.MIND)) {
            TRACKERS.remove(id);
            LAST.remove(id);
            return;
        }
        Vec3 now = player.position();
        Vec3 last = LAST.put(id, now);
        boolean still = last != null && last.distanceToSqr(now) < STILL_SQR;
        String done = TRACKERS.computeIfAbsent(id, key -> new GazeTracker()).tick(lookedAt(player.level(), player), still);
        if (done != null) {
            learn(player, state, done);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    /**
     * The key of what the viewer is looking at: the nearest imaginable creature within
     * {@link #AIM_TOLERANCE} of the look ray, else the block the ray strikes (colliders only, so it
     * reads through tall grass), out to {@link #REACH}. The server and the client's eye both call
     * this, so they can disagree only by the tick of latency.
     */
    public static String lookedAt(Level level, LivingEntity viewer) {
        Vec3 from = viewer.getEyePosition();
        Vec3 dir = viewer.getLookAngle();
        Vec3 to = from.add(dir.scale(REACH));
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        boolean struck = blockHit.getType() == HitResult.Type.BLOCK;
        double blockDist = struck ? blockHit.getLocation().distanceTo(from) : REACH;

        Entity best = null;
        double bestAlong = Double.MAX_VALUE;
        AABB sweep = new AABB(from, from.add(dir.scale(blockDist))).inflate(AIM_TOLERANCE);
        for (Entity candidate : level.getEntities(viewer, sweep, e -> e.isAlive() && keyOf(e, null) != null)) {
            Vec3 rel = candidate.getBoundingBox().getCenter().subtract(from);
            double along = rel.dot(dir);
            if (along < 0.0D || along > blockDist + 0.5D) {
                continue;
            }
            double off = rel.subtract(dir.scale(along)).length();
            if (off <= AIM_TOLERANCE + candidate.getBbWidth() * 0.5D && along < bestAlong) {
                bestAlong = along;
                best = candidate;
            }
        }
        if (best != null) {
            return keyOf(best, null);
        }
        return struck ? keyOf(null, level.getBlockState(blockHit.getBlockPos())) : null;
    }

    /** The impression key for what is under the crosshair, or null when it is nothing one can imagine. */
    public static String keyOf(Entity entity, BlockState block) {
        if (entity != null) {
            if (entity instanceof Player || entity instanceof ArmorStand || !(entity instanceof LivingEntity)) {
                return null;
            }
            return Impression.creature(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()).key();
        }
        if (block != null && !block.isAir()) {
            return Impression.block(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()).key();
        }
        return null;
    }

    static void learn(ServerPlayer player, PlayerMagicState state, String key) {
        Lexicon lexicon = state.mind().lexicon();
        int before = lexicon.fidelity(key);
        lexicon.gaze(key);
        int after = lexicon.fidelity(key);
        if (before == 0) {
            player.displayClientMessage(Component.translatable("message.magical.gaze_learned", displayName(key)), true);
        } else if (after > before) {
            player.displayClientMessage(Component.translatable("message.magical.gaze_studied", displayName(key), after), true);
        }
        state.sync(player);
    }

    public static Component displayName(String key) {
        Impression impression = Impression.parse(key);
        ResourceLocation id = impression == null ? null : ResourceLocation.tryParse(impression.id());
        boolean known = id != null && (impression.kind() == Impression.Kind.BLOCK
                ? BuiltInRegistries.BLOCK.containsKey(id)
                : BuiltInRegistries.ENTITY_TYPE.containsKey(id));
        if (!known) {
            return Component.literal(key);
        }
        return impression.kind() == Impression.Kind.BLOCK
                ? BuiltInRegistries.BLOCK.getValue(id).getName()
                : BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription();
    }
}
