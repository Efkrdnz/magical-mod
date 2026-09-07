package com.efkrdnz.magical.forge;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.efkrdnz.magical.forge.art.FireArts;
import com.efkrdnz.magical.forge.art.ForgeArt;
import com.efkrdnz.magical.forge.art.FrostArts;
import com.efkrdnz.magical.forge.art.GaleArts;
import com.efkrdnz.magical.forge.art.RadiantArts;
import com.efkrdnz.magical.forge.art.StormArts;
import com.efkrdnz.magical.forge.art.TerraArts;
import com.efkrdnz.magical.forge.art.VenomArts;
import com.efkrdnz.magical.forge.art.VoidArts;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry of the per-element/per-form Arts - the signature moves a specific element and form
 * combine into. The combat core calls {@link #lookup} on every hit; a pair with no entry simply
 * falls back to the plain element rider.
 *
 * <p>The table of which pair carries which Art lives in {@link ForgeArt}, and the behaviour is
 * bound to it here. Class initialisation fails loudly if any Art in that table has no behaviour,
 * so a half-wired matrix can never reach a running game.</p>
 */
public final class ForgeSpecials {

    /** One element and one form: the coordinates of a cell in the 8x8 Art matrix. */
    public record Key(ResourceLocation element, ResourceLocation form) {
    }

    private static final Map<ForgeArt, StrikeSpecial> BEHAVIOUR;
    private static final Map<Key, StrikeSpecial> BY_KEY;

    static {
        Map<ForgeArt, StrikeSpecial> behaviour = new EnumMap<>(ForgeArt.class);
        register(behaviour, ForgeArt.CINDER_LANCE, FireArts::cinderLance);
        register(behaviour, ForgeArt.PYRE_WHEEL, FireArts::pyreWheel);
        register(behaviour, ForgeArt.EMBER_ERUPTION, FireArts::emberEruption);
        register(behaviour, ForgeArt.KINDLING, FireArts::kindling);

        register(behaviour, ForgeArt.RIME_SPLIT, FrostArts::rimeSplit);
        register(behaviour, ForgeArt.GLACIAL_HALO, FrostArts::glacialHalo);
        register(behaviour, ForgeArt.HOAR_CRESCENT, FrostArts::hoarCrescent);
        register(behaviour, ForgeArt.ICICLE_PILLAR, FrostArts::iciclePillar);

        register(behaviour, ForgeArt.RAIL_LANCE, StormArts::railLance);
        register(behaviour, ForgeArt.TEMPEST_COIL, StormArts::tempestCoil);
        register(behaviour, ForgeArt.SKYFALL, StormArts::skyfall);
        register(behaviour, ForgeArt.STATIC_BURST, StormArts::staticBurst);

        register(behaviour, ForgeArt.PHASE_PIERCE, VoidArts::phasePierce);
        register(behaviour, ForgeArt.NULL_ORBIT, VoidArts::nullOrbit);
        register(behaviour, ForgeArt.ABYSS_RIFT, VoidArts::abyssRift);
        register(behaviour, ForgeArt.EVENT_HORIZON, VoidArts::eventHorizon);

        register(behaviour, ForgeArt.DAWN_EDGE, RadiantArts::dawnEdge);
        register(behaviour, ForgeArt.SANCTIFIED_RING, RadiantArts::sanctifiedRing);
        register(behaviour, ForgeArt.LIGHT_CRESCENT, RadiantArts::lightCrescent);
        register(behaviour, ForgeArt.ASCENSION, RadiantArts::ascension);

        register(behaviour, ForgeArt.ENVENOM, VenomArts::envenom);
        register(behaviour, ForgeArt.TOXIC_BLOOM, VenomArts::toxicBloom);
        register(behaviour, ForgeArt.MIASMA_CRESCENT, VenomArts::miasmaCrescent);
        register(behaviour, ForgeArt.FANG_STORM, VenomArts::fangStorm);

        register(behaviour, ForgeArt.STONE_EDGE, TerraArts::stoneEdge);
        register(behaviour, ForgeArt.FAULT_LINE, TerraArts::faultLine);
        register(behaviour, ForgeArt.QUAKE, TerraArts::quake);
        register(behaviour, ForgeArt.UPHEAVAL, TerraArts::upheaval);

        register(behaviour, ForgeArt.WIND_CUTTER, GaleArts::windCutter);
        register(behaviour, ForgeArt.CYCLONE, GaleArts::cyclone);
        register(behaviour, ForgeArt.UPDRAFT, GaleArts::updraft);
        register(behaviour, ForgeArt.GALE_STEP, GaleArts::galeStep);

        for (ForgeArt art : ForgeArt.values()) {
            if (!behaviour.containsKey(art)) {
                throw new IllegalStateException("Art " + art + " (" + art.key() + ") has no behaviour bound");
            }
        }
        BEHAVIOUR = Map.copyOf(behaviour);
        BY_KEY = buildKeyIndex(behaviour);
    }

    private ForgeSpecials() {}

    // --- lookup -------------------------------------------------------------------------------

    public static Optional<StrikeSpecial> lookup(ElementDefinition element, FormDefinition form) {
        return element == null || form == null ? Optional.empty() : lookup(element.id(), form.id());
    }

    public static Optional<StrikeSpecial> lookup(ResourceLocation element, ResourceLocation form) {
        return element == null || form == null
                ? Optional.empty()
                : Optional.ofNullable(BY_KEY.get(new Key(element, form)));
    }

    /** The Art the pair carries, whether or not the ids resolve to a known element or form. */
    public static Optional<ForgeArt> art(String element, String form) {
        return ForgeArt.of(element, form);
    }

    public static Optional<ForgeArt> art(ResourceLocation element, ResourceLocation form) {
        return element == null || form == null
                ? Optional.empty()
                : ForgeArt.of(element.getPath(), form.getPath());
    }

    public static boolean has(ResourceLocation element, ResourceLocation form) {
        return lookup(element, form).isPresent();
    }

    /** Every Art this element gives, in table order. */
    public static List<ForgeArt> forElement(String element) {
        return ForgeArt.forElement(element);
    }

    /** Every element that gives this form an Art, in table order. */
    public static List<ForgeArt> forForm(String form) {
        return ForgeArt.forForm(form);
    }

    public static Component name(ForgeArt art) {
        return Component.translatable(art.langKey());
    }

    public static Component description(ForgeArt art) {
        return Component.translatable(art.descriptionKey());
    }

    /** How many Arts are wired up. Pinned against the 32-row table by {@code ForgeArtTest}. */
    public static int size() {
        return BEHAVIOUR.size();
    }

    // --- wiring -------------------------------------------------------------------------------

    private static void register(Map<ForgeArt, StrikeSpecial> behaviour, ForgeArt art, StrikeSpecial special) {
        if (behaviour.put(art, special) != null) {
            throw new IllegalStateException("Art " + art + " was bound twice");
        }
    }

    private static Map<Key, StrikeSpecial> buildKeyIndex(Map<ForgeArt, StrikeSpecial> behaviour) {
        Map<Key, StrikeSpecial> index = new HashMap<>();
        for (Map.Entry<ForgeArt, StrikeSpecial> entry : behaviour.entrySet()) {
            ForgeArt art = entry.getKey();
            index.put(new Key(ForgeIds.id(art.element()), ForgeIds.id(art.form())), entry.getValue());
        }
        return Map.copyOf(index);
    }
}
