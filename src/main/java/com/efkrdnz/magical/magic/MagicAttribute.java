package com.efkrdnz.magical.magic;

public enum MagicAttribute {
    ARCANE,
    FIRE,
    WATER,
    SPATIAL,
    SOUL,
    DIVINE,
    /** Shared by MagicSchool.VOID and MagicSchool.DARK, and unrelated to ForgeElementKind.DARK. */
    DARK,
    BLOOD,
    CHAOS,
    PRIMORDIAL,
    ELDRITCH;

    public static MagicAttribute fromSchool(MagicSchool school) {
        return switch (school) {
            case ARCANE -> ARCANE;
            case FIRE -> FIRE;
            case WATER -> WATER;
            case SPATIAL -> SPATIAL;
            case SOUL -> SOUL;
            case LIGHT -> DIVINE;
            // VOID keeps mapping to DARK: the skills still in the void school must behave exactly
            // as they did before the dark school existed.
            case VOID, DARK -> DARK;
            case BLOOD -> BLOOD;
            case CHAOS -> CHAOS;
            case PRIMORDIAL -> PRIMORDIAL;
            case ELDRITCH -> ELDRITCH;
        };
    }

    public boolean counters(MagicAttribute incoming) {
        return switch (this) {
            case DARK -> incoming == DIVINE;
            // Light is the one answer that reaches every forbidden school.
            case DIVINE -> incoming == DARK || incoming == BLOOD || incoming == CHAOS
                    || incoming == PRIMORDIAL || incoming == ELDRITCH;
            case ARCANE -> incoming == SPATIAL;
            case SPATIAL -> incoming == ARCANE;
            case SOUL -> incoming == DARK || incoming == BLOOD;
            case WATER -> incoming == FIRE;
            case FIRE -> incoming == WATER;
            case BLOOD -> incoming == SOUL;
            // Chaos unmakes the language magic is written in.
            case CHAOS -> incoming == ARCANE;
            // Careful geometry means nothing to something that simply opens a hole in it.
            case ELDRITCH -> incoming == SPATIAL;
            // Nothing answers the world's own oldest power in kind.
            case PRIMORDIAL -> false;
        };
    }
}
