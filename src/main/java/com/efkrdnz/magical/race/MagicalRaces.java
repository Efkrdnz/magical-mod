package com.efkrdnz.magical.race;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.arcane.Affinity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The six races, in the order the chooser shows them.
 *
 * <p>They follow the design document exactly, including Celestial being described as rare - that is
 * flavour here, not a gate. All six are offered on the same screen from the first second of a save.
 *
 * <p>Each race names one starter skill, which <em>replaces</em> the random bonus the awakening used
 * to roll. That is the point of picking one: the difference is in your hand on turn one rather than
 * somewhere up the progression.
 */
public final class MagicalRaces {
    private static final Map<ResourceLocation, MagicalRace> RACES = new LinkedHashMap<>();

    public static final MagicalRace HUMAN = register("human",
            List.of(Affinity.ARCANE),
            0, 0,
            MagicContent.LODESTONE.id(),
            MagicPassiveContent.ADAPTABLE.id(),
            0xFFD9C7A8);

    public static final MagicalRace ELF = register("elf",
            List.of(Affinity.ARCANE, Affinity.FIRE, Affinity.WATER),
            40, 0,
            MagicContent.RIME_SNAP.id(),
            MagicPassiveContent.DEEP_WELL.id(),
            0xFF9FE8C8);

    public static final MagicalRace DWARF = register("dwarf",
            List.of(Affinity.EARTH, Affinity.METAL),
            0, 30,
            MagicContent.SLAG_ROLLER.id(),
            MagicPassiveContent.FORGEBORN.id(),
            0xFFD98A4A);

    public static final MagicalRace BEASTKIN = register("beastkin",
            List.of(Affinity.NATURE),
            0, 10,
            MagicContent.SHORTREACH.id(),
            MagicPassiveContent.QUICKENED_INSTINCT.id(),
            0xFFA8D98A);

    public static final MagicalRace DEMON = register("demon",
            List.of(Affinity.SHADOW, Affinity.CHAOS),
            20, 0,
            MagicContent.HOLLOW_MAW.id(),
            MagicPassiveContent.CORRUPTION_RESISTANCE.id(),
            0xFFC2506E);

    public static final MagicalRace CELESTIAL = register("celestial",
            List.of(Affinity.DIVINE, Affinity.LIGHT),
            30, 20,
            MagicContent.GLINT.id(),
            MagicPassiveContent.BLESSED.id(),
            0xFFF7E38A);

    private MagicalRaces() {}

    private static MagicalRace register(String path, List<Affinity> affinities, int bonusMaxMana,
            int bonusMaxBarrier, ResourceLocation starterSkill, ResourceLocation passiveId, int color) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
        MagicalRace race = new MagicalRace(id, affinities, bonusMaxMana, bonusMaxBarrier, starterSkill, passiveId, color);
        RACES.put(id, race);
        return race;
    }

    /** In chooser order. */
    public static List<MagicalRace> all() {
        return List.copyOf(RACES.values());
    }

    public static MagicalRace get(ResourceLocation id) {
        return id == null ? null : RACES.get(id);
    }

    public static boolean exists(ResourceLocation id) {
        return get(id) != null;
    }

}
