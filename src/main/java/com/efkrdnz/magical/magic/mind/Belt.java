package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;

/**
 * The nine lies at hand while Daydreaming, in place of the hotbar. Names only: a lie is an
 * impression key, never an item, so nothing on the belt can ever be carried out as matter.
 */
public final class Belt {
    public static final int SIZE = 9;

    private final String[] keys = new String[SIZE];

    public String get(int slot) {
        return slot < 0 || slot >= SIZE ? null : keys[slot];
    }

    public void set(int slot, String key) {
        if (slot >= 0 && slot < SIZE) {
            keys[slot] = key == null || key.isBlank() ? null : key;
        }
    }

    /** A newly learned lie goes to the first empty slot, as a picked-up item goes to the hotbar. */
    public boolean offer(String key) {
        if (key == null || indexOf(key) >= 0) {
            return false;
        }
        for (int i = 0; i < SIZE; i++) {
            if (keys[i] == null) {
                keys[i] = key;
                return true;
            }
        }
        return false;
    }

    public int indexOf(String key) {
        for (int i = 0; i < SIZE; i++) {
            if (key != null && key.equals(keys[i])) {
                return i;
            }
        }
        return -1;
    }

    public ListTag save() {
        ListTag tag = new ListTag();
        for (String key : keys) {
            tag.add(StringTag.valueOf(key == null ? "" : key));
        }
        return tag;
    }

    public void load(ListTag tag) {
        clear();
        for (int i = 0; i < SIZE && i < tag.size(); i++) {
            if (tag.get(i).getId() == Tag.TAG_STRING) {
                String key = tag.getString(i);
                keys[i] = Impression.parse(key) == null ? null : key;
            }
        }
    }

    public void copyFrom(Belt other) {
        System.arraycopy(other.keys, 0, keys, 0, SIZE);
    }

    public void clear() {
        Arrays.fill(keys, null);
    }
}
