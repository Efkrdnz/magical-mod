package com.efkrdnz.magical.forge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class ForgeForms {

    private static final Map<ResourceLocation, FormDefinition> BY_ID;

    public static final FormDefinition SLASH;
    public static final FormDefinition CLEAVE;
    public static final FormDefinition THRUST;
    public static final FormDefinition SPIN;
    public static final FormDefinition SLAM;
    public static final FormDefinition WAVE;
    public static final FormDefinition RISING;
    public static final FormDefinition FLURRY;

    static {
        Map<ResourceLocation, FormDefinition> map = new LinkedHashMap<>();
        SLASH = register(map, new FormDefinition(
                ForgeIds.id("slash"), FormFamily.SLASH, 1.00f, 1.70f, 3.5f, 1.6f, 150f, 0f, 4, 0.35f, 8));
        CLEAVE = register(map, new FormDefinition(
                ForgeIds.id("cleave"), FormFamily.CLEAVE, 1.25f, 2.10f, 3.0f, 0.9f, 120f, 0f, 4, 0.25f, 11));
        THRUST = register(map, new FormDefinition(
                ForgeIds.id("thrust"), FormFamily.THRUST, 1.15f, 1.90f, 4.5f, 0.6f, 0f, 0f, 6, 0.45f, 9));
        SPIN = register(map, new FormDefinition(
                ForgeIds.id("spin"), FormFamily.SPIN, 0.90f, 1.50f, 2.75f, 2.75f, 360f, 0f, 5, 0.50f, 14));
        SLAM = register(map, new FormDefinition(
                ForgeIds.id("slam"), FormFamily.SLAM, 1.40f, 2.40f, 2.5f, 2.5f, 360f, 0f, 3, 0.60f, 16));
        WAVE = register(map, new FormDefinition(
                ForgeIds.id("wave"), FormFamily.WAVE, 1.00f, 1.60f, 12f, 1.4f, 140f, 1.10f, 20, 0.30f, 10));
        RISING = register(map, new FormDefinition(
                ForgeIds.id("rising"), FormFamily.RISING, 1.20f, 2.00f, 2.5f, 1.0f, 90f, 0f, 4, 0.20f, 12));
        FLURRY = register(map, new FormDefinition(
                ForgeIds.id("flurry"), FormFamily.FLURRY, 0.45f, 0.40f, 3.0f, 0.7f, 40f, 0f, 6, 0.10f, 12));
        BY_ID = Collections.unmodifiableMap(map);
    }

    private static FormDefinition register(Map<ResourceLocation, FormDefinition> map, FormDefinition definition) {
        map.put(definition.id(), definition);
        return definition;
    }

    public static Optional<FormDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Collection<FormDefinition> all() {
        return BY_ID.values();
    }

    private ForgeForms() {}
}
