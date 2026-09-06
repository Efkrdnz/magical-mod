package com.efkrdnz.magical.tower.archetype;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.tower.archetype.generator.LiminalLawnGenerator;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * The archetype registry, and the framework's extension point.
 *
 * <p>Adding a dungeon kind is two steps and touches no framework code:
 *
 * <pre>{@code
 * // 1. declare the dimension in data/magical/dimension{,_type}/tower_ruins.json
 * // 2. register it, with a generator once one exists
 * TowerArchetypes.register(new TowerArchetype(id, dimension, new RuinsGenerator(), 80));
 * }</pre>
 *
 * <p>The map is insertion ordered so command completions and listings are stable rather than in
 * hash order.
 *
 * <p><b>No new dimensions are declared yet.</b> Dungeon content is not designed, so the archetypes
 * below deliberately point at the three dimensions the mod already ships and all use
 * {@link VoidGenerator}. That is enough to exercise allocation, entry, travel and cleanup end to
 * end. Real archetypes replace these as their dimensions are designed.
 */
public final class TowerArchetypes {
    private static final Map<ResourceLocation, TowerArchetype> ARCHETYPES = new LinkedHashMap<>();

    /**
     * Placeholder archetypes over existing dimensions, so the framework is testable before any
     * dungeon is designed. Scaffolding, not a content decision.
     */
    public static final TowerArchetype PROVING_GROUND = register(placeholder("dungeon_tower", 80));

    public static final TowerArchetype OUTER_DARK = register(placeholder("chronos_end", 80));

    public static final TowerArchetype FOLDED_ROOM = register(placeholder("pocket_space", 72));

    /**
     * Floor 2 — The Kept Grounds. The first archetype with real content behind it: a flat mown
     * plain under a sourceless overcast sky, crowded with monoliths set close enough to box the
     * view in. See DUNGEON_FLOOR_2_KEPT_GROUNDS.md.
     */
    public static final TowerArchetype KEPT_GROUNDS = register(new TowerArchetype(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "tower_liminal"),
            ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "tower_liminal")),
            LiminalLawnGenerator.INSTANCE,
            5));

    private TowerArchetypes() {}

    /** Builds a placeholder archetype over an existing dimension in this mod's namespace. */
    public static TowerArchetype placeholder(String dimensionPath, int floorY) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, dimensionPath);
        return new TowerArchetype(
                id,
                ResourceKey.create(Registries.DIMENSION, id),
                VoidGenerator.INSTANCE,
                floorY);
    }

    /**
     * Registers an archetype.
     *
     * @throws IllegalStateException if the id is already taken, which is a programming error
     *                               rather than something to recover from at runtime
     */
    public static TowerArchetype register(TowerArchetype archetype) {
        TowerArchetype previous = ARCHETYPES.putIfAbsent(archetype.id(), archetype);
        if (previous != null) {
            throw new IllegalStateException("Duplicate tower archetype: " + archetype.id());
        }
        return archetype;
    }

    /** @return the archetype, or null if nothing is registered under that id */
    public static TowerArchetype get(ResourceLocation id) {
        return id == null ? null : ARCHETYPES.get(id);
    }

    public static Collection<TowerArchetype> all() {
        return Collections.unmodifiableCollection(ARCHETYPES.values());
    }

    public static Collection<ResourceLocation> ids() {
        return Collections.unmodifiableCollection(ARCHETYPES.keySet());
    }
}
