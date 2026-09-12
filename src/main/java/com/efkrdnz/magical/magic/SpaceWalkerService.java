package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.magic.menu.SpaceWalkerMenu;
import com.efkrdnz.magical.entity.SpacePocketPortalEntity;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SpaceWalkerService {
    private static final double BLINK_RANGE = 48.0D;
    private static final double COORDINATE_RANGE = 192.0D;
    private static final int BASE_MANA = 8;
    private static final int PORTAL_EXTRA_MANA = 4;
    private static final int BASE_COOLDOWN = 30;
    private static final int PORTAL_LIFE = 20 * 70;

    private SpaceWalkerService() {}

    public static void open(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasUnlocked(MagicContent.SPACE_WALKER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new SpaceWalkerMenu(containerId, inventory),
                Component.translatable("screen.magical.space_walker")));
    }

    public static void blink(ServerPlayer player) {
        if(UnwakingCapabilities.refuseMovement(player)) return;
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasUnlocked(MagicContent.SPACE_WALKER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (state.isSkillOnCooldown(MagicContent.SPACE_WALKER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }
        Optional<Vec3> destination = findLookDestination(player);
        if (destination.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_no_target"), true);
            return;
        }
        teleport(player, state, destination.get(), BLINK_RANGE);
    }

    public static void teleportToCoordinates(ServerPlayer player, String dimension, int x, int y, int z) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (validateInteractiveUse(player, state)) {
            teleportExact(player, state, resolveDimension(player, dimension), new Vec3(x + 0.5D, y, z + 0.5D));
        }
    }

    public static void teleportToCoordinates(ServerPlayer player, int x, int y, int z) {
        teleportToCoordinates(player, "minecraft:overworld", x, y, z);
    }

    public static void teleportToWaypoint(ServerPlayer player, int index) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!validateInteractiveUse(player, state)) {
            return;
        }
        SpaceWaypoint waypoint = state.spaceWaypoint(index);
        if (waypoint == null) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_no_waypoint"), true);
            return;
        }
        teleportExact(player, state, resolveDimension(player, waypoint.dimension()), new Vec3(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D));
    }

    public static void createPortalToCoordinates(ServerPlayer player, String dimension, int x, int y, int z) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (validateInteractiveUse(player, state)) {
            createPortal(player, state, resolveDimension(player, dimension), new Vec3(x + 0.5D, y, z + 0.5D));
        }
    }

    public static void createPortalToWaypoint(ServerPlayer player, int index) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!validateInteractiveUse(player, state)) {
            return;
        }
        SpaceWaypoint waypoint = state.spaceWaypoint(index);
        if (waypoint == null) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_no_waypoint"), true);
            return;
        }
        createPortal(player, state, resolveDimension(player, waypoint.dimension()), new Vec3(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D));
    }

    public static void saveCurrentWaypoint(ServerPlayer player, String name) {
        saveWaypoint(player, name, currentDimension(player), player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    public static void saveWaypoint(ServerPlayer player, String name, String dimension, int x, int y, int z) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasUnlocked(MagicContent.SPACE_WALKER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        ResourceKey<Level> targetDimension = resolveDimension(player, dimension);
        if (!state.addSpaceWaypoint(SpaceWaypoint.at(name, targetDimension.location().toString(), new BlockPos(x, y, z)))) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_waypoint_full"), true);
            return;
        }
        state.sync(player);
        player.displayClientMessage(Component.translatable("message.magical.space_walker_waypoint_saved"), true);
    }

    public static void deleteWaypoint(ServerPlayer player, int index) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (state.removeSpaceWaypoint(index)) {
            state.sync(player);
            player.displayClientMessage(Component.translatable("message.magical.space_walker_waypoint_deleted"), true);
        }
    }

    private static boolean validateInteractiveUse(ServerPlayer player, PlayerMagicState state) {
        if (!state.hasUnlocked(MagicContent.SPACE_WALKER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return false;
        }
        if (state.isSkillOnCooldown(MagicContent.SPACE_WALKER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        return true;
    }

    private static Optional<Vec3> findLookDestination(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(BLINK_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return safeDestination(player, hit.getBlockPos().relative(hit.getDirection()));
    }

    private static void teleport(ServerPlayer player, PlayerMagicState state, Vec3 requested, double maxRange) {
        if(UnwakingCapabilities.refuseMovement(player)) return;
        double distance = player.position().distanceTo(requested);
        if (distance > maxRange) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_too_far"), true);
            return;
        }
        Optional<Vec3> safe = safeDestination(player, BlockPos.containing(requested));
        if (safe.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_unsafe"), true);
            return;
        }
        Vec3 destination = safe.get();
        int manaCost = manaCost();
        if (!MagicSinService.spendManaForSkill(player, state, manaCost)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        int cooldown = cooldownTicks();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.55F, 1.55F);
        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.65F, 1.85F);
        state.setSkillCooldown(MagicContent.SPACE_WALKER.id(), state.consumeCooldownEcho(MagicContent.SPACE_WALKER.id()) ? 0 : cooldown);
        MagicSinService.afterSuccessfulCast(player, state, MagicContent.SPACE_WALKER);
        state.sync(player);
        MagicalNetwork.playFirstPersonImpact(player, MagicContent.SPACE_WALKER.color(), 12, 0.16F, 3, 0.08F, 0, 1.2F);
    }

    private static void teleportExact(ServerPlayer player, PlayerMagicState state, ResourceKey<Level> dimension, Vec3 destination) {
        if (UnwakingCapabilities.refuseTravel(player, dimension)) return;
        ServerLevel targetLevel = player.server.getLevel(dimension);
        if (targetLevel == null || !validExactDestination(targetLevel, destination)) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_invalid_dimension"), true);
            return;
        }
        int manaCost = manaCost();
        if (!MagicSinService.spendManaForSkill(player, state, manaCost)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        int cooldown = cooldownTicks();
        ServerLevel origin = player.serverLevel();
        Vec3 originPos = player.position();
        origin.playSound(null, originPos.x, originPos.y, originPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.55F, 1.35F);
        player.teleportTo(targetLevel, destination.x, destination.y, destination.z, java.util.EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        applySlowFallingIfMidAir(player);
        targetLevel.playSound(null, destination.x, destination.y, destination.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.65F, 1.85F);
        state.setSkillCooldown(MagicContent.SPACE_WALKER.id(), state.consumeCooldownEcho(MagicContent.SPACE_WALKER.id()) ? 0 : cooldown);
        MagicSinService.afterSuccessfulCast(player, state, MagicContent.SPACE_WALKER);
        state.sync(player);
        MagicalNetwork.playFirstPersonImpact(player, MagicContent.SPACE_WALKER.color(), 12, 0.16F, 3, 0.08F, 0, 1.2F);
    }

    private static void createPortal(ServerPlayer player, PlayerMagicState state, ResourceKey<Level> dimension, Vec3 destination) {
        if (UnwakingCapabilities.refuseTravel(player, dimension)) return;
        ServerLevel targetLevel = player.server.getLevel(dimension);
        if (targetLevel == null || !validExactDestination(targetLevel, destination)) {
            player.displayClientMessage(Component.translatable("message.magical.space_walker_invalid_dimension"), true);
            return;
        }
        int manaCost = manaCost() + PORTAL_EXTRA_MANA;
        if (!MagicSinService.spendManaForSkill(player, state, manaCost)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return;
        }
        Vec3 look = player.getLookAngle().normalize();
        Vec3 portalPosition = portalSpawnPosition(player, look);
        SpacePocketPortalEntity portal = SpacePocketPortalEntity.create(
                player.serverLevel(),
                portalPosition,
                player.getYRot(),
                SpacePocketPortalEntity.MODE_ENTRY_PORTAL,
                PORTAL_LIFE,
                dimension,
                destination,
                player.getYRot(),
                player.getXRot(),
                player.getUUID(),
                isMidAir(targetLevel, BlockPos.containing(destination)));
        player.serverLevel().addFreshEntity(portal);
        player.serverLevel().playSound(null, portalPosition.x, portalPosition.y, portalPosition.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.85F, 0.78F);
        state.setSkillCooldown(MagicContent.SPACE_WALKER.id(), state.consumeCooldownEcho(MagicContent.SPACE_WALKER.id()) ? 0 : cooldownTicks());
        MagicSinService.afterSuccessfulCast(player, state, MagicContent.SPACE_WALKER);
        state.sync(player);
    }

    private static Vec3 portalSpawnPosition(ServerPlayer player, Vec3 look) {
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            horizontalLook = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        horizontalLook = horizontalLook.normalize();
        double required = 1.9D + player.getBbWidth() * 0.5D + 0.75D;
        double[] distances = {Math.max(3.3D, required), 4.2D, 5.2D, 6.2D};
        for (double distance : distances) {
            Vec3 candidate = player.position().add(horizontalLook.scale(distance)).add(0.0D, 0.35D, 0.0D);
            if (!new AABB(candidate, candidate).inflate(1.9D, 1.3D, 1.9D).intersects(player.getBoundingBox().inflate(0.12D))) {
                return candidate;
            }
        }
        return player.position().add(horizontalLook.scale(6.8D)).add(0.0D, 0.35D, 0.0D);
    }

    private static boolean validExactDestination(ServerLevel level, Vec3 destination) {
        BlockPos feet = BlockPos.containing(destination);
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height();
        return feet.getY() > minY && feet.getY() < maxY - 1 && level.getWorldBorder().isWithinBounds(feet);
    }

    public static void applySlowFallingIfMidAir(ServerPlayer player) {
        if (isMidAir(player.serverLevel(), player.blockPosition())) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 12, 0, false, false, true));
        }
    }

    private static boolean isMidAir(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    public static Optional<Vec3> safeDestination(ServerPlayer player, BlockPos originFeet) {
        BlockPos[] candidates = {
                originFeet,
                originFeet.above(),
                originFeet.above(2),
                originFeet.north(),
                originFeet.south(),
                originFeet.east(),
                originFeet.west()
        };
        for (BlockPos candidate : candidates) {
            Optional<Vec3> safe = safeExact(player, candidate);
            if (safe.isPresent()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> safeExact(ServerPlayer player, BlockPos feet) {
        ServerLevel level = player.serverLevel();
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height();
        if (feet.getY() <= minY || feet.getY() >= maxY - 2) {
            return Optional.empty();
        }
        if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) {
            return Optional.empty();
        }
        if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA)) {
            return Optional.empty();
        }
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
            return Optional.empty();
        }
        Vec3 target = new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        AABB targetBox = player.getBoundingBox().move(target.x - player.getX(), target.y - player.getY(), target.z - player.getZ());
        if (!level.noCollision(player, targetBox)) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    private static int manaCost() {
        return BASE_MANA;
    }

    private static int cooldownTicks() {
        return BASE_COOLDOWN;
    }

    public static String currentDimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static ResourceKey<Level> resolveDimension(ServerPlayer player, String dimension) {
        String raw = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.strip();
        ResourceLocation location = raw.contains(":") ? ResourceLocation.parse(raw) : ResourceLocation.fromNamespaceAndPath("minecraft", raw);
        ResourceKey<Level> requested = ResourceKey.create(Registries.DIMENSION, location);
        return player.server.getLevel(requested) == null ? Level.OVERWORLD : requested;
    }
}
