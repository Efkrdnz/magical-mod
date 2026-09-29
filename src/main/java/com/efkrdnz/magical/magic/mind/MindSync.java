package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.network.BeliefSyncPayload;
import com.efkrdnz.magical.network.IllusionEndPayload;
import com.efkrdnz.magical.network.IllusionScenePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who hears about a scene, and what each of them is told. */
final class MindSync {
    static final double AUDIENCE_RANGE = 64.0;
    static final int AUDIENCE_INTERVAL = 20;
    static final int BELIEF_INTERVAL = 5;

    private MindSync() {}

    static void tick(ServerLevel level, LiveScene scene) {
        long age = level.getGameTime() - scene.bornAt();
        // An empty audience is re-checked every tick, so the wielder sees their scene on the tick it goes up.
        if (age % AUDIENCE_INTERVAL == 0 || scene.audience.isEmpty()) {
            scene.audience.removeIf(id -> !(level.getPlayerByUUID(id) instanceof ServerPlayer));
            for (ServerPlayer player : level.getPlayers(p -> p.getBoundingBox().intersects(scene.bounds().inflate(AUDIENCE_RANGE)))) {
                if (scene.audience.add(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, scenePayload(scene, player.getUUID().equals(scene.owner())));
                }
            }
        }
        if (age % BELIEF_INTERVAL != 0) {
            return;
        }
        List<Belief.Row> rows = scene.belief().rows();
        for (UUID id : scene.audience) {
            if (!(level.getPlayerByUUID(id) instanceof ServerPlayer player)) {
                continue;
            }
            boolean owner = id.equals(scene.owner());
            List<BeliefSyncPayload.Entry> entries = new ArrayList<>();
            for (Belief.Row row : rows) {
                if ((owner || row.viewer() == player.getId()) && entries.size() < BeliefSyncPayload.MAX_ENTRIES) {
                    entries.add(new BeliefSyncPayload.Entry(row.viewer(), row.element(),
                            (byte) Math.round(row.belief() * 100.0F), row.shattered()));
                }
            }
            PacketDistributor.sendToPlayer(player, new BeliefSyncPayload(scene.id(), entries));
        }
    }

    static void ended(LiveScene scene) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (UUID id : scene.audience) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new IllusionEndPayload(scene.id()));
            }
        }
    }

    static IllusionScenePayload scenePayload(LiveScene scene, boolean mine) {
        List<IllusionScenePayload.Cell> cells = new ArrayList<>();
        for (LiveScene.Element element : scene.elements()) {
            for (int i = 0; i < element.cells().size() && cells.size() < IllusionScenePayload.MAX_CELLS; i++) {
                BlockPos cell = element.cells().get(i);
                ResourceLocation id = ResourceLocation.tryParse(element.blockIds().get(i));
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    Block block = BuiltInRegistries.BLOCK.getValue(id);
                    cells.add(new IllusionScenePayload.Cell(cell, Block.getId(block.defaultBlockState()), element.index()));
                }
            }
        }
        return new IllusionScenePayload(scene.id(), mine, cells);
    }
}
