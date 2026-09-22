package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.sword.stance.SwordStance;

/**
 * Where the six stances sit, and nothing about how they are drawn.
 *
 * <p>Pure, and pinned by {@code SwordStanceLayoutTest} at every gui size the game can hand it.
 * That is not ceremony: this is the third frameless surface in the mod and the first two both
 * shipped a geometry bug that no amount of reading caught - a caption with no truncation running
 * out through its own wall, and a wall whose contrast was three million times below the dither.
 * A count constant that drives layout arithmetic gets a test.
 *
 * <p><b>Six cells in a row, and the row is the whole design.</b> Manipulate Space is three
 * scrolling columns, the Fracture is five gates read left to right, the Grimoire is a block of
 * text - and a stance is one choice out of six, so it is one row of six. Each cell carries a
 * <em>diagram</em> above a <em>name</em>: the diagram is the real formation, run through
 * {@code Formation.place} and flattened, so the picture in the picker cannot drift from the
 * arithmetic it is a picture of.
 *
 * <p><b>Nothing here is a constant the screen has to be big enough for.</b> Minecraft guarantees
 * a gui of at least {@link #MIN_GUI_WIDTH}x{@link #MIN_GUI_HEIGHT} and nothing more - it picks
 * the largest scale that still leaves that much - and six seventy-two-pixel cells want 452 of
 * the 320 a wielder at maximum scale actually has. So the cell width, the diagram, the row's
 * drop and the hint's place are all functions of the gui, each giving up its own slack before
 * the screen gives up its edge.
 */
public final class SwordStanceLayout {

    /** Six, and this layout cannot lay out a seventh. {@code SwordStance.count()} agrees. */
    public static final int CELLS = SwordStance.count();

    /**
     * The narrowest gui the game will ever hand this overlay.
     *
     * <p>{@code Window.calculateScale} picks the largest scale whose quotient is still at least
     * 320x240 and clamps a forced {@code guiScale} to it, so this is a floor rather than a
     * convention - and it is the size a wielder at maximum scale is actually playing at, not an
     * exotic one.
     */
    public static final int MIN_GUI_WIDTH = 320;

    /** The shortest gui the game will ever hand this overlay. See {@link #MIN_GUI_WIDTH}. */
    public static final int MIN_GUI_HEIGHT = 240;

    /** As wide as a cell is ever drawn. */
    public static final int CELL_MAX_WIDTH = 72;

    /** And as narrow: past this the row stops shrinking and starts overhanging. */
    public static final int CELL_MIN_WIDTH = 34;

    /** Gap between two cells. */
    public static final int CELL_GAP = 4;

    /** Clear air either side of the row. */
    public static final int SIDE_MARGIN = 4;

    /** The square the formation diagram is drawn in, at the top of a cell, at its largest. */
    public static final int DIAGRAM_MAX = 40;

    /**
     * Half the length of the bar one sword is drawn as, and the reason it lives here rather than
     * in the painter: a diagram is scaled to {@link #diagramHalf}, so a blade at the very top of
     * one reaches this much further than the square it is scaled into. Reserving it here is what
     * makes the cell rectangle the real extent of what is drawn, which is what the test measures.
     */
    public static final int BLADE_HALF = 4;

    /** Between the diagram and the name under it. */
    public static final int NAME_GAP = 4;

    public static final int LINE_HEIGHT = 9;

    /** The name, then the second line - locked, or worn - under it. */
    public static final int NAME_LINES = 2;

    /**
     * How far below the screen's middle the row would like to sit.
     *
     * <p>Below rather than on, because the crosshair is at the middle and a picker drawn over it
     * is a picker you cannot aim through. It is a preference: {@link #rowTop} gives it up to fit
     * the block on a short gui, but never past {@link #CROSSHAIR_CLEARANCE}.
     */
    public static final int ROW_DROP = 26;

    /** Nothing this overlay draws comes within this many pixels of the crosshair. */
    public static final int CROSSHAIR_CLEARANCE = 10;

    /** Between the row and the description of whatever is focused. */
    public static final int DESC_GAP = 10;

    /** The least air between the description and the hint under it. */
    public static final int HINT_GAP = 6;

    /**
     * The hint sits this far up from the bottom edge, where there is room for that.
     *
     * <p>Fifty-two rather than a comfortable thirty because <b>vanilla's own HUD is still drawn
     * and this overlay is drawn under it</b>. The picker stands the mod's sigil down (see
     * {@code HudLayers.renderSigil}) for the same reason the Manipulate Space selector does, but
     * the hotbar, the hearts and the experience bar are vanilla's and stay: at a 360-tall gui
     * they occupy everything from y 321 down, and a hint at 330 was drawn through the middle of
     * them. This clears the topmost of the three with room for its own line.
     */
    public static final int HINT_FROM_BOTTOM = 52;

    /** Clear air under the lowest thing drawn. */
    public static final int BOTTOM_MARGIN = 6;

    /**
     * The scrim, and it is the whole of what "frameless" means here.
     *
     * <p>Not a taste call. Measured against real noon sand (220,209,165) the mod's primary ink
     * lands at <b>1.45:1</b> and its muted ink at 2.06:1 - so an unbacked highlighted row reads
     * <em>fainter</em> than the rows it is picked out from and the highlight inverts. The loadout
     * switcher settled this at 0x8C, which takes that sand to (71,86,89) and the three inks to
     * 7.23 / 5.44 / 4.71:1, in the right order. Same number here, for the same reason.
     */
    public static final int SCRIM = 0x8C0A0D10;

    private SwordStanceLayout() {}

    /** How wide one cell is drawn at this gui width. */
    public static int cellWidth(int guiWidth) {
        int room = guiWidth - 2 * SIDE_MARGIN - (CELLS - 1) * CELL_GAP;
        return Math.max(CELL_MIN_WIDTH, Math.min(CELL_MAX_WIDTH, room / CELLS));
    }

    /** The diagram square, which is the cell's width less its own margin, never larger. */
    public static int diagram(int guiWidth) {
        return Math.min(DIAGRAM_MAX, cellWidth(guiWidth) - 2);
    }

    /** Cell height: the diagram, the gap, and two lines of caption. */
    public static int cellHeight(int guiWidth) {
        return diagram(guiWidth) + NAME_GAP + NAME_LINES * LINE_HEIGHT;
    }

    /** The row, the description under it and the hint under that, as one measurement. */
    public static int blockHeight(int guiWidth) {
        return cellHeight(guiWidth) + DESC_GAP + LINE_HEIGHT + HINT_GAP + LINE_HEIGHT;
    }

    /** The full width of the row of six. */
    public static int rowWidth(int guiWidth) {
        return CELLS * cellWidth(guiWidth) + (CELLS - 1) * CELL_GAP;
    }

    /** The left edge of the row, centred. */
    public static int rowLeft(int guiWidth) {
        return Math.max(SIDE_MARGIN, (guiWidth - rowWidth(guiWidth)) / 2);
    }

    /**
     * The top of the row: {@link #ROW_DROP} below the middle, raised only as far as it must be to
     * keep the hint on the screen, and never above the crosshair's clearance.
     */
    public static int rowTop(int guiWidth, int guiHeight) {
        int wanted = guiHeight / 2 + ROW_DROP;
        int lowest = guiHeight - BOTTOM_MARGIN - blockHeight(guiWidth);
        int highest = guiHeight / 2 + CROSSHAIR_CLEARANCE;
        return Math.max(highest, Math.min(wanted, lowest));
    }

    /** The left edge of one cell. */
    public static int cellLeft(int guiWidth, int index) {
        return rowLeft(guiWidth) + clamp(index) * (cellWidth(guiWidth) + CELL_GAP);
    }

    /** The centre of one cell, which is what every string in it is centred on. */
    public static int cellCentre(int guiWidth, int index) {
        return cellLeft(guiWidth, index) + cellWidth(guiWidth) / 2;
    }

    /** The centre of one cell's diagram, where the formation is drawn about. */
    public static int diagramCentreY(int guiWidth, int guiHeight) {
        return rowTop(guiWidth, guiHeight) + diagram(guiWidth) / 2;
    }

    /**
     * Half the width and height a formation is scaled into, which is the diagram square less
     * the half-blade that sticks out past whatever sword lands on its edge.
     */
    public static int diagramHalf(int guiWidth) {
        return Math.max(1, diagram(guiWidth) / 2 - BLADE_HALF);
    }

    /** The top of a cell's name line. */
    public static int nameTop(int guiWidth, int guiHeight) {
        return rowTop(guiWidth, guiHeight) + diagram(guiWidth) + NAME_GAP;
    }

    /** The top of a cell's second line - locked, or worn. */
    public static int subTop(int guiWidth, int guiHeight) {
        return nameTop(guiWidth, guiHeight) + LINE_HEIGHT;
    }

    /** The top of the focused stance's description, under the whole row. */
    public static int descTop(int guiWidth, int guiHeight) {
        return rowTop(guiWidth, guiHeight) + cellHeight(guiWidth) + DESC_GAP;
    }

    /** The top of the release hint: near the bottom, or straight under the description. */
    public static int hintTop(int guiWidth, int guiHeight) {
        return Math.max(descTop(guiWidth, guiHeight) + LINE_HEIGHT + HINT_GAP,
                guiHeight - HINT_FROM_BOTTOM);
    }

    /**
     * How many pixels of text a cell may carry, and it is a function of the gui rather than a
     * constant.
     *
     * <p>The loadout switcher shipped a thirty-four-character caption with no truncation at all,
     * which ran out through its own right wall onto the sky. Both other frameless layouts clamp
     * theirs against {@code guiWidth} now and so does this.
     */
    public static int textLimit(int guiWidth) {
        return Math.max(16, cellWidth(guiWidth) - 2);
    }

    /** The description spans the row rather than a cell, so it gets the row's width. */
    public static int descLimit(int guiWidth) {
        return Math.max(32, Math.min(rowWidth(guiWidth), guiWidth - 2 * SIDE_MARGIN));
    }

    private static int clamp(int index) {
        return Math.max(0, Math.min(CELLS - 1, index));
    }
}
