package com.efkrdnz.magical.network;

import com.efkrdnz.magical.arcane.ArcanePlayerData;
import com.efkrdnz.magical.magic.MagicBarrageService;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicCodexService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.SpaceAuthorityService;
import com.efkrdnz.magical.magic.SpaceWalkerService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MagicalNetwork {
    private MagicalNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(ArcanePlayerDataPayload.TYPE, ArcanePlayerDataPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(PlayerMagicStatePayload.TYPE, PlayerMagicStatePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(FirstPersonEffectPayload.TYPE, FirstPersonEffectPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(CounterPromptPayload.TYPE, CounterPromptPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(CounterClearPayload.TYPE, CounterClearPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(ChronosEnvironmentPayload.TYPE, ChronosEnvironmentPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(VisualCuePayload.TYPE, VisualCuePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(StatusSyncPayload.TYPE, StatusSyncPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToServer(MagicBarrageHoldPayload.TYPE, MagicBarrageHoldPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicBarrageService.handleHold(player, payload.slot(), payload.release(), payload.chargeTicks());
                            }
                        }))
                .playToServer(CastHoldPayload.TYPE, CastHoldPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.efkrdnz.magical.magic.cast.HoldService.setHeld(player, payload.slot(), payload.held());
                            }
                        }))
                .playToServer(CastLoadoutSlotPayload.TYPE, CastLoadoutSlotPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (payload.slot() >= 0 && payload.slot() < MagicContent.LOADOUT_SIZE && context.player() instanceof ServerPlayer player) {
                                MagicCastingService.castSlot(player, payload.slot(), payload.sneakDown());
                            }
                        }))
                .playToServer(CastWheelSkillPayload.TYPE, CastWheelSkillPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCastingService.castWheelSkill(player, payload.skillId());
                            }
                        }))
                .playToServer(CastWheelSubSkillPayload.TYPE, CastWheelSubSkillPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                if (MagicContent.SOUL_VOW.id().equals(payload.parentSkillId())) {
                                    com.efkrdnz.magical.magic.SoulAuthorityService.castWheelSoulVowMode(player, payload.mode());
                                } else {
                                    MagicCastingService.castWheelSubSkill(player, payload.parentSkillId(), payload.mode());
                                }
                            }
                        }))
                .playToServer(RefillBarrierPayload.TYPE, RefillBarrierPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCastingService.refillBarrier(player);
                            }
                        }))
                .playToServer(ClassActionPayload.TYPE, ClassActionPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player && payload.action() == ClassActionPayload.EVOLVE) {
                                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                                state.evolveClass(player, payload.classId());
                                state.sync(player);
                            }
                        }))
                .playToServer(OpenMagicCodexPayload.TYPE, OpenMagicCodexPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCodexService.open(player);
                            }
                        }))
                .playToServer(CounterResponsePayload.TYPE, CounterResponsePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCounterService.respond(player, payload.threatId());
                            }
                        }))
                .playToServer(SpaceSubspaceHoldPayload.TYPE, SpaceSubspaceHoldPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                SpaceAuthorityService.handleSubspaceHold(player, payload.slot(), payload.release(), payload.chargeTicks(), payload.followOwner());
                            }
                        }))
                .playToServer(SovereignAegisCastPayload.TYPE, SovereignAegisCastPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCastingService.castSovereignAegisMode(player, payload.slot(), payload.mode());
                            }
                        }))
                .playToServer(BlackFlamesCastPayload.TYPE, BlackFlamesCastPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCastingService.castBlackFlamesMode(player, payload.slot(), payload.mode());
                            }
                        }))
                .playToServer(SpaceOffenseCastPayload.TYPE, SpaceOffenseCastPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MagicCastingService.castSpaceOffenseMode(player, payload.slot(), payload.mode());
                            }
                        }))
                .playToServer(SoulVowCastPayload.TYPE, SoulVowCastPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.efkrdnz.magical.magic.SoulAuthorityService.castSoulVowMode(player, payload.slot(), payload.mode());
                            }
                        }))
                .playToServer(BlackFlamesSwingPayload.TYPE, BlackFlamesSwingPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.efkrdnz.magical.magic.BlackFlamesService.onImbuedSwordSwing(player);
                            }
                        }))
                .playToServer(MeleeSwingPayload.TYPE, MeleeSwingPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.efkrdnz.magical.magic.MeleeCombatService.onSwing(player, payload.whiff());
                            }
                        }))
                .playToServer(ApplySpaceRulePayload.TYPE, ApplySpaceRulePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                SpaceAuthorityService.applyRule(player, payload.category(), payload.operation(), payload.targetGroup());
                            }
                        }))
                .playToServer(SpaceWalkerActionPayload.TYPE, SpaceWalkerActionPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                switch (payload.action()) {
                                    case SpaceWalkerActionPayload.TELEPORT_COORDINATES -> SpaceWalkerService.teleportToCoordinates(player, payload.dimension(), payload.x(), payload.y(), payload.z());
                                    case SpaceWalkerActionPayload.CREATE_PORTAL_COORDINATES -> SpaceWalkerService.createPortalToCoordinates(player, payload.dimension(), payload.x(), payload.y(), payload.z());
                                    case SpaceWalkerActionPayload.SAVE_CURRENT -> SpaceWalkerService.saveWaypoint(player, payload.name(), payload.dimension(), payload.x(), payload.y(), payload.z());
                                    case SpaceWalkerActionPayload.TELEPORT_WAYPOINT -> SpaceWalkerService.teleportToWaypoint(player, payload.index());
                                    case SpaceWalkerActionPayload.CREATE_PORTAL_WAYPOINT -> SpaceWalkerService.createPortalToWaypoint(player, payload.index());
                                    case SpaceWalkerActionPayload.DELETE_WAYPOINT -> SpaceWalkerService.deleteWaypoint(player, payload.index());
                                    default -> {
                                    }
                                }
                            }
                        }));
    }

    private static void handleClientPayload(Object payload) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class<?> handler = Class.forName("com.efkrdnz.magical.client.ClientPayloadHandlers");
            handler.getMethod("handle", payload.getClass()).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to handle client payload " + payload.getClass().getName(), exception);
        }
    }

    public static void syncArcaneData(ServerPlayer player, ArcanePlayerData data) {
        PacketDistributor.sendToPlayer(player, new ArcanePlayerDataPayload(data.copy()));
    }

    public static void syncMagicState(ServerPlayer player, PlayerMagicState data) {
        PacketDistributor.sendToPlayer(player, new PlayerMagicStatePayload(data.copy()));
    }

    public static void playFirstPersonEffect(ServerPlayer player, FirstPersonEffectPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendStatusSync(ServerPlayer player, StatusSyncPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendCastHold(int slot, boolean held) {
        PacketDistributor.sendToServer(new CastHoldPayload(slot, held));
    }

    /** Transient visual event to every player within range of a point. */
    public static void sendVisualCue(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos, double range, VisualCuePayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, range, payload);
    }

    public static void playFirstPersonImpact(ServerPlayer player, int color, int overlayTicks, float alpha, int shakeTicks, float shakeStrength, int freezeTicks, float fovKick) {
        playFirstPersonEffect(player, FirstPersonEffectPayload.impact(color, overlayTicks, alpha, shakeTicks, shakeStrength, freezeTicks, fovKick));
    }

    public static void sendCounterPrompt(ServerPlayer player, int threatId, ResourceLocation incomingSkillId, ResourceLocation counterSkillId, long deadlineTick, int windowTicks) {
        PacketDistributor.sendToPlayer(player, new CounterPromptPayload(threatId, incomingSkillId, counterSkillId, deadlineTick, windowTicks));
    }

    public static void sendCounterClear(ServerPlayer player, int threatId) {
        PacketDistributor.sendToPlayer(player, new CounterClearPayload(threatId));
    }

    public static void sendCounterResponse(int threatId) {
        PacketDistributor.sendToServer(new CounterResponsePayload(threatId));
    }

    public static void sendCastRequest(int slot) {
        sendCastRequest(slot, false);
    }

    public static void sendCastRequest(int slot, boolean sneakDown) {
        PacketDistributor.sendToServer(new CastLoadoutSlotPayload(slot, sneakDown));
    }

    public static void sendBarrierRefillRequest() {
        PacketDistributor.sendToServer(new RefillBarrierPayload());
    }

    public static void sendWheelCastRequest(ResourceLocation skillId) {
        PacketDistributor.sendToServer(new CastWheelSkillPayload(skillId));
    }

    public static void sendWheelSubSkillCastRequest(ResourceLocation parentSkillId, int mode) {
        PacketDistributor.sendToServer(new CastWheelSubSkillPayload(parentSkillId, mode));
    }

    public static void sendOpenCodexRequest() {
        PacketDistributor.sendToServer(new OpenMagicCodexPayload());
    }

    public static void sendSpaceSubspaceHold(int slot, boolean release, int chargeTicks, boolean followOwner) {
        PacketDistributor.sendToServer(new SpaceSubspaceHoldPayload(slot, release, chargeTicks, followOwner));
    }

    public static void sendSovereignAegisCast(int slot, int mode) {
        PacketDistributor.sendToServer(new SovereignAegisCastPayload(slot, mode));
    }

    public static void sendBlackFlamesCast(int slot, int mode) {
        PacketDistributor.sendToServer(new BlackFlamesCastPayload(slot, mode));
    }

    public static void sendSpaceOffenseCast(int slot, int mode) {
        PacketDistributor.sendToServer(new SpaceOffenseCastPayload(slot, mode));
    }

    public static void sendSoulVowCast(int slot, int mode) {
        PacketDistributor.sendToServer(new SoulVowCastPayload(slot, mode));
    }

    public static void sendBlackFlamesSwing() {
        PacketDistributor.sendToServer(new BlackFlamesSwingPayload());
    }

    public static void sendMeleeSwing(boolean whiff) {
        PacketDistributor.sendToServer(new MeleeSwingPayload(whiff));
    }

    public static void sendSpaceRule(int category, int operation, int targetGroup) {
        PacketDistributor.sendToServer(new ApplySpaceRulePayload(category, operation, targetGroup));
    }

    public static void sendSpaceWalkerAction(int action, int index, String name, int x, int y, int z) {
        sendSpaceWalkerAction(action, index, name, "", x, y, z);
    }

    public static void sendSpaceWalkerAction(int action, int index, String name, String dimension, int x, int y, int z) {
        PacketDistributor.sendToServer(new SpaceWalkerActionPayload(action, index, name == null ? "" : name, dimension == null ? "" : dimension, x, y, z));
    }

    public static void sendClassEvolveRequest(ResourceLocation classId) {
        PacketDistributor.sendToServer(new ClassActionPayload(classId, ClassActionPayload.EVOLVE));
    }

    public static void sendMagicBarrageHold(int slot, boolean release, int chargeTicks) {
        PacketDistributor.sendToServer(new MagicBarrageHoldPayload(slot, release, chargeTicks));
    }

}
