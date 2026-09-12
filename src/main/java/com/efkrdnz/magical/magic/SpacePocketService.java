package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.SpacePocketPortalEntity;
import com.efkrdnz.magical.entity.SpacePocketRoomEffectEntity;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.EnumSet;
import java.util.UUID;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SpacePocketService {
    public static final ResourceKey<Level> POCKET_DIMENSION = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "pocket_space"));
    private static final int ROOM_HALF_SIZE = 18;
    private static final int ROOM_HEIGHT = 13;
    private static final int ROOM_Y = 72;
    private static final int ENTRY_PORTAL_LIFE = 20 * 55;
    private static final double ROOM_SPACING = 96.0D;

    private SpacePocketService() {}

    public static boolean cast(ServerPlayer player, PlayerMagicState state) {
        if (UnwakingCapabilities.refuseTravel(player, POCKET_DIMENSION)) return false;
        if (!state.hasAuthority(AuthorityContent.SPACE) || !state.hasUnlocked(MagicContent.POCKET_DIMENSION.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        if (state.isSkillOnCooldown(MagicContent.POCKET_DIMENSION.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.POCKET_DIMENSION.resolve(state.tuningFor(MagicContent.POCKET_DIMENSION.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        ServerLevel pocket = player.server.getLevel(POCKET_DIMENSION);
        if (pocket == null) {
            state.setMana(state.mana() + stats.manaCost());
            state.sync(player);
            player.displayClientMessage(Component.translatable("message.magical.pocket_missing_dimension"), true);
            return false;
        }

        ServerLevel returnLevel = player.serverLevel();
        Vec3 returnPosition = player.position();
        float returnYaw = player.getYRot();
        float returnPitch = player.getXRot();
        Vec3 roomCenter = roomCenter(player.getUUID());
        buildRoom(pocket, roomCenter);
        spawnRoomEffect(pocket, roomCenter);
        spawnExitDoor(pocket, roomCenter, returnLevel.dimension(), returnPosition, returnYaw, returnPitch);
        if (player.isShiftKeyDown()) {
            spawnEntryPortal(returnLevel, player, roomCenter);
        }

        teleportPlayer(player, pocket, roomCenter.add(0.0D, 1.0D, 0.0D), returnYaw, returnPitch);
        state.setSkillCooldown(MagicContent.POCKET_DIMENSION.id(), stats.cooldownTicks());
        state.sync(player);
        returnLevel.playSound(null, returnPosition.x, returnPosition.y, returnPosition.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 0.42F);
        pocket.playSound(null, roomCenter.x, roomCenter.y, roomCenter.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.55F, 0.56F);
        return true;
    }

    public static void teleportThroughPortal(ServerPlayer player, ResourceLocation targetDimension, Vec3 targetPosition, float targetYaw, float targetPitch) {
        if (targetDimension == null || targetPosition == null) {
            return;
        }
        MinecraftServer server = player.server;
        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, targetDimension));
        if (targetLevel == null) {
            return;
        }
        if (UnwakingCapabilities.refuseTravel(player, targetLevel.dimension())) return;
        teleportPlayer(player, targetLevel, targetPosition, targetYaw, targetPitch);
        targetLevel.playSound(null, targetPosition.x, targetPosition.y, targetPosition.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.5F, 0.48F);
    }

    public static void enforcePocketBounds(ServerPlayer player) {
        if (!isPocketDimension(player.serverLevel())) {
            return;
        }
        Vec3 center = nearestRoomCenter(player.position());
        Vec3 position = player.position();
        double dx = position.x - center.x;
        double dz = position.z - center.z;
        boolean outsideHorizontal = Math.abs(dx) > ROOM_HALF_SIZE - 0.65D || Math.abs(dz) > ROOM_HALF_SIZE - 0.65D;
        boolean outsideVertical = position.y < ROOM_Y - 1.5D || position.y > ROOM_Y + ROOM_HEIGHT - 0.35D;
        if (!outsideHorizontal && !outsideVertical) {
            return;
        }
        Vec3 safe = new Vec3(
                center.x + Mth.clamp(dx, -ROOM_HALF_SIZE + 2.0D, ROOM_HALF_SIZE - 2.0D),
                Mth.clamp(position.y, ROOM_Y + 1.0D, ROOM_Y + ROOM_HEIGHT - 1.8D),
                center.z + Mth.clamp(dz, -ROOM_HALF_SIZE + 2.0D, ROOM_HALF_SIZE - 2.0D));
        teleportPlayer(player, player.serverLevel(), safe, player.getYRot(), player.getXRot());
    }

    public static boolean isProtectedPocketShell(Level level, BlockPos pos) {
        if (!isPocketDimension(level)) {
            return false;
        }
        Vec3 center = nearestRoomCenter(Vec3.atCenterOf(pos));
        BlockPos origin = BlockPos.containing(center.x, ROOM_Y, center.z);
        int localX = pos.getX() - origin.getX();
        int localY = pos.getY() - origin.getY();
        int localZ = pos.getZ() - origin.getZ();
        if (Math.abs(localX) > ROOM_HALF_SIZE || Math.abs(localZ) > ROOM_HALF_SIZE) {
            return false;
        }
        if (localY == -1 || localY == ROOM_HEIGHT) {
            return true;
        }
        return localY >= 0 && localY < ROOM_HEIGHT && (Math.abs(localX) == ROOM_HALF_SIZE || Math.abs(localZ) == ROOM_HALF_SIZE);
    }

    private static boolean isPocketDimension(Level level) {
        return level.dimension().equals(POCKET_DIMENSION);
    }

    private static void spawnEntryPortal(ServerLevel level, ServerPlayer player, Vec3 roomCenter) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 position = player.position().subtract(look.scale(1.65D)).add(0.0D, 0.2D, 0.0D);
        SpacePocketPortalEntity portal = SpacePocketPortalEntity.create(
                level,
                position,
                player.getYRot(),
                SpacePocketPortalEntity.MODE_ENTRY_PORTAL,
                ENTRY_PORTAL_LIFE,
                POCKET_DIMENSION,
                roomCenter.add(0.0D, 1.0D, 0.0D),
                player.getYRot(),
                player.getXRot());
        level.addFreshEntity(portal);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.85F, 0.58F);
    }

    private static void spawnExitDoor(ServerLevel pocket, Vec3 roomCenter, ResourceKey<Level> returnDimension, Vec3 returnPosition, float returnYaw, float returnPitch) {
        clearOldExitDoors(pocket, roomCenter);
        Vec3 doorPosition = roomCenter.add(0.0D, 1.0D, -ROOM_HALF_SIZE + 2.0D);
        SpacePocketPortalEntity door = SpacePocketPortalEntity.create(
                pocket,
                doorPosition,
                180.0F,
                SpacePocketPortalEntity.MODE_EXIT_DOOR,
                -1,
                returnDimension,
                returnPosition,
                returnYaw,
                returnPitch);
        pocket.addFreshEntity(door);
    }

    private static void clearOldExitDoors(ServerLevel pocket, Vec3 roomCenter) {
        pocket.getEntitiesOfClass(SpacePocketPortalEntity.class, new net.minecraft.world.phys.AABB(roomCenter, roomCenter).inflate(ROOM_HALF_SIZE + 4.0D, ROOM_HEIGHT + 4.0D, ROOM_HALF_SIZE + 4.0D),
                entity -> entity.mode() == SpacePocketPortalEntity.MODE_EXIT_DOOR).forEach(SpacePocketPortalEntity::discard);
    }

    private static void spawnRoomEffect(ServerLevel pocket, Vec3 roomCenter) {
        boolean existing = !pocket.getEntitiesOfClass(SpacePocketRoomEffectEntity.class, new net.minecraft.world.phys.AABB(roomCenter, roomCenter).inflate(3.0D),
                entity -> entity.distanceToSqr(roomCenter) < 4.0D).isEmpty();
        if (!existing) {
            pocket.addFreshEntity(SpacePocketRoomEffectEntity.create(pocket, roomCenter.add(0.0D, 0.05D, 0.0D), ROOM_HALF_SIZE, ROOM_HEIGHT));
        }
    }

    private static void buildRoom(ServerLevel level, Vec3 center) {
        BlockPos origin = BlockPos.containing(center.x, ROOM_Y, center.z);
        BlockState shell = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState trim = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        for (int x = -ROOM_HALF_SIZE; x <= ROOM_HALF_SIZE; x++) {
            for (int z = -ROOM_HALF_SIZE; z <= ROOM_HALF_SIZE; z++) {
                setIfReplaceable(level, origin.offset(x, -1, z), border(x, z) ? trim : shell);
                setIfReplaceable(level, origin.offset(x, ROOM_HEIGHT, z), border(x, z) ? trim : shell);
            }
        }
        for (int y = 0; y < ROOM_HEIGHT; y++) {
            for (int offset = -ROOM_HALF_SIZE; offset <= ROOM_HALF_SIZE; offset++) {
                BlockState state = (y % 4 == 0 || Math.abs(offset) == ROOM_HALF_SIZE) ? trim : shell;
                setIfReplaceable(level, origin.offset(offset, y, -ROOM_HALF_SIZE), state);
                setIfReplaceable(level, origin.offset(offset, y, ROOM_HALF_SIZE), state);
                setIfReplaceable(level, origin.offset(-ROOM_HALF_SIZE, y, offset), state);
                setIfReplaceable(level, origin.offset(ROOM_HALF_SIZE, y, offset), state);
            }
        }
        for (int y = 0; y < 5; y++) {
            for (int x = -2; x <= 2; x++) {
                level.setBlockAndUpdate(origin.offset(x, y, -ROOM_HALF_SIZE + 1), Blocks.AIR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
    }

    private static void setIfReplaceable(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.is(Blocks.BLACK_CONCRETE) || current.is(Blocks.OBSIDIAN) || current.is(Blocks.CRYING_OBSIDIAN) || current.is(Blocks.END_PORTAL)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    private static boolean border(int x, int z) {
        return Math.abs(x) == ROOM_HALF_SIZE || Math.abs(z) == ROOM_HALF_SIZE || Math.abs(x) % 8 == 0 || Math.abs(z) % 8 == 0;
    }

    private static Vec3 nearestRoomCenter(Vec3 position) {
        double x = Math.round((position.x - 0.5D) / ROOM_SPACING) * ROOM_SPACING + 0.5D;
        double z = Math.round((position.z - 0.5D) / ROOM_SPACING) * ROOM_SPACING + 0.5D;
        return new Vec3(x, ROOM_Y, z);
    }

    private static Vec3 roomCenter(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        int gridX = Math.floorMod((int) (most ^ most >>> 32), 2048);
        int gridZ = Math.floorMod((int) (least ^ least >>> 32), 2048);
        return new Vec3((gridX - 1024) * 96.0D + 0.5D, ROOM_Y, (gridZ - 1024) * 96.0D + 0.5D);
    }

    private static void teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 position, float yaw, float pitch) {
        player.teleportTo(level, position.x, position.y, position.z, EnumSet.noneOf(Relative.class), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

}
