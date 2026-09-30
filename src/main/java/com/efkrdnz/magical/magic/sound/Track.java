package com.efkrdnz.magical.magic.sound;

/**
 * The three instruments of the Song, fixed the way a mandachord's are: a kit, a bass and a melody.
 *
 * <p>Each owns a band of rows in the {@link Score} grid and a cap on how many notes a bar may hold.
 * The cap is what makes the Song a composition rather than a wall of noise: every note is also a
 * chance to be judged on, so a bar of sixteen drum hits would be a bar of sixteen free Bulwarks. The
 * kit is the one track that may sound two rows at once - a kick and a hat together is a drum pattern
 * - while the bass and the melody are single voices.
 */
public enum Track {
    PERCUSSION(3, 8, 0xFF9A3C),
    BASS(5, 4, 0xA46BFF),
    MELODY(5, 6, 0x4FC8FF);

    /** Every row of every track, stacked. */
    public static final int TOTAL_ROWS = 13;

    private final int rows;
    private final int barCap;
    private final int rgb;

    Track(int rows, int barCap, int rgb) {
        this.rows = rows;
        this.barCap = barCap;
        this.rgb = rgb;
    }

    public int rows() {
        return rows;
    }

    /** The most notes a single bar of this track may hold. */
    public int barCap() {
        return barCap;
    }

    /** The colour its notes are drawn in, on the grid and in the air. */
    public int rgb() {
        return rgb;
    }

    /** The kit sounds several rows on one step; the bass and the melody are one voice each. */
    public boolean polyphonic() {
        return this == PERCUSSION;
    }

    /** Where this track's first row sits in the stacked grid. */
    public int firstRow() {
        int offset = 0;
        for (Track track : values()) {
            if (track == this) {
                return offset;
            }
            offset += track.rows;
        }
        throw new IllegalStateException();
    }
}
