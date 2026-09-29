package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where a lie becomes real and stops being real. Every {@link MindSync#BELIEF_INTERVAL} ticks each
 * element's consensus is summed and held against its weight ({@link Consensus#real}). A cluster is
 * placed through {@link ConjuredTerrainService}, so what it replaced is always given back; a figment is
 * turned real by its own flag.
 */
final class Manifestation {
    /**
     * Blocks that pass every other refusal and still make something real on their own: an egg or
     * frogspawn hatches a real creature, TNT primes into a real explosion, powder snow is picked up
     * with a bucket, scaffolding falls as a block entity of its own (it is not {@link Fallable}).
     */
    static final TagKey<Block> NEVER_MANIFESTS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "never_manifests"));

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
        // Where the element is now: a figment has walked since it was written.
        Vec3 at = MindService.liveBox(level, scene, element).getCenter();
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
     * Whether some scene holds {@code pos}: the position is in the live edit of a manifested element.
     * The edit owns the position, not a state - a fence that reconnected, a lamp that was powered or a
     * log that was stripped is still imagined matter, and whatever stands there comes back to the
     * world's original when the element does. A cell the element refused, or one a player built into
     * after breaking it ({@link #forget}), is not in the edit and is not held.
     */
    static boolean holds(ServerLevel level, BlockPos pos) {
        return editHolding(level, pos) != null;
    }

    /** The edit holding {@code pos}, or null when no manifested element of any scene here holds it. */
    static ConjuredTerrainService.Edit editHolding(ServerLevel level, BlockPos pos) {
        for (LiveScene scene : MindService.scenesIn(level.dimension())) {
            int element = scene.elementAt(pos);
            if (element < 0 || !scene.manifested(element)) {
                continue;
            }
            UUID id = scene.edits.get(element);
            ConjuredTerrainService.Edit edit = id == null ? null : ConjuredTerrainService.lookup(level, id);
            if (edit != null && edit.positions().contains(pos)) {
                return edit;
            }
        }
        return null;
    }

    /** A player placed a block into a held cell: it is theirs, and the element gives it nothing back. */
    static void forget(ServerLevel level, BlockPos pos) {
        ConjuredTerrainService.Edit edit = editHolding(level, pos);
        if (edit != null) {
            ConjuredTerrainService.forget(level, edit, pos);
        }
    }

    /**
     * A block that cannot be made real without escaping the guard. See {@link #refusesByNature} for
     * what is refused whatever the cell; here also a block that would not survive where it stands.
     */
    static boolean refuses(ServerLevel level, BlockPos pos, String blockId, BlockState state) {
        return refusesByNature(blockId, state) || !state.canSurvive(level, pos);
    }

    /**
     * Refused whatever the cell: an unknown id; fire, lava and water; one that falls and lands
     * somewhere no edit holds ({@link Fallable}); one that keeps a block entity; ice that melts into
     * water that stays; one that ticks at random, because it grows, spreads, decays or hatches into
     * something real; one bone meal grows into more; and anything in {@link #NEVER_MANIFESTS}.
     */
    static boolean refusesByNature(String blockId, BlockState state) {
        return PhantomHarm.unmanifestable(blockId)
                || state == null
                || state.getBlock() instanceof Fallable
                || state.hasBlockEntity()
                || state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)
                || state.isRandomlyTicking()
                || state.getBlock() instanceof BonemealableBlock
                || state.is(NEVER_MANIFESTS);
    }

    static BlockState stateOf(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
    }

    private static boolean manifestCluster(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        List<Integer> placeable = new ArrayList<>();
        for (int i = 0; i < element.cells().size(); i++) {
            String id = element.blockIds().get(i);
            BlockPos pos = element.cells().get(i);
            if (placeable(level, pos, id)) {
                placeable.add(i);
            }
        }
        if (placeable.isEmpty()) {
            // Agreed on and nothing placeable: no edit is opened, or it would be opened and closed
            // (and the ledger saved) every step for as long as the agreement lasts.
            return false;
        }
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        // Real before it is placed, so a drop fired while a cell goes in is already caught by holds().
        scene.edits.put(element.index(), edit.id());
        scene.manifested.add(element.index());
        for (int i : placeable) {
            BlockState state = stateOf(element.blockIds().get(i));
            BlockPos pos = element.cells().get(i);
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
        // Everything still in the edit is imagined matter, whatever it has turned into; a block a
        // player placed was forgotten from the edit as it went in.
        ConjuredTerrainService.restore(level, edit);
    }

    /** Whether a cell can take its block now: loaded, not refused, free to replace and clear of bodies. */
    private static boolean placeable(ServerLevel level, BlockPos pos, String blockId) {
        if (!level.isLoaded(pos) || refuses(level, pos, blockId, stateOf(blockId))) {
            return false;
        }
        BlockState here = level.getBlockState(pos);
        return here.canBeReplaced() && !here.hasBlockEntity() && clearOfBodies(level, pos);
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
