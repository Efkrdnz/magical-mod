package com.efkrdnz.magical.forge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class ForgeTempers {

    private static final Map<ResourceLocation, TemperDefinition> BY_ID;

    public static final TemperDefinition KEEN;
    public static final TemperDefinition HEAVY;
    public static final TemperDefinition SWIFT;

    static {
        Map<ResourceLocation, TemperDefinition> map = new LinkedHashMap<>();
        KEEN = register(map, new TemperDefinition(
                ForgeIds.id("keen"), 1.0f, 0.85f, 1.15f, 1.0f, 0.30f, 0, 0, 0, -3));
        HEAVY = register(map, new TemperDefinition(
                ForgeIds.id("heavy"), 0f, 1.35f, 0.85f, 1.7f, 0f, 4, 3, -4, 0));
        SWIFT = register(map, new TemperDefinition(
                ForgeIds.id("swift"), -0.5f, 0.90f, 1.20f, 0.8f, 0f, 6, -3, 0, -3));
        BY_ID = Collections.unmodifiableMap(map);
    }

    private static TemperDefinition register(Map<ResourceLocation, TemperDefinition> map, TemperDefinition definition) {
        map.put(definition.id(), definition);
        return definition;
    }

    public static Optional<TemperDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Collection<TemperDefinition> all() {
        return BY_ID.values();
    }

    private ForgeTempers() {}
}
