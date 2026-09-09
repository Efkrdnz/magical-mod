package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class MagicPassiveContent {
    private static final Map<ResourceLocation, MagicPassiveDefinition> PASSIVES = new LinkedHashMap<>();
    /** Filled by {@link #classPassive}; declared first so the field initialisers below can add to it. */
    private static final Set<ResourceLocation> CLASS_PASSIVES = new LinkedHashSet<>();
    /** Filled by {@link #racePassive}; same reason. */
    private static final Set<ResourceLocation> RACE_PASSIVES = new LinkedHashSet<>();

    public static final MagicPassiveDefinition MANA_SKIN = register("mana_skin", false, 1, 0.0F, 0, 0, 0x72E4FF, 1, 5000);
    public static final MagicPassiveDefinition HEAT_RESISTANCE = register("heat_resistance", false, 5, 0.2F, 0, 0, 0xFF7A45, 3, 35000);
    public static final MagicPassiveDefinition POISON_RESISTANCE = register("poison_resistance", false, 5, 0.2F, 0, 0, 0x82D957, 3, 35000);
    public static final MagicPassiveDefinition MAGIC_RESISTANCE = register("magic_resistance", false, 3, 0.2F, 0, 0, 0xA57DFF, 4, 85000);
    public static final MagicPassiveDefinition STEADY_BREATHING = register("steady_breathing", false, 1, 0.0F, 0, 0, 0xA6E3A1, 1, 4000);
    public static final MagicPassiveDefinition MANA_SUSTENANCE = register("mana_sustenance", false, 1, 0.0F, 0, 0, 0x7FEFD4, 1, 6500);
    public static final MagicPassiveDefinition COOLDOWN_ECHO = register("cooldown_echo", false, 3, 0.0F, 0, 0, 0xFFB85A);
    public static final MagicPassiveDefinition MANA_FLIGHT = register("mana_flight", false, 1, 0.0F, 0, 0, 0x7FEFD4);
    public static final MagicPassiveDefinition BARRIER_CONVERSION = register("barrier_conversion", false, 5, 0.0F, 0, 0, 0x4AC7FF, 2, 16000);
    public static final MagicPassiveDefinition VAULT_TAP = register("vault_tap", false, 1, 0.0F, 0, 0, 0xD7F75B, 3, 50000);
    public static final MagicPassiveDefinition SIN_PRIDE = register("sin_pride", false, 1, 0.0F, 0, 0, 0xFFD166);
    public static final MagicPassiveDefinition SIN_GREED = register("sin_greed", false, 1, 0.0F, 0, 0, 0xD7F75B);
    public static final MagicPassiveDefinition SIN_LUST = register("sin_lust", false, 1, 0.0F, 0, 0, 0xFF6FAE);
    public static final MagicPassiveDefinition SIN_ENVY = register("sin_envy", false, 1, 0.0F, 0, 0, 0x41D977);
    public static final MagicPassiveDefinition SIN_GLUTTONY = register("sin_gluttony", false, 1, 0.0F, 0, 0, 0xB878FF);
    public static final MagicPassiveDefinition SIN_WRATH = register("sin_wrath", false, 1, 0.0F, 0, 0, 0xFF3C38);
    public static final MagicPassiveDefinition SIN_SLOTH = register("sin_sloth", false, 1, 0.0F, 0, 0, 0x6F7FA8);
    public static final MagicPassiveDefinition MANA_LEAK_CURSE = register("mana_leak_curse", true, 1, 0.0F, 2, 40, 0xB192FF);
    public static final MagicPassiveDefinition SIN_PRIDE_CURSE = register("sin_pride_curse", true, 1, 0.0F, 0, 0, 0xFFD166);
    public static final MagicPassiveDefinition SIN_GREED_CURSE = register("sin_greed_curse", true, 1, 0.0F, 0, 0, 0xD7F75B);
    public static final MagicPassiveDefinition SIN_LUST_CURSE = register("sin_lust_curse", true, 1, 0.0F, 0, 0, 0xFF6FAE);
    public static final MagicPassiveDefinition SIN_ENVY_CURSE = register("sin_envy_curse", true, 1, 0.0F, 0, 0, 0x41D977);
    public static final MagicPassiveDefinition SIN_GLUTTONY_CURSE = register("sin_gluttony_curse", true, 1, 0.0F, 0, 0, 0xB878FF);
    public static final MagicPassiveDefinition SIN_WRATH_CURSE = register("sin_wrath_curse", true, 1, 0.0F, 0, 0, 0xFF3C38);
    public static final MagicPassiveDefinition SIN_SLOTH_CURSE = register("sin_sloth_curse", true, 1, 0.0F, 0, 0, 0x6F7FA8);

    // ---------------------------------------------------------------------------------------
    // Class passives. Exactly one node of the evolution trees grants each of these and nothing
    // else does, so a passive's name always tells you which class it came from. Behaviour lives
    // in com.efkrdnz.magical.magic.passive; ClassPassiveEffectsTest fails the build if one of
    // these is registered without a handler claiming it.
    // ---------------------------------------------------------------------------------------

    // --- Blacksmith: the forge (amber) ---
    public static final MagicPassiveDefinition BELLOWS_RHYTHM = classPassive("bellows_rhythm", 0xFFA23D);
    public static final MagicPassiveDefinition QUENCH = classPassive("quench", 0xFF7A2E);
    public static final MagicPassiveDefinition ETCHED_GROOVES = classPassive("etched_grooves", 0xE8C27A);
    public static final MagicPassiveDefinition FITTED_PLATE = classPassive("fitted_plate", 0xC9A66B);
    public static final MagicPassiveDefinition ANVIL_DEBT = classPassive("anvil_debt", 0xB8863F);
    public static final MagicPassiveDefinition EDGE_ALIGNMENT = classPassive("edge_alignment", 0xFFD08A);
    public static final MagicPassiveDefinition COUNTERSUNK = classPassive("countersunk", 0xD98E43);
    public static final MagicPassiveDefinition GILDED_LEDGER = classPassive("gilded_ledger", 0xFFCE4A);
    public static final MagicPassiveDefinition FORGE_HEAT = classPassive("forge_heat", 0xFF5C1F);

    // --- Warrior: blood and ground (red) ---
    public static final MagicPassiveDefinition SINGLE_COMBAT = classPassive("single_combat", 0xE8503C);
    public static final MagicPassiveDefinition BLOOD_TITHE = classPassive("blood_tithe", 0xC42D2D);
    public static final MagicPassiveDefinition FIRST_WALL = classPassive("first_wall", 0xD9705C);
    public static final MagicPassiveDefinition BRACED_STANCE = classPassive("braced_stance", 0xA85A4A);
    public static final MagicPassiveDefinition RED_PAYMENT = classPassive("red_payment", 0x9E1B1B);
    public static final MagicPassiveDefinition RALLY_CRY = classPassive("rally_cry", 0xFF8C6B);
    public static final MagicPassiveDefinition SCAR_TISSUE = classPassive("scar_tissue", 0xB08278);
    public static final MagicPassiveDefinition DEEPER_WOUNDS = classPassive("deeper_wounds", 0xFF3030);
    public static final MagicPassiveDefinition HELD_GROUND = classPassive("held_ground", 0xCF6A55);

    // --- Ranger: distance and the pack (green) ---
    public static final MagicPassiveDefinition BLOOD_TRAIL = classPassive("blood_trail", 0x8FBF5A);
    public static final MagicPassiveDefinition BROADHEAD = classPassive("broadhead", 0x6FA83F);
    public static final MagicPassiveDefinition LOOSE_GROUND = classPassive("loose_ground", 0x7A8C4A);
    public static final MagicPassiveDefinition KENNEL_BOND = classPassive("kennel_bond", 0xA8C98A);
    public static final MagicPassiveDefinition LONG_STRIDE = classPassive("long_stride", 0x5FD98A);
    public static final MagicPassiveDefinition HELD_BREATH = classPassive("held_breath", 0xC2E0A0);
    public static final MagicPassiveDefinition SHARE_THE_KILL = classPassive("share_the_kill", 0x74C46B);
    public static final MagicPassiveDefinition DISTANT_EYE = classPassive("distant_eye", 0x4FA36B);
    public static final MagicPassiveDefinition SECOND_PACK = classPassive("second_pack", 0x9ED97A);

    // --- Mystic: the well and the blade (violet) ---
    public static final MagicPassiveDefinition DEEPENING_WELL = classPassive("deepening_well", 0x8A7DFF);
    public static final MagicPassiveDefinition CLEAN_CASTING = classPassive("clean_casting", 0xB5A8FF);
    public static final MagicPassiveDefinition WARD_REBOUND = classPassive("ward_rebound", 0x6FA8FF);
    public static final MagicPassiveDefinition CADENCE = classPassive("cadence", 0xC08AFF);
    public static final MagicPassiveDefinition OVERCHANNEL = classPassive("overchannel", 0x9B4DFF);
    public static final MagicPassiveDefinition NULL_FIELD = classPassive("null_field", 0x7C7C9E);
    public static final MagicPassiveDefinition ABYSSAL_TITHE = classPassive("abyssal_tithe", 0x5C2A7A);
    public static final MagicPassiveDefinition MANIFOLD = classPassive("manifold", 0xD4C2FF);
    public static final MagicPassiveDefinition EDGE_OF_THOUGHT = classPassive("edge_of_thought", 0xA88CFF);

    // --- Alchemist: dose and reaction (acid) ---
    public static final MagicPassiveDefinition WILDCRAFT = classPassive("wildcraft", 0xB8D95A);
    public static final MagicPassiveDefinition CONCENTRATE = classPassive("concentrate", 0xD7E85F);
    public static final MagicPassiveDefinition BUILDING_TOLERANCE = classPassive("building_tolerance", 0x9EC24A);
    public static final MagicPassiveDefinition SLAG_COAT = classPassive("slag_coat", 0x8A8F6B);
    public static final MagicPassiveDefinition COMPOUNDING_VENOM = classPassive("compounding_venom", 0x6FD95A);
    public static final MagicPassiveDefinition MEASURED_DOSE = classPassive("measured_dose", 0xE0F08A);
    public static final MagicPassiveDefinition BASE_METALS = classPassive("base_metals", 0xE8C74A);
    public static final MagicPassiveDefinition MIASMA = classPassive("miasma", 0x7AA84A);
    public static final MagicPassiveDefinition EQUIVALENT_EXCHANGE = classPassive("equivalent_exchange", 0xF0E68A);

    // --- race identity passives ---
    // One each, granted with the race at first spawn and never switchable. They are what a race
    // *is*, so unlike a class passive there is no checkbox: see PlayerMagicState.togglePassive.
    public static final MagicPassiveDefinition ADAPTABLE = racePassive("adaptable", 0xD9C7A8);
    public static final MagicPassiveDefinition DEEP_WELL = racePassive("deep_well", 0x9FE8C8);
    public static final MagicPassiveDefinition FORGEBORN = racePassive("forgeborn", 0xD98A4A);
    public static final MagicPassiveDefinition QUICKENED_INSTINCT = racePassive("quickened_instinct", 0xA8D98A);
    public static final MagicPassiveDefinition CORRUPTION_RESISTANCE = racePassive("corruption_resistance", 0xC2506E);
    public static final MagicPassiveDefinition BLESSED = racePassive("blessed", 0xF7E38A);

    public static final Set<ResourceLocation> STARTER_PASSIVES = Set.of(
            MANA_SKIN.id(),
            HEAT_RESISTANCE.id(),
            POISON_RESISTANCE.id(),
            MAGIC_RESISTANCE.id(),
            STEADY_BREATHING.id());

    private MagicPassiveContent() {}

    /**
     * A passive granted by exactly one evolution-tree node. Always single-level: there is no
     * level-up UI, so anything above level 1 would be unreachable in normal play.
     */
    private static MagicPassiveDefinition classPassive(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        CLASS_PASSIVES.add(definition.id());
        return definition;
    }

    /**
     * A passive granted by a race rather than earned. Single-level like a class passive, and never
     * a curse - the negative half of a race, where one exists, is expressed as a smaller bonus
     * elsewhere rather than as something the player has to dispel.
     */
    private static MagicPassiveDefinition racePassive(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        RACE_PASSIVES.add(definition.id());
        return definition;
    }

    /** Ids of every race passive, in declaration order. */
    public static Set<ResourceLocation> racePassives() {
        return java.util.Collections.unmodifiableSet(RACE_PASSIVES);
    }

    public static boolean isRacePassive(ResourceLocation id) {
        return RACE_PASSIVES.contains(id);
    }

    /** Ids of every class-granted passive, in declaration order. */
    public static Set<ResourceLocation> classPassives() {
        return java.util.Collections.unmodifiableSet(CLASS_PASSIVES);
    }

    public static boolean isClassPassive(ResourceLocation id) {
        return CLASS_PASSIVES.contains(id);
    }

    private static MagicPassiveDefinition register(String path, boolean curse, int maxLevel, float reductionPerLevel, int requiredProficiencyToDispel, int dispelManaCost, int color) {
        return register(path, curse, maxLevel, reductionPerLevel, requiredProficiencyToDispel, dispelManaCost, color, 0, 0);
    }

    private static MagicPassiveDefinition register(String path, boolean curse, int maxLevel, float reductionPerLevel, int requiredProficiencyToDispel, int dispelManaCost, int color, int shopTier, int shopCost) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
        MagicPassiveDefinition definition = new MagicPassiveDefinition(id, curse, maxLevel, reductionPerLevel, requiredProficiencyToDispel, dispelManaCost, color, shopTier, shopCost);
        PASSIVES.put(id, definition);
        return definition;
    }

    public static MagicPassiveDefinition get(ResourceLocation id) {
        return PASSIVES.get(id);
    }

    public static List<MagicPassiveDefinition> normalPassives() {
        return PASSIVES.values().stream().filter(definition -> !definition.curse()).toList();
    }

    public static List<MagicPassiveDefinition> curses() {
        return PASSIVES.values().stream().filter(MagicPassiveDefinition::curse).toList();
    }

    public static Collection<MagicPassiveDefinition> all() {
        return PASSIVES.values();
    }

    public static boolean isSinPassive(ResourceLocation id) {
        return id != null && PASSIVES.containsKey(id) && id.getPath().startsWith("sin_") && !id.getPath().endsWith("_curse");
    }

    public static ResourceLocation linkedSinPassiveForCurse(ResourceLocation curseId) {
        if (curseId == null || !curseId.getPath().startsWith("sin_") || !curseId.getPath().endsWith("_curse")) {
            return null;
        }
        ResourceLocation passiveId = ResourceLocation.fromNamespaceAndPath(curseId.getNamespace(), curseId.getPath().substring(0, curseId.getPath().length() - "_curse".length()));
        return isSinPassive(passiveId) ? passiveId : null;
    }

    public static ResourceLocation linkedCurseForSinPassive(ResourceLocation passiveId) {
        if (!isSinPassive(passiveId)) {
            return null;
        }
        ResourceLocation curseId = ResourceLocation.fromNamespaceAndPath(passiveId.getNamespace(), passiveId.getPath() + "_curse");
        MagicPassiveDefinition definition = PASSIVES.get(curseId);
        return definition != null && definition.curse() ? curseId : null;
    }

    public static List<String> normalCommandIds() {
        return normalPassives().stream().map(definition -> definition.id().getPath()).toList();
    }

    public static List<String> curseCommandIds() {
        return curses().stream().map(definition -> definition.id().getPath()).toList();
    }
}
