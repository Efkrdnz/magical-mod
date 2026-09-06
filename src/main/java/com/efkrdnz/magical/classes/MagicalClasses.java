package com.efkrdnz.magical.classes;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The class registry. Every base class owns one evolution tree of the same shape:
 *
 * <pre>base -&gt; 4 Discipline (t1) -&gt; 3 Mastery (t2) -&gt; 2 Apex (t3)</pre>
 *
 * Each Mastery lists two adjacent Disciplines as parents and each Apex lists two adjacent
 * Masteries, so the paths diverge four ways and converge back into two endings; owning any one
 * parent is enough to take a node. The eight pre-existing evolutions are reused as Apex nodes so
 * old saves keep their classes and reward skills.
 *
 * <p>Registration order matters: {@link #index(ResourceLocation)} is insertion order and feeds the
 * codex button ids {@code BUTTON_CLASS_SELECT_BASE} (700) and {@code BUTTON_EVOLVE_CLASS_BASE}
 * (900). With 52 classes those reach 751 and 951, clear of the next base. **The ceiling is 99
 * classes**; past that the codex button ranges collide.</p>
 */
public final class MagicalClasses {
    // --- bases ---
    public static final ResourceLocation BLACKSMITH = id("blacksmith");
    public static final ResourceLocation WARRIOR = id("warrior");
    public static final ResourceLocation RANGER = id("ranger");
    public static final ResourceLocation MYSTIC = id("mystic");
    public static final ResourceLocation ALCHEMIST = id("alchemist");
    public static final ResourceLocation SPELL_CREATOR = id("spell_creator");

    // --- apexes (pre-existing ids, kept so saves survive) ---
    public static final ResourceLocation DIVINESMITH = id("divinesmith");
    public static final ResourceLocation WARSMITH = id("warsmith");
    public static final ResourceLocation BERSERKER = id("berserker");
    public static final ResourceLocation WARDEN = id("warden");
    public static final ResourceLocation BEASTMASTER = id("beastmaster");
    public static final ResourceLocation SHARPSHOOTER = id("sharpshooter");
    public static final ResourceLocation ARCHMAGE = id("archmage");
    public static final ResourceLocation SPELLBLADE = id("spellblade");
    public static final ResourceLocation PLAGUEDOCTOR = id("plaguedoctor");
    public static final ResourceLocation TRANSMUTER = id("transmuter");
    public static final ResourceLocation MAGIC_ORIGINATOR = id("magic_originator");

    // --- blacksmith line ---
    public static final ResourceLocation APPRENTICE = id("apprentice");
    public static final ResourceLocation TEMPERSMITH = id("tempersmith");
    public static final ResourceLocation ENGRAVER = id("engraver");
    public static final ResourceLocation ARMORER = id("armorer");
    public static final ResourceLocation RUNEWRIGHT = id("runewright");
    public static final ResourceLocation WEAPONWRIGHT = id("weaponwright");
    public static final ResourceLocation BULWARKSMITH = id("bulwarksmith");

    // --- warrior line ---
    public static final ResourceLocation DUELIST = id("duelist");
    public static final ResourceLocation VANGUARD = id("vanguard");
    public static final ResourceLocation REAVER = id("reaver");
    public static final ResourceLocation SENTINEL = id("sentinel");
    public static final ResourceLocation BLOODLETTER = id("bloodletter");
    public static final ResourceLocation WARCALLER = id("warcaller");
    public static final ResourceLocation IRONHIDE = id("ironhide");

    // --- ranger line ---
    public static final ResourceLocation TRACKER = id("tracker");
    public static final ResourceLocation FLETCHER = id("fletcher");
    public static final ResourceLocation TRAPPER = id("trapper");
    public static final ResourceLocation HOUNDMASTER = id("houndmaster");
    public static final ResourceLocation PATHFINDER = id("pathfinder");
    public static final ResourceLocation MARKSMAN = id("marksman");
    public static final ResourceLocation PACKLEADER = id("packleader");

    // --- mystic line ---
    public static final ResourceLocation CHANNELER = id("channeler");
    public static final ResourceLocation WARDER = id("warder");
    public static final ResourceLocation ADEPT = id("adept");
    public static final ResourceLocation BLADESINGER = id("bladesinger");
    public static final ResourceLocation EVOKER = id("evoker");
    public static final ResourceLocation ABJURER = id("abjurer");
    public static final ResourceLocation DUSKBLADE = id("duskblade");

    // --- alchemist line ---
    public static final ResourceLocation HERBALIST = id("herbalist");
    public static final ResourceLocation DISTILLER = id("distiller");
    public static final ResourceLocation TOXICOLOGIST = id("toxicologist");
    public static final ResourceLocation METALLURGIST = id("metallurgist");
    public static final ResourceLocation APOTHECARY = id("apothecary");
    public static final ResourceLocation VENOMANCER = id("venomancer");
    public static final ResourceLocation CHRYSOPOEIAN = id("chrysopoeian");

    /** XP spent from the base pool, by tier. */
    public static final int COST_DISCIPLINE = 30;
    public static final int COST_MASTERY = 80;
    public static final int COST_APEX = 150;

    private static final Map<ResourceLocation, MagicalClassDefinition> CLASSES = new LinkedHashMap<>();
    /** passive id -&gt; the class that grants it, filled on first use by {@link #classGranting}. */
    private static final Map<ResourceLocation, ResourceLocation> PASSIVE_SOURCES = new LinkedHashMap<>();

    static {
        // Every non-base node below grants exactly one passive that nothing else in the game
        // grants, so a passive name always tells you which class it came from. The seven sins are
        // the exception: they have no other source in the mod, so they ride along on seven of the
        // ten apexes - one apex each, where before some appeared on two different lines.

        // ---------------- Blacksmith ----------------
        base(BLACKSMITH, List.of(APPRENTICE, TEMPERSMITH, ENGRAVER, ARMORER),
                List.of(MagicContent.HEATED_IRON.id()), List.of());
        discipline(APPRENTICE, BLACKSMITH, List.of(WEAPONWRIGHT), List.of(), List.of(MagicPassiveContent.BELLOWS_RHYTHM.id()));
        discipline(TEMPERSMITH, BLACKSMITH, List.of(RUNEWRIGHT, WEAPONWRIGHT), List.of(), List.of(MagicPassiveContent.QUENCH.id()));
        discipline(ENGRAVER, BLACKSMITH, List.of(RUNEWRIGHT, BULWARKSMITH), List.of(), List.of(MagicPassiveContent.ETCHED_GROOVES.id()));
        discipline(ARMORER, BLACKSMITH, List.of(BULWARKSMITH), List.of(), List.of(MagicPassiveContent.FITTED_PLATE.id()));
        mastery(RUNEWRIGHT, List.of(ENGRAVER, TEMPERSMITH), List.of(DIVINESMITH),
                List.of(MagicContent.SIGIL_FORGE.id()), List.of(MagicPassiveContent.ANVIL_DEBT.id()));
        mastery(WEAPONWRIGHT, List.of(APPRENTICE, TEMPERSMITH), List.of(DIVINESMITH, WARSMITH),
                List.of(), List.of(MagicPassiveContent.EDGE_ALIGNMENT.id()));
        mastery(BULWARKSMITH, List.of(ARMORER, ENGRAVER), List.of(WARSMITH),
                List.of(), List.of(MagicPassiveContent.COUNTERSUNK.id()));
        apex(DIVINESMITH, List.of(RUNEWRIGHT, WEAPONWRIGHT),
                List.of(MagicContent.DAWNHAMMER.id(), MagicContent.GILDED_CHAIN.id()),
                List.of(MagicPassiveContent.GILDED_LEDGER.id(), MagicPassiveContent.SIN_PRIDE.id()));
        apex(WARSMITH, List.of(WEAPONWRIGHT, BULWARKSMITH),
                List.of(MagicContent.ANVIL_FALL.id()),
                List.of(MagicPassiveContent.FORGE_HEAT.id(), MagicPassiveContent.SIN_WRATH.id()));

        // ---------------- Warrior ----------------
        base(WARRIOR, List.of(DUELIST, VANGUARD, REAVER, SENTINEL),
                List.of(MagicContent.IRON_CHARGE.id(), MagicContent.SUNDER_GRIP.id()), List.of());
        discipline(DUELIST, WARRIOR, List.of(BLOODLETTER), List.of(), List.of(MagicPassiveContent.SINGLE_COMBAT.id()));
        discipline(REAVER, WARRIOR, List.of(BLOODLETTER, WARCALLER), List.of(), List.of(MagicPassiveContent.BLOOD_TITHE.id()));
        discipline(VANGUARD, WARRIOR, List.of(WARCALLER, IRONHIDE), List.of(), List.of(MagicPassiveContent.FIRST_WALL.id()));
        discipline(SENTINEL, WARRIOR, List.of(IRONHIDE), List.of(), List.of(MagicPassiveContent.BRACED_STANCE.id()));
        mastery(BLOODLETTER, List.of(DUELIST, REAVER), List.of(BERSERKER),
                List.of(), List.of(MagicPassiveContent.RED_PAYMENT.id()));
        mastery(WARCALLER, List.of(REAVER, VANGUARD), List.of(BERSERKER, WARDEN),
                List.of(MagicContent.WAR_HORN.id()), List.of(MagicPassiveContent.RALLY_CRY.id()));
        mastery(IRONHIDE, List.of(VANGUARD, SENTINEL), List.of(WARDEN),
                List.of(), List.of(MagicPassiveContent.SCAR_TISSUE.id()));
        apex(BERSERKER, List.of(BLOODLETTER, WARCALLER),
                List.of(MagicContent.BLOODSHOUT.id()),
                List.of(MagicPassiveContent.DEEPER_WOUNDS.id(), MagicPassiveContent.SIN_GLUTTONY.id()));
        apex(WARDEN, List.of(WARCALLER, IRONHIDE),
                List.of(MagicContent.LIVING_BULWARK.id()), List.of(MagicPassiveContent.HELD_GROUND.id()));

        // ---------------- Ranger ----------------
        base(RANGER, List.of(TRACKER, FLETCHER, TRAPPER, HOUNDMASTER),
                List.of(MagicContent.HAIL_VOLLEY.id()), List.of());
        discipline(FLETCHER, RANGER, List.of(MARKSMAN), List.of(), List.of(MagicPassiveContent.BROADHEAD.id()));
        discipline(TRAPPER, RANGER, List.of(MARKSMAN, PATHFINDER), List.of(), List.of(MagicPassiveContent.LOOSE_GROUND.id()));
        discipline(TRACKER, RANGER, List.of(PATHFINDER, PACKLEADER), List.of(), List.of(MagicPassiveContent.BLOOD_TRAIL.id()));
        discipline(HOUNDMASTER, RANGER, List.of(PACKLEADER), List.of(), List.of(MagicPassiveContent.KENNEL_BOND.id()));
        mastery(MARKSMAN, List.of(FLETCHER, TRAPPER), List.of(SHARPSHOOTER),
                List.of(MagicContent.SIGHT_LINE.id()), List.of(MagicPassiveContent.HELD_BREATH.id()));
        mastery(PATHFINDER, List.of(TRAPPER, TRACKER), List.of(SHARPSHOOTER, BEASTMASTER),
                List.of(), List.of(MagicPassiveContent.LONG_STRIDE.id()));
        mastery(PACKLEADER, List.of(TRACKER, HOUNDMASTER), List.of(BEASTMASTER),
                List.of(), List.of(MagicPassiveContent.SHARE_THE_KILL.id()));
        apex(SHARPSHOOTER, List.of(MARKSMAN, PATHFINDER),
                List.of(MagicContent.LEAD_SHOT.id()),
                List.of(MagicPassiveContent.DISTANT_EYE.id(), MagicPassiveContent.SIN_ENVY.id()));
        apex(BEASTMASTER, List.of(PATHFINDER, PACKLEADER),
                List.of(MagicContent.SPIRIT_WOLF.id()),
                List.of(MagicPassiveContent.SECOND_PACK.id(), MagicPassiveContent.SIN_LUST.id()));

        // ---------------- Mystic ----------------
        base(MYSTIC, List.of(CHANNELER, WARDER, ADEPT, BLADESINGER),
                List.of(MagicContent.ARCANE_GRASP.id()), List.of());
        discipline(CHANNELER, MYSTIC, List.of(EVOKER), List.of(), List.of(MagicPassiveContent.DEEPENING_WELL.id()));
        discipline(ADEPT, MYSTIC, List.of(EVOKER, ABJURER), List.of(), List.of(MagicPassiveContent.CLEAN_CASTING.id()));
        discipline(WARDER, MYSTIC, List.of(ABJURER, DUSKBLADE), List.of(), List.of(MagicPassiveContent.WARD_REBOUND.id()));
        discipline(BLADESINGER, MYSTIC, List.of(DUSKBLADE), List.of(), List.of(MagicPassiveContent.CADENCE.id()));
        // Cooldown Echo and Mana Flight have no other source in the mod, so one node each keeps them
        // reachable now that the duplicated grants are gone.
        mastery(EVOKER, List.of(CHANNELER, ADEPT), List.of(ARCHMAGE),
                List.of(MagicContent.MANA_BLOOM.id()),
                List.of(MagicPassiveContent.OVERCHANNEL.id(), MagicPassiveContent.COOLDOWN_ECHO.id()));
        mastery(ABJURER, List.of(ADEPT, WARDER), List.of(ARCHMAGE, SPELLBLADE),
                List.of(), List.of(MagicPassiveContent.NULL_FIELD.id()));
        // The Duskblade is where the abyss opens: this is the only source of abyssal_discharge,
        // which is a required input to the black_flames fusion and was otherwise unobtainable.
        mastery(DUSKBLADE, List.of(WARDER, BLADESINGER), List.of(SPELLBLADE),
                List.of(MagicContent.ABYSSAL_DISCHARGE.id()), List.of(MagicPassiveContent.ABYSSAL_TITHE.id()));
        apex(ARCHMAGE, List.of(EVOKER, ABJURER),
                List.of(MagicContent.ARCANUM_HARE.id()),
                List.of(MagicPassiveContent.MANIFOLD.id(), MagicPassiveContent.MANA_FLIGHT.id()));
        apex(SPELLBLADE, List.of(ABJURER, DUSKBLADE),
                List.of(MagicContent.LEECH_CUT.id()),
                List.of(MagicPassiveContent.EDGE_OF_THOUGHT.id(), MagicPassiveContent.SIN_SLOTH.id()));

        // ---------------- Alchemist ----------------
        base(ALCHEMIST, List.of(HERBALIST, DISTILLER, TOXICOLOGIST, METALLURGIST),
                List.of(MagicContent.SOMNOLENT_DRAUGHT.id()), List.of());
        discipline(TOXICOLOGIST, ALCHEMIST, List.of(VENOMANCER), List.of(), List.of(MagicPassiveContent.BUILDING_TOLERANCE.id()));
        discipline(HERBALIST, ALCHEMIST, List.of(VENOMANCER, APOTHECARY), List.of(), List.of(MagicPassiveContent.WILDCRAFT.id()));
        discipline(DISTILLER, ALCHEMIST, List.of(APOTHECARY, CHRYSOPOEIAN), List.of(), List.of(MagicPassiveContent.CONCENTRATE.id()));
        discipline(METALLURGIST, ALCHEMIST, List.of(CHRYSOPOEIAN), List.of(), List.of(MagicPassiveContent.SLAG_COAT.id()));
        mastery(VENOMANCER, List.of(TOXICOLOGIST, HERBALIST), List.of(PLAGUEDOCTOR),
                List.of(MagicContent.VIAL_BREAK.id()), List.of(MagicPassiveContent.COMPOUNDING_VENOM.id()));
        mastery(APOTHECARY, List.of(HERBALIST, DISTILLER), List.of(PLAGUEDOCTOR, TRANSMUTER),
                List.of(), List.of(MagicPassiveContent.MEASURED_DOSE.id()));
        mastery(CHRYSOPOEIAN, List.of(DISTILLER, METALLURGIST), List.of(TRANSMUTER),
                List.of(), List.of(MagicPassiveContent.BASE_METALS.id()));
        apex(PLAGUEDOCTOR, List.of(VENOMANCER, APOTHECARY),
                List.of(MagicContent.CONTAGION.id()), List.of(MagicPassiveContent.MIASMA.id()));
        apex(TRANSMUTER, List.of(APOTHECARY, CHRYSOPOEIAN),
                List.of(MagicContent.UNSTABLE_COMPOUND.id()),
                List.of(MagicPassiveContent.EQUIVALENT_EXCHANGE.id(), MagicPassiveContent.SIN_GREED.id()));

        // ---------------- Spell Creator (special utility line, not a starting class) ----------------
        register(new MagicalClassDefinition(SPELL_CREATOR, List.of(), 0,
                nameKey(SPELL_CREATOR), descriptionKey(SPELL_CREATOR), 0,
                List.of(), List.of(), List.of(MAGIC_ORIGINATOR)));
        register(new MagicalClassDefinition(MAGIC_ORIGINATOR, List.of(SPELL_CREATOR), 1,
                nameKey(MAGIC_ORIGINATOR), descriptionKey(MAGIC_ORIGINATOR), 140,
                List.of(), List.of(), List.of()));
    }

    private MagicalClasses() {}

    // ---- registration helpers ----

    private static void base(ResourceLocation id, List<ResourceLocation> evolutions,
            List<ResourceLocation> skills, List<ResourceLocation> passives) {
        register(new MagicalClassDefinition(id, List.of(), 0, nameKey(id), descriptionKey(id), 0, skills, passives, evolutions));
    }

    private static void discipline(ResourceLocation id, ResourceLocation parent, List<ResourceLocation> evolutions,
            List<ResourceLocation> skills, List<ResourceLocation> passives) {
        register(new MagicalClassDefinition(id, List.of(parent), 1, nameKey(id), descriptionKey(id), COST_DISCIPLINE, skills, passives, evolutions));
    }

    private static void mastery(ResourceLocation id, List<ResourceLocation> parents, List<ResourceLocation> evolutions,
            List<ResourceLocation> skills, List<ResourceLocation> passives) {
        register(new MagicalClassDefinition(id, parents, 2, nameKey(id), descriptionKey(id), COST_MASTERY, skills, passives, evolutions));
    }

    private static void apex(ResourceLocation id, List<ResourceLocation> parents,
            List<ResourceLocation> skills, List<ResourceLocation> passives) {
        register(new MagicalClassDefinition(id, parents, 3, nameKey(id), descriptionKey(id), COST_APEX, skills, passives, List.of()));
    }

    private static String nameKey(ResourceLocation id) {
        return "class.magical." + id.getPath();
    }

    private static String descriptionKey(ResourceLocation id) {
        return "class.magical." + id.getPath() + ".desc";
    }

    // ---- queries ----

    public static Collection<MagicalClassDefinition> all() {
        return CLASSES.values();
    }

    /**
     * The node that hands out a passive, or null if nothing does. Built lazily from the reward lists
     * so it cannot drift out of step with them; the codex uses it to say where a passive came from.
     */
    public static ResourceLocation classGranting(ResourceLocation passiveId) {
        if (PASSIVE_SOURCES.isEmpty()) {
            for (MagicalClassDefinition definition : CLASSES.values()) {
                for (ResourceLocation granted : definition.rewardPassives()) {
                    PASSIVE_SOURCES.putIfAbsent(granted, definition.id());
                }
            }
        }
        return PASSIVE_SOURCES.get(passiveId);
    }

    public static MagicalClassDefinition get(ResourceLocation id) {
        return CLASSES.get(id);
    }

    public static List<MagicalClassDefinition> roots() {
        return CLASSES.values().stream().filter(MagicalClassDefinition::isBase).toList();
    }

    public static List<MagicalClassDefinition> startingRoots() {
        return roots().stream().filter(definition -> !SPELL_CREATOR.equals(definition.id())).toList();
    }

    /** Every node whose tree is rooted at the given base, the base itself included, in registration order. */
    public static List<MagicalClassDefinition> treeOf(ResourceLocation baseId) {
        List<MagicalClassDefinition> nodes = new ArrayList<>();
        for (MagicalClassDefinition definition : CLASSES.values()) {
            if (baseId.equals(baseOf(definition.id()))) {
                nodes.add(definition);
            }
        }
        return nodes;
    }

    /** Walks parents up to the base class of the node's tree; returns the id itself for a base. */
    public static ResourceLocation baseOf(ResourceLocation id) {
        MagicalClassDefinition definition = get(id);
        int guard = 0;
        while (definition != null && !definition.isBase() && guard++ < 16) {
            MagicalClassDefinition parent = get(definition.parentId());
            if (parent == null) {
                break;
            }
            definition = parent;
        }
        return definition == null ? id : definition.id();
    }

    /** Direct children of a node, derived from the parents lists so convergence is respected. */
    public static List<MagicalClassDefinition> childrenOf(ResourceLocation id) {
        return CLASSES.values().stream().filter(definition -> definition.parents().contains(id)).toList();
    }

    public static int index(ResourceLocation id) {
        int index = 0;
        for (ResourceLocation classId : CLASSES.keySet()) {
            if (classId.equals(id)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    public static List<String> commandIds() {
        return CLASSES.keySet().stream().map(ResourceLocation::getPath).toList();
    }

    public static List<String> rootCommandIds() {
        return roots().stream().map(definition -> definition.id().getPath()).toList();
    }

    public static List<String> startingRootCommandIds() {
        return startingRoots().stream().map(definition -> definition.id().getPath()).toList();
    }

    public static boolean isRoot(ResourceLocation id) {
        MagicalClassDefinition definition = get(id);
        return definition != null && definition.isBase();
    }

    public static boolean isStartingRoot(ResourceLocation id) {
        MagicalClassDefinition definition = get(id);
        return definition != null && definition.isBase() && !SPELL_CREATOR.equals(id);
    }

    private static void register(MagicalClassDefinition definition) {
        CLASSES.put(definition.id(), definition);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
    }
}
