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
