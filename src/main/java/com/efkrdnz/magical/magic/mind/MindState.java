package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The wielder's side of the Authority of Mind: what they can imagine and three written scenes. */
public final class MindState {
    public static final int SLOTS = 3;

    private final Lexicon lexicon = new Lexicon();
    private final Reverie[] reveries = {new Reverie(), new Reverie(), new Reverie()};
    private final Belt belt = new Belt();
    private int activeSlot;

    public Lexicon lexicon() {
        return lexicon;
    }

    public Belt belt() {
        return belt;
    }

    public Reverie reverie(int slot) {
        return reveries[clamp(slot)];
    }

    public int activeSlot() {
        return activeSlot;
    }

    public void setActiveSlot(int slot) {
        activeSlot = clamp(slot);
    }

    public Reverie active() {
        return reveries[activeSlot];
    }

    private static int clamp(int slot) {
        return Math.max(0, Math.min(SLOTS - 1, slot));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("lexicon", lexicon.save());
        ListTag slots = new ListTag();
        for (Reverie reverie : reveries) {
            slots.add(ReverieNbt.save(reverie));
        }
        tag.put("reveries", slots);
        tag.put("belt", belt.save());
        tag.putInt("active", activeSlot);
        return tag;
    }

    public void load(CompoundTag tag) {
        lexicon.load(tag.getCompound("lexicon"));
        ListTag slots = tag.getList("reveries", Tag.TAG_COMPOUND);
        for (int i = 0; i < SLOTS; i++) {
            reveries[i].copyFrom(i < slots.size() ? ReverieNbt.load(slots.getCompound(i)) : new Reverie());
        }
        belt.load(tag.getList("belt", Tag.TAG_STRING));
        setActiveSlot(tag.getInt("active"));
    }

    public void copyFrom(MindState other) {
        lexicon.copyFrom(other.lexicon);
        for (int i = 0; i < SLOTS; i++) {
            reveries[i].copyFrom(other.reveries[i]);
        }
        belt.copyFrom(other.belt);
        activeSlot = other.activeSlot;
    }

    public void clear() {
        lexicon.clear();
        for (Reverie reverie : reveries) {
            reverie.clear();
        }
        belt.clear();
        activeSlot = 0;
    }
}
