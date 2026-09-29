package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where a lie becomes real and stops being real. Every {@link MindSync#BELIEF_INTERVAL} ticks each
 * element's consensus is summed and held against its weight ({@link Consensus#real}). A cluster is
 * placed through {@link ConjuredTerrainService}, so what it replaced is always given back; a figment is
 * turned real by its own flag.
 */
final class Manifestation {
    private Manifestation() {}

    static void step(ServerLevel level, LiveScene scene, long now) {
        if ((now - scene.bornAt()) % MindSync.BELIEF_INTERVAL != 0) {
            return;
        }
        scene.consensus = scene.belief().consensus(scene.elements().size(), viewer -> voter(level, viewer));
        for (LiveScene.Element element : scene.elements()) {
            int index = element.index();
            if (scene.slain(index)) {
                continue;
            }
            boolean was = scene.manifested(index);
            boolean real = Consensus.real(was, scene.consensus(index), scene.weight(index));
            if (real && !was) {
                if (manifest(level, scene, element)) {
                    scene.manifested.add(index);
                    tell(level, scene, "message.magical.manifested");
                }
            } else if (!real && was) {
                unmanifest(level, scene, element, true);
            } else if (real && element.kind() == LiveScene.Kind.FIGMENT
                    && level.getEntity(scene.figmentEntity(index)) instanceof FigmentEntity figment) {
                figment.hold(KindStats.maxHealth(element.figment().creatureId())
                        * Consensus.healthFraction(scene.consensus(index), scene.weight(index)));
            }
        }
    }

    private static double voter(ServerLevel level, int viewer) {
        Entity entity = level.getEntity(viewer);
        return entity == null ? 0.0 : Consensus.voter(MindService.typeId(entity));
    }

    static boolean manifest(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        return switch (element.kind()) {
            case CLUSTER -> manifestCluster(level, scene, element);
            case FIGMENT -> manifestFigment(level, scene, element);
        };
    }

    static void unmanifest(ServerLevel level, LiveScene scene, LiveScene.Element element, boolean tell) {
        if (!scene.manifested.remove(element.index())) {
            return;
        }
        if (element.kind() == LiveScene.Kind.CLUSTER) {
            unmanifestCluster(level, scene, element);
        } else if (level.getEntity(scene.figmentEntity(element.index())) instanceof FigmentEntity figment) {
            figment.unmanifest();
        }
        Vec3 at = element.box().getCenter();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (tell) {
            tell(level, scene, "message.magical.unmanifested");
        }
    }

    /** Gives back everything a scene made real; called as the scene ends. */
    static void revertAll(LiveScene scene) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(scene.dimension());
        if (level == null) {
            return;
        }
        for (int index : scene.manifestedList()) {
            unmanifest(level, scene, scene.elements().get(index), false);
        }
    }

    /**
     * Whether {@code state} at {@code pos} is a block some scene actually placed: the position is in
     * that element's live edit and the state is one the element placed. The geometry of a scene is not
     * enough, or a real block built into a cell the wall refused would drop nothing and never burst.
     */
    static boolean holds(ServerLevel level, BlockPos pos, BlockState state) {
        for (LiveScene scene : MindService.scenesIn(level.dimension())) {
            int element = scene.elementAt(pos);
            if (element < 0 || !scene.manifested(element)) {
                continue;
            }
            UUID id = scene.edits.get(element);
            ConjuredTerrainService.Edit edit = id == null ? null : ConjuredTerrainService.lookup(level, id);
            if (edit != null && edit.positions().contains(pos) && placedStates(scene.elements().get(element)).contains(state)) {
                return true;
            }
        }
        return false;
    }

    private static Set<BlockState> placedStates(LiveScene.Element element) {
        Set<BlockState> placed = new HashSet<>();
        for (String blockId : element.blockIds()) {
            BlockState state = stateOf(blockId);
            if (state != null) {
                placed.add(state);
            }
        }
        return placed;
    }

    /**
     * A block that cannot be made real without escaping the guard: one that falls (and lands for
     * good), keeps a block entity, would not survive in its cell, or melts into water that stays.
     */
    static boolean refuses(ServerLevel level, BlockPos pos, String blockId, BlockState state) {
        return PhantomHarm.unmanifestable(blockId)
                || state == null
                || state.getBlock() instanceof FallingBlock
                || state.hasBlockEntity()
                || state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)
                || !state.canSurvive(level, pos);
    }

    static BlockState stateOf(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
    }

    private static boolean manifestCluster(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        // Real before it is placed, so a drop fired while a cell goes in is already caught by holds().
        scene.edits.put(element.index(), edit.id());
        scene.manifested.add(element.index());
        for (int i = 0; i < element.cells().size(); i++) {
            String id = element.blockIds().get(i);
            BlockState state = stateOf(id);
            BlockPos pos = element.cells().get(i);
            if (!level.isLoaded(pos) || refuses(level, pos, id, state)) {
                continue;
            }
            BlockState here = level.getBlockState(pos);
            if (!here.canBeReplaced() || here.hasBlockEntity() || !clearOfBodies(level, pos)) {
                continue;
            }
            if (ConjuredTerrainService.replace(level, edit, pos, state)) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            }
        }
        if (edit.size() == 0) {
            scene.edits.remove(element.index());
            scene.manifested.remove(element.index());
            ConjuredTerrainService.restore(level, edit);
            return false;
        }
        return true;
    }

    private static boolean manifestFigment(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        if (!(level.getEntity(scene.figmentEntity(element.index())) instanceof FigmentEntity figment) || !figment.isAlive()) {
            return false;
        }
        String kind = element.figment().creatureId();
        figment.manifest(KindStats.maxHealth(kind) * Consensus.healthFraction(scene.consensus(element.index()), scene.weight(element.index())),
                KindStats.attack(kind));
        level.playSound(null, figment.getX(), figment.getY(), figment.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static void unmanifestCluster(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        UUID id = scene.edits.remove(element.index());
        ConjuredTerrainService.Edit edit = id == null ? null : ConjuredTerrainService.lookup(level, id);
        if (edit == null) {
            return;
        }
        Set<BlockState> placed = placedStates(element);
        ConjuredTerrainService.restoreUnlessBuiltOver(level, edit, placed::contains);
    }

    /** No real block is put inside a living body; a ghost figment is no body. */
    private static boolean clearOfBodies(ServerLevel level, BlockPos pos) {
        List<LivingEntity> bodies = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                entity -> entity.isAlive() && !FigmentEntity.isFigment(entity));
        return bodies.isEmpty();
    }

    private static void tell(ServerLevel level, LiveScene scene, String key) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(scene.owner());
        if (owner != null) {
            owner.displayClientMessage(Component.translatable(key), true);
        }
    }
}
