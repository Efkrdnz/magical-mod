package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.magic.ChronosDimensionService;
import com.efkrdnz.magical.magic.status.MagicStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Encounter ownership is independent of armor, damage resistance, and player loadouts. */
public final class UnwakingCapabilities {
    private UnwakingCapabilities() {}
    public static boolean controlled(Entity entity) { return entity instanceof UnwakingGodEntity; }
    public static boolean rejectsStatus(Entity entity, MagicStatus status) {
        return controlled(entity) && switch (status) {
            case EXILED, PUPPETED, POLYMORPHED, ASLEEP, FACING_PINNED, ROOTED, COMPRESSED, TAUNTED -> true;
            default -> false;
        };
    }
    public static boolean refuseControl(ServerPlayer player, Entity target) {
        if (!controlled(target)) return false;
        player.displayClientMessage(Component.translatable("message.magical.unwaking.resists_control"), true);
        return true;
    }
    public static boolean refuseMovement(ServerPlayer player) {
        if(!UnwakingEncounterService.get(player.server).movementLocked(player)) return false;
        player.displayClientMessage(Component.translatable("message.magical.unwaking.time_locked"),true); return true;
    }
    public static boolean refuseTravel(ServerPlayer player, ResourceKey<Level> dimension) {
        if(refuseMovement(player)) return true;
        if (dimension.equals(player.level().dimension())) return false;
        var service = UnwakingEncounterService.get(player.server);
        if (!service.reserved() || service.internalTransfer()
                || !service.participant(player.getUUID()) && !dimension.equals(ChronosDimensionService.CHRONOS_DIMENSION)) return false;
        player.displayClientMessage(Component.translatable("message.magical.unwaking.travel_locked"), true);
        return true;
    }
}
