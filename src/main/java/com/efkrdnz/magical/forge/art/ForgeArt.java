package com.efkrdnz.magical.forge.art;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The named Arts: the signature move a particular combination of weapon, element, form and temper
 * turns into. This enum is the table itself - which combination carries which Art, and when it
 * fires - and nothing else. The behaviour hangs off it in {@code ForgeSpecials}, which refuses to
 * load unless every constant here has one, and the player-facing name hangs off {@link #langKey()}.
 *
 * <p>An Art keys on four columns, of which only {@link #form()} is required. A {@code null} in any
 * of the others means "any", so the original element/form Arts keep working untouched while newer
 * ones can key on the shape of weapon or the temper instead - the dagger/slash/rush combination
 * that fires THOUSAND_CUTS names no element at all, because what makes it that move is the dagger.
 *
 * <p>Lookup is most-specific-wins, counted by {@link #specificity()}. Two Arts tying on a real
 * combination would make which one fires depend on enum order, so {@code ForgeArtTest} asserts no
 * pair can tie - that assertion is what keeps this table honest as it grows.
 *
 * <p>Minecraft-free on purpose ({@code java.*} only), so the completeness of the table is pinned by
 * a plain unit test rather than by a game launch.</p>
 */
public enum ForgeArt {

    CINDER_LANCE("fire", "thrust", ArtTrigger.HEAVY),
    PYRE_WHEEL("fire", "spin", ArtTrigger.ANY),
    EMBER_ERUPTION("fire", "slam", ArtTrigger.FINISHER_OR_HEAVY),
    KINDLING("fire", "flurry", ArtTrigger.LIGHT),

    RIME_SPLIT("frost", "cleave", ArtTrigger.HEAVY),
    GLACIAL_HALO("frost", "spin", ArtTrigger.FINISHER),
    HOAR_CRESCENT("frost", "wave", ArtTrigger.LIGHT),
    ICICLE_PILLAR("frost", "rising", ArtTrigger.HEAVY),

    RAIL_LANCE("storm", "thrust", ArtTrigger.ANY),
    TEMPEST_COIL("storm", "spin", ArtTrigger.FINISHER),
    SKYFALL("storm", "rising", ArtTrigger.HEAVY),
    STATIC_BURST("storm", "flurry", ArtTrigger.LIGHT),

    PHASE_PIERCE("void", "thrust", ArtTrigger.HEAVY),
    NULL_ORBIT("void", "spin", ArtTrigger.FINISHER),
    ABYSS_RIFT("void", "slam", ArtTrigger.HEAVY),
    EVENT_HORIZON("void", "wave", ArtTrigger.ANY),

    DAWN_EDGE("radiant", "slash", ArtTrigger.FINISHER),
    SANCTIFIED_RING("radiant", "spin", ArtTrigger.FINISHER),
    LIGHT_CRESCENT("radiant", "wave", ArtTrigger.LIGHT),
    ASCENSION("radiant", "rising", ArtTrigger.HEAVY),

    ENVENOM("venom", "thrust", ArtTrigger.HEAVY),
    TOXIC_BLOOM("venom", "slam", ArtTrigger.FINISHER),
    MIASMA_CRESCENT("venom", "wave", ArtTrigger.LIGHT),
    FANG_STORM("venom", "flurry", ArtTrigger.LIGHT),

    STONE_EDGE("terra", "slash", ArtTrigger.LIGHT),
    FAULT_LINE("terra", "cleave", ArtTrigger.ANY),
    QUAKE("terra", "slam", ArtTrigger.FINISHER_OR_HEAVY),
    UPHEAVAL("terra", "rising", ArtTrigger.HEAVY),

    WIND_CUTTER("gale", "slash", ArtTrigger.LIGHT),
    CYCLONE("gale", "spin", ArtTrigger.FINISHER),
    UPDRAFT("gale", "rising", ArtTrigger.HEAVY),
    GALE_STEP("gale", "flurry", ArtTrigger.LIGHT),

    // Arts that key on the weapon rather than on the element. Everything above names an element
    // and nothing else; everything below names an archetype, a temper, or both.

    /**
     * A dagger tempered for the rush, cutting. The strike stops being one cut and becomes a dozen
     * from every side at once - the move the whole archetype exists for.
     */
    THOUSAND_CUTS(null, "slash", ArtTrigger.ANY, "dagger", "rush"),

    /** Blood driven in on a charged thrust, and taken back out as barrier. */
    CRIMSON_TITHE("blood", "thrust", ArtTrigger.HEAVY, null, null),

    /** Blood on a dagger, finding the gap in anything already more than half gone. */
    HEARTSEEKER("blood", "lunge", ArtTrigger.ANY, "dagger", null),

    /** A blood scythe, reaping. Every kill inside the swing pays the reaper back. */
    RED_HARVEST("blood", "reap", ArtTrigger.FINISHER, null, null),

    /** A greatsword coming down with all its weight behind it, and the guard giving way. */
    BREACH(null, "plunge", ArtTrigger.FINISHER_OR_HEAVY, "greatsword", "heft"),

    /** A coiled spear released: the target goes nowhere until it works the point out. */
    IMPALE(null, "lunge", ArtTrigger.HEAVY, "spear", "coil"),

    /** Claws that land twice, opening a wound that keeps opening. */
    RIBBONS(null, "hook", ArtTrigger.ANY, "claws", "split");

    /** Every Art's name and description live under this prefix, keyed by {@link #key()}. */
    public static final String LANG_PREFIX = "forge.magical.art.";

    private static final char SEPARATOR = '_';

    private static final Map<String, ForgeArt> BY_KEY;
    private static final Map<String, List<ForgeArt>> BY_ELEMENT;
    private static final Map<String, List<ForgeArt>> BY_FORM;

    static {
        Map<String, ForgeArt> byKey = new HashMap<>();
        Map<String, List<ForgeArt>> byElement = new HashMap<>();
        Map<String, List<ForgeArt>> byForm = new HashMap<>();
        for (ForgeArt art : values()) {
            if (byKey.put(art.key(), art) != null) {
                throw new IllegalStateException("two Arts claim the same combination: " + art.key());
            }
            // An Art keyed on the weapon rather than the element has no element to index under,
            // and Map.copyOf below refuses a null key outright. It is still reachable through
            // bestMatch and through forForm; only the by-element listing leaves it out, which is
            // right - "the Arts fire gives you" should not include one fire has nothing to do with.
            if (art.element != null) {
                byElement.computeIfAbsent(art.element, ignored -> new ArrayList<>()).add(art);
            }
            byForm.computeIfAbsent(art.form, ignored -> new ArrayList<>()).add(art);
        }
        BY_KEY = Map.copyOf(byKey);
        BY_ELEMENT = unmodifiableLists(byElement);
        BY_FORM = unmodifiableLists(byForm);
    }

    private final String element;
    private final String form;
    private final ArtTrigger trigger;
    private final String archetype;
    private final String temper;

    ForgeArt(String element, String form, ArtTrigger trigger) {
        this(element, form, trigger, null, null);
    }

    ForgeArt(String element, String form, ArtTrigger trigger, String archetype, String temper) {
        this.element = element;
        this.form = form;
        this.trigger = trigger;
        this.archetype = archetype;
        this.temper = temper;
    }

    /** The element this Art demands, or null when it fires whatever the blade is made of. */
    public String element() {
        return element;
    }

    /** The archetype this Art demands, or null when the shape of the weapon does not matter. */
    public String archetype() {
        return archetype;
    }

    /** The temper this Art demands, or null when it fires however the weapon was worked. */
    public String temper() {
        return temper;
    }

    /**
     * Whether an element and a form between them are enough to say this is the Art.
     *
     * <p>True only for the Arts that name nothing else. HEARTSEEKER is blood and a lunge <em>and a
     * dagger</em>, so a blood lunge on a spear is not HEARTSEEKER, and no index keyed on the pair
     * alone should answer with it - {@link #bestMatch} is where an Art like that is found.
     *
     * <p>This is exactly what {@link #of(String, String)} can reach, and what {@code ForgeSpecials}
     * builds its pair index from. Both used to be every Art in the table, because before an Art
     * could name a weapon there was nothing else for one to name.
     */
    public boolean keyedOnPairAlone() {
        return element != null && archetype == null && temper == null;
    }

    /**
     * How many columns beyond the form this Art pins down. Higher wins a lookup, because an Art
     * naming three things is a more particular claim on a combination than one naming one.
     */
    public int specificity() {
        return (element == null ? 0 : 1) + (archetype == null ? 0 : 1) + (temper == null ? 0 : 1);
    }

    /** Whether every column this Art names is satisfied. A null column matches anything. */
    public boolean matches(String element, String form, String archetype, String temper) {
        return same(this.form, form)
                && (this.element == null || same(this.element, element))
                && (this.archetype == null || same(this.archetype, archetype))
                && (this.temper == null || same(this.temper, temper));
    }

    private static boolean same(String expected, String actual) {
        return actual != null && expected.equalsIgnoreCase(actual);
    }

    public String form() {
        return form;
    }

    public ArtTrigger trigger() {
        return trigger;
    }

    /**
     * The combination this Art belongs to, and its lang suffix: the columns it names, joined in
     * table order, skipping the ones it leaves open.
     *
     * <p>An element/form Art is still {@code fire_thrust}, so nothing that existed before this
     * widened has moved. A weapon-keyed one reads {@code dagger_slash_rush}.
     */
    public String key() {
        StringBuilder key = new StringBuilder();
        if (element != null) {
            key.append(element).append(SEPARATOR);
        }
        if (archetype != null) {
            key.append(archetype).append(SEPARATOR);
        }
        key.append(form);
        if (temper != null) {
            key.append(SEPARATOR).append(temper);
        }
        return key.toString();
    }

    public String langKey() {
        return LANG_PREFIX + key();
    }

    public String descriptionKey() {
        return langKey() + ".desc";
    }

    public static Optional<ForgeArt> of(String element, String form) {
        if (element == null || form == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEY.get(
                element.toLowerCase(Locale.ROOT) + "_" + form.toLowerCase(Locale.ROOT)));
    }

    /**
     * The Art a whole combination fires: the most specific one whose every named column is met.
     *
     * <p>A tie is a bug in the table rather than a decision to make at runtime, so this keeps the
     * first of the best - and {@code ForgeArtTest} makes sure there is never a second to choose
     * between.
     */
    public static Optional<ForgeArt> bestMatch(String element, String form, String archetype, String temper) {
        if (form == null) {
            return Optional.empty();
        }
        ForgeArt best = null;
        for (ForgeArt art : values()) {
            if (art.matches(element, form, archetype, temper)
                    && (best == null || art.specificity() > best.specificity())) {
                best = art;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The Arts this element carries, in table order; empty for an element with none. */
    public static List<ForgeArt> forElement(String element) {
        return element == null ? List.of() : BY_ELEMENT.getOrDefault(element.toLowerCase(Locale.ROOT), List.of());
    }

    /** The Arts this form carries, in table order; empty for a form no element gives an Art. */
    public static List<ForgeArt> forForm(String form) {
        return form == null ? List.of() : BY_FORM.getOrDefault(form.toLowerCase(Locale.ROOT), List.of());
    }

    private static Map<String, List<ForgeArt>> unmodifiableLists(Map<String, List<ForgeArt>> source) {
        Map<String, List<ForgeArt>> copy = new HashMap<>();
        for (Map.Entry<String, List<ForgeArt>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
