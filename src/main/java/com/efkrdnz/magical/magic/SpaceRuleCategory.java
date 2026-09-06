package com.efkrdnz.magical.magic;

public enum SpaceRuleCategory {
    GRAVITY,
    VELOCITY,
    ACCELERATION,
    AIR_RESISTANCE,
    PRESSURE,
    MASS,
    TIME_FLOW,
    VECTOR_FIELD,
    ENTROPY,
    FRICTION;

    public String translationKey() {
        return "space.magical.category." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
