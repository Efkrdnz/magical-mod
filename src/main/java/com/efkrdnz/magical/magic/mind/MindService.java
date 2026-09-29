package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.AimResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    public static final double UNVEIL_REACH = 24.0;
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
        for (LiveScene.Element element : scene.elements()) {
            if (element.kind() == LiveScene.Kind.FIGMENT) {
                com.efkrdnz.magical.entity.mind.FigmentEntity figment =
                        com.efkrdnz.magical.entity.mind.FigmentEntity.spawn(level, scene, element);
                scene.figmentEntities.put(element.index(), figment.getId());
            }
        }
        live.add(scene);
        return scene;
    }

    /**
     * Sets the active reverie down in front of the block face the wielder looks at, turned to the
     * way they face. Every refusal is checked before anything is billed.
     */
    public static boolean unveil(ServerPlayer player, PlayerMagicState state) {
        if (player == null) {
            return false;
        }
        Reverie reverie = state.mind().active();
        if (reverie.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.unveil_empty"), true);
            return false;
        }
        if (scenesOf(player.getUUID()).size() >= MAX_LIVE) {
            player.displayClientMessage(Component.translatable("message.magical.unveil_too_many"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        AimResolver.Result aim = AimResolver.resolve(level, player, UNVEIL_REACH, 0.0, false);
        if (!aim.hitBlock()) {
            player.displayClientMessage(Component.translatable("message.magical.unveil_nowhere"), true);
            return false;
        }
        if (!payFor(player, state, MagicContent.UNVEIL, UnveilCost.of(reverie))) {
            return false;
        }
        BlockPos anchor = aim.blockPos().relative(aim.face());
        int turns = player.getDirection().get2DDataValue() - reverie.facing();
        unveilAt(level, player.getUUID(), reverie, anchor, turns, state.mind().lexicon());
        level.playSound(null, anchor, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.3F);
        state.sync(player);
        return true;
    }

    /**
     * A Daydream draft sent home. Nothing in it is trusted: it is read through {@link ReverieNbt#load},
     * which drops what is out of reach and caps the count, then refused whole if any element was never
     * studied or the scene is over budget. A refusal still syncs, so a client that drafted on a stale
     * lexicon snaps back to the truth.
     */
    public static void saveReverie(ServerPlayer player, int slot, net.minecraft.nbt.CompoundTag data) {
        PlayerMagicState state = player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(com.efkrdnz.magical.magic.AuthorityContent.MIND) || slot < 0 || slot >= MindState.SLOTS
                || data == null) {
            return;
        }
        Reverie draft = ReverieNbt.load(data);
        Reverie.Refusal refusal = draft.validate(state.mind().lexicon());
        if (refusal != Reverie.Refusal.NONE) {
            player.displayClientMessage(Component.translatable("message.magical.reverie_refused",
                    Component.translatable("mind.magical.refusal." + refusal.name().toLowerCase(java.util.Locale.ROOT))), true);
            state.sync(player);
            return;
        }
        state.mind().reverie(slot).copyFrom(draft);
        state.mind().setActiveSlot(slot);
        state.sync(player);
        player.displayClientMessage(Component.translatable("message.magical.reverie_saved"), true);
    }

    /** Bills a self-managed press by hand: the cast pipeline returns before it charges anything. */
    private static boolean payFor(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int baseMana) {
        if (state.isSkillOnCooldown(skill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        int mana = Math.max(1, Math.round(baseMana * stats.costScale()));
        if (!MagicSinService.spendManaForSkill(player, state, mana)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setSkillCooldown(skill.id(), stats.cooldownTicks());
        return true;
    }

    public static List<LiveScene> scenesOf(UUID owner) {
        return List.copyOf(SCENES.getOrDefault(owner, List.of()));
    }

    public static List<LiveScene> allScenes() {
        List<LiveScene> all = new ArrayList<>();
        SCENES.values().forEach(all::addAll);
        return all;
    }

    public static boolean anyLive() {
        return !SCENES.isEmpty();
    }

    public static List<LiveScene> scenesIn(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        List<LiveScene> here = new ArrayList<>();
        for (List<LiveScene> scenes : SCENES.values()) {
            for (LiveScene scene : scenes) {
                if (scene.dimension().equals(dimension)) {
                    here.add(scene);
                }
            }
        }
        return here;
    }

    public static void end(LiveScene scene) {
        List<LiveScene> live = SCENES.get(scene.owner());
        if (live == null || !live.remove(scene)) {
            return;
        }
        if (live.isEmpty()) {
            SCENES.remove(scene.owner());
        }
        MindSync.ended(scene);
    }

    public static void endAll(UUID owner) {
        scenesOf(owner).forEach(MindService::end);
    }

    /** How strongly this entity believes this element, zero when it has never seen it. */
    public static float believes(Entity viewer, LiveScene scene, int element) {
        return scene.belief().get(viewer.getId(), element);
    }

    public static LiveScene scene(int id) {
        for (LiveScene scene : allScenes()) {
            if (scene.id() == id) {
                return scene;
            }
        }
        return null;
    }

    public static float believes(Entity viewer, com.efkrdnz.magical.entity.mind.FigmentEntity figment) {
        LiveScene scene = scene(figment.sceneId());
        return scene == null ? 0.0F : scene.belief().get(viewer.getId(), figment.element());
    }

    /** A blow through a figment: the striker learns, and so does everyone watching. */
    public static void figmentStruck(com.efkrdnz.magical.entity.mind.FigmentEntity figment, LivingEntity attacker) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null || !(figment.level() instanceof ServerLevel level)) {
            return;
        }
        LiveScene.Element element = scene.elements().get(figment.element());
        long now = level.getGameTime();
        expose(scene, attacker, element, Contradiction.TOUCH, now);
        witnessed(level, scene, viewers(level, scene), attacker, element, now);
    }

    /** Whether this viewer has seen through this figment: the element is shattered for them. */
    public static boolean shattered(Entity viewer, com.efkrdnz.magical.entity.mind.FigmentEntity figment) {
        LiveScene scene = scene(figment.sceneId());
        return scene == null || scene.belief().shattered(viewer.getId(), figment.element());
    }

    /** Where an element is right now: a figment walks, so its box is its creature's, not its birthplace. */
    static AABB liveBox(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        if (element.kind() == LiveScene.Kind.FIGMENT
                && level.getEntity(scene.figmentEntity(element.index())) instanceof com.efkrdnz.magical.entity.mind.FigmentEntity figment
                && figment.isAlive()) {
            return figment.getBoundingBox();
        }
        return element.box();
    }

    /** Everything a scene occupies now: its birthplace bounds and wherever its figments have walked to. */
    private static AABB reach(ServerLevel level, LiveScene scene) {
        AABB all = scene.bounds();
        for (LiveScene.Element element : scene.elements()) {
            if (element.kind() == LiveScene.Kind.FIGMENT) {
                all = all.minmax(liveBox(level, scene, element));
            }
        }
        return all;
    }

    /** Everyone else in view of an actor's exposure sees through the element too. */
    private static void witnessed(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, LivingEntity actor,
                                  LiveScene.Element element, long now) {
        for (LivingEntity witness : viewers) {
            if (witness != actor && perceives(level, scene, witness, element, Susceptibility.blind(typeId(witness)))) {
                contradict(scene, witness, element, Contradiction.WITNESS, now);
            }
        }
    }

    /** A figment lands a blow. On a doubter nothing happens, and that is the evidence. */
    public static void figmentStrikes(com.efkrdnz.magical.entity.mind.FigmentEntity figment, LivingEntity target) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null || !(figment.level() instanceof ServerLevel level)) {
            return;
        }
        if (scene.belief().get(target.getId(), figment.element()) < Belief.CONVINCED) {
            expose(scene, target, scene.elements().get(figment.element()), Contradiction.HOLLOW_STRIKE, level.getGameTime());
        }
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
        // A memory's expiry is an absolute game time; carried into a world with a lower clock it would
        // never be pruned, and the host's own player would bring the old world's doubt with them.
        SCEPTICISM.clear();
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
            if (viewer instanceof Mob mob && mob.getTarget() instanceof com.efkrdnz.magical.entity.mind.FigmentEntity figment
                    && figment.sceneId() == scene.id()
                    && scene.belief().get(mob.getId(), figment.element()) < Belief.CONVINCED) {
                mob.setTarget(null);
            }
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
        MindSync.tick(level, scene);
    }

    static List<LivingEntity> viewers(ServerLevel level, LiveScene scene) {
        return level.getEntitiesOfClass(LivingEntity.class, reach(level, scene).inflate(VIEW_RANGE),
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(scene.owner())
                        && !(entity instanceof com.efkrdnz.magical.entity.mind.FigmentEntity)
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
            if (perceives(level, scene, viewer, element, blind)) {
                float novelty = SCEPTICISM.novelty(viewer.getStringUUID(), element.impressions(), now);
                scene.belief().gain(id, index, scene.plausibility(index), Sense.multiplier(element.senses()),
                        susceptibility, novelty);
            } else {
                scene.belief().decay(id, index);
            }
        }
    }

    static boolean perceives(ServerLevel level, LiveScene scene, LivingEntity viewer, LiveScene.Element element, boolean blind) {
        Vec3 eye = viewer.getEyePosition();
        AABB box = liveBox(level, scene, element);
        Vec3 centre = box.getCenter();
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
        return hit.getType() == HitResult.Type.MISS || box.inflate(0.5).contains(hit.getLocation());
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
                expose(scene, viewer, element, Contradiction.TOUCH, now);
                witnessed(level, scene, viewers, viewer, element, now);
            }
        }
    }

    private static void projectiles(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        for (LiveScene.Element element : scene.elements()) {
            AABB box = liveBox(level, scene, element);
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box.inflate(4.0))) {
                AABB path = new AABB(projectile.xo, projectile.yo, projectile.zo,
                        projectile.getX(), projectile.getY(), projectile.getZ()).inflate(0.1);
                boolean crosses = element.kind() == LiveScene.Kind.FIGMENT ? path.intersects(box) : crosses(path, element);
                if (!crosses || !scene.seenProjectiles.add(LiveScene.key(projectile.getId(), element.index()))) {
                    continue;
                }
                for (LivingEntity viewer : viewers) {
                    if (perceives(level, scene, viewer, element, Susceptibility.blind(typeId(viewer)))) {
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

    /**
     * First-hand evidence: the viewer's own body met the element. Unlike watching someone else, this
     * needs no belief to act on - see {@link Belief#expose}.
     */
    static void expose(LiveScene scene, LivingEntity viewer, LiveScene.Element element, Contradiction contradiction, long now) {
        if (scene.belief().expose(viewer.getId(), element.index(), contradiction)) {
            SCEPTICISM.seenThrough(viewer.getStringUUID(), element.impressions(), now);
        }
    }

    static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
