package com.efkrdnz.magical.client.screen.causality;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.causality.CausalNode;
import com.efkrdnz.magical.magic.causality.Weave;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the Causal Board: a wall of pins floating over the world, with the string between
 * them.
 *
 * <p>No panel, no plate and no frame, the way the Grimoire and the Manipulate Space selector are
 * drawn: the world is dimmed and the pins, the words and the string are simply on it. Top to
 * bottom, block-local: a line of <b>kinds</b> - Causes, Conditions, Effects - with a mark under the
 * one whose vocabulary is showing; the <b>palette</b> of that vocabulary as a strip of glyphs; the
 * <b>board</b>, which is everything that is left and the only part that matters; a <b>caption</b>
 * naming whatever the cursor is over; an <b>inspector</b> line carrying the number, the scope and
 * the tools of the selected pin, with Save and Clear at its right; and the <b>reading</b>, which
 * says in words what the selected chain does and what is wrong with the board.
 *
 * <p>The board keeps a <b>uniform scale</b>. A pin lives at a position in {@link Weave} board units
 * and the same factor is applied to both axes, so a board built on a wide screen and reopened on a
 * narrow one is the same drawing rather than a squashed one - which matters here more than in any
 * other screen in the mod, because the wielder is reading a shape and not a list.
 *
 * <p>The layout has no font. Every row of words is placed from measured widths the screen passes
 * in, and every hit-test that could otherwise depend on a label is done against a fixed grab box
 * around the glyph instead, so {@code CausalBoardLayoutTest} can sweep every screen size the game
 * hands us without knowing what a glyph of text is worth.
 */
public final class CausalBoardLayout {

    public static final int SCREEN_MARGIN = 6;
    public static final int BLOCK_W_MAX = 600;
    public static final int LINE_H = 9;
    public static final int ICON = CausalGlyphs.SIZE;

    public static final int KIND_COUNT = 3;
    public static final int KIND_Y = 0;
    public static final int KIND_GAP = 18;
    /** The mark that slides between the three headings to say which vocabulary is showing. */
    public static final int KIND_RULE_Y = 12;
    public static final int RULE_H = 1;

    public static final int PALETTE_Y = 20;
    public static final int PALETTE_STRIDE = 20;

    public static final int BOARD_Y = 42;
    public static final int BOARD_H_MIN = 96;
    public static final int BOARD_H_MAX = 228;

    /** How far from a pin glyph a press still counts as on it. Font-independent, so a test can pin it. */
    public static final int PIN_GRAB = 5;
    /** The mark at each end of a pin that string is tied to, and how close a press must be. */
    public static final int PORT_W = 3;
    public static final int PORT_GRAB = 5;
    /**
     * How far a pin's name is drawn from its glyph, and the widest the board ever gives it.
     *
     * <p>The ceiling is wide enough for the longest name with its number on the end, because a
     * number on a pin is not decoration - {@code Ledger Reaches 20} without the 20 is not the rule.
     */
    public static final int LABEL_GAP = 3;
    public static final int LABEL_W_MAX = 120;
    /** How close to the middle of a piece of string a press must be to cut it. */
    public static final int WIRE_GRAB = 5;

    public static final int CAPTION_GAP = 5;
    /** The name of what the cursor is over with its weight, then what it does. */
    public static final int CAPTION_LINES = 2;

    public static final int INSPECTOR_GAP = 7;
    public static final int PARAM_W = 46;
    public static final int STEP_W = 7;
    public static final int SCOPE_W = 66;
    public static final int MOD_STRIDE = 13;
    public static final int MOD_COUNT = 6;
    public static final int GROUP_GAP = 12;

    public static final int READING_GAP = 5;
    /** What the selected chain does, and the first thing wrong with the board. */
    public static final int READING_LINES = 2;

    /** Everything under the board, so the board can be given whatever height is left. */
    public static final int BELOW_BOARD = CAPTION_GAP + CAPTION_LINES * LINE_H
            + INSPECTOR_GAP + LINE_H + READING_GAP + READING_LINES * LINE_H;

    private CausalBoardLayout() {}

    // ---- the block --------------------------------------------------------------------------------

    public static int blockWidth(int guiWidth) {
        return Math.max(1, Math.min(BLOCK_W_MAX, guiWidth - 2 * SCREEN_MARGIN));
    }

    /** As tall as the screen will allow, between a floor a board is still usable at and a ceiling. */
    public static int boardHeight(int guiHeight) {
        int room = guiHeight - 2 * SCREEN_MARGIN - BOARD_Y - BELOW_BOARD;
        return Math.max(BOARD_H_MIN, Math.min(BOARD_H_MAX, room));
    }

    public static int blockHeight(int boardHeight) {
        return BOARD_Y + boardHeight + BELOW_BOARD;
    }

    /** The block the whole thing occupies, centred. Nothing paints it; it only places the rest. */
    public static Rect block(int guiWidth, int guiHeight) {
        int w = blockWidth(guiWidth);
        int h = blockHeight(boardHeight(guiHeight));
        return new Rect("block", Math.max(0, (guiWidth - w) / 2), Math.max(0, (guiHeight - h) / 2), w, h);
    }

    // ---- rows of words ----------------------------------------------------------------------------

    /**
     * Words across the block on one line, centred as a group.
     *
     * <p>A row that fits keeps every word at the width it measured; one that does not gives up its
     * gap first and only then clamps each word to an equal share, so a row always fits its block and
     * the screen clips a label to the width it gets back.
     */
    public static List<Rect> wordsRow(String name, int blockWidth, int y, int[] widths, int gap) {
        int n = widths.length;
        List<Rect> rects = new ArrayList<>(n);
        if (n == 0) {
            return rects;
        }
        int sum = 0;
        for (int w : widths) {
            sum += w;
        }
        int useGap = n == 1 ? 0 : Math.max(1, Math.min(gap, (blockWidth - sum) / (n - 1)));
        boolean fits = sum + (n - 1) * useGap <= blockWidth;
        int share = Math.max(1, (blockWidth - (n - 1) * useGap) / n);
        int total = -useGap;
        for (int w : widths) {
            total += (fits ? w : Math.min(w, share)) + useGap;
        }
        int x = (blockWidth - total) / 2;
        for (int i = 0; i < n; i++) {
            int w = fits ? widths[i] : Math.min(widths[i], share);
            rects.add(new Rect(name + " " + i, x, y, w, LINE_H));
            x += w + useGap;
        }
        return rects;
    }

    public static List<Rect> kinds(int blockWidth, int[] widths) {
        return wordsRow("kind", blockWidth, KIND_Y, widths, KIND_GAP);
    }

    public static Rect kindRule(Rect kind) {
        return new Rect(kind.name() + " rule", kind.x(), KIND_RULE_Y, kind.w(), RULE_H);
    }

    /** Which of a row of rectangles a point is in, or -1. Shared by every row on the screen. */
    public static int indexAt(List<Rect> rects, double x, double y) {
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            if (x >= r.x() && x < r.right() && y >= r.y() && y < r.bottom()) {
                return i;
            }
        }
        return -1;
    }

    // ---- the palette ------------------------------------------------------------------------------

    /** One glyph per word of the showing vocabulary, in a centred strip. */
    public static List<Rect> palette(int blockWidth, int count) {
        List<Rect> rects = new ArrayList<>(count);
        if (count <= 0) {
            return rects;
        }
        int stride = Math.max(ICON + 1, Math.min(PALETTE_STRIDE, blockWidth / count));
        int x = Math.max(0, (blockWidth - count * stride) / 2) + (stride - ICON) / 2;
        for (int i = 0; i < count; i++) {
            rects.add(new Rect("palette " + i, x + i * stride, PALETTE_Y, ICON, ICON));
        }
        return rects;
    }

    public static int paletteIndexAt(int blockWidth, int count, double x, double y) {
        return indexAt(palette(blockWidth, count), x, y);
    }

    // ---- the board --------------------------------------------------------------------------------

    public static Rect board(int blockWidth, int boardHeight) {
        return new Rect("board", 0, BOARD_Y, blockWidth, boardHeight);
    }

    /**
     * One factor for both axes, so a pin wall is never stretched.
     *
     * <p>Whichever of the two the board runs out of first decides it, and the spare space on the
     * other axis becomes margin through {@link #boardOriginX} and {@link #boardOriginY}. A pin at
     * the far corner of the {@link Weave} board is therefore always exactly at the far corner of the
     * drawn one, on any screen.
     */
    public static double boardScale(int blockWidth, int boardHeight) {
        double room = Math.min((blockWidth - ICON) / (double) Weave.BOARD_W,
                (boardHeight - ICON) / (double) Weave.BOARD_H);
        return Math.max(0.05D, room);
    }

    public static int boardOriginX(int blockWidth, int boardHeight) {
        double used = Weave.BOARD_W * boardScale(blockWidth, boardHeight) + ICON;
        return (int) Math.round((blockWidth - used) / 2.0D);
    }

    public static int boardOriginY(int blockWidth, int boardHeight) {
        double used = Weave.BOARD_H * boardScale(blockWidth, boardHeight) + ICON;
        return BOARD_Y + (int) Math.round((boardHeight - used) / 2.0D);
    }

    /** Where a pin glyph is drawn, block-local. The label hangs off its right and is not hit-tested. */
    public static Rect pin(int blockWidth, int boardHeight, CausalNode node) {
        return pinAt(blockWidth, boardHeight, node.x(), node.y(), "pin " + node.id());
    }

    public static Rect pinAt(int blockWidth, int boardHeight, int boardX, int boardY, String name) {
        double scale = boardScale(blockWidth, boardHeight);
        int x = boardOriginX(blockWidth, boardHeight) + (int) Math.round(boardX * scale);
        int y = boardOriginY(blockWidth, boardHeight) + (int) Math.round(boardY * scale);
        return new Rect(name, x, y, ICON, ICON);
    }

    /** Back the other way: where a drop at this point on the screen puts a pin on the board. */
    public static int boardXFrom(int blockWidth, int boardHeight, double x) {
        double scale = boardScale(blockWidth, boardHeight);
        return Weave.clampX((int) Math.round((x - boardOriginX(blockWidth, boardHeight) - ICON / 2.0D) / scale));
    }

    public static int boardYFrom(int blockWidth, int boardHeight, double y) {
        double scale = boardScale(blockWidth, boardHeight);
        return Weave.clampY((int) Math.round((y - boardOriginY(blockWidth, boardHeight) - ICON / 2.0D) / scale));
    }

    /** True when a point is close enough to a pin glyph to have meant it. */
    public static boolean onPin(Rect pin, double x, double y) {
        return x >= pin.x() - PIN_GRAB && x < pin.right() + PIN_GRAB
                && y >= pin.y() - PIN_GRAB && y < pin.bottom() + PIN_GRAB;
    }

    /** Where string is tied on: in at the left of a pin, out at the right. */
    public static Rect inPort(Rect pin) {
        return new Rect(pin.name() + " in", pin.x() - PORT_W - 1, pin.y() + (ICON - PORT_W) / 2, PORT_W, PORT_W);
    }

    /**
     * Where string leaves a pin: after its name, not at its glyph.
     *
     * <p>A name is drawn to the right of its glyph, which is exactly the ground the string to the
     * next pin has to cross, so tying it at the glyph ran it underneath the label and a chain read
     * as unconnected precisely where it was most connected. The name is part of the pin, so the
     * string is tied to the end of it and every run is in clear air.
     */
    public static Rect outPort(Rect pin, int measuredName) {
        int after = nameWidth(measuredName);
        int x = pin.right() + (after <= 0 ? 1 : LABEL_GAP + after + 1);
        return new Rect(pin.name() + " out", x, pin.y() + (ICON - PORT_W) / 2, PORT_W, PORT_W);
    }

    /** The width the board actually gives a name, whatever the font measured for it. */
    public static int nameWidth(int measured) {
        return Math.max(0, Math.min(LABEL_W_MAX, measured));
    }

    public static boolean onPort(Rect port, double x, double y) {
        return x >= port.x() - PORT_GRAB && x < port.right() + PORT_GRAB
                && y >= port.y() - PORT_GRAB && y < port.bottom() + PORT_GRAB;
    }

    /**
     * The point on a piece of string a press has to be near to cut it.
     *
     * <p>The middle of the curve rather than anywhere along it: one point is enough to aim at, it is
     * where the string is furthest from either pin, and it is a number the test can check exactly
     * instead of re-deriving a bezier.
     */
    public static double[] wireMiddle(Rect from, int fromName, Rect to) {
        Rect out = outPort(from, fromName);
        Rect in = inPort(to);
        double x0 = out.x() + out.w() / 2.0D;
        double y0 = out.y() + out.h() / 2.0D;
        double x1 = in.x() + in.w() / 2.0D;
        double y1 = in.y() + in.h() / 2.0D;
        double[] control = wireControl(x0, y0, x1, y1);
        // The midpoint of a quadratic bezier: a quarter each end, a half the control point.
        return new double[] {
                0.25D * x0 + 0.5D * control[0] + 0.25D * x1,
                0.25D * y0 + 0.5D * control[1] + 0.25D * y1};
    }

    /**
     * The control point that gives a piece of string its sag.
     *
     * <p>It bows out sideways from the straight line and down, so two pins level with each other are
     * still joined by a visible curve rather than by a line that could be mistaken for the edge of
     * something, and a run of chains never draws two wires exactly on top of each other.
     */
    public static double[] wireControl(double x0, double y0, double x1, double y1) {
        double mx = (x0 + x1) / 2.0D;
        double my = (y0 + y1) / 2.0D;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        double sag = Math.min(18.0D, 4.0D + length * 0.12D);
        if (length < 1.0E-4D) {
            return new double[] {mx, my + sag};
        }
        return new double[] {mx - dy / length * sag * 0.35D, my + dx / length * sag * 0.35D + sag * 0.5D};
    }

    public static boolean onWire(Rect from, int fromName, Rect to, double x, double y) {
        double[] middle = wireMiddle(from, fromName, to);
        return Math.abs(x - middle[0]) <= WIRE_GRAB && Math.abs(y - middle[1]) <= WIRE_GRAB;
    }

    // ---- under the board --------------------------------------------------------------------------

    public static Rect caption(int blockWidth, int boardHeight, int line) {
        int y = BOARD_Y + boardHeight + CAPTION_GAP + line * LINE_H;
        return new Rect("caption " + line, 0, y, blockWidth, LINE_H);
    }

    public static int inspectorY(int boardHeight) {
        return BOARD_Y + boardHeight + CAPTION_GAP + CAPTION_LINES * LINE_H + INSPECTOR_GAP;
    }

    /**
     * The inspector line: what the selected pin is set to, and the two words that keep the board.
     *
     * <p>Laid out from both ends. The number, the scope and the six tools run from the left in that
     * order, because that is the order a wielder reads a pin in; Save and Clear are pushed against
     * the right, because they are about the whole board rather than about the pin, and because a
     * destructive word should never sit next to a word that only nudges a number.
     */
    public record Inspector(Rect minus, Rect value, Rect plus, Rect scope, List<Rect> modifiers,
            Rect save, Rect clear) {

        public List<Rect> all() {
            List<Rect> rects = new ArrayList<>();
            rects.add(minus);
            rects.add(value);
            rects.add(plus);
            rects.add(scope);
            rects.addAll(modifiers);
            rects.add(save);
            rects.add(clear);
            return rects;
        }
    }

    public static Inspector inspector(int blockWidth, int boardHeight, int saveWidth, int clearWidth) {
        int y = inspectorY(boardHeight);
        Rect minus = new Rect("minus", 0, y, STEP_W, LINE_H);
        Rect value = new Rect("value", STEP_W + 2, y, PARAM_W - 2 * STEP_W - 4, LINE_H);
        Rect plus = new Rect("plus", PARAM_W - STEP_W, y, STEP_W, LINE_H);
        Rect scope = new Rect("scope", PARAM_W + GROUP_GAP, y, SCOPE_W, LINE_H);
        int modsX = PARAM_W + GROUP_GAP + SCOPE_W + GROUP_GAP;
        List<Rect> modifiers = new ArrayList<>(MOD_COUNT);
        for (int i = 0; i < MOD_COUNT; i++) {
            modifiers.add(new Rect("mod " + i, modsX + i * MOD_STRIDE, y, ICON, LINE_H));
        }
        // Both words are pushed against the right edge, and when the block is too narrow to hold
        // them at the width they measured they are clamped to an equal share of what is left
        // rather than allowed to walk back over the tools. Clipping a word is a fallback the
        // screen can draw; two words on top of each other is not.
        int wordsX = modsX + MOD_COUNT * MOD_STRIDE + GROUP_GAP;
        int room = Math.max(2, blockWidth - wordsX);
        int gap = Math.min(GROUP_GAP, Math.max(1, room / 6));
        int save = saveWidth;
        int clear = clearWidth;
        if (save + gap + clear > room) {
            int share = Math.max(1, (room - gap) / 2);
            save = Math.min(save, share);
            clear = Math.min(clear, share);
        }
        int clearX = blockWidth - clear;
        int saveX = clearX - gap - save;
        return new Inspector(minus, value, plus, scope, modifiers,
                new Rect("save", saveX, y, save, LINE_H),
                new Rect("clear", clearX, y, clear, LINE_H));
    }

    public static Rect reading(int blockWidth, int boardHeight, int line) {
        int y = inspectorY(boardHeight) + LINE_H + READING_GAP + line * LINE_H;
        return new Rect("reading " + line, 0, y, blockWidth, LINE_H);
    }

    /**
     * True when the inspector line has room for Save and Clear without them running into the tools.
     *
     * <p>When it has not, the screen drops the two words to their initials rather than letting them
     * overlap - the same fallback the Grimoire tabs and the selector categories make, and the reason
     * this is a question the layout answers rather than a thing the screen guesses at.
     */
    public static boolean inspectorFitsWords(int blockWidth, int saveWidth, int clearWidth) {
        int used = PARAM_W + GROUP_GAP + SCOPE_W + GROUP_GAP + MOD_COUNT * MOD_STRIDE
                + GROUP_GAP + saveWidth + GROUP_GAP + clearWidth;
        return used <= blockWidth;
    }
}
