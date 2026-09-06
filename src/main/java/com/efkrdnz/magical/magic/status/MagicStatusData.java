package com.efkrdnz.magical.magic.status;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Per-entity status table (not persisted: statuses are short-lived combat state). */
public final class MagicStatusData {
    private final Map<MagicStatus, Entry> entries = new EnumMap<>(MagicStatus.class);

    public record Entry(long expiryTick, int amplifier, float value, ResourceLocation sourceSkill, UUID source, float yaw, float pitch) {
    }

    public Map<MagicStatus, Entry> entries() {
        return entries;
    }

    public Entry get(MagicStatus status) {
        return entries.get(status);
    }

    public void put(MagicStatus status, Entry entry) {
        entries.put(status, entry);
    }

    public void remove(MagicStatus status) {
        entries.remove(status);
    }

    public boolean active(MagicStatus status, long now) {
        Entry entry = entries.get(status);
        return entry != null && entry.expiryTick() > now;
    }

    public void expire(long now) {
        entries.entrySet().removeIf(e -> e.getValue().expiryTick() <= now);
    }
}
