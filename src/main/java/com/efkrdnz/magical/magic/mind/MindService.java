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
import java.util.HashMap;
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
    /** How far from an element a projectile is watched for crossing it. */
    private static final double PROJECTILE_WATCH = 4.0;

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
        Manifestation.revertAll(scene);
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
        // The wielder is never a viewer: a blow on their own figment teaches them nothing, or it
        // would write them a shattered row and dent their scepticism for someone else's lies.
        // A real figment is really there: a blow on it is a blow, not evidence.
        if (scene == null || !(figment.level() instanceof ServerLevel level) || attacker.getUUID().equals(scene.owner())
                || figment.isManifested()) {
            return;
        }
        LiveScene.Element element = scene.elements().get(figment.element());
        long now = level.getGameTime();
        expose(scene, attacker, element, Contradiction.TOUCH, now);
        Sight sight = new Sight(level, scene);
        witnessed(sight, sight.viewers(), attacker, element, now);
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

    /** Everyone else in view of an actor's exposure sees through the element too. */
    private static void witnessed(Sight sight, List<LivingEntity> viewers, LivingEntity actor,
                                  LiveScene.Element element, long now) {
        for (LivingEntity witness : viewers) {
            if (witness != actor && sight.sees(witness, element)) {
                contradict(sight.scene, witness, element, Contradiction.WITNESS, now);
            }
        }
    }

    /**
     * A figment lands a blow. A real one bites with its kind's attack; on a believer it is phantom harm
     * (see MindHarm); on a doubter nothing happens, and that is the evidence.
     */
    public static void figmentStrikes(com.efkrdnz.magical.entity.mind.FigmentEntity figment, LivingEntity target) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null || !(figment.level() instanceof ServerLevel level)) {
            return;
        }
        if (figment.isManifested()) {
            float attack = (float) figment.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attack > 0.0F) {
                target.hurtServer(level, figment.damageSources().mobAttack(figment), attack);
            }
            return;
        }
        LiveScene.Element element = scene.elements().get(figment.element());
        float belief = scene.belief().get(target.getId(), figment.element());
        if (belief >= Belief.CONVINCED) {
            MindHarm.struck(level, scene, figment, target, element, belief);
            return;
        }
        expose(scene, target, element, Contradiction.HOLLOW_STRIKE, level.getGameTime());
    }

    /** A real figment was killed: it is gone from its scene for good. */
    public static void figmentSlain(com.efkrdnz.magical.entity.mind.FigmentEntity figment) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null) {
            return;
        }
        scene.slain.add(figment.element());
        scene.manifested.remove(figment.element());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (LiveScene scene : allScenes()) {
            ServerLevel level = server.getLevel(scene.dimension());
            if (level == null || scene.over(level.getGameTime())) {
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
        // The ledger would give the blocks back at the next load anyway; this gives them back now.
        allScenes().forEach(Manifestation::revertAll);
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
        Sight sight = new Sight(level, scene);
        List<LivingEntity> viewers = sight.viewers();
        // Before touches, so a believer who only just stepped in is not burned twice on one tick.
        MindHarm.smoulder(level, scene, viewers, now);
        touches(sight, viewers, now);
        projectiles(sight, viewers, now);
        Set<Integer> present = new HashSet<>();
        for (LivingEntity viewer : viewers) {
            present.add(viewer.getId());
            perceiveAll(sight, viewer, now);
            if (viewer instanceof Mob mob && mob.getTarget() instanceof com.efkrdnz.magical.entity.mind.FigmentEntity figment
                    && figment.sceneId() == scene.id() && !figment.isManifested()
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
        Manifestation.step(level, scene, now);
        MindSync.tick(level, scene);
    }

    /**
     * One tick of looking at one scene. A tick asks the same questions over and over - where each
     * element stands now (a figment has to be found by id), what kind of eyes a viewer has (a registry
     * lookup), and above all whether a viewer sees an element, which is a ray through the world - once
     * while perceiving, and again for every witness of every touch and every viewer of every projectile.
     * Nothing in the world moves inside a tick, so each is answered once and kept until the tick ends:
     * the answer is the one asking again would have got. That is why this changes what a tick costs and
     * never what anybody believes.
     */
    private static final class Sight {
        private static final byte UNASKED = 0;
        private static final byte UNSEEN = 1;
        private static final byte SEEN = 2;

        final ServerLevel level;
        final LiveScene scene;
        /** Each element's box where it stands this tick, by element index. */
        private final AABB[] boxes;
        /** Everything the scene occupies this tick: its birthplace bounds and wherever its figments walked. */
        private final AABB reach;
        private final Map<Integer, Eyes> eyes = new HashMap<>();

        Sight(ServerLevel level, LiveScene scene) {
            this.level = level;
            this.scene = scene;
            List<LiveScene.Element> elements = scene.elements();
            this.boxes = new AABB[elements.size()];
            AABB all = scene.bounds();
            for (LiveScene.Element element : elements) {
                AABB box = liveBox(level, scene, element);
                boxes[element.index()] = box;
                if (element.kind() == LiveScene.Kind.FIGMENT) {
                    all = all.minmax(box);
                }
            }
            this.reach = all;
        }

        AABB box(LiveScene.Element element) {
            return boxes[element.index()];
        }

        List<LivingEntity> viewers() {
            return level.getEntitiesOfClass(LivingEntity.class, reach.inflate(VIEW_RANGE),
                    entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(scene.owner())
                            && !(entity instanceof com.efkrdnz.magical.entity.mind.FigmentEntity)
                            && (entity instanceof Mob || entity instanceof Player));
        }

        /** Every projectile near enough to any element to cross it, in one query for the whole scene. */
        List<Projectile> projectiles() {
            return level.getEntitiesOfClass(Projectile.class, reach.inflate(PROJECTILE_WATCH));
        }

        Eyes eyes(LivingEntity viewer) {
            return eyes.computeIfAbsent(viewer.getId(), id -> new Eyes(typeId(viewer), boxes.length));
        }

        boolean sees(LivingEntity viewer, LiveScene.Element element) {
            Eyes of = eyes(viewer);
            int index = element.index();
            if (of.seen[index] == UNASKED) {
                of.seen[index] = perceives(level, viewer, boxes[index], element.senses(), of.blind) ? SEEN : UNSEEN;
            }
            return of.seen[index] == SEEN;
        }
    }

    /** What a viewer is, asked once a tick, and what it has been found to see this tick. */
    private static final class Eyes {
        final float susceptibility;
        final boolean blind;
        final byte[] seen;

        Eyes(String type, int elements) {
            this.susceptibility = Susceptibility.of(type);
            this.blind = Susceptibility.blind(type);
            this.seen = new byte[elements];
        }
    }

    private static void perceiveAll(Sight sight, LivingEntity viewer, long now) {
        LiveScene scene = sight.scene;
        float susceptibility = sight.eyes(viewer).susceptibility;
        int id = viewer.getId();
        for (LiveScene.Element element : scene.elements()) {
            int index = element.index();
            if (scene.slain(index) || scene.belief().shattered(id, index) || scene.inside.contains(LiveScene.key(id, index))) {
                continue;
            }
            if (sight.sees(viewer, element)) {
                float novelty = SCEPTICISM.novelty(viewer.getStringUUID(), element.impressions(), now);
                scene.belief().gain(id, index, scene.plausibility(index), Sense.multiplier(element.senses()),
                        susceptibility, novelty);
            } else {
                scene.belief().decay(id, index);
            }
        }
    }

    /** Whether a viewer perceives an element whose box is {@code box} this tick. */
    static boolean perceives(ServerLevel level, LivingEntity viewer, AABB box, Set<Sense> senses, boolean blind) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 centre = box.getCenter();
        double distance = eye.distanceTo(centre);
        if (blind) {
            return senses.contains(Sense.SOUND) && distance <= HEARING_RANGE;
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

    private static void touches(Sight sight, List<LivingEntity> viewers, long now) {
        LiveScene scene = sight.scene;
        for (LivingEntity viewer : viewers) {
            AABB body = viewer.getBoundingBox();
            for (LiveScene.Element element : scene.elements()) {
                if (element.kind() != LiveScene.Kind.CLUSTER) {
                    continue;
                }
                long key = LiveScene.key(viewer.getId(), element.index());
                if (scene.manifested(element.index())) {
                    // A real block cannot be walked into; nothing about touching it is evidence.
                    scene.inside.remove(key);
                    continue;
                }
                if (!crosses(body, element)) {
                    scene.inside.remove(key);
                    continue;
                }
                if (!scene.inside.add(key)) {
                    continue;
                }
                float belief = scene.belief().get(viewer.getId(), element.index());
                if (belief >= Belief.CONVINCED && PhantomHarm.of(element.blockIds()) > 0.0F) {
                    // A lie that hurts is believed more: the burn is not evidence against the lava.
                    MindHarm.touched(sight.level, scene, viewer, element, belief);
                    continue;
                }
                expose(scene, viewer, element, Contradiction.TOUCH, now);
                witnessed(sight, viewers, viewer, element, now);
            }
        }
    }

    /**
     * A projectile that crosses an element is evidence to everyone who sees it cross. One query covers
     * the whole scene; each element then takes only the projectiles whose box meets its own widened by
     * {@link #PROJECTILE_WATCH}, which is exactly the set a query of that box would have returned.
     */
    private static void projectiles(Sight sight, List<LivingEntity> viewers, long now) {
        List<Projectile> near = sight.projectiles();
        if (near.isEmpty()) {
            return;
        }
        LiveScene scene = sight.scene;
        for (LiveScene.Element element : scene.elements()) {
            if (scene.manifested(element.index()) || scene.slain(element.index())) {
                continue;
            }
            AABB box = sight.box(element);
            AABB watch = box.inflate(PROJECTILE_WATCH);
            for (Projectile projectile : near) {
                if (!projectile.getBoundingBox().intersects(watch)) {
                    continue;
                }
                AABB path = new AABB(projectile.xo, projectile.yo, projectile.zo,
                        projectile.getX(), projectile.getY(), projectile.getZ()).inflate(0.1);
                boolean crosses = element.kind() == LiveScene.Kind.FIGMENT ? path.intersects(box) : crosses(path, element);
                if (!crosses || !scene.seenProjectiles.add(LiveScene.key(projectile.getId(), element.index()))) {
                    continue;
                }
                for (LivingEntity viewer : viewers) {
                    if (sight.sees(viewer, element)) {
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
