package com.efkrdnz.magical.magic.mind;

import java.util.Map;
import java.util.Set;

/** What a kind of creature is like, as far as a viewer's common sense goes. */
public record CreatureTraits(boolean aquatic, boolean undead, Set<Reaction> natural, Set<Reaction> unnatural) {
    public static final CreatureTraits NEUTRAL = new CreatureTraits(false, false, Set.of(), Set.of());

    private static final CreatureTraits HUNTER = new CreatureTraits(false, false,
            Set.of(Reaction.CHASE, Reaction.APPROACH), Set.of(Reaction.FLEE));
    private static final CreatureTraits UNDEAD_HUNTER = new CreatureTraits(false, true,
            Set.of(Reaction.CHASE, Reaction.APPROACH), Set.of(Reaction.FLEE));
    private static final CreatureTraits PREY = new CreatureTraits(false, false,
            Set.of(Reaction.FLEE, Reaction.IGNORE), Set.of(Reaction.CHASE));
    private static final CreatureTraits WARDEN = new CreatureTraits(false, false,
            Set.of(Reaction.CHASE, Reaction.STARE), Set.of(Reaction.FLEE));
    private static final CreatureTraits PET = new CreatureTraits(false, false,
            Set.of(Reaction.IGNORE, Reaction.STARE, Reaction.APPROACH), Set.of(Reaction.CHASE));
    private static final CreatureTraits FISH = new CreatureTraits(true, false,
            Set.of(Reaction.IGNORE, Reaction.FLEE), Set.of(Reaction.CHASE));

    private static final Map<String, CreatureTraits> TABLE = Map.ofEntries(
            Map.entry("minecraft:zombie", UNDEAD_HUNTER),
            Map.entry("minecraft:drowned", UNDEAD_HUNTER),
            Map.entry("minecraft:zombie_villager", UNDEAD_HUNTER),
            Map.entry("minecraft:skeleton", UNDEAD_HUNTER),
            Map.entry("minecraft:stray", UNDEAD_HUNTER),
            Map.entry("minecraft:phantom", UNDEAD_HUNTER),
            Map.entry("minecraft:husk", HUNTER),
            Map.entry("minecraft:spider", HUNTER),
            Map.entry("minecraft:cave_spider", HUNTER),
            Map.entry("minecraft:creeper", HUNTER),
            Map.entry("minecraft:pillager", HUNTER),
            Map.entry("minecraft:vindicator", HUNTER),
            Map.entry("minecraft:witch", HUNTER),
            Map.entry("minecraft:enderman", HUNTER),
            Map.entry("minecraft:iron_golem", WARDEN),
            Map.entry("minecraft:wolf", WARDEN),
            Map.entry("minecraft:villager", PREY),
            Map.entry("minecraft:cow", PREY),
            Map.entry("minecraft:sheep", PREY),
            Map.entry("minecraft:pig", PREY),
            Map.entry("minecraft:chicken", PREY),
            Map.entry("minecraft:rabbit", PREY),
            Map.entry("minecraft:horse", PREY),
            Map.entry("minecraft:cat", PET),
            Map.entry("minecraft:ocelot", PET),
            Map.entry("minecraft:cod", FISH),
            Map.entry("minecraft:salmon", FISH),
            Map.entry("minecraft:tropical_fish", FISH),
            Map.entry("minecraft:pufferfish", FISH),
            Map.entry("minecraft:squid", FISH),
            Map.entry("minecraft:glow_squid", FISH),
            Map.entry("minecraft:dolphin", FISH),
            Map.entry("minecraft:axolotl", FISH));

    public static CreatureTraits of(String creatureId) {
        return TABLE.getOrDefault(creatureId, NEUTRAL);
    }
}
