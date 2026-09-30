package com.efkrdnz.magical.client.screen.sound;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.sound.Riff;
import com.efkrdnz.magical.magic.sound.Score;
import com.efkrdnz.magical.magic.sound.Track;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything on the Score sits, as pure arithmetic of the gui size; pinned by
 * {@code ScoreLayoutTest}.
 *
 * <p>The Song tab is the grid: the melody on top, the bass under it, the kit at the bottom, each row a
 * note and each column a sixteenth. Sixty-four columns is the whole loop and needs a wide gui, so the
 * grid shows as much of the loop as fits - all four bars, two, or one - and pages through the rest. A
 * cell is always 12 or 8 pixels, the two sizes the beads and sockets are drawn at in the atlas: a bead
 * scaled to any other size is a smear. The Riff tab is eight strings, one a note, each fretted with
 * the 25 pitches a sample can be shifted through and the note written sitting on it.
 *
 * <p>Nothing here is a constant the screen has to be big enough for: the smallest gui the game hands
 * out is 320x240, and the test sweeps from there to 1280x720.
 */
public final class ScoreLayout {

    public enum Tab {
        SONG,
        RIFF
    }

    /** A cell of the song grid under the pointer: its track, its row and its column on screen. */
    public record Cell(Track track, int row, int column) {}

    public static final int MARGIN = 8;
    public static final int TOP = 6;
    public static final int TAB_W = 60;
    public static final int TAB_H = 16;
    public static final int LINE = 12;
    public static final int FLOURISH_W = 128;
    public static final int FLOURISH_H = 9;
    /** The first row of cells; the bar numbers sit in the {@link #NUMBERS} pixels above it. */
    public static final int GRID_TOP = 48;
    public static final int NUMBERS = 11;
    /** A track's glyph, a gap, and the widest row name ("Snare"). */
    public static final int LABEL_W = 56;
    public static final int GLYPH = 16;
    public static final int TRACK_GAP = 5;
    public static final int LARGE_CELL = 12;
    public static final int SMALL_CELL = 8;
    public static final int MIN_CELL = SMALL_CELL;
    /** Two rows of controls, a readout line and a hint line under the grid. */
    public static final int BELOW = 4 * (LINE + 2) + 6;
    public static final int ARROW_W = 12;
    public static final int PAGE_W = 52;
    /** A word control on the first row: tempo, scale, preset. Four of them and the pager fit in 304. */
    public static final int WORD_W = 70;
    public static final int BUTTON_W = 56;
    public static final int GAP = 6;
    public static final int MAX_SLOT_W = 46;
    public static final int MAX_PITCH_CELL = 6;
    /** The family icon over each string and the gap under it. */
    public static final int ICON_ROW = GLYPH + 2;
    public static final int AMPLITUDE_W = 58;
    public static final int PIP_W = 6;
    public static final int PIP_GAP = 3;

    /** The display order of the tracks, top to bottom. */
    public static final Track[] DISPLAY = {Track.MELODY, Track.BASS, Track.PERCUSSION};

    private static final int[] SPANS = {Score.STEPS, Score.STEPS / 2, Score.BAR};

    private final int width;
    private final int height;

    public ScoreLayout(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Rect tab(Tab tab) {
        int left = width / 2 - TAB_W - GAP / 2;
        return new Rect("tab_" + tab.name().toLowerCase(), left + tab.ordinal() * (TAB_W + GAP), TOP, TAB_W, TAB_H);
    }

    /** The divider under the tabs. */
    public Rect flourish() {
        return new Rect("flourish", (width - FLOURISH_W) / 2, TOP + TAB_H + 2, FLOURISH_W, FLOURISH_H);
    }

    // ---------------------------------------------------------------- the Song

    private int roomFor(int span) {
        int across = (width - 2 * MARGIN - LABEL_W) / span;
        int down = (height - GRID_TOP - BELOW - 2 * TRACK_GAP) / Track.TOTAL_ROWS;
        return Math.min(across, down);
    }

    /** The largest of the atlas's two cell sizes that fits, or whatever fits below that. */
    private static int snap(int room) {
        if (room >= LARGE_CELL) {
            return LARGE_CELL;
        }
        return room >= SMALL_CELL ? SMALL_CELL : Math.max(2, room);
    }

    /** How many steps of the loop the grid shows at once: 64, 32 or 16. */
    public int visibleSteps() {
        for (int span : SPANS) {
            if (roomFor(span) >= MIN_CELL) {
                return span;
            }
        }
        return Score.BAR;
    }

    public int cell() {
        return snap(roomFor(visibleSteps()));
    }

    public int pages() {
        return Score.STEPS / visibleSteps();
    }

    public int gridLeft() {
        return (width - (LABEL_W + visibleSteps() * cell())) / 2 + LABEL_W;
    }

    public int gridRight() {
        return gridLeft() + visibleSteps() * cell();
    }

    public int gridHeight() {
        return Track.TOTAL_ROWS * cell() + 2 * TRACK_GAP;
    }

    public int gridBottom() {
        return GRID_TOP + gridHeight();
    }

    /** The top of the first row of a track as drawn, its highest note. */
    public int trackTop(Track track) {
        int y = GRID_TOP;
        for (Track shown : DISPLAY) {
            if (shown == track) {
                return y;
            }
            y += shown.rows() * cell() + TRACK_GAP;
        }
        throw new IllegalArgumentException(String.valueOf(track));
    }

    /** The top of a row of a track: higher notes higher, the melody above the bass above the kit. */
    public int rowTop(Track track, int row) {
        return trackTop(track) + (track.rows() - 1 - row) * cell();
    }

    public Rect cell(Track track, int row, int column) {
        return new Rect("cell", gridLeft() + column * cell(), rowTop(track, row), cell(), cell());
    }

    /** The label beside one row: a drum name or a note name, right-aligned against the grid. */
    public Rect rowLabel(Track track, int row) {
        return new Rect("label", gridLeft() - LABEL_W + GLYPH + 2, rowTop(track, row), LABEL_W - GLYPH - 6, cell());
    }

    /** The track's glyph, centred down the height of its rows at the far left of the labels. */
    public Rect trackGlyph(Track track) {
        int top = trackTop(track) + (track.rows() * cell() - GLYPH) / 2;
        return new Rect("glyph", gridLeft() - LABEL_W, top, GLYPH, GLYPH);
    }

    /** The bar number over the first column of a bar that starts on this page. */
    public Rect barNumber(int column) {
        return new Rect("bar_number", gridLeft() + column * cell(), GRID_TOP - NUMBERS, Score.BAR * cell(), NUMBERS - 2);
    }

    public Cell cellAt(double x, double y) {
        if (x < gridLeft() || x >= gridRight()) {
            return null;
        }
        int column = (int) ((x - gridLeft()) / cell());
        for (Track track : DISPLAY) {
            for (int row = 0; row < track.rows(); row++) {
                int top = rowTop(track, row);
                if (y >= top && y < top + cell()) {
                    return new Cell(track, row, column);
                }
            }
        }
        return null;
    }

    private int controlsTop() {
        return gridBottom() + 8;
    }

    public Rect pagePrev() {
        return new Rect("page_prev", songRowLeft(), controlsTop(), ARROW_W, LINE);
    }

    public Rect pageLabel() {
        return new Rect("page", songRowLeft() + ARROW_W, controlsTop(), PAGE_W, LINE);
    }

    public Rect pageNext() {
        return new Rect("page_next", songRowLeft() + ARROW_W + PAGE_W, controlsTop(), ARROW_W, LINE);
    }

    public Rect tempo() {
        return new Rect("tempo", songRowLeft() + pagerSpace(), controlsTop(), WORD_W, LINE);
    }

    public Rect scale() {
        return new Rect("scale", songRowLeft() + pagerSpace() + WORD_W + GAP, controlsTop(), WORD_W, LINE);
    }

    /** The preset button: a click loads the next of the three, a right-click the one before. */
    public Rect preset() {
        return new Rect("preset", songRowLeft() + pagerSpace() + 2 * (WORD_W + GAP), controlsTop(), WORD_W, LINE);
    }

    private static int pagerWidth() {
        return 2 * ARROW_W + PAGE_W;
    }

    /** The pager and its gap when the loop is paged, nothing when it all shows: the row stays centred. */
    private int pagerSpace() {
        return pages() > 1 ? pagerWidth() + GAP : 0;
    }

    private int songRowLeft() {
        return (width - (pagerSpace() + 3 * WORD_W + 2 * GAP)) / 2;
    }

    /** The second row of either tab: play, save, import, export, in that order and centred. */
    public static final int ACTIONS = 4;

    private Rect action(String name, int top, int index) {
        int left = (width - (ACTIONS * BUTTON_W + (ACTIONS - 1) * GAP)) / 2;
        return new Rect(name, left + index * (BUTTON_W + GAP), top, BUTTON_W, LINE);
    }

    private int actionsTop(Tab tab) {
        return (tab == Tab.SONG ? controlsTop() : riffControlsTop()) + LINE + 4;
    }

    public Rect play() {
        return action("play", actionsTop(Tab.SONG), 0);
    }

    public Rect save() {
        return action("save", actionsTop(Tab.SONG), 1);
    }

    /** Import reads the clipboard; export writes the tab shown to it. */
    public Rect importButton(Tab tab) {
        return action("import", actionsTop(tab), 2);
    }

    public Rect exportButton(Tab tab) {
        return action("export", actionsTop(tab), 3);
    }

    public Rect readout(Tab tab) {
        int top = (tab == Tab.SONG ? controlsTop() : riffControlsTop()) + 2 * (LINE + 4);
        return new Rect("readout", MARGIN, top, width - 2 * MARGIN, LINE);
    }

    public Rect hint(Tab tab) {
        return new Rect("hint", MARGIN, readout(tab).bottom() + 2, width - 2 * MARGIN, LINE);
    }

    /** Everything on the Song tab that must not overlap anything else, the grid and its labels as one block. */
    public List<Rect> songControls() {
        int top = GRID_TOP - NUMBERS;
        List<Rect> out = new ArrayList<>(List.of(tab(Tab.SONG), tab(Tab.RIFF), flourish(),
                new Rect("grid", gridLeft() - LABEL_W, top, LABEL_W + visibleSteps() * cell(), gridBottom() - top),
                tempo(), scale(), preset(), play(), save(), importButton(Tab.SONG), exportButton(Tab.SONG),
                readout(Tab.SONG), hint(Tab.SONG)));
        if (pages() > 1) {
            out.add(pagePrev());
            out.add(pageLabel());
            out.add(pageNext());
        }
        return out;
    }

    // ---------------------------------------------------------------- the Riff

    public int slotWidth() {
        return Math.min(MAX_SLOT_W, (width - 2 * MARGIN) / Riff.MAX_NOTES);
    }

    public int pitchCell() {
        int room = height - stringsTop() - BELOW - 2 * LINE - 8;
        return Math.max(2, Math.min(MAX_PITCH_CELL, room / Riff.PITCHES));
    }

    private static int stringsTop() {
        return GRID_TOP + ICON_ROW;
    }

    public int riffLeft() {
        return (width - Riff.MAX_NOTES * slotWidth()) / 2;
    }

    public Rect strip(int slot) {
        return new Rect("strip", riffLeft() + slot * slotWidth() + 3, stringsTop(), slotWidth() - 6, Riff.PITCHES * pitchCell());
    }

    /** The x of the string drawn down the middle of a strip. */
    public int stringX(int slot) {
        Rect strip = strip(slot);
        return strip.x() + strip.w() / 2;
    }

    /** The family icon over a strip. */
    public Rect familyIcon(int slot) {
        return new Rect("family", stringX(slot) - GLYPH / 2, GRID_TOP, GLYPH, GLYPH);
    }

    /** The name under a strip: its instrument, then its note. */
    public Rect slotLabel(int slot, int line) {
        Rect strip = strip(slot);
        return new Rect("slot_label", riffLeft() + slot * slotWidth(), strip.bottom() + 3 + line * (LINE - 2), slotWidth(), LINE - 2);
    }

    /** The top of the band pitch {@code pitch} is drawn in, the highest pitch at the top. */
    public int pitchTop(int pitch) {
        return stringsTop() + (Riff.PITCHES - 1 - pitch) * pitchCell();
    }

    /** The y of the middle of a pitch's band, where a note written at it sits. */
    public int pitchCentre(int pitch) {
        return pitchTop(pitch) + pitchCell() / 2;
    }

    /** The slot a point is over, icon, strip or its labels, or -1. */
    public int slotAt(double x, double y) {
        if (y < GRID_TOP || y >= slotLabel(0, 1).bottom() || x < riffLeft() || x >= riffLeft() + Riff.MAX_NOTES * slotWidth()) {
            return -1;
        }
        return (int) ((x - riffLeft()) / slotWidth());
    }

    /** The pitch a point on a strip picks, or -1 off the strip. */
    public int pitchAt(int slot, double y) {
        Rect strip = strip(slot);
        if (y < strip.y() || y >= strip.bottom()) {
            return -1;
        }
        return Riff.PITCHES - 1 - (int) ((y - strip.y()) / pitchCell());
    }

    private int riffControlsTop() {
        return slotLabel(0, 1).bottom() + 6;
    }

    public Rect lengthPrev() {
        return new Rect("length_prev", riffRowLeft(), riffControlsTop(), ARROW_W, LINE);
    }

    public Rect lengthLabel() {
        return new Rect("length", riffRowLeft() + ARROW_W, riffControlsTop(), PAGE_W, LINE);
    }

    public Rect lengthNext() {
        return new Rect("length_next", riffRowLeft() + ARROW_W + PAGE_W, riffControlsTop(), ARROW_W, LINE);
    }

    public Rect amplitudeLabel() {
        return new Rect("amplitude", riffRowLeft() + pagerWidth() + GAP, riffControlsTop(), AMPLITUDE_W, LINE);
    }

    /** The clickable column of one amplitude bar; the bar drawn in it rises with the amplitude. */
    public Rect pip(int amplitude) {
        int left = amplitudeLabel().right() + 2;
        return new Rect("pip", left + (amplitude - 1) * (PIP_W + PIP_GAP), riffControlsTop(), PIP_W, LINE);
    }

    private int riffRowLeft() {
        int wide = pagerWidth() + GAP + AMPLITUDE_W + 2 + Riff.MAX_AMPLITUDE * (PIP_W + PIP_GAP);
        return (width - wide) / 2;
    }

    public Rect riffPlay() {
        return action("play", actionsTop(Tab.RIFF), 0);
    }

    public Rect riffSave() {
        return action("save", actionsTop(Tab.RIFF), 1);
    }

    public List<Rect> riffControls() {
        List<Rect> out = new ArrayList<>(List.of(tab(Tab.SONG), tab(Tab.RIFF), flourish(),
                new Rect("strings", riffLeft(), GRID_TOP, Riff.MAX_NOTES * slotWidth(), slotLabel(0, 1).bottom() - GRID_TOP),
                lengthPrev(), lengthLabel(), lengthNext(), amplitudeLabel(), riffPlay(), riffSave(),
                importButton(Tab.RIFF), exportButton(Tab.RIFF), readout(Tab.RIFF), hint(Tab.RIFF)));
        for (int amplitude = Riff.MIN_AMPLITUDE; amplitude <= Riff.MAX_AMPLITUDE; amplitude++) {
            out.add(pip(amplitude));
        }
        return out;
    }
}
