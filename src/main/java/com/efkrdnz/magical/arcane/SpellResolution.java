package com.efkrdnz.magical.arcane;

import java.util.List;

public record SpellResolution(
        boolean valid,
        List<String> problems,
        ArcaneRuneDefinition rune,
        ArcaneShapeDefinition shape,
        List<ArcaneModifierDefinition> modifiers,
        int manaCost,
        int stability,
        float power,
        float range,
        int durationTicks,
        int castCooldownTicks,
        int extraTargets,
        boolean homing,
        float misfireChance) {

    public boolean unstable() {
        return misfireChance > 0.0F;
    }
}
