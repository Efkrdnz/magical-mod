package com.efkrdnz.magical.magic;

public enum MagicAttribute {
    ARCANE,
    FIRE,
    WATER,
    SPATIAL,
    SOUL,
    DIVINE,
    DARK;

    public static MagicAttribute fromSchool(MagicSchool school) {
        return switch (school) {
            case ARCANE -> ARCANE;
            case FIRE -> FIRE;
            case WATER -> WATER;
            case SPATIAL -> SPATIAL;
            case SOUL -> SOUL;
            case LIGHT -> DIVINE;
            case VOID -> DARK;
        };
    }

    public boolean counters(MagicAttribute incoming) {
        return switch (this) {
            case DARK -> incoming == DIVINE;
            case DIVINE -> incoming == DARK;
            case ARCANE -> incoming == SPATIAL;
            case SPATIAL -> incoming == ARCANE;
            case SOUL -> incoming == DARK;
            case WATER -> incoming == FIRE;
            case FIRE -> incoming == WATER;
        };
    }
}
