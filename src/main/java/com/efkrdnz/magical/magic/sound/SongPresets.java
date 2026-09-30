package com.efkrdnz.magical.magic.sound;

import java.util.List;

/**
 * Worked Songs, for a wielder who has not written one and for captures. Each is written as sixteen
 * characters a row, one bar, repeated four times: {@code x} a note, anything else a rest. Rows are
 * listed top down the way the grid is drawn, so the first string of a track is its highest row.
 */
public final class SongPresets {

    public static final List<String> NAMES = List.of("pulse", "march", "lullaby");

    private SongPresets() {}

    public static Score named(String name) {
        return switch (name) {
            case "march" -> march();
            case "lullaby" -> lullaby();
            default -> pulse();
        };
    }

    /** The Song a wielder starts with: four on the floor, a walking bass, a minor figure over it. */
    public static Score pulse() {
        return build(Tempo.STEADY, Scale.MINOR,
                new String[] {"..x.......x.....", "....x.......x...", "x...x...x...x..."},
                new String[] {"................", "......x.........", "........x.......", "..............x.", "x..............."},
                new String[] {"............x...", "........x.......", "....x.........x.", "..x.............", "x..............."});
    }

    public static Score march() {
        return build(Tempo.BRISK, Scale.MAJOR,
                new String[] {"x.x.x.x.........", "....x.......x...", "x.......x......."},
                new String[] {"................", "................", "........x...x...", "................", "x...x..........."},
                new String[] {"x...............", "....x...x.......", "............x...", "..............x.", "..x............."});
    }

    public static Score lullaby() {
        return build(Tempo.SLOW, Scale.HIRAJOSHI,
                new String[] {"................", "........x.......", "x..............."},
                new String[] {"................", "................", "........x.......", "................", "x..............."},
                new String[] {"....x...........", "........x.......", "..x.........x...", "..........x.....", "x..............."});
    }

    private static Score build(Tempo tempo, Scale scale, String[] kit, String[] bass, String[] melody) {
        long[] rows = new long[Track.TOTAL_ROWS];
        fill(rows, Track.PERCUSSION, kit);
        fill(rows, Track.BASS, bass);
        fill(rows, Track.MELODY, melody);
        return Score.of(tempo, scale, rows);
    }

    private static void fill(long[] rows, Track track, String[] topDown) {
        for (int i = 0; i < topDown.length; i++) {
            int row = track.rows() - 1 - i;
            long mask = 0L;
            for (int bar = 0; bar < Score.BARS; bar++) {
                for (int step = 0; step < Score.BAR; step++) {
                    if (topDown[i].charAt(step) == 'x') {
                        mask |= 1L << (bar * Score.BAR + step);
                    }
                }
            }
            rows[track.firstRow() + row] = mask;
        }
    }
}
