package com.efkrdnz.magical.arcane;

import com.efkrdnz.magical.MagicalMod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

public final class ArcaneContent {
    public static final ArcaneRuneDefinition FIRE_RUNE = new ArcaneRuneDefinition(
            id("fire"), "Fire", Affinity.FIRE, ChatFormatting.RED, 14, 80, 5.5F);
    public static final ArcaneRuneDefinition WATER_RUNE = new ArcaneRuneDefinition(
            id("water"), "Water", Affinity.WATER, ChatFormatting.AQUA, 12, 84, 4.5F);
    public static final ArcaneRuneDefinition FORCE_RUNE = new ArcaneRuneDefinition(
            id("force"), "Force", Affinity.FORCE, ChatFormatting.GRAY, 15, 78, 5.0F);
    public static final ArcaneRuneDefinition LIGHT_RUNE = new ArcaneRuneDefinition(
            id("light"), "Light", Affinity.LIGHT, ChatFormatting.YELLOW, 13, 86, 4.0F);

    public static final ArcaneShapeDefinition BOLT_SHAPE = new ArcaneShapeDefinition(
            id("bolt"), "Bolt", ArcaneShape.BOLT, 6, 88, 16.0F, 60);
    public static final ArcaneShapeDefinition WAVE_SHAPE = new ArcaneShapeDefinition(
            id("wave"), "Wave", ArcaneShape.WAVE, 9, 76, 10.0F, 80);
    public static final ArcaneShapeDefinition BARRIER_SHAPE = new ArcaneShapeDefinition(
            id("barrier"), "Barrier", ArcaneShape.BARRIER, 11, 74, 0.0F, 160);
    public static final ArcaneShapeDefinition BURST_SHAPE = new ArcaneShapeDefinition(
            id("burst"), "Burst", ArcaneShape.BURST, 10, 72, 6.0F, 40);

    public static final ArcaneModifierDefinition RANGE_MODIFIER = new ArcaneModifierDefinition(
            id("range"), "Range", ModifierEffect.RANGE, 4, -4);
    public static final ArcaneModifierDefinition DURATION_MODIFIER = new ArcaneModifierDefinition(
            id("duration"), "Duration", ModifierEffect.DURATION, 5, -6);
    public static final ArcaneModifierDefinition SPLIT_MODIFIER = new ArcaneModifierDefinition(
            id("split"), "Split", ModifierEffect.SPLIT, 6, -12);
    public static final ArcaneModifierDefinition HOMING_MODIFIER = new ArcaneModifierDefinition(
            id("homing"), "Homing", ModifierEffect.HOMING, 5, -8);
    public static final ArcaneModifierDefinition AMPLIFY_MODIFIER = new ArcaneModifierDefinition(
            id("amplify"), "Amplify", ModifierEffect.AMPLIFY, 7, -10);
    public static final ArcaneModifierDefinition STABILIZE_MODIFIER = new ArcaneModifierDefinition(
            id("stabilize"), "Stabilize", ModifierEffect.STABILIZE, 3, 14);

    public static final List<ArcaneRuneDefinition> RUNES = List.of(FIRE_RUNE, WATER_RUNE, FORCE_RUNE, LIGHT_RUNE);
    public static final List<ArcaneShapeDefinition> SHAPES = List.of(BOLT_SHAPE, WAVE_SHAPE, BARRIER_SHAPE, BURST_SHAPE);
    public static final List<ArcaneModifierDefinition> MODIFIERS = List.of(
            RANGE_MODIFIER,
            DURATION_MODIFIER,
            SPLIT_MODIFIER,
            HOMING_MODIFIER,
            AMPLIFY_MODIFIER,
            STABILIZE_MODIFIER);

    public static final ArcaneRuneDefinition DEFAULT_RUNE = FIRE_RUNE;
    public static final ArcaneShapeDefinition DEFAULT_SHAPE = BOLT_SHAPE;

    public static final Set<ResourceLocation> STARTER_UNLOCKS = Set.of(
            FIRE_RUNE.id(),
            WATER_RUNE.id(),
            FORCE_RUNE.id(),
            BOLT_SHAPE.id(),
            WAVE_SHAPE.id(),
            RANGE_MODIFIER.id(),
            STABILIZE_MODIFIER.id());

    public static final Set<ResourceLocation> ALL_COMPONENTS = Set.copyOf(componentMap().keySet());

    private static final Map<ResourceLocation, Object> COMPONENTS = componentMap();

    private ArcaneContent() {}

    public static ArcaneRuneDefinition rune(ResourceLocation id) {
        Object value = COMPONENTS.get(id);
        return value instanceof ArcaneRuneDefinition rune ? rune : DEFAULT_RUNE;
    }

    public static ArcaneShapeDefinition shape(ResourceLocation id) {
        Object value = COMPONENTS.get(id);
        return value instanceof ArcaneShapeDefinition shape ? shape : DEFAULT_SHAPE;
    }

    public static ArcaneModifierDefinition modifier(ResourceLocation id) {
        Object value = COMPONENTS.get(id);
        return value instanceof ArcaneModifierDefinition modifier ? modifier : STABILIZE_MODIFIER;
    }

    public static int runeIndex(ResourceLocation id) {
        return Math.max(0, RUNES.indexOf(rune(id)));
    }

    public static int shapeIndex(ResourceLocation id) {
        return Math.max(0, SHAPES.indexOf(shape(id)));
    }

    public static int modifierIndex(ResourceLocation id) {
        return Math.max(0, MODIFIERS.indexOf(modifier(id)));
    }

    public static ResourceLocation runeIdByIndex(int index) {
        return RUNES.get(Math.floorMod(index, RUNES.size())).id();
    }

    public static ResourceLocation shapeIdByIndex(int index) {
        return SHAPES.get(Math.floorMod(index, SHAPES.size())).id();
    }

    public static ResourceLocation modifierIdByIndex(int index) {
        return MODIFIERS.get(Math.floorMod(index, MODIFIERS.size())).id();
    }

    public static String buildPresetName(SpellRecipe recipe) {
        ArcaneRuneDefinition rune = rune(recipe.runeId());
        ArcaneShapeDefinition shape = shape(recipe.shapeId());
        return rune.displayName() + " " + shape.displayName();
    }

    private static Map<ResourceLocation, Object> componentMap() {
        Map<ResourceLocation, Object> components = new LinkedHashMap<>();
        RUNES.forEach(rune -> components.put(rune.id(), rune));
        SHAPES.forEach(shape -> components.put(shape.id(), shape));
        MODIFIERS.forEach(modifier -> components.put(modifier.id(), modifier));
        return Map.copyOf(components);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
    }
}
