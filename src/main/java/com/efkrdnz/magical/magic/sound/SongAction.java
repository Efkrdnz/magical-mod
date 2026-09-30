package com.efkrdnz.magical.magic.sound;

/** The two things a wielder does in time with the Song: drop into a crouch, or swing the hand. */
public enum SongAction {
    CROUCH,
    SWING;

    public static SongAction byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }
}
