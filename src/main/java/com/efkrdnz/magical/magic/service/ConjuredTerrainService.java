package com.efkrdnz.magical.magic.service;

import com.efkrdnz.magical.MagicalMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Temporary block edits that always come back: every replaced state is recorded in a per-level
 * ledger (SavedData) keyed by edit id, so a crash or unload restores it on next load, and the
 * owning entity restores it normally on expiry. Never use it for permanent terrain changes.
 */
public final class ConjuredTerrainService {
    private static final String DATA_NAME = "magical_conjured_terrain";

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

    /** Replace a block, remembering the original (only the first replacement of a position counts). */
    public static boolean replace(ServerLevel level, Edit edit, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState original = level.getBlockState(pos);
        if (original == state) {
            return false;
        }
        if (!edit.positions.contains(pos)) {
            edit.positions.add(pos.immutable());
            edit.originals.add(original);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        ledger(level).setDirty();
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

    /** Restore every edit still pending in the ledger (called on level load). */
    public static void restoreOrphans(ServerLevel level) {
        Ledger ledger = ledger(level);
        if (ledger.edits.isEmpty()) {
            return;
        }
        List<Edit> pending = new ArrayList<>(ledger.edits.values());
        for (Edit edit : pending) {
            restore(level, edit);
        }
        MagicalMod.LOGGER.info("Restored {} orphaned conjured-terrain edits in {}", pending.size(), level.dimension().location());
    }

    private static Ledger ledger(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Ledger::new, Ledger::load), DATA_NAME);
    }

    private static final class Ledger extends SavedData {
        private final Map<UUID, Edit> edits = new HashMap<>();

        private Ledger() {}

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
