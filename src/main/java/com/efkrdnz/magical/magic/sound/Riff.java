package com.efkrdnz.magical.magic.sound;

import java.util.Arrays;

/**
 * The Riff as written: up to eight notes looped while the key is held, and one amplitude for all.
 *
 * <p>A note is an {@link Instrument} and a pitch from 0 to 24, the two octaves a sample can be
 * shifted through; pitch 12 is the sample as recorded. A slot may be a rest, which costs nothing and
 * fires nothing, and is how a riff gets its rhythm. The amplitude is the whole of the mana decision:
 * {@link RiffNote} bills its square and pays out its line, so turning it up is always stronger and
 * always dearer per point of damage.
 *
 * <p>Immutable, repaired on the way in from outside the way {@link Score} is.
 */
public final class Riff {

    public static final int MAX_NOTES = 8;
    public static final int PITCHES = 25;
    public static final int CENTRE = 12;
    public static final int REST = -1;
    public static final int MIN_AMPLITUDE = 1;
    public static final int MAX_AMPLITUDE = 5;
    /** Ticks between notes while the Riff is held: eighth notes at 150 beats a minute. */
    public static final int STEP_TICKS = 4;

    private static final Riff DEFAULT = of(2,
            new int[] {Instrument.HARP.ordinal(), Instrument.HARP.ordinal(), Instrument.HARP.ordinal(), REST},
            new int[] {12, 15, 19, 12});

    private final int[] instruments;
    private final int[] pitches;
    private final int amplitude;

    private Riff(int[] instruments, int[] pitches, int amplitude) {
        this.instruments = instruments;
        this.pitches = pitches;
        this.amplitude = amplitude;
    }

    /** The riff every wielder starts with: a rising harp figure and a rest. */
    public static Riff standard() {
        return DEFAULT;
    }

    /** A riff built from outside data: the length clamped to 1..8, unknown instruments read as rests. */
    public static Riff of(int amplitude, int[] instruments, int[] pitches) {
        int length = Math.max(1, Math.min(MAX_NOTES, instruments == null ? 0 : instruments.length));
        int[] kept = new int[length];
        int[] tones = new int[length];
        for (int i = 0; i < length; i++) {
            int raw = instruments == null || i >= instruments.length ? REST : instruments[i];
            kept[i] = Instrument.byOrdinal(raw) == null ? REST : raw;
            int pitch = pitches == null || i >= pitches.length ? CENTRE : pitches[i];
            tones[i] = Math.max(0, Math.min(PITCHES - 1, pitch));
        }
        return new Riff(kept, tones, clampAmplitude(amplitude));
    }

    public static int clampAmplitude(int amplitude) {
        return Math.max(MIN_AMPLITUDE, Math.min(MAX_AMPLITUDE, amplitude));
    }

    public int length() {
        return instruments.length;
    }

    public int amplitude() {
        return amplitude;
    }

    /** The instrument in a slot, or null for a rest. */
    public Instrument instrument(int slot) {
        return slot >= 0 && slot < instruments.length ? Instrument.byOrdinal(instruments[slot]) : null;
    }

    public int pitch(int slot) {
        return slot >= 0 && slot < pitches.length ? pitches[slot] : CENTRE;
    }

    public boolean rest(int slot) {
        return instrument(slot) == null;
    }

    /** The slot the n-th note of a held riff plays. */
    public int slotAt(long note) {
        return (int) Math.floorMod(note, (long) instruments.length);
    }

    public int[] instrumentOrdinals() {
        return instruments.clone();
    }

    public int[] pitches() {
        return pitches.clone();
    }

    public Riff withNote(int slot, Instrument instrument, int pitch) {
        if (slot < 0 || slot >= instruments.length) {
            return this;
        }
        int[] kept = instruments.clone();
        int[] tones = pitches.clone();
        kept[slot] = instrument == null ? REST : instrument.ordinal();
        tones[slot] = Math.max(0, Math.min(PITCHES - 1, pitch));
        return new Riff(kept, tones, amplitude);
    }

    public Riff withRest(int slot) {
        return withNote(slot, null, pitch(slot));
    }

    /** Longer by rests at the centre pitch, or shorter from the end. */
    public Riff withLength(int length) {
        int clamped = Math.max(1, Math.min(MAX_NOTES, length));
        int[] kept = Arrays.copyOf(instruments, clamped);
        int[] tones = Arrays.copyOf(pitches, clamped);
        for (int i = instruments.length; i < clamped; i++) {
            kept[i] = REST;
            tones[i] = CENTRE;
        }
        return new Riff(kept, tones, amplitude);
    }

    public Riff withAmplitude(int amplitude) {
        return new Riff(instruments, pitches, clampAmplitude(amplitude));
    }

    /** The playback rate of a pitch: 0.5 at the bottom, 1 at the centre, 2 at the top. */
    public static float pitchRate(int pitch) {
        return (float) Math.pow(2.0D, (pitch - CENTRE) / 12.0D);
    }

    /** Mana a second while held, rests included in the time and free. */
    public float manaPerSecond() {
        float perLoop = 0.0F;
        for (int slot = 0; slot < length(); slot++) {
            Instrument instrument = instrument(slot);
            if (instrument != null) {
                perLoop += RiffNote.resolve(instrument, pitch(slot), amplitude).mana();
            }
        }
        return perLoop * 20.0F / (length() * STEP_TICKS);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Riff riff && amplitude == riff.amplitude
                && Arrays.equals(instruments, riff.instruments) && Arrays.equals(pitches, riff.pitches);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * amplitude + Arrays.hashCode(instruments)) + Arrays.hashCode(pitches);
    }
}
