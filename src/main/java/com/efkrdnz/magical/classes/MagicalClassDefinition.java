package com.efkrdnz.magical.classes;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One node of a class evolution tree.
 *
 * <p>{@code parents} is a list rather than a single id so branches can converge: a node reachable
 * from several mid-tier paths lists them all, and owning <em>any</em> one of them is enough to
 * take it. {@code tier} is 0 for a base class and 1..3 outward, which drives the radial ring in
 * the class tree screen and lets the shape be validated. {@code xpCost} is spent from the base
 * class's pool, not merely compared against it.</p>
 */
public record MagicalClassDefinition(
        ResourceLocation id,
        List<ResourceLocation> parents,
        int tier,
        String nameKey,
        String descriptionKey,
        int xpCost,
        List<ResourceLocation> rewardSkills,
        List<ResourceLocation> rewardPassives,
        List<ResourceLocation> evolutions) {

    public MagicalClassDefinition {
        parents = parents == null ? List.of() : List.copyOf(parents);
        rewardSkills = rewardSkills == null ? List.of() : List.copyOf(rewardSkills);
        rewardPassives = rewardPassives == null ? List.of() : List.copyOf(rewardPassives);
        evolutions = evolutions == null ? List.of() : List.copyOf(evolutions);
    }

    /** The first parent, or null for a base class. Kept so single-parent callers still read naturally. */
    public ResourceLocation parentId() {
        return parents.isEmpty() ? null : parents.get(0);
    }

    public boolean isBase() {
        return parents.isEmpty();
    }

    /** Legacy accessor: the XP cost used to be a threshold named this way. */
    public int requiredClassXp() {
        return xpCost;
    }
}
