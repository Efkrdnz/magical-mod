package com.efkrdnz.magical.magic.sound;

/**
 * The five notes the bass and the melody may use.
 *
 * <p>Five-note scales because they have no semitone clashes between neighbours in the common ones, so
 * a grid clicked at random still sounds like a tune: that is the whole reason a mandachord is fun to
 * poke at. A row is a degree of the scale, bottom row lowest; its pitch is counted in semitones above
 * the sample's centre note, which is F sharp, the note a note block is tuned to.
 */
public enum Scale {
    MAJOR(0, 2, 4, 7, 9),
    MINOR(0, 3, 5, 7, 10),
    HIRAJOSHI(0, 2, 3, 7, 8),
    EGYPTIAN(0, 2, 5, 7, 10);

    public static final int DEGREES = 5;

    private final int[] semitones;

    Scale(int... semitones) {
        this.semitones = semitones;
    }

    /** Semitones above the centre for this degree, 0 lowest. */
    public int semitone(int degree) {
        return semitones[Math.max(0, Math.min(DEGREES - 1, degree))];
    }

    public Scale next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Scale byName(String name) {
        for (Scale scale : values()) {
            if (scale.name().equalsIgnoreCase(name)) {
                return scale;
            }
        }
        return MINOR;
    }
}
