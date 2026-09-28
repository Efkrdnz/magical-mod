package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.LoadoutSwitcherLayout;
import com.efkrdnz.magical.client.screen.CodexLayout;
import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Every position the magic HUD draws at, computed from the GUI size, the anchor corner and the
 * scale option - and nothing else.
 *
 * <p>It lives outside the renderer for the reason {@code CodexLayout} does: a HUD that places its
 * rows by bare numbers inside draw calls collides with itself unnoticed. {@code HudLayoutTest}
 * sweeps every rectangle here for overlap, at every anchor and scale, and checks it all fits a
 * 480x270 GUI - the size vanilla's "auto" scale gives a 1080p window.
 *
 * <p>The corner block is three bands and nothing else, with no panel, plate or frame behind any of
 * it: the <b>pools</b> (a mana bar over a thinner barrier bar, the counts beside them), the
 * <b>slots</b> (the four skill glyphs in key order, each with one text cell under it for its key
 * or its seconds) and the <b>readouts</b> (up to three lines of tokens, each a small stamp and its
 * reading, only while something needs saying). Every rectangle lands on whole GUI units at every
 * scale, which is what keeps the bars crisp. Text boxes are nine tall and never scale, and nor do
 * the stamps, which are characters of their line rather than parts of the HUD.
 *
 * <p>Nothing in the block enters vanilla's centre column - the boss bars at the top, the hotbar and
 * its heart and food rows at the bottom - which is {@link #CENTRE_COLUMN_HALF} either side of the
 * middle. That is what sizes the counts and the readout lines. The counts and the slots sit level
 * with the first two boss bars, so they never go in: where the counts - and the key cells, which
 * hang past a glyph smaller than they are - would not fit beside the bars at the chosen scale the
 * block is drawn smaller, down to the option's floor, and from a 394-wide GUI up that is always
 * enough. The readout lines sit level with a third stacked bar, so they give way instead: a line
 * never runs narrower than {@link #READOUT_MIN_W}, so below a 480-wide GUI it reaches in by 240
 * less half the width - 27 on 1280x720 at vanilla's auto scale, which is 427 wide, and 48 at 384 -
 * rather than leave readings off at the commonest window there is.
 *
 * <p>At a right anchor the block hangs from the right edge, but the glyph row is never mirrored -
 * it is a picture of the keyboard, so Z stays leftmost - and the bars still fill left to right.
 * At a bottom anchor the bands stack the other way, so the pools stay nearest the edge and the
 * readouts grow away from it.
 */
public final class HudLayout {

    /** A rectangle with a name, so a failed overlap assertion says which two things collided. */
    public record Rect(String name, int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public int centreX() {
            return x + w / 2;
        }

        public int centreY() {
            return y + h / 2;
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public boolean contains(Rect inner) {
            return inner.x >= x && inner.y >= y && inner.right() <= right() && inner.bottom() <= bottom();
        }
    }

    // ---- the corner block ------------------------------------------------------------------------

    public static final int MARGIN = 6;
    public static final int TEXT_H = 9;
    public static final int SLOTS = MagicContent.LOADOUT_SIZE;

    /** A skill glyph's side at the design scale. */
    public static final int GLYPH = 16;
    /** Glyph to glyph. It never shrinks below this, so the key cells under them never touch. */
    public static final int PITCH = 20;

    public static final int MANA_BAR_H = 4;
    public static final int MANA_BAR_H_MIN = 3;
    public static final int BAR_GAP = 1;
    public static final int BARRIER_BAR_H = 3;
    public static final int BARRIER_BAR_H_MIN = 2;
    /**
     * Between the pools band and the slots band; {@link #READOUT_GAP} is the same air between the
     * slots and the readouts. It shrinks with a small scale and does not grow with a large one,
     * and the two together can be no wider: on a 240-tall GUI - 1280x720, 1440p and 4K on vanilla's
     * auto scale - the third readout line at the design scale ends on the very row the B loadout
     * rail's first mark starts on, and a unit more hides the whole block every time the switcher
     * is held.
     */
    public static final int ROW_GAP = 3;
    public static final int ROW_GAP_MIN = 2;

    /**
     * The counts beside the bars: the mana as current over maximum, then the barrier. The box runs
     * from the bars to vanilla's centre column, clamped between these two widths.
     */
    public static final int POOLS_GAP = 3;
    public static final int POOLS_TEXT_MIN_W = 24;
    public static final int POOLS_TEXT_MAX_W = 88;
    /** Between the mana count and the barrier count on one line. */
    public static final int POOLS_BARRIER_GAP = 4;
    /** Half the width of vanilla's centre column: the boss bar and the hotbar are 182 wide. */
    public static final int CENTRE_COLUMN_HALF = 91;

    /** The key letter or the seconds, under each glyph: three characters wide. */
    public static final int CELL_W = 18;
    public static final int CELL_GAP = 1;

    /**
     * The readouts: tokens flowed onto at most three lines, only while they apply. A line runs from
     * the margin to vanilla's centre column, never narrower than the first width - the vault and
     * two sins cut short, beside a resource on each line above - nor wider than the second, past
     * which a line reads as a sentence.
     */
    public static final int READOUT_GAP = 3;
    public static final int READOUT_MIN_W = 143;
    public static final int READOUT_MAX_W = 220;
    public static final int READOUT_STRIDE = 10;
    public static final int READOUT_LINES_MAX = 3;
    public static final int READOUT_TOKEN_GAP = 5;
    public static final int READOUT_TOKENS_MAX = 8;
    /**
     * A token's stamp: nine units square, one unit above its line, so its seven inked rows land on
     * the seven rows a capital letter covers, with its text one unit after it.
     */
    public static final int STAMP = 9;
    public static final int STAMP_GAP = 1;
    public static final int STAMP_LEAD = STAMP + STAMP_GAP;
    /** Room for the "+n" that says how many readings were left off: "+99". */
    public static final int MORE_W = 18;

    /** Potion effect icons live in the top-right corner; that anchor gives them the room, always. */
    public static final int POTION_RESERVE = 50;
    /** The hotbar with the health and food rows above it; bottom anchors sit above it. */
    public static final int HOTBAR_RESERVE = 50;

    // ---- the centre group -----------------------------------------------------------------------

    public static final int STATUS_CHIP = 14;
    public static final int STATUS_CHIP_DY = 26;
    public static final int STATUS_CHIP_STRIDE = 18;
    public static final int STATUS_CHIPS_MAX = 6;
    public static final int UNWAKING_CUE_DY = 46;
    public static final int UNWAKING_CUE_W = 240;

    /**
     * The rule flash: a formula plate this far above the crosshair, two text rows tall for the
     * formula at twice the text size, with a caption row under it. It is the same size at every
     * HUD scale and ignores the anchor.
     */
    public static final int RULE_FLASH_DY = 52;
    public static final int RULE_FLASH_PAD = 5;
    public static final int RULE_FLASH_MAX_W = 220;
    public static final int RULE_FLASH_CAPTION_GAP = 3;

    /** The scale option's range; the block may be drawn smaller than asked, never below the floor. */
    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 1.5F;
    /** How far the scale gives way at a time while the counts do not fit beside the bars. */
    private static final float SCALE_STEP = 0.05F;

    private final int guiWidth;
    private final int guiHeight;
    private final HudAnchor anchor;
    private final float scale;

    private final int glyph;
    private final int pitch;
    private final int blockW;
    private final int manaH;
    private final int barGap;
    private final int barrierH;
    private final int rowH;
    private final int slotsH;
    private final int x0;
    private final int poolsY;
    private final int slotsY;
    private final boolean clearsLoadoutRail;

    private HudLayout(int guiWidth, int guiHeight, HudAnchor anchor, float scale) {
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.anchor = anchor;
        this.scale = scale;
        this.glyph = Math.round(scale * GLYPH);
        this.pitch = Math.max(PITCH, Math.round(scale * PITCH));
        this.blockW = blockWidth(scale);
        this.manaH = Math.max(MANA_BAR_H_MIN, Math.round(scale * MANA_BAR_H));
        this.barGap = Math.max(BAR_GAP, Math.round(scale * BAR_GAP));
        this.barrierH = Math.max(BARRIER_BAR_H_MIN, Math.round(scale * BARRIER_BAR_H));
        this.rowH = Math.max(TEXT_H, manaH + barGap + barrierH);
        int rowGap = Math.max(ROW_GAP_MIN, Math.min(ROW_GAP, Math.round(scale * ROW_GAP)));
        this.slotsH = glyph + CELL_GAP + TEXT_H;
        this.x0 = anchor.right() ? guiWidth - MARGIN - blockW : MARGIN;
        if (anchor.bottom()) {
            this.poolsY = guiHeight - HOTBAR_RESERVE - rowH;
            this.slotsY = poolsY - rowGap - slotsH;
        } else {
            this.poolsY = MARGIN + (anchor == HudAnchor.TOP_RIGHT ? POTION_RESERVE : 0);
            this.slotsY = poolsY + rowH + rowGap;
        }
        this.clearsLoadoutRail = measureLoadoutRail();
    }

    /**
     * The layout at the asked scale, or the largest scale under it at which the counts still fit
     * between the block and vanilla's centre column: a HUD at 1.5 on a 427-wide GUI would otherwise
     * print its mana over the first boss bar.
     */
    public static HudLayout of(int guiWidth, int guiHeight, HudAnchor anchor, float scale) {
        float fitted = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        int room = cornerRoom(guiWidth, anchor) - POOLS_GAP - POOLS_TEXT_MIN_W;
        while (fitted > MIN_SCALE && reach(fitted, anchor) > room) {
            fitted = Math.max(MIN_SCALE, fitted - SCALE_STEP);
        }
        return new HudLayout(guiWidth, guiHeight, anchor, fitted);
    }

    /** The glyph row's width at a scale: four glyphs at their pitch, which never closes below the key cells'. */
    private static int blockWidth(float scale) {
        return (SLOTS - 1) * Math.max(PITCH, Math.round(scale * PITCH)) + Math.round(scale * GLYPH);
    }

    /**
     * How far the block reaches toward the middle at a scale: the glyph row, and past it whatever
     * of the key cell on that side hangs beyond a glyph smaller than the cell. The barrier count's
     * second line starts after that cell, so a fit that measured the glyphs alone left it five
     * units short of its four digits at the floor.
     */
    private static int reach(float scale, HudAnchor anchor) {
        int glyph = Math.round(scale * GLYPH);
        int lead = Math.floorDiv(glyph - CELL_W, 2);
        int overhang = anchor.right() ? -lead : lead + CELL_W - glyph;
        return blockWidth(scale) + Math.max(0, overhang);
    }

    /** From the margin to vanilla's centre column, on the anchor's side. */
    private static int cornerRoom(int guiWidth, HudAnchor anchor) {
        return anchor.right()
                ? guiWidth - MARGIN - (guiWidth / 2 + CENTRE_COLUMN_HALF)
                : guiWidth / 2 - CENTRE_COLUMN_HALF - MARGIN;
    }

    public int guiWidth() {
        return guiWidth;
    }

    public int guiHeight() {
        return guiHeight;
    }

    public HudAnchor anchor() {
        return anchor;
    }

    public float scale() {
        return scale;
    }

    /** True when the block hangs from the right edge and its text reads toward it. */
    public boolean right() {
        return anchor.right();
    }

    /** A skill glyph's side at this scale, in GUI units. */
    public int glyphSize() {
        return glyph;
    }

    public int pitch() {
        return pitch;
    }

    // ---- pools ----------------------------------------------------------------------------------

    private int barsY() {
        return poolsY + (rowH - (manaH + barGap + barrierH)) / 2;
    }

    /** The mana bar: exactly as long as the glyph row under it. */
    public Rect manaBar() {
        return new Rect("mana bar", x0, barsY(), blockW, manaH);
    }

    public Rect barrierBar() {
        return new Rect("barrier bar", x0, barsY() + manaH + barGap, blockW, barrierH);
    }

    /**
     * The counts, beside the bars on the side away from the edge, running to vanilla's centre
     * column - so the room grows and shrinks with the scale, never with the values.
     */
    public Rect poolsText() {
        int y = poolsY + (rowH - TEXT_H) / 2;
        if (anchor.right()) {
            int right = x0 - POOLS_GAP;
            int w = clampPoolsWidth(right - (guiWidth / 2 + CENTRE_COLUMN_HALF));
            return new Rect("pools text", right - w, y, w, TEXT_H);
        }
        int x = x0 + blockW + POOLS_GAP;
        return new Rect("pools text", x, y, clampPoolsWidth(guiWidth / 2 - CENTRE_COLUMN_HALF - x), TEXT_H);
    }

    private static int clampPoolsWidth(int w) {
        return Math.max(POOLS_TEXT_MIN_W, Math.min(POOLS_TEXT_MAX_W, w));
    }

    /**
     * Where the barrier count goes when it will not fit beside the mana count: the line under it,
     * or over it at a bottom anchor, so it grows away from the edge as the rest of the block does.
     * It stands beside the slot band, clear of the glyphs.
     */
    public Rect poolsStackedLine() {
        Rect text = poolsText();
        int y = anchor.bottom() ? text.y() - TEXT_H - 1 : text.bottom() + 1;
        // Beside the slots rather than the bars, and a key cell is wider than a small glyph, so
        // at the small scales the last cell reaches past the bars' end.
        if (anchor.right()) {
            int right = Math.min(text.right(), Math.min(x0, cell(0).x()) - POOLS_GAP);
            return new Rect("pools stacked line", text.x(), y, right - text.x(), TEXT_H);
        }
        int x = Math.max(text.x(), Math.max(x0 + blockW, cell(SLOTS - 1).right()) + POOLS_GAP);
        return new Rect("pools stacked line", x, y, text.right() - x, TEXT_H);
    }

    /** How the counts share their room; chosen from the widest values alone, so it never flickers. */
    public enum PoolsForm {
        /** "82/100  49": the mana over its maximum, then the barrier. */
        FULL,
        /** "82  49": no room for the maximum. */
        NO_MAX,
        /** "82" over "49": no room for both on one line. */
        STACKED
    }

    /**
     * Where the counts go: the form, the x the mana's slash stands at, and the right edge the
     * barrier count is aligned against, with the line each is on.
     *
     * <p>Both counts are tabular. The mana's current value is right-aligned against
     * {@code slashX} and "/max" starts there, so the slash never moves as the value changes; the
     * barrier is right-aligned against {@code barrierRight} for the same reason.
     */
    public record PoolsPlan(PoolsForm form, int slashX, int manaY, int barrierRight, int barrierY) {
        public boolean showsMax() {
            return form == PoolsForm.FULL;
        }
    }

    /**
     * Lays the counts out from the widths of the widest value each can show - {@code manaField}
     * and {@code barrierField}, the widths of their maxima - and of "/max". Nothing here reads the
     * current values, so the form holds still while they move.
     */
    public PoolsPlan planPools(int manaField, int maxWidth, int barrierField) {
        Rect text = poolsText();
        PoolsForm form;
        if (manaField + maxWidth + POOLS_BARRIER_GAP + barrierField <= text.w()) {
            form = PoolsForm.FULL;
        } else if (manaField + POOLS_BARRIER_GAP + barrierField <= text.w()) {
            form = PoolsForm.NO_MAX;
        } else {
            form = PoolsForm.STACKED;
        }
        if (form == PoolsForm.STACKED) {
            // One right edge for both lines where it fits, so the two counts stand as a column.
            Rect stacked = poolsStackedLine();
            int right = anchor.right()
                    ? Math.min(text.right(), stacked.right())
                    : Math.min(text.right(), Math.max(text.x() + manaField, stacked.x() + barrierField));
            return new PoolsPlan(form, right, text.y(), right, stacked.y());
        }
        int max = form == PoolsForm.FULL ? maxWidth : 0;
        if (anchor.right()) {
            int barrierRight = text.right();
            return new PoolsPlan(form, barrierRight - barrierField - POOLS_BARRIER_GAP - max, text.y(), barrierRight, text.y());
        }
        int slashX = text.x() + manaField;
        return new PoolsPlan(form, slashX, text.y(), slashX + max + POOLS_BARRIER_GAP + barrierField, text.y());
    }

    // ---- slots ----------------------------------------------------------------------------------

    /** Slot {@code k}'s glyph; Z is always the leftmost, at every anchor. */
    public Rect glyph(int k) {
        return new Rect("glyph " + k, x0 + k * pitch, slotsY, glyph, glyph);
    }

    /** The key letter or the seconds under slot {@code k}, centred on its glyph. */
    public Rect cell(int k) {
        return new Rect("cell " + k, x0 + k * pitch + Math.floorDiv(glyph - CELL_W, 2), slotsY + glyph + CELL_GAP, CELL_W, TEXT_H);
    }

    // ---- readouts -------------------------------------------------------------------------------

    /** How wide a readout line runs: to vanilla's centre column, between the two bounds. */
    public int readoutWidth() {
        return Math.max(READOUT_MIN_W, Math.min(READOUT_MAX_W, cornerRoom(guiWidth, anchor)));
    }

    /** Readout line {@code i}; line 0 is the one nearest the slots. */
    public Rect readoutLine(int i) {
        int w = readoutWidth();
        int x = anchor.right() ? guiWidth - MARGIN - w : x0;
        int y = anchor.bottom()
                ? slotsY - READOUT_GAP - TEXT_H - i * READOUT_STRIDE
                : slotsY + slotsH + READOUT_GAP + i * READOUT_STRIDE;
        return new Rect("readout line " + i, x, y, w, TEXT_H);
    }

    /** Where a token's stamp goes: at its left end, one unit above its line. */
    public static Rect stamp(Rect token) {
        return new Rect(token.name() + " stamp", token.x(), token.y() - 1, STAMP, STAMP);
    }

    /**
     * The placed readouts: a rectangle per token that made it, in order, the index in the input
     * each one belongs to, how many were left off, and where the "+n" that says so goes (null when
     * nothing was). A resource left off does not leave off the passives after it, so the placed
     * tokens are not always a prefix of the input - read each one's reading through
     * {@code sources}, never by its place in the list.
     */
    public record Flow(Rect[] tokens, int[] sources, int dropped, Rect more) {}

    /**
     * Places readout tokens of the given widths, greedily and in order: each goes on the current
     * line if it fits, else on the next. Tokens before {@code passivesFrom} are the resources and
     * the rest the passives, and the passives always start a line of their own - so while any
     * passive is showing, the resources get every line but the last.
     *
     * <p>Within each group the first token that fits nowhere stops the group, and it and
     * everything after it in the group are left off whole - so a token never moves one placed
     * before it, and a reading is never cut in half. Nothing is left off in silence: a muted "+n"
     * trails the last token, and if there is no room for it the last token gives up its place.
     *
     * <p>At a left anchor tokens run right from the margin; at a right anchor they run left from
     * the edge, so the first reading is always the one nearest the corner.
     */
    public Flow flow(int[] widths, int passivesFrom) {
        int split = Math.max(0, Math.min(passivesFrom, widths.length));
        boolean passives = split < widths.length;
        int width = readoutWidth();
        List<Rect> placed = new ArrayList<>(Math.min(widths.length, READOUT_TOKENS_MAX));
        List<Integer> sources = new ArrayList<>(Math.min(widths.length, READOUT_TOKENS_MAX));
        List<int[]> ends = new ArrayList<>();
        int[] cursor = {0, 0};
        int dropped = flowGroup(widths, 0, split, passives ? READOUT_LINES_MAX - 1 : READOUT_LINES_MAX, width, cursor, placed, sources, ends);
        if (!placed.isEmpty()) {
            cursor[0]++;
            cursor[1] = 0;
        }
        dropped += flowGroup(widths, split, widths.length, READOUT_LINES_MAX, width, cursor, placed, sources, ends);
        while (dropped > 0) {
            int line = ends.isEmpty() ? 0 : ends.get(ends.size() - 1)[0];
            int used = ends.isEmpty() ? 0 : ends.get(ends.size() - 1)[1];
            int start = used == 0 ? 0 : used + READOUT_TOKEN_GAP;
            if (start + MORE_W > width) {
                line++;
                start = 0;
            }
            if (line < READOUT_LINES_MAX) {
                return flowOf(placed, sources, dropped, place("more", line, start, MORE_W));
            }
            placed.remove(placed.size() - 1);
            sources.remove(sources.size() - 1);
            ends.remove(ends.size() - 1);
            dropped++;
        }
        return flowOf(placed, sources, 0, null);
    }

    private static Flow flowOf(List<Rect> placed, List<Integer> sources, int dropped, Rect more) {
        int[] from = new int[sources.size()];
        for (int i = 0; i < from.length; i++) {
            from[i] = sources.get(i);
        }
        return new Flow(placed.toArray(new Rect[0]), from, dropped, more);
    }

    /** One group of the flow; returns how many of its tokens were left off. */
    private int flowGroup(int[] widths, int from, int to, int lines, int width, int[] cursor,
            List<Rect> placed, List<Integer> sources, List<int[]> ends) {
        for (int i = from; i < to; i++) {
            int w = widths[i];
            if (placed.size() >= READOUT_TOKENS_MAX || w > width) {
                return to - i;
            }
            int line = cursor[0];
            int used = cursor[1];
            if (used > 0 && used + READOUT_TOKEN_GAP + w > width) {
                line++;
                used = 0;
            }
            if (line >= lines) {
                return to - i;
            }
            int start = used == 0 ? 0 : used + READOUT_TOKEN_GAP;
            placed.add(place("readout " + i, line, start, w));
            sources.add(i);
            cursor[0] = line;
            cursor[1] = start + w;
            ends.add(new int[] {line, start + w});
        }
        return 0;
    }

    private Rect place(String name, int line, int start, int w) {
        Rect box = readoutLine(line);
        int x = anchor.right() ? box.right() - start - w : box.x() + start;
        return new Rect(name, x, box.y(), w, TEXT_H);
    }

    // ---- the whole block ------------------------------------------------------------------------

    /**
     * Every rectangle the block can occupy, for the overlap sweep: the pools with both places the
     * barrier count can take, then the slots if any is equipped, then the readout lines, each
     * with the row above it that its stamps stand in. The order is fixed, so a list with more
     * lines begins with the list with fewer.
     */
    public List<Rect> blockShapes(boolean slots, int readoutLines) {
        List<Rect> shapes = new ArrayList<>();
        shapes.add(manaBar());
        shapes.add(barrierBar());
        shapes.add(poolsText());
        shapes.add(poolsStackedLine());
        if (slots) {
            for (int k = 0; k < SLOTS; k++) {
                shapes.add(glyph(k));
            }
            for (int k = 0; k < SLOTS; k++) {
                shapes.add(cell(k));
            }
        }
        for (int i = 0; i < Math.min(readoutLines, READOUT_LINES_MAX); i++) {
            Rect line = readoutLine(i);
            shapes.add(new Rect(line.name(), line.x(), line.y() - 1, line.w(), line.h() + 1));
        }
        return shapes;
    }

    /**
     * Whether the block, at its fullest, stays off the B loadout rail on this screen.
     *
     * <p>The rail is centred on the left edge, so the default corner clears it and a bottom corner
     * usually does not. Where it does not, the block stands down while the switcher is held rather
     * than print its readouts through the rail's names. The rail is taken at its tallest, because
     * it is centred and a longer list reaches higher.
     */
    public boolean clearsLoadoutRail() {
        return clearsLoadoutRail;
    }

    /** Measured once, when the layout is made: the HUD asks every frame the switcher is up. */
    private boolean measureLoadoutRail() {
        List<CodexLayout.Rect> rail = LoadoutSwitcherLayout.rects(MagicContent.MAX_LOADOUTS, guiWidth, guiHeight);
        for (Rect shape : blockShapes(true, READOUT_LINES_MAX)) {
            for (CodexLayout.Rect r : rail) {
                if (shape.overlaps(new Rect(r.name(), r.x(), r.y(), r.w(), r.h()))) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- centre group ---------------------------------------------------------------------------

    public int centreX() {
        return guiWidth / 2;
    }

    public int centreY() {
        return guiHeight / 2;
    }

    public Rect statusChip(int i, int n) {
        int shown = Math.min(n, STATUS_CHIPS_MAX);
        int x = Math.round(centreX() + (i - (shown - 1) / 2.0F) * STATUS_CHIP_STRIDE) - STATUS_CHIP / 2;
        return new Rect("status chip " + i, x, centreY() + STATUS_CHIP_DY, STATUS_CHIP, STATUS_CHIP);
    }

    public Rect unwakingCue() {
        return new Rect("unwaking cue", centreX() - UNWAKING_CUE_W / 2, centreY() + UNWAKING_CUE_DY, UNWAKING_CUE_W, TEXT_H);
    }

    /** The rule flash's plate, hugging a formula {@code contentWidth} wide (already at its drawn size). */
    public Rect ruleFlashPlate(int contentWidth) {
        int w = Math.min(RULE_FLASH_MAX_W, Math.max(0, contentWidth)) + 2 * RULE_FLASH_PAD;
        int h = 2 * TEXT_H + 2 * RULE_FLASH_PAD;
        return new Rect("rule flash plate", centreX() - w / 2, centreY() - RULE_FLASH_DY - h / 2, w, h);
    }

    /** The caption under the flash's plate: category, operation and target in one muted row. */
    public Rect ruleFlashCaption() {
        Rect plate = ruleFlashPlate(RULE_FLASH_MAX_W);
        return new Rect("rule flash caption", centreX() - RULE_FLASH_MAX_W / 2, plate.bottom() + RULE_FLASH_CAPTION_GAP, RULE_FLASH_MAX_W, TEXT_H);
    }

    /** Everything drawn around the crosshair while nothing modal is open. */
    public List<Rect> centreShapes(int statusChips) {
        List<Rect> shapes = new ArrayList<>();
        for (int i = 0; i < Math.min(statusChips, STATUS_CHIPS_MAX); i++) {
            shapes.add(statusChip(i, statusChips));
        }
        shapes.add(unwakingCue());
        shapes.add(ruleFlashPlate(RULE_FLASH_MAX_W));
        shapes.add(ruleFlashCaption());
        return shapes;
    }
}
