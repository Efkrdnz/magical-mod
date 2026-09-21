package com.efkrdnz.magical.magic.sword;

import java.util.Arrays;

/**
 * What one enforcement pass did: the slots that let go, in the order they let go, and what strain
 * is still standing afterwards.
 *
 * <p>Slots and not stations, because the caller has to draw a line home from each one and then
 * credit its Edge back, and both of those want the station still in place with its bearing intact.
 * A shed <b>empties</b> a station; it does not unwrite it. That is also what makes the tie-break
 * rule sayable at all - "lowest slot index" only means something while the slots do not move.
 *
 * <p>{@code strainLeft} is non-zero in exactly one case: Sword God, under the draw, where the
 * overdraw rule says nothing sheds and the strain simply stands.
 *
 * <p>An array in a record does not get value equality for free, so this one writes its own; the
 * array handed to the constructor is copied, and the one handed back by the accessor is the
 * copy and must not be written to.
 */
public record Settlement(int[] shedSlots, int strainLeft) {

    private static final int[] NOTHING = new int[0];

    public Settlement {
        shedSlots = shedSlots == null ? NOTHING : shedSlots.clone();
        strainLeft = Math.max(0, strainLeft);
    }

    /** Nothing shed. Either there was no strain, or Sword God is carrying it. */
    public static Settlement quiet(int strainLeft) {
        return new Settlement(NOTHING, strainLeft);
    }

    public int shed() {
        return shedSlots.length;
    }

    public boolean shedAnything() {
        return shedSlots.length > 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Settlement that
                && strainLeft == that.strainLeft
                && Arrays.equals(shedSlots, that.shedSlots);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(shedSlots) * 31 + strainLeft;
    }

    @Override
    public String toString() {
        return "Settlement[shedSlots=" + Arrays.toString(shedSlots) + ", strainLeft=" + strainLeft + "]";
    }
}
