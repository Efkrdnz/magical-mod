package com.efkrdnz.magical.classes;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
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
 * (900). With 56 classes those reach 755 and 955, clear of the next base. **The ceiling is 100
 * classes**; past that the codex button ranges collide. A new class therefore goes at the
 * <em>end</em> of the static block and nowhere else, because the index is the button id.</p>
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

    // --- the Sword Summoner line: a hidden chain, found by the rite rather than chosen ---
    public static final ResourceLocation SWORD_SUMMONER = id("sword_summoner");
    public static final ResourceLocation SWORD_RIDER = id("sword_rider");
    public static final ResourceLocation SWORD_SAINT = id("sword_saint");
    public static final ResourceLocation SWORD_GOD = id("sword_god");

    /** XP spent from the base pool, by tier. */
    public static final int COST_DISCIPLINE = 30;
    public static final int COST_MASTERY = 80;
    public static final int COST_APEX = 150;

    /**
     * The Sword Summoner chain prices itself rather than reusing the tier costs above: it is four
     * rungs of one line instead of a fan, so the whole ladder is 370 pooled at its base, and each
     * rung must cost strictly more than its parent or {@code ClassTreeTest} fails.
     */
    public static final int COST_SWORD_SUMMONER = 0;
    public static final int COST_SWORD_RIDER = 40;
    public static final int COST_SWORD_SAINT = 110;
    public static final int COST_SWORD_GOD = 220;

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

        // ------- Sword Summoner (hidden, and registered LAST for a reason) -------
        //
        // A class button id is the registration index on both sides - ClassTreeMenu.nodes() and
        // MagicPyramidMenu both walk all() positionally - so a class inserted anywhere but the end
        // silently renumbers every class button after it and nothing in the build checks it.
        //
        // Sword Summoner is a ROOT that is not a starting root, which is the Spell Creator's shape
        // taken for one more reason: evolveClass returns early on isBase(), so the forged packet
        // MagicalNetwork hands straight to evolveClass cannot take a secret base, and the three
        // rungs above it are safe because their parent is itself secret. Never graft a secret rung
        // onto a visible tree. It also buys exemption from the four tree-shape tests, absence from
        // the first-spawn chooser and, most of all, no re-sectoring: a sixth *starting* base turns
        // 360/5 into 360/6 and moves four of five trees for every player, with a green build.
        //
        // Each rung flips exactly one rule of the structure, and what it grants is what that rule
        // makes legal - the skills are not a shopping list bolted onto the ladder. Summoner is the
        // Array existing at all; Rider takes the origin off the body, which is what the Keel moves
        // and what Below sinks; Saint permits coincidence, which is the only thing that makes One
        // Blade arithmetically possible.
        //
        // All four passives register with forbiddenPassive() rather than classPassive(), so this
        // chain stays out of ClassTreeTest's global class-passive count and out of the codex's
        // "From %s" line. Do not "fix" that to classPassive: the test collects over starting-root
        // trees only but closes on a global count, so a class passive granted here alone reads as
        // ungranted and turns the build red for a reason unrelated to the change.
        register(new MagicalClassDefinition(SWORD_SUMMONER, List.of(), 0,
                nameKey(SWORD_SUMMONER), descriptionKey(SWORD_SUMMONER), COST_SWORD_SUMMONER,
                List.of(MagicContent.CALL_THE_BLADE.id(), MagicContent.SWORD_STANCE.id(), MagicContent.LOOSE.id()),
                List.of(MagicPassiveContent.SWORD_HEART.id()),
                List.of(SWORD_RIDER), true));
        register(new MagicalClassDefinition(SWORD_RIDER, List.of(SWORD_SUMMONER), 1,
                nameKey(SWORD_RIDER), descriptionKey(SWORD_RIDER), COST_SWORD_RIDER,
                List.of(MagicContent.THE_KEEL.id(), MagicContent.BELOW.id()),
                List.of(MagicPassiveContent.WARD_OF_THE_ARRAY.id()),
                List.of(SWORD_SAINT), true));
        register(new MagicalClassDefinition(SWORD_SAINT, List.of(SWORD_RIDER), 2,
                nameKey(SWORD_SAINT), descriptionKey(SWORD_SAINT), COST_SWORD_SAINT,
                List.of(MagicContent.ONE_BLADE.id()),
                List.of(MagicPassiveContent.RETURNING.id()),
                List.of(SWORD_GOD), true));
        // Sword God grants NO ACTIVE, and that is the design, not an unfinished list. The apex
        // rung removes a rule - overdraw - rather than adding a verb, which is the only way an
        // apex survives in a mod that already ships five Authorities: exceeding the draw becomes
        // legal, every point of strain sharpens every blade, and the wielder bleeds for it. The
        // passive is the reflection that rule pays for. Do not add a skill here to make the row
        // look even.
        register(new MagicalClassDefinition(SWORD_GOD, List.of(SWORD_SAINT), 3,
                nameKey(SWORD_GOD), descriptionKey(SWORD_GOD), COST_SWORD_GOD,
                List.of(),
                List.of(MagicPassiveContent.MIRROR_OF_THE_ARRAY.id()),
                List.of(), true));
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
                // A secret class is never a source, or the codex Passives tab prints its name in
                // the "From %s" line the moment somebody hangs a class passive off it. Today the
                // sword chain grants forbidden passives and this is moot; the gate is here so the
                // next passive added to it cannot leak the class that grants it.
                if (definition.secret()) {
                    continue;
                }
                for (ResourceLocation granted : definition.rewardPassives()) {
                    PASSIVE_SOURCES.putIfAbsent(granted, definition.id());
                }
            }
        }
        return PASSIVE_SOURCES.get(passiveId);
    }

    /**
     * Whether the stock UI may draw this node for this wielder.
     *
     * <p>Visibility cascades from the <b>root</b>, not per node: taking Sword Summoner reveals the
     * whole chain at once. This is deliberately not "hidden until owned", which would reveal the
     * ladder one rung at a time and turn a reveal into a drip - the point of the rite is that the
     * moment you find it you are shown the whole climb ahead of you.</p>
     *
     * <p>A null state is the console, or any caller with no wielder to ask about, and sees only
     * what everybody sees.</p>
     */
    public static boolean isVisible(MagicalClassDefinition definition, PlayerMagicState state) {
        if (definition == null) {
            return false;
        }
        if (!definition.secret()) {
            return true;
        }
        return state != null && state.hasClass(baseOf(definition.id()));
    }

    public static MagicalClassDefinition get(ResourceLocation id) {
        return CLASSES.get(id);
    }

    public static List<MagicalClassDefinition> roots() {
        return CLASSES.values().stream().filter(MagicalClassDefinition::isBase).toList();
    }

    /**
     * The roots a new player may be offered: the five evolution trees, and nothing else ever.
     *
     * <p>This used to test one hard-coded id. It tests a property now, because there are two kinds
     * of root that are not starting roots for two different reasons and a third would have made
     * three ids in a boolean expression. Everything downstream of this - the first-spawn chooser,
     * {@code ClassSelectMenu.choices()}, {@code chooseStartingClass}, the class-tree sectoring and
     * {@code grantTestClassXp} - is gated for free by asking here.</p>
     */
    public static List<MagicalClassDefinition> startingRoots() {
        return roots().stream()
                .filter(definition -> !definition.secret() && !SPELL_CREATOR.equals(definition.id()))
                .toList();
    }

    /**
     * Every root that may be drawn, the starting ones first and in their own order.
     *
     * <p>The class tree paints a node's accent and prints an owner's XP pool by a root's place in
     * a list, and that list used to be {@link #startingRoots()} - which by design holds only the
     * five trees a new player may be offered. So a root that is not one of them had no place at
     * all: the Spell Creator and the hidden Sword chain fell through the accent loop to arcane
     * blue, and their owners were shown the "choose a class" hint where their own pool belonged.
     * Neither failure raises anything; the screen draws perfectly and draws the wrong thing.
     *
     * <p>The starting roots stay at the front because the accent is an <em>index</em> into a
     * palette - appending is free, inserting repaints every class in the game. This widens what
     * is drawn and nothing else: the chooser, the first spawn and the random rewards all still
     * ask {@code startingRoots}, so a secret root is still never offered.
     */
    public static List<MagicalClassDefinition> displayRoots() {
        List<MagicalClassDefinition> ordered = new ArrayList<>(startingRoots());
        for (MagicalClassDefinition root : roots()) {
            if (!ordered.contains(root)) {
                ordered.add(root);
            }
        }
        return List.copyOf(ordered);
    }

    /**
     * The one school every skill a root's tree grants belongs to, or {@code null} where the tree
     * speaks with more than one voice.
     *
     * <p>This is how a root outside the accent palette gets a colour that means something. The
     * Sword chain grants nothing but {@link com.efkrdnz.magical.magic.MagicSchool#SWORD}, whose
     * pewter is deliberately the one near-neutral grey among the schools, so the line reads as
     * steel rather than as another saturated branch. A mixed tree - a Blacksmith grants forge
     * work across several schools - answers null and keeps the old fallback, because picking the
     * first school met would repaint a tree that has looked the same since the screen was drawn.
     */
    public static com.efkrdnz.magical.magic.MagicSchool schoolOf(ResourceLocation rootId) {
        com.efkrdnz.magical.magic.MagicSchool found = null;
        for (MagicalClassDefinition node : treeOf(rootId)) {
            for (ResourceLocation skillId : node.rewardSkills()) {
                var skill = MagicContent.get(skillId);
                if (skill == null) {
                    continue;
                }
                if (found == null) {
                    found = skill.school();
                } else if (found != skill.school()) {
                    return null;
                }
            }
        }
        return found;
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

    /**
     * What tab completion may say to a wielder with nothing found. Tab completion is an enumeration
     * point like any other: it is permission 2, so in multiplayer this is polish, but in single
     * player the player <em>is</em> the operator and is exactly the person the reveal is being kept
     * from. The command still parses any word, so a capture or an operator who knows the id can
     * type it - completion simply never says it out loud.
     */
    public static List<String> commandIds() {
        return commandIds(null);
    }

    /** As {@link #commandIds()}, but a wielder who has found the chain gets to complete its rungs. */
    public static List<String> commandIds(PlayerMagicState state) {
        return CLASSES.values().stream()
                .filter(definition -> isVisible(definition, state))
                .map(definition -> definition.id().getPath())
                .toList();
    }

    public static List<String> rootCommandIds() {
        return rootCommandIds(null);
    }

    /** As {@link #rootCommandIds()}, gated the same way. */
    public static List<String> rootCommandIds(PlayerMagicState state) {
        return roots().stream()
                .filter(definition -> isVisible(definition, state))
                .map(definition -> definition.id().getPath())
                .toList();
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
        return definition != null && definition.isBase() && !definition.secret() && !SPELL_CREATOR.equals(id);
    }

    private static void register(MagicalClassDefinition definition) {
        CLASSES.put(definition.id(), definition);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
    }
}
