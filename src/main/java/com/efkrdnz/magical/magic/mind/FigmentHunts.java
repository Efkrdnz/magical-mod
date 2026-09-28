package com.efkrdnz.magical.magic.mind;

import java.util.Map;
import java.util.Set;

/** Who goes after, and who runs from, a creature they believe is there. Vanilla's instincts, by id. */
public final class FigmentHunts {
    private static final Set<String> TOWNSFOLK = Set.of("minecraft:villager", "minecraft:iron_golem", "minecraft:wandering_trader");
    private static final Set<String> MONSTERS = Set.of("minecraft:zombie", "minecraft:husk", "minecraft:drowned",
            "minecraft:zombie_villager", "minecraft:skeleton", "minecraft:stray", "minecraft:spider",
            "minecraft:cave_spider", "minecraft:pillager", "minecraft:vindicator", "minecraft:witch");

    private static final Map<String, Set<String>> HUNTS = Map.ofEntries(
            Map.entry("minecraft:zombie", TOWNSFOLK),
            Map.entry("minecraft:husk", TOWNSFOLK),
            Map.entry("minecraft:drowned", TOWNSFOLK),
            Map.entry("minecraft:zombie_villager", TOWNSFOLK),
            Map.entry("minecraft:skeleton", Set.of("minecraft:iron_golem", "minecraft:wolf")),
            Map.entry("minecraft:stray", Set.of("minecraft:iron_golem", "minecraft:wolf")),
            Map.entry("minecraft:spider", Set.of("minecraft:iron_golem")),
            Map.entry("minecraft:pillager", TOWNSFOLK),
            Map.entry("minecraft:vindicator", TOWNSFOLK),
            Map.entry("minecraft:iron_golem", MONSTERS),
            Map.entry("minecraft:snow_golem", MONSTERS),
            Map.entry("minecraft:wolf", Set.of("minecraft:skeleton", "minecraft:stray", "minecraft:sheep", "minecraft:rabbit", "minecraft:fox")));

    private static final Map<String, Set<String>> FEARS = Map.of(
            "minecraft:creeper", Set.of("minecraft:cat", "minecraft:ocelot"),
            "minecraft:skeleton", Set.of("minecraft:wolf"),
            "minecraft:stray", Set.of("minecraft:wolf"));

    private FigmentHunts() {}

    public static boolean hunts(String hunter, String prey) {
        return HUNTS.getOrDefault(hunter, Set.of()).contains(prey);
    }

    public static boolean fears(String fearer, String feared) {
        return FEARS.getOrDefault(fearer, Set.of()).contains(feared);
    }

    public static boolean huntsAny(String hunter) {
        return HUNTS.containsKey(hunter);
    }

    public static boolean fearsAny(String fearer) {
        return FEARS.containsKey(fearer);
    }
}
