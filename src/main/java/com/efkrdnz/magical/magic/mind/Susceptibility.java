package com.efkrdnz.magical.magic.mind;

import java.util.Map;

/** How readily a kind of mind believes; anything unlisted is ordinary. The wielder is 0 by the caller. */
public final class Susceptibility {
    public static final float ORDINARY = 1.0F;

    private static final Map<String, Float> TABLE = Map.ofEntries(
            Map.entry("minecraft:zombie", 1.3F),
            Map.entry("minecraft:husk", 1.3F),
            Map.entry("minecraft:drowned", 1.3F),
            Map.entry("minecraft:villager", 1.2F),
            Map.entry("minecraft:creeper", 1.1F),
            Map.entry("minecraft:skeleton", 1.0F),
            Map.entry("minecraft:player", 1.0F),
            Map.entry("minecraft:spider", 0.9F),
            Map.entry("minecraft:witch", 0.5F),
            Map.entry("minecraft:enderman", 0.4F),
            Map.entry("minecraft:wither", 0.2F),
            Map.entry("minecraft:ender_dragon", 0.2F),
            Map.entry("minecraft:elder_guardian", 0.2F));

    private Susceptibility() {}

    public static float of(String entityTypeId) {
        return TABLE.getOrDefault(entityTypeId, ORDINARY);
    }

    /** A blind mind believes only what it can hear. */
    public static boolean blind(String entityTypeId) {
        return "minecraft:warden".equals(entityTypeId);
    }
}
