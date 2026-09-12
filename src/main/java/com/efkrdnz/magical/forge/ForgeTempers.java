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
    public static final TemperDefinition RUSH;
    public static final TemperDefinition HEFT;
    public static final TemperDefinition COIL;
    public static final TemperDefinition SPLIT;

    static {
        Map<ResourceLocation, TemperDefinition> map = new LinkedHashMap<>();
        KEEN = register(map, new TemperDefinition(
                ForgeIds.id("keen"), 1.0f, 0.85f, 1.15f, 1.0f, 0.30f, 0, 0, 0, -3));
        HEAVY = register(map, new TemperDefinition(
                ForgeIds.id("heavy"), 0f, 1.35f, 0.85f, 1.7f, 0f, 4, 3, -4, 0));
        SWIFT = register(map, new TemperDefinition(
                ForgeIds.id("swift"), -0.5f, 0.90f, 1.20f, 0.8f, 0f, 6, -3, 0, -3));
        // Archetype tempers. Each is locked to one weapon shape by ForgeVocabulary, so these are
        // allowed to be sharper than the three universal tempers above - nobody can put RUSH on a
        // greatsword to get a fast greatsword.
        //
        // Dagger: the widest combo window in the set, bought with knockback and crit.
        RUSH = register(map, new TemperDefinition(
                ForgeIds.id("rush"), 0.5f, 0.80f, 1.35f, 0.50f, 0.15f, 8, -3, -2, -5));
        // Greatsword: slower and wider, and it charges sooner than anything else.
        HEFT = register(map, new TemperDefinition(
                ForgeIds.id("heft"), 0.2f, 1.45f, 0.75f, 1.90f, 0f, 2, 4, -5, 2));
        // Spear: twice keen's reach on a narrower hit shape, and slow to charge.
        COIL = register(map, new TemperDefinition(
                ForgeIds.id("coil"), 2.0f, 0.70f, 0.95f, 0.90f, 0.10f, 0, 2, 3, -2));
        // Claws: the fastest combo in the game and the least stable chain in it.
        SPLIT = register(map, new TemperDefinition(
                ForgeIds.id("split"), -0.3f, 0.75f, 1.25f, 0.45f, 0.20f, 10, -2, 0, -6));
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
