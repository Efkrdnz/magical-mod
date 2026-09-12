package com.efkrdnz.magical.forge.weapon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.chain.ForgeMaterial;

import net.minecraft.resources.ResourceLocation;

/**
 * The weapon catalogue. One weapon is one line.
 *
 * <p>The numbers are authored, not derived. There is no power curve and no tier table behind them -
 * a weapon hits for what its line says it hits for, and a later weapon may sit anywhere beside an
 * earlier one. That is deliberate: the catalogue is meant to grow by someone adding a row they
 * liked the sound of, and a curve would make every new row an argument with the rows around it.
 *
 * <p>Two of the columns carry the exclusivity the forge reads. The archetype decides which shared
 * pool of glyphs the weapon can draw from - every dagger can draw from the dagger pool - and
 * {@code signatureGlyphs} names the shapes this one weapon alone knows. {@code ForgeVocabulary}
 * owns the first; this file owns the second.
 *
 * <p><strong>A signature is a claim on the whole game.</strong> Naming a glyph here takes it away
 * from every other weapon, including vanilla ones - that is the entire point of it, and the reason
 * the column is nearly always empty. Nine glyphs are claimed out of fifty-odd.
 */
public final class MagicalWeapons {

    private static final Map<ResourceLocation, WeaponDefinition> BY_ID;
    private static final List<ResourceLocation> ORDERED_IDS;

    // --- daggers: the shortest reach, the quickest recovery, the least knockback --------------

    /** The plain dagger. No signature at all, so there is a baseline to compare the rest against. */
    public static final WeaponDefinition HOLLOW_FANG;
    /** Blood's dagger: the fastest way to reach a target that is already hurt. */
    public static final WeaponDefinition VEIN_RIPPER;
    public static final WeaponDefinition WHISPER_THORN;
    public static final WeaponDefinition FROSTBITE_NEEDLE;
    public static final WeaponDefinition ASHFANG;
    public static final WeaponDefinition GLOOMTOOTH;

    // --- swords: the baseline every other archetype is a deviation from -----------------------

    /** A sword that gets louder the more of a crowd is already bleeding. */
    public static final WeaponDefinition CHORUS_EDGE;
    public static final WeaponDefinition EMBERBRAND;
    public static final WeaponDefinition TIDEWRACK;
    public static final WeaponDefinition STARFALL_BLADE;
    public static final WeaponDefinition GRAVEWORN;
    public static final WeaponDefinition VERDANT_SABER;

    // --- axes: slower than a sword, and they throw what they hit ------------------------------

    /** The axe that spends what defends you, and gives it back as weight. */
    public static final WeaponDefinition TITHEWRIGHT;
    public static final WeaponDefinition CINDERJAW;
    /** The only weapon that knows SHATTER, which is why frozen things die to it and nothing else. */
    public static final WeaponDefinition RIMEBITER;
    public static final WeaponDefinition ROOTSPLITTER;

    // --- greatswords: the heaviest single blows in the game, and the slowest ------------------

    /** The heaviest single blow in the catalogue, and the slowest swing behind it. */
    public static final WeaponDefinition MOUNTAINBREAKER;
    public static final WeaponDefinition DUSKFALL;
    public static final WeaponDefinition SUNDERLIGHT;
    public static final WeaponDefinition STORMCLEAVER;

    // --- spears: reach as the whole identity ---------------------------------------------------

    /** Reach as the whole identity: the spear that hits from outside what can hit back. */
    public static final WeaponDefinition COILSPINE;
    public static final WeaponDefinition LANCET_OF_THORNS;
    public static final WeaponDefinition GLACIER_PIKE;
    public static final WeaponDefinition SUNSPIRE;

    // --- scythes: wide, slow, and they keep what they catch close -----------------------------

    /** Blood's scythe. Wide, slow, and worth more the more bodies are already open. */
    public static final WeaponDefinition RED_HARVEST;
    /** The only weapon that knows LEECH: everything else has to kill the hard way. */
    public static final WeaponDefinition WIDOWMAKER;
    public static final WeaponDefinition CINDER_REAPER;
    public static final WeaponDefinition GALE_SICKLE;

    // --- claws: faster than anything, shorter than anything ------------------------------------

    /** Two strikes for every swing, at half each, faster than anything else can answer. */
    public static final WeaponDefinition SPLIT_TONGUE;
    public static final WeaponDefinition STORMTALONS;
    public static final WeaponDefinition FROSTRAKES;
    public static final WeaponDefinition RUIN_CLAWS;

    static {
        Map<ResourceLocation, WeaponDefinition> map = new LinkedHashMap<>();
        // path, attack, attacks per second, grade cap, theme element, signature glyphs

        HOLLOW_FANG = register(map, dagger("hollow_fang", 96f, 2.4f, ForgeMaterial.DIAMOND, null));
        VEIN_RIPPER = register(map, dagger("vein_ripper", 118f, 2.2f, ForgeMaterial.NETHERITE, "blood"));
        WHISPER_THORN = register(map, dagger("whisper_thorn", 84f, 2.6f, ForgeMaterial.IRON, "venom"));
        FROSTBITE_NEEDLE = register(map, dagger("frostbite_needle", 104f, 2.3f, ForgeMaterial.DIAMOND, "frost"));
        ASHFANG = register(map, dagger("ashfang", 122f, 2.2f, ForgeMaterial.NETHERITE, "fire"));
        GLOOMTOOTH = register(map, dagger("gloomtooth", 110f, 2.4f, ForgeMaterial.DIAMOND, "dark"));

        CHORUS_EDGE = register(map, sword("chorus_edge", 128f, 1.6f, ForgeMaterial.NETHERITE, "gale", "chorus"));
        EMBERBRAND = register(map, sword("emberbrand", 134f, 1.6f, ForgeMaterial.NETHERITE, "fire"));
        TIDEWRACK = register(map, sword("tidewrack", 112f, 1.7f, ForgeMaterial.DIAMOND, "frost"));
        STARFALL_BLADE = register(map, sword("starfall_blade", 186f, 1.5f, ForgeMaterial.NETHERITE, "radiant"));
        GRAVEWORN = register(map, sword("graveworn", 98f, 1.8f, ForgeMaterial.IRON, "dark"));
        VERDANT_SABER = register(map, sword("verdant_saber", 126f, 1.7f, ForgeMaterial.DIAMOND, "terra"));

        TITHEWRIGHT = register(map, axe("tithewright", 176f, 0.9f, ForgeMaterial.NETHERITE, "radiant", "tithe"));
        CINDERJAW = register(map, axe("cinderjaw", 158f, 1.0f, ForgeMaterial.DIAMOND, "fire"));
        RIMEBITER = register(map, axe("rimebiter", 168f, 0.9f, ForgeMaterial.NETHERITE, "frost"));
        ROOTSPLITTER = register(map, axe("rootsplitter", 144f, 1.1f, ForgeMaterial.DIAMOND, "terra"));

        MOUNTAINBREAKER = register(map, greatsword("mountainbreaker", 212f, 0.7f, ForgeMaterial.NETHERITE,
                "terra", "carry"));
        DUSKFALL = register(map, greatsword("duskfall", 238f, 0.6f, ForgeMaterial.NETHERITE, "dark"));
        SUNDERLIGHT = register(map, greatsword("sunderlight", 226f, 0.7f, ForgeMaterial.NETHERITE, "radiant"));
        STORMCLEAVER = register(map, greatsword("stormcleaver", 198f, 0.8f, ForgeMaterial.DIAMOND, "storm"));

        COILSPINE = register(map, spear("coilspine", 142f, 1.3f, ForgeMaterial.NETHERITE, "storm", "chorus"));
        LANCET_OF_THORNS = register(map, spear("lancet_of_thorns", 128f, 1.4f, ForgeMaterial.DIAMOND, "venom"));
        GLACIER_PIKE = register(map, spear("glacier_pike", 154f, 1.2f, ForgeMaterial.NETHERITE, "frost"));
        SUNSPIRE = register(map, spear("sunspire", 166f, 1.3f, ForgeMaterial.NETHERITE, "radiant"));

        RED_HARVEST = register(map, scythe("red_harvest", 164f, 1.0f, ForgeMaterial.NETHERITE, "blood"));
        WIDOWMAKER = register(map, scythe("widowmaker", 148f, 1.1f, ForgeMaterial.DIAMOND, "dark"));
        CINDER_REAPER = register(map, scythe("cinder_reaper", 172f, 0.9f, ForgeMaterial.NETHERITE, "fire"));
        GALE_SICKLE = register(map, scythe("gale_sickle", 132f, 1.2f, ForgeMaterial.DIAMOND, "gale"));

        SPLIT_TONGUE = register(map, claws("split_tongue", 88f, 2.8f, ForgeMaterial.DIAMOND, "venom", "carry"));
        STORMTALONS = register(map, claws("stormtalons", 94f, 3.0f, ForgeMaterial.NETHERITE, "storm"));
        FROSTRAKES = register(map, claws("frostrakes", 82f, 2.9f, ForgeMaterial.DIAMOND, "frost"));
        RUIN_CLAWS = register(map, claws("ruin_claws", 106f, 2.6f, ForgeMaterial.NETHERITE, "dark"));

        BY_ID = Collections.unmodifiableMap(map);
        ORDERED_IDS = List.copyOf(new ArrayList<>(map.keySet()));
    }

    private MagicalWeapons() {}

    // One short factory per archetype, so a row says what shape it is without repeating the enum.

    private static WeaponDefinition dagger(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.DAGGER, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition sword(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.SWORD, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition axe(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.AXE, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition greatsword(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.GREATSWORD, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition spear(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.SPEAR, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition scythe(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.SCYTHE, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition claws(String path, float attack, float speed, ForgeMaterial cap,
            String theme, String... signature) {
        return weapon(path, WeaponClass.CLAWS, attack, speed, cap, theme, signature);
    }

    private static WeaponDefinition weapon(String path, WeaponClass archetype, float attack, float speed,
            ForgeMaterial cap, String theme, String... signature) {
        return new WeaponDefinition(ForgeIds.id(path), archetype, attack, speed, cap,
                Optional.ofNullable(theme).map(ForgeIds::id), Set.of(signature));
    }

    private static WeaponDefinition register(Map<ResourceLocation, WeaponDefinition> map, WeaponDefinition weapon) {
        map.put(weapon.id(), weapon);
        return weapon;
    }

    public static Collection<WeaponDefinition> all() {
        return BY_ID.values();
    }

    /** Registration order, which is also creative-tab order. */
    public static List<ResourceLocation> orderedIds() {
        return ORDERED_IDS;
    }

    public static Optional<WeaponDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** Every weapon that alone may draw {@code glyphId}, which is usually none. */
    public static Set<ResourceLocation> owners(String glyphId) {
        Set<ResourceLocation> owners = new LinkedHashSet<>();
        for (WeaponDefinition weapon : BY_ID.values()) {
            if (weapon.signatureGlyphs().contains(glyphId)) {
                owners.add(weapon.id());
            }
        }
        return Collections.unmodifiableSet(owners);
    }

    /** Every glyph some weapon in the catalogue claims as its own. */
    public static Set<String> allSignatureGlyphs() {
        Set<String> glyphs = new LinkedHashSet<>();
        for (WeaponDefinition weapon : BY_ID.values()) {
            glyphs.addAll(weapon.signatureGlyphs());
        }
        return Collections.unmodifiableSet(glyphs);
    }
}
