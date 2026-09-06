package com.efkrdnz.magical.tower.archetype;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A reusable dungeon dimension, plus how to fill a plot inside it.
 *
 * <p>An archetype is a blueprint, never an instance. Many runs share one archetype at the same
 * time, each in its own plot — see {@code tower.instance.PlotGrid}. That sharing is the whole
 * reason this framework exists: Minecraft dimensions are declared statically in a datapack and
 * cannot be created at runtime, so "one dimension per dungeon run" is not available to us.
 *
 * @param id        stable identity, used in commands and tower definitions
 * @param dimension the datapack dimension this archetype's plots live in
 * @param generator fills a plot; {@link VoidGenerator} until real content is designed
 * @param floorY    Y level plots are built at, per archetype, so a cave-like world and an open one
 *                  can sit at different heights
 */
public record TowerArchetype(
        ResourceLocation id,
        ResourceKey<Level> dimension,
        ArchetypeGenerator generator,
        int floorY) {

    /** True while this archetype still has no real dungeon content behind it. */
    public boolean isPlaceholder() {
        return generator == VoidGenerator.INSTANCE;
    }
}
