package com.efkrdnz.magical.magic.sound;

import java.util.Locale;

/**
 * The sixteen instruments a Riff may play, named and ordered as the note block names them, each in
 * the {@link Family} that decides what its note does. The Song does not use these; its three
 * instruments are fixed ({@link Track}).
 */
public enum Instrument {
    HARP(Family.KEYS),
    BASEDRUM(Family.DRUMS),
    SNARE(Family.DRUMS),
    HAT(Family.DRUMS),
    BASS(Family.LOW),
    FLUTE(Family.STRINGS),
    BELL(Family.BELLS),
    GUITAR(Family.STRINGS),
    CHIME(Family.BELLS),
    XYLOPHONE(Family.BELLS),
    IRON_XYLOPHONE(Family.BELLS),
    COW_BELL(Family.BELLS),
    DIDGERIDOO(Family.LOW),
    BIT(Family.KEYS),
    BANJO(Family.STRINGS),
    PLING(Family.KEYS);

    private final Family family;

    Instrument(Family family) {
        this.family = family;
    }

    public Family family() {
        return family;
    }

    /** The sound file and the lang key: {@code iron_xylophone}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Instrument byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }

    public static Instrument byId(String id) {
        for (Instrument instrument : values()) {
            if (instrument.id().equalsIgnoreCase(id)) {
                return instrument;
            }
        }
        return null;
    }

    public Instrument next(int delta) {
        return values()[Math.floorMod(ordinal() + delta, values().length)];
    }
}
