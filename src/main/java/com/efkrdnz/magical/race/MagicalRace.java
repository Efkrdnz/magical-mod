package com.efkrdnz.magical.race;

import com.efkrdnz.magical.arcane.Affinity;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One of the six races, chosen once at first spawn and never again.
 *
 * <p>A race is deliberately not a class. It grants no skills over time, gates nothing, and does not
 * interact with the class tree at all - the design document's line is "not restrictions,
 * affinities". What it does is set where a character starts: the size of their pools, the one skill
 * they wake up knowing, and a single passive that never turns off.
 *
 * <p>{@code affinities} is carried for the chooser to display and for later systems to read. It
 * intentionally drives no mechanic yet; naming a tendency the player can see is the point.
 */
public record MagicalRace(
        ResourceLocation id,
        List<Affinity> affinities,
        int bonusMaxMana,
        int bonusMaxBarrier,
        ResourceLocation starterSkill,
        ResourceLocation passiveId,
        int color) {

    public MagicalRace {
        affinities = affinities == null ? List.of() : List.copyOf(affinities);
    }

    public String nameKey() {
        return "race.magical." + id.getPath();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    /** The passive's own name, so the chooser can say what you are signing up for. */
    public String passiveNameKey() {
        return "passive.magical." + passiveId.getPath();
    }
}
