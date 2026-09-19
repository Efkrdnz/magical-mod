package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * One incantation: an order of verses, each with its uses left, and a breath (the root draw
 * budget, Noita's spells per cast made a property of the tape). Writing re-seeds uses from the
 * catalogue; play writes spent uses back through {@link #setUses}.
 */
public final class Incantation {

    public record Entry(ResourceLocation id, int usesRemaining) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int breath = ReciteCaps.MIN_BREATH;

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int breath() {
        return breath;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Replaces everything. False, and nothing changed, when the validator objects. */
    public boolean write(List<ResourceLocation> ids, int breath, VerseCatalogue catalogue) {
        if (!IncantationValidator.problems(ids, breath, catalogue).isEmpty()) {
            return false;
        }
        entries.clear();
        for (ResourceLocation id : ids) {
            entries.add(new Entry(id, catalogue.get(id).maxUses()));
        }
        this.breath = breath;
        return true;
    }

    public void setUses(int index, int uses) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.set(index, new Entry(entries.get(index).id(), Math.max(Verse.UNLIMITED, uses)));
    }

    public void clear() {
        entries.clear();
        breath = ReciteCaps.MIN_BREATH;
    }

    public void copyFrom(Incantation other) {
        entries.clear();
        entries.addAll(other.entries);
        breath = other.breath;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("breath", breath);
        ListTag verses = new ListTag();
        for (Entry entry : entries) {
            CompoundTag verse = new CompoundTag();
            verse.putString("id", entry.id().toString());
            verse.putInt("uses", entry.usesRemaining());
            verses.add(verse);
        }
        tag.put("verses", verses);
        return tag;
    }

    /** Anything unreadable is dropped rather than poisoning the tape; the numbers are clamped. */
    public void load(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        breath = Math.max(ReciteCaps.MIN_BREATH, Math.min(ReciteCaps.MAX_BREATH, tag.getInt("breath")));
        ListTag verses = tag.getList("verses", Tag.TAG_COMPOUND);
        for (int i = 0; i < verses.size() && entries.size() < ReciteCaps.MAX_VERSES; i++) {
            CompoundTag verse = verses.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(verse.getString("id"));
            if (id == null) {
                continue;
            }
            entries.add(new Entry(id, Math.max(Verse.UNLIMITED, verse.getInt("uses"))));
        }
    }
}
