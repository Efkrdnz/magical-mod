package com.efkrdnz.magical.magic.sound;

import java.util.List;

/**
 * The three Songs the Score ships with, one for each tempo, each a four-bar phrase that turns round
 * in its last bar the way a loop has to or it drones: Pulse (steady, minor - the one a wielder starts
 * with), March (brisk, major, a snare roll into the top) and Lullaby (slow, hirajoshi, almost empty).
 *
 * <p>They are written in the share format ({@link ScoreText}) rather than as bit masks, so every
 * preset is also a worked example of a song a player can paste, and reading one goes through exactly
 * the parser an import does. A preset that fails to read or loses a note to a bar's cap fails at
 * class load, and {@code SongPresetsTest} holds all three to that.
 */
public final class SongPresets {

    /** A preset: the id the command takes and the name the Score shows. */
    public record Preset(String id, String name, Score score) {}

    private static final String PULSE = """
            {
              "magical_song": 1,
              "name": "Pulse",
              "tempo": "steady",
              "scale": "minor",
              "melody": "1...3...4.3.....|1...3...5.4.....|1...3...4.3...2.|3...2...1.......",
              "bass":   "1.....1...4.....|1.....1...3.....|2.....2...4.....|1.....1.....4.3.",
              "hat":    "..x.......x...x.|..x.......x...x.|..x.......x...x.|..x.............",
              "snare":  "....x.......x...|....x.......x...|....x.......x...|....x.......x.xx",
              "kick":   "x.....x...x.....|x.....x...x.....|x.....x...x.....|x.....x..x......"
            }
            """;

    private static final String MARCH = """
            {
              "magical_song": 1,
              "name": "March",
              "tempo": "brisk",
              "scale": "major",
              "melody": "1.1.3...4...3...|4...5...4...3...|1.1.3...4...5...|5...4...3...1...",
              "bass":   "1.......4.......|1.......4.......|5.......4.......|1...4...5...4...",
              "hat":    "..x.......x.....|..x.......x.....|..x.......x.....|................",
              "snare":  "....x..x....x.x.|....x..x....x.x.|....x..x....x.x.|..........x.x.xx",
              "kick":   "x.......x.......|x.......x.......|x.......x.......|x...x...x...x..."
            }
            """;

    private static final String LULLABY = """
            {
              "magical_song": 1,
              "name": "Lullaby",
              "tempo": "slow",
              "scale": "hirajoshi",
              "melody": "5...4...3.......|2.......1.......|5...4...3...5...|4.......2.3.1...",
              "bass":   "1...............|3...............|1...............|4.......3.......",
              "hat":    "........x.......|........x.......|........x.......|....x.......x...",
              "snare":  "................|................|................|..............x.",
              "kick":   "x...............|x...............|x...............|x.......x......."
            }
            """;

    public static final List<Preset> ALL = List.of(preset("pulse", PULSE), preset("march", MARCH), preset("lullaby", LULLABY));

    public static final List<String> NAMES = ALL.stream().map(Preset::id).toList();

    private SongPresets() {}

    /** The preset with this id, or Pulse. */
    public static Preset byId(String id) {
        for (Preset preset : ALL) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return ALL.get(0);
    }

    public static Score named(String id) {
        return byId(id).score();
    }

    /** The Song a wielder starts with. */
    public static Score pulse() {
        return ALL.get(0).score();
    }

    /** The preset shown after {@code index}, wrapping, for a button that walks them. */
    public static int next(int index, int delta) {
        return Math.floorMod(index + delta, ALL.size());
    }

    private static Preset preset(String id, String text) {
        ScoreText.Read read = ScoreText.read(text);
        if (!read.ok() || !read.isSong() || read.dropped() != 0) {
            throw new IllegalStateException("song preset " + id + " does not read cleanly: " + read.problem() + " " + read.detail()
                    + ", dropped " + read.dropped());
        }
        return new Preset(id, read.name(), read.song());
    }
}
