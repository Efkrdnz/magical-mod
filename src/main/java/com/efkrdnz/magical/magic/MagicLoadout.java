package com.efkrdnz.magical.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * One named set of cast slots. Selecting a loadout is what Z/X/C/V point at.
 *
 * <p>Mutable rather than a record because the state that owns it is mutable throughout, and a
 * record would mean rebuilding the object on every slot edit for no gain.
 *
 * <p>A null slot is a real state, not a bug: a loadout may deliberately leave a key unbound, and
 * switching to it must leave that key doing nothing rather than keeping whatever the previous
 * loadout had there.
 */
public final class MagicLoadout {
    /** Long enough to be descriptive, short enough for the switcher's left-hand column. */
    public static final int MAX_NAME_LENGTH = 24;

    private String name;
    private final ResourceLocation[] slots = new ResourceLocation[MagicContent.LOADOUT_SIZE];

    public MagicLoadout(String name) {
        this.name = sanitizeName(name);
    }

    /**
     * Names arrive from a client packet, so they are never trusted.
     *
     * <p>Control characters and the section sign are stripped because the switcher and the codex
     * draw this straight into a GUI, and a colour code would let a loadout name repaint the screen
     * around it.
     */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            return "Loadout";
        }
        StringBuilder clean = new StringBuilder(MAX_NAME_LENGTH);
        for (int i = 0; i < raw.length() && clean.length() < MAX_NAME_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c >= ' ' && c != 127 && c != '§') {
                clean.append(c);
            }
        }
        String trimmed = clean.toString().trim();
        return trimmed.isEmpty() ? "Loadout" : trimmed;
    }

    public String name() {
        return name;
    }

    public void setName(String raw) {
        name = sanitizeName(raw);
    }

    public ResourceLocation slot(int index) {
        return index >= 0 && index < slots.length ? slots[index] : null;
    }

    public void setSlot(int index, ResourceLocation skillId) {
        if (index >= 0 && index < slots.length) {
            slots[index] = skillId;
        }
    }

    public int size() {
        return slots.length;
    }

    public boolean contains(ResourceLocation skillId) {
        if (skillId == null) {
            return false;
        }
        for (ResourceLocation slot : slots) {
            if (skillId.equals(slot)) {
                return true;
            }
        }
        return false;
    }

    /** True when nothing is bound. Such a loadout is kept in memory but left off the wire. */
    public boolean isEmpty() {
        for (ResourceLocation slot : slots) {
            if (slot != null) {
                return false;
            }
        }
        return true;
    }

    /** Drop a skill from every slot it occupies. Called when a skill is removed from the player. */
    public void forget(ResourceLocation skillId) {
        for (int i = 0; i < slots.length; i++) {
            if (skillId != null && skillId.equals(slots[i])) {
                slots[i] = null;
            }
        }
    }

    public MagicLoadout copy() {
        MagicLoadout copy = new MagicLoadout(name);
        System.arraycopy(slots, 0, copy.slots, 0, slots.length);
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        ListTag slotTags = new ListTag();
        for (ResourceLocation slot : slots) {
            slotTags.add(StringTag.valueOf(slot == null ? "" : slot.toString()));
        }
        tag.put("slots", slotTags);
        return tag;
    }

    /**
     * A slot naming a skill this build no longer has comes back empty rather than blocking the
     * load, the same way an unknown race does.
     */
    public static MagicLoadout load(CompoundTag tag) {
        MagicLoadout loadout = new MagicLoadout(tag.getString("name"));
        ListTag slotTags = tag.getList("slots", Tag.TAG_STRING);
        for (int i = 0; i < slotTags.size() && i < loadout.slots.length; i++) {
            String raw = slotTags.getString(i);
            if (raw.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null && MagicContent.get(id) != null) {
                loadout.slots[i] = id;
            }
        }
        return loadout;
    }
}
