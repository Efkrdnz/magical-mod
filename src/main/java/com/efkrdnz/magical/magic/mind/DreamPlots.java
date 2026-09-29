package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Who owns which plot of the dream level, and what each owner's Dreamscape says. Saved with the level. */
final class DreamPlots extends SavedData {
    static final String NAME = "magical_dream_plots";

    private final Map<UUID, Dreamscape> scapes = new LinkedHashMap<>();
    private int next;

    DreamPlots() {}

    static DreamPlots of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DreamPlots::new, DreamPlots::load), NAME);
    }

    Dreamscape get(UUID owner) {
        return scapes.get(owner);
    }

    Dreamscape claim(UUID owner) {
        Dreamscape scape = new Dreamscape(next++);
        scapes.put(owner, scape);
        setDirty();
        return scape;
    }

    UUID ownerOf(int plot) {
        for (Map.Entry<UUID, Dreamscape> entry : scapes.entrySet()) {
            if (entry.getValue().plot() == plot) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** A Dreamscape is mutable: whoever changed one says so, or the change is not saved. */
    void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("next", next);
        ListTag list = new ListTag();
        scapes.forEach((owner, scape) -> {
            CompoundTag entry = DreamscapeNbt.save(scape);
            entry.putUUID("owner", owner);
            list.add(entry);
        });
        tag.put("scapes", list);
        return tag;
    }

    static DreamPlots load(CompoundTag tag, HolderLookup.Provider provider) {
        DreamPlots plots = new DreamPlots();
        plots.next = tag.getInt("next");
        for (Tag raw : tag.getList("scapes", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Dreamscape scape = DreamscapeNbt.load(entry);
            plots.scapes.put(entry.getUUID("owner"), scape);
            plots.next = Math.max(plots.next, scape.plot() + 1);
        }
        return plots;
    }
}
