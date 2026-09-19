package com.efkrdnz.magical.magic.incantation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The wielder's book: four incantations, the verses they know, and the toggle Clause: Every Other
 * shares across all four. This is the whole of the Authority of Mana's authored state and the data
 * model the Grimoire screen will edit.
 */
public final class Grimoire {

    public static final int SLOTS = 4;

    private final Incantation[] incantations = new Incantation[SLOTS];
    private final Set<ResourceLocation> known = new LinkedHashSet<>();
    private boolean everyOtherSkip;

    public Grimoire() {
        for (int slot = 0; slot < SLOTS; slot++) {
            incantations[slot] = new Incantation();
        }
    }

    public Incantation incantation(int slot) {
        return incantations[Math.max(0, Math.min(SLOTS - 1, slot))];
    }

    public Set<ResourceLocation> known() {
        return Collections.unmodifiableSet(known);
    }

    public boolean knows(ResourceLocation id) {
        return known.contains(id);
    }

    public boolean learn(ResourceLocation id) {
        return known.add(id);
    }

    public void learnAll(Collection<ResourceLocation> ids) {
        known.addAll(ids);
    }

    /** Clause: Every Other's {@code GUN_ACTION_IF_HALF_STATUS}: returns whether to skip, then flips. */
    public boolean everyOtherSkipAndFlip() {
        boolean skip = everyOtherSkip;
        everyOtherSkip = !everyOtherSkip;
        return skip;
    }

    public void clear() {
        for (Incantation incantation : incantations) {
            incantation.clear();
        }
        known.clear();
        everyOtherSkip = false;
    }

    public void copyFrom(Grimoire other) {
        for (int slot = 0; slot < SLOTS; slot++) {
            incantations[slot].copyFrom(other.incantations[slot]);
        }
        known.clear();
        known.addAll(other.known);
        everyOtherSkip = other.everyOtherSkip;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Incantation incantation : incantations) {
            list.add(incantation.save());
        }
        tag.put("incantations", list);
        ListTag knownList = new ListTag();
        for (ResourceLocation id : known) {
            knownList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("known", knownList);
        tag.putBoolean("everyOther", everyOtherSkip);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        ListTag list = tag.getList("incantations", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < Math.min(SLOTS, list.size()); slot++) {
            incantations[slot].load(list.getCompound(slot));
        }
        ListTag knownList = tag.getList("known", Tag.TAG_STRING);
        for (int i = 0; i < knownList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(knownList.getString(i));
            if (id != null) {
                known.add(id);
            }
        }
        everyOtherSkip = tag.getBoolean("everyOther");
    }
}
