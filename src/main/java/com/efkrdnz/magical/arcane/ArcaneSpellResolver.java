package com.efkrdnz.magical.arcane;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArcaneSpellResolver {
    private ArcaneSpellResolver() {}

    public static SpellResolution resolve(SpellRecipe recipe) {
        ArcaneRuneDefinition rune = ArcaneContent.rune(recipe.runeId());
        ArcaneShapeDefinition shape = ArcaneContent.shape(recipe.shapeId());
        List<ArcaneModifierDefinition> modifiers = recipe.uniqueModifierIds().stream()
                .map(ArcaneContent::modifier)
                .toList();

        List<String> problems = new ArrayList<>();
        if (recipe.modifierIds().size() != modifiers.size()) {
            problems.add("Duplicate modifiers destabilize the circle.");
        }

        Set<ModifierEffect> effects = modifiers.stream().map(ArcaneModifierDefinition::effect).collect(java.util.stream.Collectors.toSet());
        if (shape.shape() == ArcaneShape.BARRIER && effects.contains(ModifierEffect.SPLIT)) {
            problems.add("Barrier spells cannot split.");
        }
        if (shape.shape() == ArcaneShape.BARRIER && effects.contains(ModifierEffect.HOMING)) {
            problems.add("Barrier spells cannot track targets.");
        }
        if (shape.shape() == ArcaneShape.BURST && effects.contains(ModifierEffect.HOMING)) {
            problems.add("Burst spells do not support homing.");
        }

        int manaCost = rune.baseCost() + shape.baseCost();
        int stability = Math.min(rune.baseStability(), shape.baseStability());
        float power = rune.basePower();
        float range = shape.baseRange();
        int durationTicks = shape.baseDurationTicks();
        int cooldown = 18;
        int extraTargets = 0;
        boolean homing = false;

        for (ArcaneModifierDefinition modifier : modifiers) {
            manaCost += modifier.manaDelta();
            stability += modifier.stabilityDelta();
            switch (modifier.effect()) {
                case RANGE -> range += 6.0F;
                case DURATION -> durationTicks += 80;
                case SPLIT -> extraTargets += 2;
                case HOMING -> homing = true;
                case AMPLIFY -> power += 2.0F;
                case STABILIZE -> cooldown = Math.max(10, cooldown - 2);
            }
        }

        if (shape.shape() == ArcaneShape.BARRIER) {
            range = 0.0F;
            power *= 0.7F;
        }

        if (shape.shape() == ArcaneShape.WAVE) {
            power += 0.5F;
        }

        if (shape.shape() == ArcaneShape.BURST) {
            power += 1.0F;
            cooldown += 4;
        }

        stability = Math.max(5, Math.min(100, stability));
        manaCost = Math.max(6, manaCost);
        float misfireChance = stability >= 65 ? 0.0F : (65 - stability) / 100.0F;

        return new SpellResolution(
                problems.isEmpty(),
                List.copyOf(problems),
                rune,
                shape,
                modifiers,
                manaCost,
                stability,
                power,
                range,
                durationTicks,
                cooldown,
                extraTargets,
                homing,
                misfireChance);
    }
}
