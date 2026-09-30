package com.efkrdnz.magical.magic.sound;

import java.util.Arrays;

/**
 * The Song as written: a loop of 64 sixteenth-note steps, four bars, over thirteen rows.
 *
 * <p>Every row is one {@code long}, one bit a step, which is the whole reason the loop is 64 long:
 * the grid is thirteen numbers on the wire and in the save, and a step's notes are a mask test. The
 * rows are stacked by {@link Track} - kit, then bass, then melody - and counted bottom up within a
 * track, so a higher row is a higher note.
 *
 * <p>A score is immutable. Every edit returns a new one or refuses, and {@link #of} repairs anything
 * read from outside - a save, a packet - to the same rules the editor enforces, so a hand-forged grid
 * can never hold a chord in the bass or a ninth drum hit in a bar.
 */
public final class Score {

    public static final int STEPS = 64;
    public static final int BAR = 16;
    public static final int BARS = STEPS / BAR;

    /** Why an edit was refused. */
    public enum Refusal {
        NONE,
        OUT_OF_RANGE,
        BAR_FULL
    }

    /** The outcome of an edit: the score after it, and the reason when nothing changed. */
    public record Edit(Score score, Refusal refusal) {
        public boolean accepted() {
            return refusal == Refusal.NONE;
        }
    }

    private static final Score EMPTY = new Score(Tempo.STEADY, Scale.MINOR, new long[Track.TOTAL_ROWS]);

    private final Tempo tempo;
    private final Scale scale;
    private final long[] rows;

    private Score(Tempo tempo, Scale scale, long[] rows) {
        this.tempo = tempo;
        this.scale = scale;
        this.rows = rows;
    }

    public static Score empty() {
        return EMPTY;
    }

    /** A score built from outside data, repaired to the editor's rules. */
    public static Score of(Tempo tempo, Scale scale, long[] raw) {
        long[] rows = new long[Track.TOTAL_ROWS];
        if (raw != null) {
            System.arraycopy(raw, 0, rows, 0, Math.min(raw.length, rows.length));
        }
        for (Track track : Track.values()) {
            repair(track, rows);
        }
        return new Score(tempo == null ? Tempo.STEADY : tempo, scale == null ? Scale.MINOR : scale, rows);
    }

    private static void repair(Track track, long[] rows) {
        int first = track.firstRow();
        if (!track.polyphonic()) {
            for (int step = 0; step < STEPS; step++) {
                long bit = 1L << step;
                boolean kept = false;
                for (int row = 0; row < track.rows(); row++) {
                    if ((rows[first + row] & bit) != 0) {
                        if (kept) {
                            rows[first + row] &= ~bit;
                        }
                        kept = true;
                    }
                }
            }
        }
        for (int bar = 0; bar < BARS; bar++) {
            int count = 0;
            for (int step = bar * BAR; step < (bar + 1) * BAR; step++) {
                long bit = 1L << step;
                for (int row = 0; row < track.rows(); row++) {
                    if ((rows[first + row] & bit) != 0 && ++count > track.barCap()) {
                        rows[first + row] &= ~bit;
                    }
                }
            }
        }
    }

    public Tempo tempo() {
        return tempo;
    }

    public Scale scale() {
        return scale;
    }

    /** The raw mask of one stacked row, for the codec. */
    public long row(int stackedRow) {
        return rows[stackedRow];
    }

    public long[] rows() {
        return rows.clone();
    }

    public boolean has(Track track, int row, int step) {
        if (!inRange(track, row, step)) {
            return false;
        }
        return (rows[track.firstRow() + row] & (1L << step)) != 0;
    }

    /** Whether any track sounds on this step. */
    public boolean sounds(int step) {
        long bit = 1L << Math.floorMod(step, STEPS);
        for (long row : rows) {
            if ((row & bit) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether this track sounds on this step. */
    public boolean sounds(Track track, int step) {
        long bit = 1L << Math.floorMod(step, STEPS);
        int first = track.firstRow();
        for (int row = 0; row < track.rows(); row++) {
            if ((rows[first + row] & bit) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Notes this track holds in one bar: every row counted for the kit, every step for a voice. */
    public int notesInBar(Track track, int bar) {
        int first = track.firstRow();
        long mask = barMask(bar);
        int count = 0;
        for (int row = 0; row < track.rows(); row++) {
            count += Long.bitCount(rows[first + row] & mask);
        }
        return count;
    }

    public int noteCount() {
        int count = 0;
        for (long row : rows) {
            count += Long.bitCount(row);
        }
        return count;
    }

    public boolean isEmpty() {
        return noteCount() == 0;
    }

    /**
     * The score with one cell set or cleared. Setting a note in a single-voice track moves that
     * track's voice to the new row on that step rather than refusing, which is what a click on a
     * different row of the same column means.
     */
    public Edit set(Track track, int row, int step, boolean on) {
        if (!inRange(track, row, step)) {
            return new Edit(this, Refusal.OUT_OF_RANGE);
        }
        if (has(track, row, step) == on) {
            return new Edit(this, Refusal.NONE);
        }
        long[] next = rows.clone();
        int first = track.firstRow();
        long bit = 1L << step;
        if (!on) {
            next[first + row] &= ~bit;
            return new Edit(new Score(tempo, scale, next), Refusal.NONE);
        }
        boolean moved = false;
        if (!track.polyphonic()) {
            for (int other = 0; other < track.rows(); other++) {
                if ((next[first + other] & bit) != 0) {
                    next[first + other] &= ~bit;
                    moved = true;
                }
            }
        }
        if (!moved && notesInBar(track, step / BAR) >= track.barCap()) {
            return new Edit(this, Refusal.BAR_FULL);
        }
        next[first + row] |= bit;
        return new Edit(new Score(tempo, scale, next), Refusal.NONE);
    }

    public Edit toggle(Track track, int row, int step) {
        return set(track, row, step, !has(track, row, step));
    }

    public Score withTempo(Tempo tempo) {
        return new Score(tempo, scale, rows);
    }

    public Score withScale(Scale scale) {
        return new Score(tempo, scale, rows);
    }

    /** Everything in one track cleared. */
    public Score cleared(Track track) {
        long[] next = rows.clone();
        for (int row = 0; row < track.rows(); row++) {
            next[track.firstRow() + row] = 0L;
        }
        return new Score(tempo, scale, next);
    }

    /** The row of a single-voice track sounding on a step, or -1. */
    public int voiceAt(Track track, int step) {
        int wrapped = Math.floorMod(step, STEPS);
        for (int row = 0; row < track.rows(); row++) {
            if (has(track, row, wrapped)) {
                return row;
            }
        }
        return -1;
    }

    private static long barMask(int bar) {
        return 0xFFFFL << (bar * BAR);
    }

    private static boolean inRange(Track track, int row, int step) {
        return track != null && row >= 0 && row < track.rows() && step >= 0 && step < STEPS;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Score score && tempo == score.tempo && scale == score.scale && Arrays.equals(rows, score.rows);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * tempo.hashCode() + scale.hashCode()) + Arrays.hashCode(rows);
    }
}
