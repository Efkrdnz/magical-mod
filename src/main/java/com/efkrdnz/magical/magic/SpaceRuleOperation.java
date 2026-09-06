package com.efkrdnz.magical.magic;

import java.util.List;
import java.util.Locale;

public enum SpaceRuleOperation {
    REMOVE_GRAVITY(SpaceRuleCategory.GRAVITY),
    DECREASE_GRAVITY(SpaceRuleCategory.GRAVITY),
    INCREASE_GRAVITY(SpaceRuleCategory.GRAVITY),
    REVERSE_GRAVITY(SpaceRuleCategory.GRAVITY),
    CONTROL_GRAVITY(SpaceRuleCategory.GRAVITY),
    CLEAR_GRAVITY(SpaceRuleCategory.GRAVITY),
    UNIFORM_MOTION(SpaceRuleCategory.VELOCITY),
    STOP(SpaceRuleCategory.VELOCITY),
    CLEAR_VELOCITY(SpaceRuleCategory.VELOCITY),
    ACCELERATE(SpaceRuleCategory.ACCELERATION),
    DECELERATE(SpaceRuleCategory.ACCELERATION),
    REMOVE_ACCELERATION(SpaceRuleCategory.ACCELERATION),
    REVERSE_ACCELERATION(SpaceRuleCategory.ACCELERATION),
    CLEAR_ACCELERATION(SpaceRuleCategory.ACCELERATION),
    VACUUM(SpaceRuleCategory.AIR_RESISTANCE),
    THIN_AIR(SpaceRuleCategory.AIR_RESISTANCE),
    DENSE_AIR(SpaceRuleCategory.AIR_RESISTANCE),
    DRAG_LOCK(SpaceRuleCategory.AIR_RESISTANCE),
    CLEAR_AIR_RESISTANCE(SpaceRuleCategory.AIR_RESISTANCE),
    CRUSH_PRESSURE(SpaceRuleCategory.PRESSURE),
    EXPAND_PRESSURE(SpaceRuleCategory.PRESSURE),
    IMPLODE_PRESSURE(SpaceRuleCategory.PRESSURE),
    BURST_PRESSURE(SpaceRuleCategory.PRESSURE),
    CLEAR_PRESSURE(SpaceRuleCategory.PRESSURE),
    LIGHTEN_MASS(SpaceRuleCategory.MASS),
    WEIGH_DOWN(SpaceRuleCategory.MASS),
    ANCHOR_MASS(SpaceRuleCategory.MASS),
    NORMALIZE_MASS(SpaceRuleCategory.MASS),
    CLEAR_MASS(SpaceRuleCategory.MASS),
    HASTEN_TIME(SpaceRuleCategory.TIME_FLOW),
    SLOW_TIME(SpaceRuleCategory.TIME_FLOW),
    STASIS_TIME(SpaceRuleCategory.TIME_FLOW),
    NORMALIZE_TIME(SpaceRuleCategory.TIME_FLOW),
    CLEAR_TIME_FLOW(SpaceRuleCategory.TIME_FLOW),
    PULL_NORTH(SpaceRuleCategory.VECTOR_FIELD),
    PULL_SOUTH(SpaceRuleCategory.VECTOR_FIELD),
    ORBIT(SpaceRuleCategory.VECTOR_FIELD),
    CONVERGE(SpaceRuleCategory.VECTOR_FIELD),
    CLEAR_VECTOR_FIELD(SpaceRuleCategory.VECTOR_FIELD),
    STABILIZE_ENTROPY(SpaceRuleCategory.ENTROPY),
    DESTABILIZE_ENTROPY(SpaceRuleCategory.ENTROPY),
    CHAOTIC_MOTION(SpaceRuleCategory.ENTROPY),
    CLEAR_ENTROPY(SpaceRuleCategory.ENTROPY),
    SLIPPERY(SpaceRuleCategory.FRICTION),
    STICKY(SpaceRuleCategory.FRICTION),
    NORMALIZE_FRICTION(SpaceRuleCategory.FRICTION),
    CLEAR_FRICTION(SpaceRuleCategory.FRICTION),
    SEAL_BOUNDARY(SpaceRuleCategory.BOUNDARY),
    REPEL_BOUNDARY(SpaceRuleCategory.BOUNDARY),
    ATTRACT_BOUNDARY(SpaceRuleCategory.BOUNDARY),
    WRAP_BOUNDARY(SpaceRuleCategory.BOUNDARY),
    CLEAR_BOUNDARY(SpaceRuleCategory.BOUNDARY),
    DISABLE_COLLISION(SpaceRuleCategory.COLLISION),
    INTENSIFY_COLLISION(SpaceRuleCategory.COLLISION),
    SELECTIVE_COLLISION(SpaceRuleCategory.COLLISION),
    RICOCHET_COLLISION(SpaceRuleCategory.COLLISION),
    CLEAR_COLLISION(SpaceRuleCategory.COLLISION);

    private final SpaceRuleCategory category;

    SpaceRuleOperation(SpaceRuleCategory category) {
        this.category = category;
    }

    public SpaceRuleCategory category() {
        return category;
    }

    public boolean clear() {
        return this == CLEAR_GRAVITY
                || this == CLEAR_VELOCITY
                || this == CLEAR_ACCELERATION
                || this == CLEAR_AIR_RESISTANCE
                || this == CLEAR_PRESSURE
                || this == CLEAR_MASS
                || this == CLEAR_TIME_FLOW
                || this == CLEAR_VECTOR_FIELD
                || this == CLEAR_ENTROPY
                || this == CLEAR_FRICTION
                || this == CLEAR_BOUNDARY
                || this == CLEAR_COLLISION;
    }

    public String translationKey() {
        return "space.magical.operation." + name().toLowerCase(Locale.ROOT);
    }

    public static List<SpaceRuleOperation> forCategory(SpaceRuleCategory category) {
        return java.util.Arrays.stream(values()).filter(operation -> operation.category == category).toList();
    }
}
