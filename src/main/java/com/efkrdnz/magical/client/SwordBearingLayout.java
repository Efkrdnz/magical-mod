package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.sword.Station;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry for the Bearing hold: the Array drawn as an azimuthal disc, off to one side of the
 * crosshair.
 *
 * <p><b>Why a disc and not a row or a column.</b> The two frameless holds this sits beside are a
 * row and three columns because what they show is a <em>sequence</em> and a <em>choice</em>.
 * What this one has to show is a <em>direction</em>, and twelve directions round a sphere cannot
 * be listed without inventing an order that the thing itself does not have. So it is plotted:
 * angle is the station's yaw, radius is its pitch, the centre is straight up and the rim is
 * straight down. Yaw 0 is the wielder's own facing and it is drawn at the top, with yaw
 * increasing clockwise, exactly as {@code Station.unitBearing} lays the lattice out - so a mark
 * at the bottom of the disc is a blade behind your head, which is the one reading this overlay
 * exists to give and the one the crosshair can never reach.
 *
 * <p><b>The poles are reserved.</b> The lattice stops at pitch +/-4, which is 72 degrees, so the
 * zenith and the nadir are places no station can occupy. Mapping pitch +4 onto radius zero would
 * collapse all twenty-four bearings at that elevation onto one pixel; instead the marks live in
 * an annulus from {@link #RING_INNER} of the usable radius out to all of it, which keeps the
 * projection injective over the whole lattice and leaves the dead centre free for the one glyph
 * that means "straight up and nothing is there".
 *
 * <p><b>It sits left of the crosshair and not over it.</b> A hold is open while the wielder is
 * still aiming, so the plot may not grow into the middle of the screen - the rule
 * {@link LoadoutSwitcherLayout} settled and {@link CausalAnchorLayout} obeys from the other side.
 * The block is therefore pinned to the left margin and given whatever room is left before
 * {@link #CROSSHAIR_KEEP}, and every text budget here is a function of {@code guiWidth} for the
 * same reason: a fixed width is how the loadout switcher came to draw thirty-four characters out
 * through its own wall.
 *
 * <p>The class is pure geometry with no {@code Font} and no {@code GuiGraphics}: the overlay
 * measures its strings and passes the widths in, the way all three frameless precedents do, so
 * {@code SwordBearingLayoutTest} can sweep every screen size the game can hand us without a
 * render context.
 *
 * <p>All coordinates are block-local unless a method says otherwise: add {@link #block}'s x and y.
 */
public final class SwordBearingLayout {

    /**
     * Clear water either side of the crosshair.
     *
     * <p>Vanilla draws a 15px crosshair centred on {@code guiWidth / 2}; this is that half-width
     * with room to spare, and it is the same number the loadout switcher keeps. Nothing this
     * class places may cross it, which is what forces the plot off centre - a disc centred on the
     * screen would be a disc centred on the crosshair.
     */
    public static final int CROSSHAIR_KEEP = 24;

    /** How far in from the left edge the block starts. */
    public static final int SCREEN_MARGIN = 6;

    /** The block never runs closer than this to the top or bottom of the screen. */
    public static final int EDGE_KEEP = 4;

    public static final int LINE_H = 9;

    /**
     * The widest the block gets.
     *
     * <p>A bigger disc does not read better: the marks are three to seven pixels and the lattice
     * is fixed, so past about this the plot is mostly empty and the words under it are stranded.
     */
    public static final int BLOCK_W_MAX = 200;

    /** Between the disc and the four bearing words round it. */
    public static final int CARDINAL_GAP = 4;

    /** The four bearing words, in the order they are indexed. */
    public static final int CARD_AHEAD = 0;
    public static final int CARD_RIGHT = 1;
    public static final int CARD_BEHIND = 2;
    public static final int CARD_LEFT = 3;
    public static final int CARD_COUNT = 4;

    /**
     * How much of the block one side gutter may take: a fifth, so the disc keeps three fifths of
     * it and the words never win an argument with the thing they are labelling.
     */
    public static final int CARDINAL_SHARE = 5;

    public static final int CARDINAL_W_MIN = 14;
    public static final int CARDINAL_W_MAX = 38;

    /** Between the bottom bearing word and the first line of reading. */
    public static final int CAPTION_GAP = 6;

    /** The bill, the focused station, and what a release would do. */
    public static final int READING_LINES = 3;

    /** A mark for a station with no metal on it, and for a one-Edge one. */
    public static final int MARK_MIN = 3;

    /** A mark at the rung's own {@code maxEdge}. Odd, so it centres on a pixel. */
    public static final int MARK_MAX = 7;

    /** How far outside a mark its focus ring stands. */
    public static final int FOCUS_PAD = 2;

    /**
     * Where the innermost ring of the lattice sits, as a fraction of the usable radius.
     *
     * <p>Not zero. Pitch +4 is twenty-four distinct bearings and they have to stay twenty-four:
     * at radius zero they are one mark, and the single densest legal packing this kit allows -
     * twelve stations two yaw steps apart - would be drawn on top of itself.
     */
    public static final float RING_INNER = 0.32F;

    /** How many ticks the focus ring takes to travel from one mark to the next. */
    public static final float FOCUS_GLIDE_TICKS = 3.0F;

    private SwordBearingLayout() {}

    // ---- the block ------------------------------------------------------------------------------

    /** The line the overlay may not cross, in screen coordinates. */
    public static int wall(int guiWidth) {
        return guiWidth / 2 - CROSSHAIR_KEEP;
    }

    /** How much width is left between the left margin and the wall. */
    public static int room(int guiWidth) {
        return wall(guiWidth) - SCREEN_MARGIN;
    }

    public static int blockWidth(int guiWidth) {
        return Math.max(1, Math.min(BLOCK_W_MAX, room(guiWidth)));
    }

    /**
     * As wide as a bearing word may be drawn on this screen.
     *
     * <p>A share of the block rather than a constant, so on a 320-wide GUI - where the block is
     * 130 and the disc has to come out of the same 130 - the words give ground first.
     */
    public static int cardinalLimit(int guiWidth) {
        return clamp(blockWidth(guiWidth) / CARDINAL_SHARE, CARDINAL_W_MIN, CARDINAL_W_MAX);
    }

    /** A side gutter: a bearing word plus its gap. */
    public static int gutter(int guiWidth) {
        return cardinalLimit(guiWidth) + CARDINAL_GAP;
    }

    /** As wide as a line of reading may be drawn: the whole block, and never more. */
    public static int readingLimit(int guiWidth) {
        return blockWidth(guiWidth);
    }

    /** What a string of {@code measured} pixels is actually drawn at, given a budget. */
    public static int textWidth(int measured, int limit) {
        return Math.max(0, Math.min(measured, limit));
    }

    /** How much height is left for the disc once the words above, beside and below it are paid for. */
    public static int heightRoom(int guiHeight) {
        return guiHeight - 2 * EDGE_KEEP - 2 * (LINE_H + CARDINAL_GAP)
                - CAPTION_GAP - READING_LINES * LINE_H;
    }

    /**
     * The diameter of the plot: whatever the block can spare after both gutters, or whatever the
     * screen is tall enough for, whichever is less.
     *
     * <p>Never clamped <em>up</em>. A floor here would be a disc wider than the room it was given,
     * which is the whole failure this class is arranged to avoid.
     */
    public static int discSize(int guiWidth, int guiHeight) {
        return Math.max(1, Math.min(blockWidth(guiWidth) - 2 * gutter(guiWidth), heightRoom(guiHeight)));
    }

    public static int blockHeight(int guiWidth, int guiHeight) {
        return 2 * (LINE_H + CARDINAL_GAP) + discSize(guiWidth, guiHeight)
                + CAPTION_GAP + READING_LINES * LINE_H;
    }

    /**
     * The block the whole thing occupies: against the left margin, vertically centred.
     *
     * <p>Left-aligned rather than centred in the strip it is allowed, because the strip grows with
     * the screen and a block that drifts toward the crosshair as the window widens is a block that
     * lands somewhere different every time you open it.
     */
    public static Rect block(int guiWidth, int guiHeight) {
        int w = blockWidth(guiWidth);
        int h = blockHeight(guiWidth, guiHeight);
        int centred = (guiHeight - h) / 2;
        int lowest = Math.max(EDGE_KEEP, guiHeight - EDGE_KEEP - h);
        return new Rect("block", SCREEN_MARGIN, Math.max(EDGE_KEEP, Math.min(centred, lowest)), w, h);
    }

    // ---- block-local rectangles -----------------------------------------------------------------

    /** The plot itself. Nothing paints its edge; it is the ground the marks are placed on. */
    public static Rect disc(int guiWidth, int guiHeight) {
        int size = discSize(guiWidth, guiHeight);
        return new Rect("disc", (blockWidth(guiWidth) - size) / 2, LINE_H + CARDINAL_GAP, size, size);
    }

    /**
     * One of the four bearing words, in the gutter on its own side of the disc.
     *
     * <p>They are the only thing that says which way the plot is pointed, and they cost nothing:
     * the corners of the block are empty, so four words in four gutters cannot reach each other
     * whatever the font does to them.
     */
    public static Rect cardinal(int guiWidth, int guiHeight, int quarter, int measured) {
        Rect disc = disc(guiWidth, guiHeight);
        int w = textWidth(measured, cardinalLimit(guiWidth));
        int middleY = disc.y() + (disc.h() - LINE_H) / 2;
        return switch (quarter) {
            case CARD_RIGHT -> new Rect("cardinal right", disc.right() + CARDINAL_GAP, middleY, w, LINE_H);
            case CARD_BEHIND -> new Rect("cardinal behind", disc.x() + (disc.w() - w) / 2,
                    disc.bottom() + CARDINAL_GAP, w, LINE_H);
            case CARD_LEFT -> new Rect("cardinal left", disc.x() - CARDINAL_GAP - w, middleY, w, LINE_H);
            default -> new Rect("cardinal ahead", disc.x() + (disc.w() - w) / 2,
                    disc.y() - CARDINAL_GAP - LINE_H, w, LINE_H);
        };
    }

    /** Where the reading starts: under the bottom bearing word. */
    public static int readingY(int guiWidth, int guiHeight) {
        return disc(guiWidth, guiHeight).bottom() + CARDINAL_GAP + LINE_H + CAPTION_GAP;
    }

    /** One line of the reading, full block width; the overlay places its words inside it. */
    public static Rect reading(int guiWidth, int guiHeight, int index) {
        return new Rect("reading " + index, 0, readingY(guiWidth, guiHeight) + index * LINE_H,
                blockWidth(guiWidth), LINE_H);
    }

    // ---- the plot -------------------------------------------------------------------------------

    /**
     * How far from the centre a mark may be placed, in pixels.
     *
     * <p>The disc's own radius less the room a mark at its largest needs with its focus ring
     * round it, so nothing this class places can hang over the edge of the plot at any lattice
     * position - which {@code SwordBearingLayoutTest} walks all 1296 of.
     */
    public static double usableRadius(Rect disc) {
        return Math.max(1.0D, disc.w() / 2.0D - (MARK_MAX / 2.0D + FOCUS_PAD + 1.5D));
    }

    /**
     * Where a pitch lands as a fraction of {@link #usableRadius}: {@link #RING_INNER} at the top
     * of the lattice, all of it at the bottom.
     */
    public static double radiusFraction(int pitch) {
        int clamped = clamp(pitch, Station.PITCH_MIN, Station.PITCH_MAX);
        double t = (Station.PITCH_MAX - clamped) / (double) (Station.PITCH_MAX - Station.PITCH_MIN);
        return RING_INNER + (1.0D - RING_INNER) * t;
    }

    /** The screen bearing of a yaw step: degrees clockwise from the top of the disc. */
    public static double bearingDegrees(int yaw) {
        return yaw * Station.YAW_STEP_DEGREES;
    }

    /**
     * A square of {@code size} centred on a point of the plot, given in polar coordinates.
     *
     * <p>Clockwise from the top, which is Minecraft's own handedness read from above and the same
     * one {@code Station.unitBearing} uses - so the mark for a blade on your right hand is drawn
     * on the right of the disc and the plot never needs a mirror.
     */
    public static Rect dot(String name, Rect disc, double fraction, double bearingDeg, int size) {
        double radius = usableRadius(disc) * clamp(fraction, 0.0D, 1.0D);
        double angle = Math.toRadians(bearingDeg);
        double cx = disc.x() + disc.w() / 2.0D;
        double cy = disc.y() + disc.h() / 2.0D;
        int x = (int) Math.round(cx + radius * Math.sin(angle)) - size / 2;
        int y = (int) Math.round(cy - radius * Math.cos(angle)) - size / 2;
        return new Rect(name, x, y, size, size);
    }

    /**
     * How big a station's mark is: its Edge against what the rung allows.
     *
     * <p>A station Ward or a shed emptied still has a bearing and still counts against the station
     * cap, so it keeps the smallest mark rather than vanishing - the overlay draws it hollow. A
     * bearing you cannot see is a bearing you cannot pull, and pulling is what this is for.
     */
    public static int markSize(int edge, int maxEdge) {
        if (edge <= 0) {
            return MARK_MIN;
        }
        float t = Math.min(1.0F, edge / (float) Math.max(1, maxEdge));
        return MARK_MIN + Math.round((MARK_MAX - MARK_MIN) * t);
    }

    /** One station's mark, sized by its Edge. */
    public static Rect mark(Rect disc, int slot, Station station, int maxEdge) {
        return dot("mark " + slot, disc, radiusFraction(station.pitch()),
                bearingDegrees(station.yaw()), markSize(station.edge(), maxEdge));
    }

    /** A mark placed off the lattice, part way through the focus glide. */
    public static Rect glidingMark(Rect disc, double fraction, double bearingDeg, int size) {
        return dot("focus", disc, fraction, bearingDeg, size);
    }

    /** The ring that says which mark the scroll is on. Shape, so it survives the marked hue. */
    public static Rect focusRing(Rect mark) {
        return new Rect(mark.name() + " ring", mark.x() - FOCUS_PAD, mark.y() - FOCUS_PAD,
                mark.w() + 2 * FOCUS_PAD, mark.h() + 2 * FOCUS_PAD);
    }

    /**
     * The order the scroll walks the marks in: clockwise from straight ahead, inside out.
     *
     * <p>Slot order is the Array's insertion order and it is load-bearing elsewhere - it is the
     * tie-break in a shed - but it is meaningless here: walking it makes the focus jump about the
     * disc at random. Walking the drawing instead means the ring travels the way the eye already
     * expects, and the glide between two neighbouring marks is short, which is the other half of
     * the reading.
     *
     * @return slot indices into {@code stations}, every one exactly once
     */
    public static int[] bearingOrder(List<Station> stations) {
        int count = stations == null ? 0 : stations.size();
        Integer[] slots = new Integer[count];
        for (int i = 0; i < count; i++) {
            slots[i] = i;
        }
        java.util.Arrays.sort(slots, (a, b) -> {
            Station left = stations.get(a);
            Station right = stations.get(b);
            if (left.yaw() != right.yaw()) {
                return Integer.compare(left.yaw(), right.yaw());
            }
            if (left.pitch() != right.pitch()) {
                // Descending, so a bearing with two stations on it reads from the inside out.
                return Integer.compare(right.pitch(), left.pitch());
            }
            return Integer.compare(a, b);
        });
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = slots[i];
        }
        return order;
    }

    // ---- the sweep ------------------------------------------------------------------------------

    /**
     * Everything drawn outside the plot, for the overlap and containment sweep.
     *
     * <p>The marks are deliberately not in here. Two stations two yaw steps apart on the innermost
     * ring genuinely do draw within a pixel of each other, and that is the shape the wielder
     * authored rather than a layout fault; what has to be true of a mark is that it is inside the
     * disc, which the test asserts over the whole lattice instead.
     */
    public static List<Rect> rects(int guiWidth, int guiHeight, int[] cardinalWidths) {
        List<Rect> rects = new ArrayList<>();
        rects.add(disc(guiWidth, guiHeight));
        for (int quarter = 0; quarter < CARD_COUNT; quarter++) {
            int measured = cardinalWidths == null || quarter >= cardinalWidths.length
                    ? cardinalLimit(guiWidth)
                    : cardinalWidths[quarter];
            rects.add(cardinal(guiWidth, guiHeight, quarter, measured));
        }
        for (int line = 0; line < READING_LINES; line++) {
            rects.add(reading(guiWidth, guiHeight, line));
        }
        return rects;
    }

    public static boolean hit(Rect rect, double lx, double ly) {
        return lx >= rect.x() && lx < rect.right() && ly >= rect.y() && ly < rect.bottom();
    }

    public static int indexAt(List<Rect> rects, double lx, double ly) {
        for (int i = 0; i < rects.size(); i++) {
            if (hit(rects.get(i), lx, ly)) {
                return i;
            }
        }
        return -1;
    }

    // ---- the curves -----------------------------------------------------------------------------

    /**
     * Eased at both ends, and <b>deliberately without the overshoot</b> the Grimoire and the
     * Manipulate Space columns settle on.
     *
     * <p>Those two slide a list past a fixed line, where a little overshoot reads as the column
     * being answered. This one carries a ring from one mark to another on a disc where the marks
     * are three pixels wide and two of them can be four pixels apart: an overshoot would take the
     * ring past its mark and, for a moment, onto its neighbour, which is the focus saying the
     * wrong thing about which bearing a release is going to pull.
     */
    public static float ease(float t) {
        float k = clamp(t, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Fast out of the gate and settling, for the open. It never passes its target either. */
    public static float easeOut(float t) {
        float k = 1.0F - clamp(t, 0.0F, 1.0F);
        return 1.0F - k * k * k;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
