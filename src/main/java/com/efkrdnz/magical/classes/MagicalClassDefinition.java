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
 *
 * <p>{@code secret} is one property rather than a growing list of special-cased ids: the stock UI
 * does not draw a secret node, does not offer it as a tower wish and does not name it in tab
 * completion until its tree's base is owned. It is emphatically <em>not</em> a security boundary -
 * {@code MagicalClasses} is common code the client imports and every {@code class.magical.*} string
 * ships in plaintext in {@code en_us.json}. <b>"Hidden" means the stock UI does not draw it, never
 * that the client cannot know.</b> The value is the rite, not the ignorance.</p>
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
        List<ResourceLocation> evolutions,
        boolean secret) {

    public MagicalClassDefinition {
        parents = parents == null ? List.of() : List.copyOf(parents);
        rewardSkills = rewardSkills == null ? List.of() : List.copyOf(rewardSkills);
        rewardPassives = rewardPassives == null ? List.of() : List.copyOf(rewardPassives);
        evolutions = evolutions == null ? List.of() : List.copyOf(evolutions);
    }

    /**
     * The ordinary nine-argument shape: a class everybody can see. Kept as a delegating constructor
     * so adding {@code secret} did not have to touch fifty-two registrations and four helpers, none
     * of which have anything to say about it.
     */
    public MagicalClassDefinition(
            ResourceLocation id,
            List<ResourceLocation> parents,
            int tier,
            String nameKey,
            String descriptionKey,
            int xpCost,
            List<ResourceLocation> rewardSkills,
            List<ResourceLocation> rewardPassives,
            List<ResourceLocation> evolutions) {
        this(id, parents, tier, nameKey, descriptionKey, xpCost, rewardSkills, rewardPassives, evolutions, false);
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
