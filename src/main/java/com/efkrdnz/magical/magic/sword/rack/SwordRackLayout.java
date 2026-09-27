package com.efkrdnz.magical.magic.sword.rack;

/**
 * Where everything on the rack screen is, as pure arithmetic both the menu and the screen read.
 *
 * <p>The twelve sockets stand on a ring, <b>clockwise from the top</b>, because that is what they
 * are: socket <i>i</i> is flown as sword <i>i</i>, and a formation is a thing round the wielder
 * rather than a row in a chest. The stance's own count lights the first sockets and the rung's
 * count opens the rest, so reading round the ring from the top is reading down the formation.
 *
 * <p>The radius is the one number that is not a matter of taste. Two sockets thirty degrees apart
 * on the diagonals sit {@code 0.366 r} apart on both axes, and a well is eighteen pixels square, so
 * any radius under fifty puts two wells on top of each other. {@code SwordRackLayoutTest} holds
 * the ring, the centre text, the title and the inventory apart at every socket.
 */
public final class SwordRackLayout {

    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 235;

    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 6;

    public static final int RING_X = 88;
    public static final int RING_Y = 78;
    public static final int RING_RADIUS = 52;

    /** Degrees between two sockets, and where socket zero stands: straight up. */
    public static final double STEP_DEGREES = 30.0D;
    public static final double FIRST_DEGREES = -90.0D;

    /** An item is sixteen pixels; its well draws a pixel of border round that. */
    public static final int ITEM = 16;
    public static final int WELL = 18;

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_LABEL_Y = 143;
    public static final int INVENTORY_Y = 154;
    public static final int HOTBAR_Y = 212;

    /** The two lines in the middle of the ring: the stance, and how many of its swords fly. */
    public static final int STANCE_LINE_Y = 69;
    public static final int COUNT_LINE_Y = 81;
    public static final int LINE_HEIGHT = 9;
    public static final int CENTRE_TEXT_WIDTH = 72;

    private SwordRackLayout() {
    }

    public static double socketDegrees(int index) {
        return FIRST_DEGREES + STEP_DEGREES * index;
    }

    /** Left edge of socket {@code index}'s item, from the image's left. */
    public static int socketX(int index) {
        return (int) Math.round(RING_X + RING_RADIUS * Math.cos(Math.toRadians(socketDegrees(index)))) - ITEM / 2;
    }

    /** Top edge of socket {@code index}'s item, from the image's top. Screen y runs down. */
    public static int socketY(int index) {
        return (int) Math.round(RING_Y + RING_RADIUS * Math.sin(Math.toRadians(socketDegrees(index)))) - ITEM / 2;
    }

    /** The socket whose item area holds this point, from the image's corner, or -1. */
    public static int socketAt(double x, double y) {
        for (int i = 0; i < SwordRack.SIZE; i++) {
            int left = socketX(i);
            int top = socketY(i);
            if (x >= left && x < left + ITEM && y >= top && y < top + ITEM) {
                return i;
            }
        }
        return -1;
    }

    public static int centreTextLeft() {
        return RING_X - CENTRE_TEXT_WIDTH / 2;
    }
}
