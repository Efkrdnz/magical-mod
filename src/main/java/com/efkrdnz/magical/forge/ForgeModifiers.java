package com.efkrdnz.magical.forge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class ForgeModifiers {

    private static final Map<ResourceLocation, ModifierDefinition> BY_ID;

    public static final ModifierDefinition ECHO;
    public static final ModifierDefinition PIERCE;
    public static final ModifierDefinition SEEKING;
    public static final ModifierDefinition LEECH;
    public static final ModifierDefinition BINDING;
    public static final ModifierDefinition REACH;
    public static final ModifierDefinition HASTE;
    public static final ModifierDefinition BRAND;
    public static final ModifierDefinition SHATTER;
    public static final ModifierDefinition GUARD;

    static {
        Map<ResourceLocation, ModifierDefinition> map = new LinkedHashMap<>();
        ECHO = register(map, new ModifierDefinition(ForgeIds.id("echo"), ForgeModifierKind.ECHO, 0.40f, 8, -6));
        PIERCE = register(map, new ModifierDefinition(ForgeIds.id("pierce"), ForgeModifierKind.PIERCE, 0.20f, 10, -8));
        SEEKING = register(map, new ModifierDefinition(ForgeIds.id("seeking"), ForgeModifierKind.SEEKING, 0.12f, 8, -10));
        LEECH = register(map, new ModifierDefinition(ForgeIds.id("leech"), ForgeModifierKind.LEECH, 0.08f, 12, -10));
        BINDING = register(map, new ModifierDefinition(ForgeIds.id("binding"), ForgeModifierKind.BINDING, 0.25f, 6, 4));
        REACH = register(map, new ModifierDefinition(ForgeIds.id("reach"), ForgeModifierKind.REACH, 1.0f, 6, -4));
        HASTE = register(map, new ModifierDefinition(ForgeIds.id("haste"), ForgeModifierKind.HASTE, 3f, 10, -8));
        BRAND = register(map, new ModifierDefinition(ForgeIds.id("brand"), ForgeModifierKind.BRAND, 0.15f, 9, -6));
        SHATTER = register(map, new ModifierDefinition(ForgeIds.id("shatter"), ForgeModifierKind.SHATTER, 0.30f, 9, -6));
        GUARD = register(map, new ModifierDefinition(ForgeIds.id("guard"), ForgeModifierKind.GUARD, 0.30f, 8, 2));
        BY_ID = Collections.unmodifiableMap(map);
    }

    private static ModifierDefinition register(Map<ResourceLocation, ModifierDefinition> map, ModifierDefinition definition) {
        map.put(definition.id(), definition);
        return definition;
    }

    public static Optional<ModifierDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Collection<ModifierDefinition> all() {
        return BY_ID.values();
    }

    private ForgeModifiers() {}
}
