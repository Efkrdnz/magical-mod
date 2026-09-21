package com.efkrdnz.magical.magic.service;

import com.efkrdnz.magical.MagicalMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Temporary block edits that always come back: every replaced state is recorded in a per-level
 * ledger (SavedData) keyed by edit id, so a crash or unload restores it on the next load (chunk by
 * chunk, as the chunks come back), and the owning entity restores it normally on expiry. Never
 * use it for permanent terrain changes.
 */
public final class ConjuredTerrainService {
    private static final String DATA_NAME = "magical_conjured_terrain";
    /** How often each level gives back the orphans whose chunks have come. */
    static final int ORPHAN_SWEEP_TICKS = 20;

    private ConjuredTerrainService() {}

    /** One reversible edit session. */
    public static final class Edit {
        private final UUID id;
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> originals = new ArrayList<>();

        private Edit(UUID id) {
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        public int size() {
            return positions.size();
        }

        public List<BlockPos> positions() {
            return positions;
        }
    }

    public static Edit begin(ServerLevel level) {
        Edit edit = new Edit(UUID.randomUUID());
        ledger(level).edits.put(edit.id, edit);
        ledger(level).setDirty();
        return edit;
    }

    /**
     * Replace a block, remembering the original (only the first replacement of a position counts).
     * A position another pending edit already holds changes hands: this edit takes over that
     * edit's original and the other forgets the position, so conjured matter laid over conjured
     * matter gives back the world that was there before either, and never the first conjuring.
     */
    public static boolean replace(ServerLevel level, Edit edit, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState original = level.getBlockState(pos);
        if (original == state) {
            return false;
        }
        Ledger ledger = ledger(level);
        if (!edit.positions.contains(pos)) {
            BlockState inherited = ledger.release(pos, edit);
            edit.positions.add(pos.immutable());
            edit.originals.add(inherited != null ? inherited : original);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        ledger.setDirty();
        return true;
    }

    /** Restore in reverse order (bottom-up placements come back top-down). */
    public static void restore(ServerLevel level, Edit edit) {
        for (int i = edit.positions.size() - 1; i >= 0; i--) {
            BlockPos pos = edit.positions.get(i);
            if (level.isLoaded(pos)) {
                level.setBlock(pos, edit.originals.get(i), Block.UPDATE_ALL);
            }
        }
        edit.positions.clear();
        edit.originals.clear();
        Ledger ledger = ledger(level);
        ledger.edits.remove(edit.id);
        ledger.setDirty();
    }

    /**
     * Restore every position still holding what was placed there ({@code stillOurs}) or nothing at
     * all, and yield the rest: a player who built over a conjured block keeps what they built. The
     * edit is closed either way.
     */
    public static void restoreUnlessBuiltOver(ServerLevel level, Edit edit, Predicate<BlockState> stillOurs) {
        for (int i = edit.positions.size() - 1; i >= 0; i--) {
            BlockPos pos = edit.positions.get(i);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            if (now.isAir() || stillOurs.test(now)) {
                level.setBlock(pos, edit.originals.get(i), Block.UPDATE_ALL);
            }
        }
        edit.positions.clear();
        edit.originals.clear();
        Ledger ledger = ledger(level);
        ledger.edits.remove(edit.id);
        ledger.setDirty();
    }

    /** Restore a subset (e.g. one layer) and keep the rest pending. */
    public static void restorePositions(ServerLevel level, Edit edit, List<BlockPos> subset) {
        for (BlockPos pos : subset) {
            int idx = edit.positions.indexOf(pos);
            if (idx >= 0) {
                if (level.isLoaded(pos)) {
                    level.setBlock(pos, edit.originals.get(idx), Block.UPDATE_ALL);
                }
                edit.positions.remove(idx);
                edit.originals.remove(idx);
            }
        }
        ledger(level).setDirty();
    }

    public static Edit lookup(ServerLevel level, UUID id) {
        return ledger(level).edits.get(id);
    }

    /**
     * Every edit still pending when the level loads belongs to nobody: its owner died with the last
     * session. Nothing is restored here, because a level has no chunks when it loads and a block in
     * a chunk that is not there cannot be set (it used to be tried, and every orphan was dropped
     * from the ledger untouched). The orphans are adopted, and {@link #restoreLoadedOrphans} gives
     * each back once its chunk is here; one whose chunk never loads this session is still in the
     * ledger for the next.
     */
    public static void restoreOrphans(ServerLevel level) {
        Ledger ledger = ledger(level);
        if (ledger.edits.isEmpty()) {
            return;
        }
        ledger.orphans.addAll(ledger.edits.keySet());
        MagicalMod.LOGGER.info("Adopted {} orphaned conjured-terrain edits in {}; they come back as their chunks load",
                ledger.orphans.size(), level.dimension().location());
    }

    /**
     * The orphans whose chunks are here, given back through the level so that their neighbours hear
     * of it - the water that flowed from a conjured source drains only once the removal of the
     * source reaches it, and a write straight into a loading chunk tells nobody - and an orphan with
     * nothing left is closed. Swept from the server tick, so a chunk is at most a sweep old when its
     * orphans come back.
     */
    public static void restoreLoadedOrphans(ServerLevel level) {
        Ledger ledger = ledger(level);
        if (ledger.orphans.isEmpty()) {
            return;
        }
        boolean touched = false;
        Iterator<UUID> ids = ledger.orphans.iterator();
        while (ids.hasNext()) {
            Edit edit = ledger.edits.get(ids.next());
            if (edit == null) {
                ids.remove();
                continue;
            }
            for (int i = edit.positions.size() - 1; i >= 0; i--) {
                BlockPos pos = edit.positions.get(i);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                level.setBlock(pos, edit.originals.get(i), Block.UPDATE_ALL);
                edit.positions.remove(i);
                edit.originals.remove(i);
                touched = true;
            }
            if (edit.positions.isEmpty()) {
                ledger.edits.remove(edit.id);
                ids.remove();
            }
        }
        if (touched) {
            ledger.setDirty();
        }
    }

    /** Every {@link #ORPHAN_SWEEP_TICKS}, each level gives back the orphans whose chunks have come. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % ORPHAN_SWEEP_TICKS != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            restoreLoadedOrphans(level);
        }
    }

    private static Ledger ledger(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Ledger::new, Ledger::load), DATA_NAME);
    }

    private static final class Ledger extends SavedData {
        private final Map<UUID, Edit> edits = new HashMap<>();
        /** The edits the level loaded with, waiting for their chunks. Not saved: at the next load every pending edit is an orphan again. */
        private final Set<UUID> orphans = new LinkedHashSet<>();

        private Ledger() {}

        /** The original another edit recorded for {@code pos}, that edit forgetting the position; null when no other edit holds it. */
        private BlockState release(BlockPos pos, Edit except) {
            for (Edit other : edits.values()) {
                if (other == except) {
                    continue;
                }
                int index = other.positions.indexOf(pos);
                if (index >= 0) {
                    BlockState original = other.originals.get(index);
                    other.positions.remove(index);
                    other.originals.remove(index);
                    return original;
                }
            }
            return null;
        }

        private static Ledger load(CompoundTag tag, HolderLookup.Provider provider) {
            Ledger ledger = new Ledger();
            HolderLookup<Block> blocks = provider.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
            ListTag list = tag.getList("Edits", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag editTag = list.getCompound(i);
                Edit edit = new Edit(editTag.getUUID("Id"));
                ListTag entries = editTag.getList("Blocks", Tag.TAG_COMPOUND);
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag entry = entries.getCompound(j);
                    edit.positions.add(BlockPos.of(entry.getLong("Pos")));
                    edit.originals.add(NbtUtils.readBlockState(blocks, entry.getCompound("State")));
                }
                ledger.edits.put(edit.id, edit);
            }
            return ledger;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Edit edit : edits.values()) {
                CompoundTag editTag = new CompoundTag();
                editTag.putUUID("Id", edit.id);
                ListTag entries = new ListTag();
                for (int i = 0; i < edit.positions.size(); i++) {
                    CompoundTag entry = new CompoundTag();
                    entry.putLong("Pos", edit.positions.get(i).asLong());
                    entry.put("State", NbtUtils.writeBlockState(edit.originals.get(i)));
                    entries.add(entry);
                }
                editTag.put("Blocks", entries);
                list.add(editTag);
            }
            tag.put("Edits", list);
            return tag;
        }
    }
}
