package com.efkrdnz.magical.magic.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

/**
 * A Song or a Riff as text a person can read, edit and paste to a friend - the mandachord's share
 * code, written as JSON so it survives a chat window, a forum post and a text editor.
 *
 * <p>A song is its tempo, its scale and one string a row, 64 steps with a {@code |} between bars:
 * <pre>{@code
 * {
 *   "magical_song": 1,
 *   "name": "March",
 *   "tempo": "brisk",
 *   "scale": "major",
 *   "melody": "1.1.3...4...3...|4...5...4...3...|...",
 *   "bass":   "1.......4.......|...",
 *   "hat":    "..x.......x.....|...",
 *   "snare":  "....x..x....x.x.|...",
 *   "kick":   "x.......x.......|..."
 * }
 * }</pre>
 * The bass and the melody sing one note a step, so each is a single line of scale degrees, {@code 1}
 * the lowest row and {@code 5} the highest; the kit is three lines of {@code x}. Any of {@code . - _}
 * is a rest, spaces and bar lines are ignored, a line of one bar or two is repeated to fill the loop,
 * a shorter one is padded with rests, and a missing line is silence. What the editor would refuse - a
 * bar over its track's cap - is dropped and counted, never smuggled in.
 *
 * <p>A riff is its amplitude and its notes in the command's own spelling:
 * {@code {"magical_riff": 1, "amplitude": 3, "notes": ["harp:12", "rest", "bell:19"]}}.
 *
 * <p>Pure: Gson and the sound core, nothing of Minecraft, pinned by {@code ScoreTextTest}.
 */
public final class ScoreText {

    public static final String SONG_KEY = "magical_song";
    public static final String RIFF_KEY = "magical_riff";
    public static final int VERSION = 1;
    /** Longer than any song this writes by a wide margin; anything bigger is not one. */
    public static final int MAX_CHARS = 16_384;
    public static final int MAX_NAME = 32;
    /** The kit's rows by index, bottom up, the way {@link Track#PERCUSSION} counts them. */
    public static final String[] KIT = {"kick", "snare", "hat"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String NOTE_MARKS = "xXo*#";
    private static final String REST_MARKS = ".-_";

    /** Why a paste was not music. The detail names the line or the value at fault. */
    public enum Problem {
        EMPTY,
        TOO_LONG,
        NOT_JSON,
        NOT_MUSIC,
        NEWER_VERSION,
        BAD_TEMPO,
        BAD_SCALE,
        BAD_ROW,
        BAD_AMPLITUDE,
        BAD_NOTE
    }

    /**
     * What a paste turned out to be: a song or a riff with the name it carried and how many notes
     * the editor's rules dropped, or a problem and its detail.
     */
    public record Read(Score song, Riff riff, String name, int dropped, Problem problem, String detail) {

        static Read failed(Problem problem, String detail) {
            return new Read(null, null, "", 0, problem, detail == null ? "" : detail);
        }

        public boolean ok() {
            return problem == null;
        }

        public boolean isSong() {
            return song != null;
        }

        public boolean isRiff() {
            return riff != null;
        }
    }

    private static final class Bad extends Exception {
        private final Problem problem;
        private final String detail;

        Bad(Problem problem, String detail) {
            super(problem + " " + detail, null, false, false);
            this.problem = problem;
            this.detail = detail;
        }
    }

    private ScoreText() {}

    // ---------------------------------------------------------------- writing

    public static String writeSong(Score score, String name) {
        JsonObject root = new JsonObject();
        root.addProperty(SONG_KEY, VERSION);
        String cleaned = cleanName(name);
        if (!cleaned.isEmpty()) {
            root.addProperty("name", cleaned);
        }
        root.addProperty("tempo", score.tempo().name().toLowerCase(Locale.ROOT));
        root.addProperty("scale", score.scale().name().toLowerCase(Locale.ROOT));
        root.addProperty("melody", voiceLine(score, Track.MELODY));
        root.addProperty("bass", voiceLine(score, Track.BASS));
        for (int row = Track.PERCUSSION.rows() - 1; row >= 0; row--) {
            root.addProperty(KIT[row], kitLine(score, row));
        }
        return GSON.toJson(root);
    }

    public static String writeRiff(Riff riff) {
        JsonObject root = new JsonObject();
        root.addProperty(RIFF_KEY, VERSION);
        root.addProperty("amplitude", riff.amplitude());
        JsonArray notes = new JsonArray();
        for (int slot = 0; slot < riff.length(); slot++) {
            Instrument instrument = riff.instrument(slot);
            notes.add(instrument == null ? "rest" : instrument.id() + ":" + riff.pitch(slot));
        }
        root.add("notes", notes);
        return GSON.toJson(root);
    }

    private static String voiceLine(Score score, Track track) {
        StringBuilder out = new StringBuilder(Score.STEPS + Score.BARS);
        for (int step = 0; step < Score.STEPS; step++) {
            barLine(out, step);
            int row = score.voiceAt(track, step);
            out.append(row < 0 ? '.' : (char) ('1' + row));
        }
        return out.toString();
    }

    private static String kitLine(Score score, int row) {
        StringBuilder out = new StringBuilder(Score.STEPS + Score.BARS);
        for (int step = 0; step < Score.STEPS; step++) {
            barLine(out, step);
            out.append(score.has(Track.PERCUSSION, row, step) ? 'x' : '.');
        }
        return out.toString();
    }

    private static void barLine(StringBuilder out, int step) {
        if (step > 0 && step % Score.BAR == 0) {
            out.append('|');
        }
    }

    // ---------------------------------------------------------------- reading

    /** Reads a pasted song or riff, whichever it says it is. */
    public static Read read(String text) {
        if (text == null || text.isBlank()) {
            return Read.failed(Problem.EMPTY, "");
        }
        if (text.length() > MAX_CHARS) {
            return Read.failed(Problem.TOO_LONG, String.valueOf(text.length()));
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(text.strip());
            if (!parsed.isJsonObject()) {
                return Read.failed(Problem.NOT_MUSIC, "");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return Read.failed(Problem.NOT_JSON, "");
        }
        try {
            if (root.has(SONG_KEY)) {
                version(root, SONG_KEY);
                return readSong(root);
            }
            if (root.has(RIFF_KEY)) {
                version(root, RIFF_KEY);
                return readRiff(root);
            }
            return Read.failed(Problem.NOT_MUSIC, "");
        } catch (Bad bad) {
            return Read.failed(bad.problem, bad.detail);
        }
    }

    private static void version(JsonObject root, String key) throws Bad {
        JsonElement element = root.get(key);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new Bad(Problem.NOT_MUSIC, key);
        }
        if (primitive.getAsInt() > VERSION) {
            throw new Bad(Problem.NEWER_VERSION, String.valueOf(primitive.getAsInt()));
        }
    }

    private static Read readSong(JsonObject root) throws Bad {
        Tempo tempo = named(Tempo.values(), string(root, "tempo", "steady"), Problem.BAD_TEMPO);
        Scale scale = named(Scale.values(), string(root, "scale", "minor"), Problem.BAD_SCALE);
        long[] rows = new long[Track.TOTAL_ROWS];
        voice(rows, Track.MELODY, root, "melody");
        voice(rows, Track.BASS, root, "bass");
        for (int row = 0; row < KIT.length; row++) {
            String line = cells(root, KIT[row]);
            long mask = 0L;
            for (int step = 0; step < Score.STEPS; step++) {
                char mark = line.charAt(step);
                if (NOTE_MARKS.indexOf(mark) >= 0) {
                    mask |= 1L << step;
                } else if (REST_MARKS.indexOf(mark) < 0) {
                    throw new Bad(Problem.BAD_ROW, KIT[row] + " '" + mark + "'");
                }
            }
            rows[Track.PERCUSSION.firstRow() + row] = mask;
        }
        int written = 0;
        for (long row : rows) {
            written += Long.bitCount(row);
        }
        Score score = Score.of(tempo, scale, rows);
        return new Read(score, null, cleanName(string(root, "name", "")), written - score.noteCount(), null, "");
    }

    private static void voice(long[] rows, Track track, JsonObject root, String key) throws Bad {
        String line = cells(root, key);
        for (int step = 0; step < Score.STEPS; step++) {
            char mark = line.charAt(step);
            if (mark >= '1' && mark < '1' + track.rows()) {
                rows[track.firstRow() + (mark - '1')] |= 1L << step;
            } else if (REST_MARKS.indexOf(mark) < 0) {
                throw new Bad(Problem.BAD_ROW, key + " '" + mark + "'");
            }
        }
    }

    /** A line's 64 marks: bar lines and spaces out, one or two bars repeated, a short line padded. */
    private static String cells(JsonObject root, String key) throws Bad {
        if (!root.has(key)) {
            return ".".repeat(Score.STEPS);
        }
        JsonElement element = root.get(key);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new Bad(Problem.BAD_ROW, key);
        }
        StringBuilder marks = new StringBuilder();
        for (char mark : primitive.getAsString().toCharArray()) {
            if (mark != '|' && !Character.isWhitespace(mark)) {
                marks.append(mark);
            }
        }
        String line = marks.toString();
        if (line.length() > Score.STEPS) {
            throw new Bad(Problem.BAD_ROW, key + " " + line.length());
        }
        if (!line.isEmpty() && Score.STEPS % line.length() == 0 && line.length() >= Score.BAR) {
            line = line.repeat(Score.STEPS / line.length());
        }
        return line + ".".repeat(Score.STEPS - line.length());
    }

    private static Read readRiff(JsonObject root) throws Bad {
        JsonElement amplitudeElement = root.get("amplitude");
        int amplitude = Riff.MIN_AMPLITUDE + 1;
        if (amplitudeElement != null) {
            if (!(amplitudeElement instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                throw new Bad(Problem.BAD_AMPLITUDE, String.valueOf(amplitudeElement));
            }
            amplitude = primitive.getAsInt();
            if (amplitude < Riff.MIN_AMPLITUDE || amplitude > Riff.MAX_AMPLITUDE) {
                throw new Bad(Problem.BAD_AMPLITUDE, String.valueOf(amplitude));
            }
        }
        JsonElement notesElement = root.get("notes");
        if (!(notesElement instanceof JsonArray notes) || notes.isEmpty()) {
            throw new Bad(Problem.BAD_NOTE, "notes");
        }
        int length = Math.min(Riff.MAX_NOTES, notes.size());
        int[] instruments = new int[length];
        int[] pitches = new int[length];
        for (int slot = 0; slot < length; slot++) {
            JsonElement note = notes.get(slot);
            if (!(note instanceof JsonPrimitive primitive) || !primitive.isString()) {
                throw new Bad(Problem.BAD_NOTE, String.valueOf(note));
            }
            String spelled = primitive.getAsString().strip().toLowerCase(Locale.ROOT);
            if (spelled.equals("rest")) {
                instruments[slot] = Riff.REST;
                pitches[slot] = Riff.CENTRE;
                continue;
            }
            String[] parts = spelled.split(":");
            Instrument instrument = Instrument.byId(parts[0]);
            if (instrument == null || parts.length > 2) {
                throw new Bad(Problem.BAD_NOTE, spelled);
            }
            int pitch = Riff.CENTRE;
            if (parts.length == 2) {
                try {
                    pitch = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    throw new Bad(Problem.BAD_NOTE, spelled);
                }
                if (pitch < 0 || pitch >= Riff.PITCHES) {
                    throw new Bad(Problem.BAD_NOTE, spelled);
                }
            }
            instruments[slot] = instrument.ordinal();
            pitches[slot] = pitch;
        }
        Riff riff = Riff.of(amplitude, instruments, pitches);
        return new Read(null, riff, cleanName(string(root, "name", "")), notes.size() - length, null, "");
    }

    private static String string(JsonObject root, String key, String fallback) throws Bad {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new Bad(key.equals("tempo") ? Problem.BAD_TEMPO : key.equals("scale") ? Problem.BAD_SCALE : Problem.NOT_MUSIC, key);
        }
        return primitive.getAsString();
    }

    private static <E extends Enum<E>> E named(E[] values, String name, Problem problem) throws Bad {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(name.strip())) {
                return value;
            }
        }
        throw new Bad(problem, name);
    }

    /** A name as shown in a message: printable, no formatting codes, at most {@link #MAX_NAME}. */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : name.strip().toCharArray()) {
            if (c != '§' && !Character.isISOControl(c)) {
                out.append(c);
            }
            if (out.length() == MAX_NAME) {
                break;
            }
        }
        return out.toString().strip();
    }
}
