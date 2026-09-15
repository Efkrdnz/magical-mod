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
    /**
     * Passives belonging to a forbidden school rather than to a class or a race. Kept as their own
     * category because they are earned by studying the school, not handed over by a tree node - but
     * they answer to the same handler rule, so the coverage test treats all three sets alike.
     */
    private static final Set<ResourceLocation> FORBIDDEN_PASSIVES = new LinkedHashSet<>();
    /**
     * The two halves of a Blood Sacrifice pact. Filled by {@link #ritualBoon} and
     * {@link #ritualPrice}; declared here for the same reason the three sets above are, so the
     * field initialisers further down can add to them.
     */
    private static final Set<ResourceLocation> RITUAL_BOONS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> RITUAL_PRICES = new LinkedHashSet<>();

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
    /**
     * The endurance line. Every level widens the barrier pool and makes each point of it stop more
     * than a point of damage.
     *
     * <p>Not to be confused with BARRIER_CONVERSION above, which spends barrier <em>as</em> mana.
     * This one is what barrier is for: the player is pinned at twenty health and always will be, so
     * everything that would otherwise have been "more health" has to arrive here instead.
     *
     * <p>{@code reductionPerLevel} stays zero deliberately. That field feeds
     * {@code PlayerMagicState.passiveReduction}, which cuts <em>all</em> incoming damage; this
     * passive's reduction only applies to what the barrier is actually soaking, and lives in
     * {@code absorbDamage} instead.
     */
    public static final MagicPassiveDefinition ENDURANCE = register("endurance", false, 8, 0.0F, 0, 0, 0x4AC7FF, 2, 12000);
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
    /**
     * DARK, layer -2. What a fully corrupted mage is left holding, and the one curse in this file
     * with teeth: while it holds, mana stops regenerating on its own
     * ({@code PlayerMagicState.tickServer}). Dispellable only once Corruption is back under
     * {@code DarkService.CURSE_AT}, which nothing but Purification can do - so the way out of it is
     * always the same way, and it is never the button on the codex alone.
     */
    public static final MagicPassiveDefinition CORRUPTION_CURSE =
            register("corruption_curse", true, 1, 0.0F, 3, 40, 0x5B3A78);

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

    // BLOOD, layer -1. Three, per the school's budget of six actives and three passives.
    public static final MagicPassiveDefinition BLOODSCENT = forbiddenPassive("bloodscent", 0xE8425E);
    public static final MagicPassiveDefinition CLOTTING = forbiddenPassive("clotting", 0x8A0B1E);
    public static final MagicPassiveDefinition VESSEL_OVERFLOWS = forbiddenPassive("vessel_overflows", 0xC4122B);

    // DARK, layer -2. Two, per the school's budget of four actives and two passives. One makes the
    // debt readable, the other makes it worse on purpose.
    public static final MagicPassiveDefinition LEDGER = forbiddenPassive("ledger", 0x8A6BB5);
    public static final MagicPassiveDefinition WILLING = forbiddenPassive("willing", 0x3F2456);

    // ELDRITCH, layer -5. Two, per the budget of six actives and two passives. Both change what
    // being noticed does to you: one keeps you seen for longer, the other makes the top a home.
    public static final MagicPassiveDefinition LIDLESS = forbiddenPassive("lidless", 0x5FEFD0);
    public static final MagicPassiveDefinition DEEP_BARGAIN = forbiddenPassive("deep_bargain", 0x06322A);


    // ---------------------------------------------------------------------------------------
    // BLOOD SACRIFICE, layer -1. Thirty-two temporary passives a ritual grants and a clock takes
    // back, plus the Hellbroker that brokers them.
    //
    // Every one is registered as a normal passive, prices included. A price is a curse in fiction,
    // but the codex's curse column carries a Dispel button and a price you can dispel is not a
    // price. What sorts them is RITUAL_BOONS / RITUAL_PRICES below, never the curse flag.
    //
    // Behaviour lives in magic/passive/SacrificeBoons and SacrificeCurses; point costs and list
    // order live in magic/blood/SacrificeCatalogue.
    // ---------------------------------------------------------------------------------------

    public static final MagicPassiveDefinition HELLBROKER = forbiddenPassive("hellbroker", 0x7A0E2E);

    // --- boons (crimson) ---
    public static final MagicPassiveDefinition CRIMSON_EDGE = ritualBoon("crimson_edge", 0xE8425E);
    public static final MagicPassiveDefinition LONG_REACH = ritualBoon("long_reach", 0xD4627A);
    public static final MagicPassiveDefinition UNFEELING = ritualBoon("unfeeling", 0xB05C6B);
    public static final MagicPassiveDefinition SURE_FOOTING = ritualBoon("sure_footing", 0xC97F86);
    public static final MagicPassiveDefinition SANGUINE_MIGHT = ritualBoon("sanguine_might", 0xD62839);
    public static final MagicPassiveDefinition QUICKENED_PULSE = ritualBoon("quickened_pulse", 0xFF5C74);
    public static final MagicPassiveDefinition THINNED_BLOOD = ritualBoon("thinned_blood", 0xE07A99);
    public static final MagicPassiveDefinition CLOTTED_HIDE = ritualBoon("clotted_hide", 0x9E3B44);
    public static final MagicPassiveDefinition VESSEL_SIPHON = ritualBoon("vessel_siphon", 0xC4122B);
    public static final MagicPassiveDefinition SCARLET_TIDE = ritualBoon("scarlet_tide", 0xEF4B5C);
    public static final MagicPassiveDefinition BLOOD_SCENT = ritualBoon("blood_scent", 0xFF7286);
    public static final MagicPassiveDefinition SECOND_HEART = ritualBoon("second_heart", 0xA8142E);
    public static final MagicPassiveDefinition HAEMOPHAGE = ritualBoon("haemophage", 0x8A0B1E);
    public static final MagicPassiveDefinition RACING_HEART = ritualBoon("racing_heart", 0xFF3355);
    /** The one boon that pays for a heavy pact, and what makes Hellbroker a build rather than a discount. */
    public static final MagicPassiveDefinition BLOODBORNE_FURY = ritualBoon("bloodborne_fury", 0xB01732);
    /** One-shot: spending it removes it, so the pact buys exactly one refused death. */
    public static final MagicPassiveDefinition IRONBLOOD = ritualBoon("ironblood", 0x7E2230);

    // --- prices (bruised) ---
    public static final MagicPassiveDefinition OPEN_WOUND = ritualPrice("open_wound", 0x6B2436);
    public static final MagicPassiveDefinition DULLED_SENSES = ritualPrice("dulled_senses", 0x4F3A55);
    public static final MagicPassiveDefinition THIN_SKIN = ritualPrice("thin_skin", 0x6E4350);
    public static final MagicPassiveDefinition LEADEN_STEP = ritualPrice("leaden_step", 0x40323F);
    public static final MagicPassiveDefinition WEEPING_VESSEL = ritualPrice("weeping_vessel", 0x5A2030);
    public static final MagicPassiveDefinition HEMORRHAGE = ritualPrice("hemorrhage", 0x8B1024);
    public static final MagicPassiveDefinition LIFE_TAX = ritualPrice("life_tax", 0x77132A);
    public static final MagicPassiveDefinition BRITTLE_BARRIER = ritualPrice("brittle_barrier", 0x3F5A70);
    public static final MagicPassiveDefinition SLOW_BLOOD = ritualPrice("slow_blood", 0x4A3550);
    public static final MagicPassiveDefinition GLASS_BONES = ritualPrice("glass_bones", 0x8E6B73);
    public static final MagicPassiveDefinition MANA_DROUGHT = ritualPrice("mana_drought", 0x574B7A);
    public static final MagicPassiveDefinition BINDING_CHAINS = ritualPrice("binding_chains", 0x5C4A3A);
    /** Never granted. {@code BloodSacrificeService.seal} rolls it into one of five real prices. */
    public static final MagicPassiveDefinition THE_UNKNOWN = ritualPrice("the_unknown", 0x2E2440);
    public static final MagicPassiveDefinition ECHOING_MISERY = ritualPrice("echoing_misery", 0x7A2038);
    public static final MagicPassiveDefinition SPELL_FIZZLE = ritualPrice("spell_fizzle", 0x453A66);
    /** The one price with no clock: corruption is permanent and only Purification lifts it. */
    public static final MagicPassiveDefinition BLOOD_DEBT = ritualPrice("blood_debt", 0x5B3A78);

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

    /**
     * A passive that comes with a forbidden school. Single-level and never a curse, like the other
     * two kinds - a forbidden school already charges its price through its own currency, and making
     * the passive itself a curse would be billing twice for the same choice.
     */
    private static MagicPassiveDefinition forbiddenPassive(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        FORBIDDEN_PASSIVES.add(definition.id());
        return definition;
    }

    /**
     * A boon a Blood Sacrifice can grant. Registered as a normal passive so that hasPassive,
     * isPassiveEnabled and ClassPassiveEffects.on all work on it with no special cases; what makes
     * it temporary is the clock {@code PlayerMagicState.grantRitualPassive} puts on it.
     */
    private static MagicPassiveDefinition ritualBoon(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        RITUAL_BOONS.add(definition.id());
        return definition;
    }

    /**
     * A price a Blood Sacrifice charges. Deliberately not a curse: the codex's curse column carries
     * a Dispel button, and a price the player can dispel is not a price.
     */
    private static MagicPassiveDefinition ritualPrice(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        RITUAL_PRICES.add(definition.id());
        return definition;
    }

    /** Ids of every ritual boon, in declaration order: cheapest first, as the screen lists them. */
    public static Set<ResourceLocation> ritualBoons() {
        return java.util.Collections.unmodifiableSet(RITUAL_BOONS);
    }

    /** Ids of every ritual price, in declaration order. */
    public static Set<ResourceLocation> ritualPrices() {
        return java.util.Collections.unmodifiableSet(RITUAL_PRICES);
    }

    public static boolean isRitualBoon(ResourceLocation id) {
        return RITUAL_BOONS.contains(id);
    }

    public static boolean isRitualPrice(ResourceLocation id) {
        return RITUAL_PRICES.contains(id);
    }

    /** True for anything a ritual grants: what the codex pins at the top and refuses to toggle. */
    public static boolean isRitual(ResourceLocation id) {
        return isRitualBoon(id) || isRitualPrice(id);
    }

    /** Ids of every forbidden-school passive, in declaration order. */
    public static Set<ResourceLocation> forbiddenPassives() {
        return java.util.Collections.unmodifiableSet(FORBIDDEN_PASSIVES);
    }

    public static boolean isForbiddenPassive(ResourceLocation id) {
        return FORBIDDEN_PASSIVES.contains(id);
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
