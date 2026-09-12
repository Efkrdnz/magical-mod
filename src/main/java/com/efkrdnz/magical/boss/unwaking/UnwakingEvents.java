package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.ChronosDimensionService;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MagicalMod.MODID)
public final class UnwakingEvents {
    private UnwakingEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { UnwakingEncounterService.get(event.getServer()).tick(); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void pin(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer player) UnwakingEncounterService.get(player.server).enforceMovement(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { UnwakingEncounterService.stop(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) UnwakingEncounterService.get(player.server).leave(player.getUUID());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) UnwakingEncounterService.get(player.server).recover(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) UnwakingEncounterService.get(player.server).recover(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectTransition(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && UnwakingEncounterService.get(player.server).protects(player)
                && (event.getSource().getEntity() instanceof UnwakingGodEntity || ChronosDimensionService.isChronos(player.level()))) {
            // Never cancel operator /kill, which uses a separate kill path. While recovering
            // from a failed transfer, hazards and void damage must not strand the player.
            if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void defeat(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UnwakingEncounterService service = UnwakingEncounterService.get(player.server);
        if (!service.participant(player.getUUID())) return;
        if (event.getSource().getEntity() instanceof UnwakingGodEntity) {
            event.setCanceled(true);
            player.setHealth(1);
            player.sendSystemMessage(Component.translatable("message.magical.unwaking.defeated"));
        }
        service.leave(player.getUUID());
    }

    @SubscribeEvent public static void blockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && UnwakingEncounterService.get(player.server).protectsShrine(event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent event) {
        if (UnwakingCapabilities.controlled(event.getEntity())) { event.setCanceled(true); return; }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UnwakingEncounterService service = UnwakingEncounterService.get(player.server);
        if (service.reserved() && !service.internalTransfer() && (service.participant(player.getUUID()) || event.getDimension().equals(ChronosDimensionService.CHRONOS_DIMENSION))) event.setCanceled(true);
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("unwaking")
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    UnwakingEncounterService.get(p.server).leave(p.getUUID());
                    return 1;
                }))
                .then(Commands.literal("visuals")
                        .then(Commands.literal("reduced").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).visuals(c.getSource().getPlayerOrException(),null,true)))
                        .then(Commands.literal("full").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).visuals(c.getSource().getPlayerOrException(),null,false))))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(UnwakingEncounterService.get(c.getSource().getServer()).status()), false);
                    return 1;
                }))
                .then(Commands.literal("abort").requires(s -> s.hasPermission(2)).executes(c -> { UnwakingEncounterService.get(c.getSource().getServer()).abort(); return 1; }))
                .then(Commands.literal("shrine").requires(s -> s.hasPermission(2)).then(Commands.literal("create").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!(p.pick(8, 0, false) instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
                        c.getSource().sendFailure(Component.translatable("message.magical.unwaking.look_at_ground")); return 0;
                    }
                    return UnwakingEncounterService.get(p.server).createShrine(p, hit.getBlockPos().above());
                })))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("assault")
                                .then(Commands.literal("heaven").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.SKY))))
                                .then(Commands.literal("world").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.VORTEX))))
                                .then(Commands.literal("moment").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.CLOCK))))
                                .then(Commands.literal("eyes").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.EYES))))
                                .then(Commands.literal("mirror").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.MIRROR))))
                                .then(Commands.literal("tunnel").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.TUNNEL))))
                                .then(Commands.literal("giant").executes(c->UnwakingEncounterService.get(c.getSource().getServer()).debugAssault(c.getSource().getPlayerOrException(),UnwakingAssaultState.orderStartingAt(UnwakingAssaultState.Passage.GIANT)))))
                        .then(Commands.literal("hitboxes").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->UnwakingEncounterService.get(c.getSource().getServer()).visuals(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled"),null))))
                        .then(Commands.literal("start").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugPhase(c.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("phase")
                                .then(Commands.literal("sleeping").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugPhase(c.getSource().getPlayerOrException(), false)))
                                .then(Commands.literal("trial_sky").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugDomainPhase(c.getSource().getPlayerOrException(), UnwakingPhase.TRIAL_SKY)))
                                .then(Commands.literal("trial_breath").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugDomainPhase(c.getSource().getPlayerOrException(), UnwakingPhase.TRIAL_BREATH)))
                                .then(Commands.literal("trial_chime").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugDomainPhase(c.getSource().getPlayerOrException(), UnwakingPhase.TRIAL_CHIME)))
                                .then(Commands.literal("final").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugDomainPhase(c.getSource().getPlayerOrException(), UnwakingPhase.FINAL)))
                                .then(Commands.literal("awake").executes(c -> UnwakingEncounterService.get(c.getSource().getServer()).debugPhase(c.getSource().getPlayerOrException(), true))))));
    }
}
