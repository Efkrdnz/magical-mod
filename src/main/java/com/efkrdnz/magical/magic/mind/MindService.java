package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every live scene, per wielder, and the tick that decides who believes what. The only class in
 * the Authority that knows what a level is, besides {@link LevelMindWorld}.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindService {
    public static final int MAX_LIVE = 2;
    public static final double VIEW_RANGE = 32.0;
    public static final double HEARING_RANGE = 16.0;
    public static final double PLAYER_VIEW_CONE = 0.5;
    private static final int SCEPTICISM_PRUNE_TICKS = 1200;

    private static final Map<UUID, List<LiveScene>> SCENES = new LinkedHashMap<>();
    private static final Scepticism SCEPTICISM = new Scepticism();
    private static int nextId = 1;

    private MindService() {}

    public static LiveScene unveilAt(ServerLevel level, UUID owner, Reverie reverie, BlockPos anchor, int turns, Lexicon lexicon) {
        List<LiveScene> live = SCENES.computeIfAbsent(owner, key -> new ArrayList<>());
        if (live.size() >= MAX_LIVE || reverie.isEmpty()) {
            if (live.isEmpty()) {
                SCENES.remove(owner);
            }
            return null;
        }
        LiveScene scene = new LiveScene(nextId++, owner, level.dimension(), reverie.copy(), anchor, turns, lexicon,
                level.getGameTime());
        scene.reread(new LevelMindWorld(level));
        live.add(scene);
        return scene;
    }

    public static List<LiveScene> scenesOf(UUID owner) {
        return List.copyOf(SCENES.getOrDefault(owner, List.of()));
    }

    public static List<LiveScene> allScenes() {
        List<LiveScene> all = new ArrayList<>();
        SCENES.values().forEach(all::addAll);
        return all;
    }

    public static void end(LiveScene scene) {
        List<LiveScene> live = SCENES.get(scene.owner());
        if (live == null || !live.remove(scene)) {
            return;
        }
        if (live.isEmpty()) {
            SCENES.remove(scene.owner());
        }
    }

    public static void endAll(UUID owner) {
        scenesOf(owner).forEach(MindService::end);
    }

    /** How strongly this entity believes this element, zero when it has never seen it. */
    public static float believes(Entity viewer, LiveScene scene, int element) {
        return scene.belief().get(viewer.getId(), element);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (LiveScene scene : allScenes()) {
            ServerLevel level = server.getLevel(scene.dimension());
            if (level == null || scene.expired(level.getGameTime())) {
                end(scene);
                continue;
            }
            tickScene(level, scene);
        }
        if (server.getTickCount() % SCEPTICISM_PRUNE_TICKS == 0) {
            SCEPTICISM.prune(server.overworld().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        endAll(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SCENES.clear();
    }

    static void tickScene(ServerLevel level, LiveScene scene) {
        long now = level.getGameTime();
        if ((now - scene.bornAt()) % LiveScene.REREAD_TICKS == 0) {
            scene.reread(new LevelMindWorld(level));
        }
        List<LivingEntity> viewers = viewers(level, scene);
        touches(level, scene, viewers, now);
        projectiles(level, scene, viewers, now);
        Set<Integer> present = new HashSet<>();
        for (LivingEntity viewer : viewers) {
            present.add(viewer.getId());
            perceiveAll(level, scene, viewer, now);
        }
        for (Integer gone : List.copyOf(scene.knownViewers)) {
            if (present.contains(gone)) {
                continue;
            }
            if (level.getEntity(gone) == null) {
                scene.belief().forget(gone);
                scene.knownViewers.remove(gone);
            } else {
                for (LiveScene.Element element : scene.elements()) {
                    scene.belief().decay(gone, element.index());
                }
            }
        }
        scene.knownViewers.addAll(present);
    }

    static List<LivingEntity> viewers(ServerLevel level, LiveScene scene) {
        return level.getEntitiesOfClass(LivingEntity.class, scene.bounds().inflate(VIEW_RANGE),
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(scene.owner())
                        && (entity instanceof Mob || entity instanceof Player));
    }

    private static void perceiveAll(ServerLevel level, LiveScene scene, LivingEntity viewer, long now) {
        String type = typeId(viewer);
        float susceptibility = Susceptibility.of(type);
        boolean blind = Susceptibility.blind(type);
        int id = viewer.getId();
        for (LiveScene.Element element : scene.elements()) {
            int index = element.index();
            if (scene.belief().shattered(id, index) || scene.inside.contains(LiveScene.key(id, index))) {
                continue;
            }
            if (perceives(level, viewer, element, blind)) {
                float novelty = SCEPTICISM.novelty(viewer.getStringUUID(), element.impressions(), now);
                scene.belief().gain(id, index, scene.plausibility(index), Sense.multiplier(element.senses()),
                        susceptibility, novelty);
            } else {
                scene.belief().decay(id, index);
            }
        }
    }

    static boolean perceives(ServerLevel level, LivingEntity viewer, LiveScene.Element element, boolean blind) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 centre = element.box().getCenter();
        double distance = eye.distanceTo(centre);
        if (blind) {
            return element.senses().contains(Sense.SOUND) && distance <= HEARING_RANGE;
        }
        if (distance > VIEW_RANGE) {
            return false;
        }
        if (viewer instanceof Player player
                && player.getViewVector(1.0F).dot(centre.subtract(eye).normalize()) < PLAYER_VIEW_CONE) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS || element.box().inflate(0.5).contains(hit.getLocation());
    }

    private static void touches(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        for (LivingEntity viewer : viewers) {
            AABB body = viewer.getBoundingBox();
            for (LiveScene.Element element : scene.elements()) {
                if (element.kind() != LiveScene.Kind.CLUSTER) {
                    continue;
                }
                long key = LiveScene.key(viewer.getId(), element.index());
                if (!crosses(body, element)) {
                    scene.inside.remove(key);
                    continue;
                }
                if (!scene.inside.add(key)) {
                    continue;
                }
                contradict(scene, viewer, element, Contradiction.TOUCH, now);
                for (LivingEntity witness : viewers) {
                    if (witness != viewer && perceives(level, witness, element, Susceptibility.blind(typeId(witness)))) {
                        contradict(scene, witness, element, Contradiction.WITNESS, now);
                    }
                }
            }
        }
    }

    private static void projectiles(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        for (LiveScene.Element element : scene.elements()) {
            if (element.kind() != LiveScene.Kind.CLUSTER) {
                continue;
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, element.box().inflate(4.0))) {
                AABB path = new AABB(projectile.xo, projectile.yo, projectile.zo,
                        projectile.getX(), projectile.getY(), projectile.getZ()).inflate(0.1);
                if (!crosses(path, element)
                        || !scene.seenProjectiles.add(LiveScene.key(projectile.getId(), element.index()))) {
                    continue;
                }
                for (LivingEntity viewer : viewers) {
                    if (perceives(level, viewer, element, Susceptibility.blind(typeId(viewer)))) {
                        contradict(scene, viewer, element, Contradiction.PROJECTILE, now);
                    }
                }
            }
        }
    }

    private static boolean crosses(AABB box, LiveScene.Element element) {
        if (!box.intersects(element.box())) {
            return false;
        }
        for (BlockPos cell : element.cells()) {
            if (box.intersects(new AABB(cell))) {
                return true;
            }
        }
        return false;
    }

    static void contradict(LiveScene scene, LivingEntity viewer, LiveScene.Element element, Contradiction contradiction, long now) {
        if (scene.belief().contradict(viewer.getId(), element.index(), contradiction)) {
            SCEPTICISM.seenThrough(viewer.getStringUUID(), element.impressions(), now);
        }
    }

    static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
