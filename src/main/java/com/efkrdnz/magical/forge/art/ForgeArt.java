package com.efkrdnz.magical.forge.art;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The 32 named Arts: the signature move a specific element and form combine into. This enum is the
 * table itself - which element/form pair carries which Art, and when it fires - and nothing else.
 * The behaviour hangs off it in {@code ForgeSpecials}, which refuses to load unless every constant
 * here has one, and the player-facing name hangs off {@link #langKey()}.
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
    GALE_STEP("gale", "flurry", ArtTrigger.LIGHT);

    /** Every Art's name and description live under this prefix, keyed by {@link #key()}. */
    public static final String LANG_PREFIX = "forge.magical.art.";

    private static final Map<String, ForgeArt> BY_KEY;
    private static final Map<String, List<ForgeArt>> BY_ELEMENT;
    private static final Map<String, List<ForgeArt>> BY_FORM;

    static {
        Map<String, ForgeArt> byKey = new HashMap<>();
        Map<String, List<ForgeArt>> byElement = new HashMap<>();
        Map<String, List<ForgeArt>> byForm = new HashMap<>();
        for (ForgeArt art : values()) {
            if (byKey.put(art.key(), art) != null) {
                throw new IllegalStateException("two Arts claim the same element/form pair: " + art.key());
            }
            byElement.computeIfAbsent(art.element, ignored -> new ArrayList<>()).add(art);
            byForm.computeIfAbsent(art.form, ignored -> new ArrayList<>()).add(art);
        }
        BY_KEY = Map.copyOf(byKey);
        BY_ELEMENT = unmodifiableLists(byElement);
        BY_FORM = unmodifiableLists(byForm);
    }

    private final String element;
    private final String form;
    private final ArtTrigger trigger;

    ForgeArt(String element, String form, ArtTrigger trigger) {
        this.element = element;
        this.form = form;
        this.trigger = trigger;
    }

    public String element() {
        return element;
    }

    public String form() {
        return form;
    }

    public ArtTrigger trigger() {
        return trigger;
    }

    /** {@code <element>_<form>} - the pair this Art belongs to, and its lang suffix. */
    public String key() {
        return element + "_" + form;
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
