package com.efkrdnz.magical.forge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class ForgeElements {

    private static final Map<ResourceLocation, ElementDefinition> BY_ID;

    public static final ElementDefinition FIRE;
    public static final ElementDefinition FROST;
    public static final ElementDefinition STORM;
    public static final ElementDefinition VOID;
    public static final ElementDefinition RADIANT;
    public static final ElementDefinition VENOM;
    public static final ElementDefinition TERRA;
    public static final ElementDefinition GALE;
    public static final ElementDefinition DARK;
    public static final ElementDefinition BLOOD;

    // Compound elements. No glyph draws these; the grammar fuses a pair into one.
    public static final ElementDefinition BLACK_FLAME;
    public static final ElementDefinition EXPLOSION;
    public static final ElementDefinition RIME_GALE;
    public static final ElementDefinition PLASMA;
    public static final ElementDefinition MAGMA;
    public static final ElementDefinition HAILSTORM;
    public static final ElementDefinition ECLIPSE;
    public static final ElementDefinition BLIGHT;
    public static final ElementDefinition VERDIGRIS;
    public static final ElementDefinition CORRUPTION;
    public static final ElementDefinition MARTYR;
    public static final ElementDefinition CLOT;

    static {
        Map<ResourceLocation, ElementDefinition> map = new LinkedHashMap<>();
        FIRE = register(map, new ElementDefinition(
                ForgeIds.id("fire"), ForgeElementKind.FIRE, 0xFF6A2A, 0xFFC84A, 0x7A1E00, 0.60f));
        FROST = register(map, new ElementDefinition(
                ForgeIds.id("frost"), ForgeElementKind.FROST, 0xBDF3FF, 0x6FC9FF, 0xE9FBFF, 0.55f));
        STORM = register(map, new ElementDefinition(
                ForgeIds.id("storm"), ForgeElementKind.STORM, 0x7FE7FF, 0xC9F5FF, 0x2A5BFF, 0.50f));
        VOID = register(map, new ElementDefinition(
                ForgeIds.id("void"), ForgeElementKind.VOID, 0x7B4DFF, 0x2A0A4A, 0xD9C2FF, 0.50f));
        RADIANT = register(map, new ElementDefinition(
                ForgeIds.id("radiant"), ForgeElementKind.RADIANT, 0xFFE9A0, 0xFFFFFF, 0xFFB627, 0.60f));
        VENOM = register(map, new ElementDefinition(
                ForgeIds.id("venom"), ForgeElementKind.VENOM, 0x8FE04B, 0x2E7A1C, 0xD6FF8A, 0.65f));
        TERRA = register(map, new ElementDefinition(
                ForgeIds.id("terra"), ForgeElementKind.TERRA, 0xC28B4A, 0x6B4423, 0xE8C99A, 0.55f));
        GALE = register(map, new ElementDefinition(
                ForgeIds.id("gale"), ForgeElementKind.GALE, 0xD8F5E8, 0x8FE3C2, 0xFFFFFF, 0.60f));
        // Deliberately not void's lavender: dark is the colour of a bruise going bad, so the two
        // read apart at a glance on a blade and in the glyph list.
        DARK = register(map, new ElementDefinition(
                ForgeIds.id("dark"), ForgeElementKind.DARK, 0x3A1F52, 0x7E52A6, 0x120A1C, 0.55f));
        // Arterial red against dark's bruise-purple and fire's orange, so the three warm elements
        // stay apart on a blade at a glance.
        BLOOD = register(map, new ElementDefinition(
                ForgeIds.id("blood"), ForgeElementKind.BLOOD, 0xC21E32, 0x6E0B18, 0xFF5A6E, 0.65f));
        BLACK_FLAME = register(map, new ElementDefinition(
                ForgeIds.id("black_flame"), ForgeElementKind.BLACK_FLAME, 0x2A0B3D, 0x8A2BE2, 0xFF4FD8, 0.70f));
        EXPLOSION = register(map, new ElementDefinition(
                ForgeIds.id("explosion"), ForgeElementKind.EXPLOSION, 0xFF8A1E, 0xFFE08A, 0x6B2A00, 0.75f));
        RIME_GALE = register(map, new ElementDefinition(
                ForgeIds.id("rime_gale"), ForgeElementKind.RIME_GALE, 0xCFF6FF, 0x9FE8D8, 0xFFFFFF, 0.65f));
        PLASMA = register(map, new ElementDefinition(
                ForgeIds.id("plasma"), ForgeElementKind.PLASMA, 0xFFB0FF, 0xFFF0B0, 0x6A00FF, 0.70f));
        MAGMA = register(map, new ElementDefinition(
                ForgeIds.id("magma"), ForgeElementKind.MAGMA, 0xFF5A1E, 0x8A3A10, 0xFFC46A, 0.65f));
        HAILSTORM = register(map, new ElementDefinition(
                ForgeIds.id("hailstorm"), ForgeElementKind.HAILSTORM, 0xCDEEFF, 0x8FD8FF, 0x2A5BFF, 0.60f));
        ECLIPSE = register(map, new ElementDefinition(
                ForgeIds.id("eclipse"), ForgeElementKind.ECLIPSE, 0x1A1030, 0xFFE9A0, 0x9A7BFF, 0.60f));
        BLIGHT = register(map, new ElementDefinition(
                ForgeIds.id("blight"), ForgeElementKind.BLIGHT, 0x5A7A2A, 0x2A0A4A, 0xB8FF6A, 0.70f));
        VERDIGRIS = register(map, new ElementDefinition(
                ForgeIds.id("verdigris"), ForgeElementKind.VERDIGRIS, 0x4AA88A, 0x2E7A1C, 0xC8FFD8, 0.65f));
        // The blood compounds. Each takes blood's red somewhere else so the three never read as
        // one another mid-swing: corruption toward dark's bruise, martyr toward radiant's gold,
        // clot toward the brown-black of blood that has stopped moving.
        CORRUPTION = register(map, new ElementDefinition(
                ForgeIds.id("corruption"), ForgeElementKind.CORRUPTION, 0x8A1E52, 0x3A0B2A, 0xE05A9A, 0.60f));
        MARTYR = register(map, new ElementDefinition(
                ForgeIds.id("martyr"), ForgeElementKind.MARTYR, 0xFF8A7A, 0xFFE9A0, 0xC21E32, 0.60f));
        CLOT = register(map, new ElementDefinition(
                ForgeIds.id("clot"), ForgeElementKind.CLOT, 0x7A2028, 0x3A1014, 0xC9D8E8, 0.60f));
        BY_ID = Collections.unmodifiableMap(map);
    }

    private static ElementDefinition register(Map<ResourceLocation, ElementDefinition> map, ElementDefinition definition) {
        map.put(definition.id(), definition);
        return definition;
    }

    public static Optional<ElementDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Collection<ElementDefinition> all() {
        return BY_ID.values();
    }

    private ForgeElements() {}
}
