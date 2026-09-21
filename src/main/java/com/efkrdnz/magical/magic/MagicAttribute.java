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
    ELDRITCH,
    /** The Sword school. An edge is a shape argument, which is why its arms are the two schools that refuse shape. */
    SWORD;

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
            case SWORD -> SWORD;
        };
    }

    public boolean counters(MagicAttribute incoming) {
        return switch (this) {
            case DARK -> incoming == DIVINE;
            // Light is the one answer that reaches every forbidden school.
            case DIVINE -> incoming == DARK || incoming == BLOOD || incoming == CHAOS
                    || incoming == PRIMORDIAL || incoming == ELDRITCH || incoming == SWORD;
            case ARCANE -> incoming == SPATIAL;
            // A cut lands where the swing went, and a spatial answer is that the target was
            // never there: you cannot cut what is not where you swung.
            case SPATIAL -> incoming == ARCANE || incoming == SWORD;
            case SOUL -> incoming == DARK || incoming == BLOOD;
            case WATER -> incoming == FIRE;
            case FIRE -> incoming == WATER;
            case BLOOD -> incoming == SOUL;
            // Chaos unmakes the language magic is written in.
            case CHAOS -> incoming == ARCANE;
            // Careful geometry means nothing to something that simply opens a hole in it.
            case ELDRITCH -> incoming == SPATIAL;
            // An edge answers the formless and the unmade. Both are arguments about what a
            // shape is, and a blade settles that argument by dividing the shape in two.
            case SWORD -> incoming == ELDRITCH || incoming == CHAOS;
            // Nothing answers the world's own oldest power in kind.
            case PRIMORDIAL -> false;
        };
    }
}
