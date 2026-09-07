package com.efkrdnz.magical.tower;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.MagicOpponentEntity;
import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DungeonTowerService {
    public static final ResourceLocation MAIN_TOWER_ID = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "main");
    public static final ResourceKey<Level> TOWER_DIMENSION = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dungeon_tower"));
    public static final int MAIN_TOWER_FLOORS = 8;

    private static final int FLOOR_SPACING = 256;
    private static final int ARENA_Y = 80;
    private static final int ARENA_RADIUS = 22;
    private static final Map<Integer, FloorSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, RewardOffer> OFFERS = new ConcurrentHashMap<>();

    private DungeonTowerService() {}

    public static int enter(ServerPlayer player, int requestedFloor) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        int highestAvailable = Math.min(MAIN_TOWER_FLOORS, state.towerClearedFloor(MAIN_TOWER_ID) + 1);
        int floor = Mth.clamp(requestedFloor <= 0 ? highestAvailable : requestedFloor, 1, highestAvailable);
        ServerLevel level = player.server.getLevel(TOWER_DIMENSION);
        if (level == null) {
            player.displayClientMessage(Component.translatable("message.magical.tower_missing_dimension"), false);
            return 0;
        }

        FloorSession session = SESSIONS.compute(floor, (ignored, existing) -> {
            if (existing == null || existing.completed || existing.failed) {
                FloorSession created = new FloorSession(floor, level.getGameTime());
                generateFloor(level, floor);
                return created;
            }
            return existing;
        });
        session.participants.add(player.getUUID());
        teleportToFloor(player, level, floor);
        refillForAttempt(player);
        if (!session.spawned) {
            spawnFloorOpponents(level, player, session);
            session.spawned = true;
        }
        player.displayClientMessage(Component.translatable("message.magical.tower_entered", floor), false);
        return 1;
    }

    public static int status(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        int cleared = state.towerClearedFloor(MAIN_TOWER_ID);
        int next = Math.min(MAIN_TOWER_FLOORS, cleared + 1);
        player.displayClientMessage(Component.translatable("message.magical.tower_status", cleared, next), false);
        return 1;
    }

    public static int wish(ServerPlayer player, int choice) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        RewardOffer offer = OFFERS.computeIfAbsent(player.getUUID(), ignored -> buildUnclaimedOffer(player, state));
        if (offer == null) {
            player.displayClientMessage(Component.translatable("message.magical.tower_no_wish"), false);
            return 0;
        }
        if (state.hasClaimedTowerReward(MAIN_TOWER_ID, offer.floor())) {
            OFFERS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.magical.tower_reward_claimed"), false);
            return 0;
        }
        int index = choice - 1;
        if (index < 0 || index >= offer.rewards().size()) {
            showOffer(player, offer);
            return 0;
        }
        DungeonTowerReward reward = offer.rewards().get(index);
        if (!grantReward(player, state, reward)) {
            OFFERS.remove(player.getUUID());
            RewardOffer fresh = buildUnclaimedOffer(player, state);
            if (fresh != null) {
                OFFERS.put(player.getUUID(), fresh);
                showOffer(player, fresh);
            }
            return 0;
        }
        state.claimTowerReward(MAIN_TOWER_ID, offer.floor());
        state.sync(player);
        OFFERS.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("message.magical.tower_wish_granted", rewardName(reward)), false);
        return 1;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(TOWER_DIMENSION);
        if (level == null) {
            return;
        }
        for (FloorSession session : List.copyOf(SESSIONS.values())) {
            if (session.completed || session.failed) {
                continue;
            }
            if (level.getGameTime() - session.startedGameTime < 40) {
                continue;
            }
            AABB arena = arenaBox(session.floor);
            List<ServerPlayer> players = level.players().stream()
                    .filter(player -> arena.contains(player.position()))
                    .toList();
            if (players.isEmpty()) {
                if (level.getGameTime() - session.lastPlayerSeenGameTime > 200) {
                    failSession(level, session, Component.translatable("message.magical.tower_failed_empty"));
                }
                continue;
            }
            session.lastPlayerSeenGameTime = level.getGameTime();
            players.forEach(player -> session.participants.add(player.getUUID()));
            boolean opponentsAlive = !level.getEntitiesOfClass(MagicOpponentEntity.class, arena, LivingEntity::isAlive).isEmpty();
            if (!opponentsAlive) {
                completeSession(level, session, players);
            }
        }
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (!player.level().dimension().equals(TOWER_DIMENSION)) {
            return;
        }
        int floor = floorFromPosition(player.position());
        FloorSession session = SESSIONS.get(floor);
        if (session != null && !session.completed) {
            failSession(player.serverLevel(), session, Component.translatable("message.magical.tower_failed_death", player.getDisplayName()));
        }
    }

    private static RewardOffer buildUnclaimedOffer(ServerPlayer player, PlayerMagicState state) {
        int floor = state.towerClearedFloor(MAIN_TOWER_ID);
        if (floor <= 0 || state.hasClaimedTowerReward(MAIN_TOWER_ID, floor)) {
            return null;
        }
        List<DungeonTowerReward> rewards = rollRewards(player, state, floor);
        return rewards.isEmpty() ? null : new RewardOffer(floor, rewards);
    }

    private static void completeSession(ServerLevel level, FloorSession session, List<ServerPlayer> players) {
        session.completed = true;
        level.playSound(null, center(session.floor).x, ARENA_Y + 2.0D, center(session.floor).z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.6F, 1.25F);
        for (ServerPlayer player : players) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            state.markTowerFloorCleared(MAIN_TOWER_ID, session.floor);
            state.sync(player);
            RewardOffer offer = new RewardOffer(session.floor, rollRewards(player, state, session.floor));
            OFFERS.put(player.getUUID(), offer);
            player.displayClientMessage(Component.translatable("message.magical.tower_floor_cleared", session.floor), false);
            showOffer(player, offer);
        }
    }

    private static void failSession(ServerLevel level, FloorSession session, Component reason) {
        session.failed = true;
        AABB arena = arenaBox(session.floor);
        for (MagicOpponentEntity opponent : level.getEntitiesOfClass(MagicOpponentEntity.class, arena)) {
            opponent.discard();
        }
        for (ServerPlayer player : level.players()) {
            if (arena.contains(player.position())) {
                player.displayClientMessage(reason, false);
                if (player.isAlive()) {
                    teleportOut(player);
                }
            }
        }
        SESSIONS.remove(session.floor);
    }

    private static void showOffer(ServerPlayer player, RewardOffer offer) {
        player.displayClientMessage(Component.translatable("message.magical.tower_altar_prompt", offer.floor()).withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < offer.rewards().size(); i++) {
            int choice = i + 1;
            DungeonTowerReward reward = offer.rewards().get(i);
            MutableComponent line = Component.literal("[" + choice + "] ")
                    .append(rewardName(reward))
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/magicaltower wish " + choice)));
            player.displayClientMessage(line, false);
        }
    }

    private static List<DungeonTowerReward> rollRewards(ServerPlayer player, PlayerMagicState state, int floor) {
        List<DungeonTowerReward> candidates = new ArrayList<>();
        addClassRewards(candidates, state, floor);
        addSkillRewards(candidates, state, floor);
        addPassiveRewards(candidates, state, floor);
        candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.MAX_MANA, null, 10 + floor * 2, "reward.magical.tower.max_mana"));
        candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.MAX_BARRIER, null, 4 + floor, "reward.magical.tower.max_barrier"));
        candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.PROFICIENCY, null, 25 + floor * 10, "reward.magical.tower.proficiency"));

        candidates.sort(Comparator.comparing(reward -> reward.kind().ordinal() + ":" + (reward.id() == null ? reward.labelKey() : reward.id().toString())));
        RandomSource random = RandomSource.create(player.getUUID().getMostSignificantBits() ^ (long) floor * 341873128712L);
        List<DungeonTowerReward> choices = new ArrayList<>();
        while (!candidates.isEmpty() && choices.size() < 3) {
            choices.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return choices;
    }

    private static void addClassRewards(List<DungeonTowerReward> candidates, PlayerMagicState state, int floor) {
        for (MagicalClassDefinition definition : MagicalClasses.roots()) {
            if (MagicalClasses.SPELL_CREATOR.equals(definition.id()) && floor < 8) {
                continue;
            }
            if (!state.hasClass(definition.id())) {
                candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.CLASS_UNLOCK, definition.id(), 0, definition.nameKey()));
            }
        }
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            if (state.canEvolveClass(definition.id())) {
                candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.CLASS_EVOLUTION, definition.id(), 0, definition.nameKey()));
            }
        }
    }

    private static void addSkillRewards(List<DungeonTowerReward> candidates, PlayerMagicState state, int floor) {
        Set<ResourceLocation> classRewardSkills = new LinkedHashSet<>();
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            classRewardSkills.addAll(definition.rewardSkills());
            if (state.hasClass(definition.id())) {
                for (ResourceLocation skillId : definition.rewardSkills()) {
                    if (!state.hasUnlocked(skillId)) {
                        candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.CLASS_SKILL, skillId, 0, MagicContent.get(skillId).nameKey()));
                    }
                }
            }
        }
        int maxTier = Math.min(4, Math.max(0, (floor + 1) / 2));
        for (MagicSkillDefinition skill : MagicContent.allSkills()) {
            if (skill.tier() < 0
                    || skill.tier() > maxTier
                    || MagicContent.STARTER_SKILL.equals(skill.id())
                    || MagicContent.isAuthoritySkill(skill.id())
                    || MagicContent.isCreatedSkill(skill.id())
                    || classRewardSkills.contains(skill.id())
                    || state.hasUnlocked(skill.id())) {
                continue;
            }
            candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.GENERAL_SKILL, skill.id(), 0, skill.nameKey()));
        }
    }

    private static void addPassiveRewards(List<DungeonTowerReward> candidates, PlayerMagicState state, int floor) {
        int maxShopTier = floor >= 8 ? 4 : floor >= 6 ? 3 : floor >= 3 ? 2 : 1;
        for (MagicPassiveDefinition passive : MagicPassiveContent.normalPassives()) {
            if (state.hasPassive(passive.id())) {
                continue;
            }
            if (passive.shopTier() > 0 && passive.shopTier() <= maxShopTier) {
                candidates.add(new DungeonTowerReward(DungeonTowerReward.Kind.PASSIVE, passive.id(), 0, passive.nameKey()));
            }
        }
    }

    private static boolean grantReward(ServerPlayer player, PlayerMagicState state, DungeonTowerReward reward) {
        return switch (reward.kind()) {
            case CLASS_UNLOCK -> state.unlockClass(player, reward.id());
            case CLASS_EVOLUTION -> state.evolveClass(player, reward.id());
            case CLASS_SKILL, GENERAL_SKILL -> state.unlock(reward.id());
            case PASSIVE -> state.unlockPassive(reward.id());
            case MAX_MANA -> {
                state.addMaxManaBonus(reward.amount());
                yield true;
            }
            case MAX_BARRIER -> {
                state.addMaxBarrierBonus(reward.amount());
                yield true;
            }
            case PROFICIENCY -> {
                state.addProficiency(player, reward.amount());
                yield true;
            }
        };
    }

    private static Component rewardName(DungeonTowerReward reward) {
        return switch (reward.kind()) {
            case MAX_MANA -> Component.translatable(reward.labelKey(), reward.amount());
            case MAX_BARRIER -> Component.translatable(reward.labelKey(), reward.amount());
            case PROFICIENCY -> Component.translatable(reward.labelKey(), reward.amount());
            default -> Component.translatable(reward.labelKey());
        };
    }

    /**
     * Floors 2-10 are untouched: clones at difficulty 1-5, as they have always been. Past floor 10
     * the tower stops copying the player and starts sending Ascendants, one tier per two floors.
     */
    static int tierForFloor(int floor) {
        if (floor <= 10) {
            return Math.max(1, floor / 2);
        }
        return Math.min(AscendantTier.MAX_TIER, AscendantTier.MIN_TIER - 1 + (floor - 10 + 1) / 2);
    }

    private static void spawnFloorOpponents(ServerLevel level, ServerPlayer template, FloorSession session) {
        int tier = tierForFloor(session.floor);
        // Seven tier-10 Ascendants is 3,920 health and a wall of telegraphs, which is not a fight.
        // An Ascendant floor sends one.
        int count = AscendantTier.isAscendant(tier) ? 1 : Math.min(7, 1 + session.floor / 2);
        for (int i = 0; i < count; i++) {
            MagicOpponentEntity opponent = AscendantTier.isAscendant(tier)
                    ? MagicOpponentEntity.ascendant(level, tier)
                    : MagicOpponentEntity.cloneFrom(template, tier);
            double angle = Math.PI * 2.0D * i / count;
            Vec3 pos = center(session.floor).add(Math.cos(angle) * 9.0D, 0.0D, Math.sin(angle) * 9.0D);
            opponent.moveTo(pos.x, ARENA_Y + 1.0D, pos.z, (float) Math.toDegrees(angle), 0.0F);
            opponent.setCustomName(session.floor >= MAIN_TOWER_FLOORS && i == 0
                    ? Component.translatable("entity.magical.tower_final_boss")
                    : Component.translatable("entity.magical.tower_opponent", session.floor));
            opponent.setTarget(template);
            level.addFreshEntity(opponent);
        }
    }

    private static void generateFloor(ServerLevel level, int floor) {
        BlockPos center = BlockPos.containing(center(floor).x, ARENA_Y, center(floor).z);
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);
                BlockPos floorPos = center.offset(x, 0, z);
                level.setBlock(floorPos, distance <= ARENA_RADIUS ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                for (int y = 1; y <= 8; y++) {
                    boolean wall = distance >= ARENA_RADIUS - 1 && distance <= ARENA_RADIUS + 0.5D;
                    level.setBlock(floorPos.above(y), wall ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(center.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(center.offset(0, 1, ARENA_RADIUS - 3), Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
        level.setBlock(center.offset(0, 2, ARENA_RADIUS - 3), Blocks.END_ROD.defaultBlockState(), 3);
    }

    private static void teleportToFloor(ServerPlayer player, ServerLevel level, int floor) {
        Vec3 center = center(floor);
        player.teleportTo(level, center.x, ARENA_Y + 1.0D, center.z, EnumSet.noneOf(net.minecraft.world.entity.Relative.class), player.getYRot(), player.getXRot(), true);
    }

    private static void teleportOut(ServerPlayer player) {
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, EnumSet.noneOf(net.minecraft.world.entity.Relative.class), player.getYRot(), player.getXRot(), true);
        }
    }

    private static void refillForAttempt(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.refillMana();
        state.setBarrier(state.maxBarrier());
        state.clearCooldowns();
        state.sync(player);
    }

    private static AABB arenaBox(int floor) {
        Vec3 center = center(floor);
        return new AABB(center.x - ARENA_RADIUS - 4, ARENA_Y - 4, center.z - ARENA_RADIUS - 4, center.x + ARENA_RADIUS + 4, ARENA_Y + 12, center.z + ARENA_RADIUS + 4);
    }

    private static Vec3 center(int floor) {
        return new Vec3(floor * FLOOR_SPACING, ARENA_Y, 0.0D);
    }

    private static int floorFromPosition(Vec3 position) {
        return Mth.clamp((int) Math.round(position.x / FLOOR_SPACING), 1, MAIN_TOWER_FLOORS);
    }

    private static final class FloorSession {
        private final int floor;
        private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
        private final long startedGameTime;
        private long lastPlayerSeenGameTime;
        private boolean spawned;
        private boolean completed;
        private boolean failed;

        private FloorSession(int floor, long startedGameTime) {
            this.floor = floor;
            this.startedGameTime = startedGameTime;
            this.lastPlayerSeenGameTime = startedGameTime;
        }
    }

    private record RewardOffer(int floor, List<DungeonTowerReward> rewards) {}
}
