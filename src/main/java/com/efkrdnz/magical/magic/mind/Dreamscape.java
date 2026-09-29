package com.efkrdnz.magical.magic.mind;

import java.util.UUID;

/**
 * One wielder's Dreamscape: the plot it is built on, where a dreamer arrives, and the one thing in it
 * the wielder marked as wrong. The blocks and creatures themselves are real and live in the level; this
 * is only what the level cannot say. Pure.
 */
public final class Dreamscape {
    /** Exactly one of a block (an offset from the plot's origin) or a figment (its entity UUID). */
    public record Flaw(Offset block, UUID figment) {
        public Flaw {
            if ((block == null) == (figment == null)) {
                throw new IllegalArgumentException("a Flaw is exactly one block or one figment");
            }
        }
    }

    private final int plot;
    private Offset arrival = new Offset(0, 0, 0);
    private float arrivalYaw;
    private Flaw flaw;

    public Dreamscape(int plot) {
        this.plot = plot;
    }

    public int plot() { return plot; }
    public Offset arrival() { return arrival; }
    public float arrivalYaw() { return arrivalYaw; }
    public Flaw flaw() { return flaw; }

    public void setArrival(Offset at, float yaw) {
        this.arrival = at;
        this.arrivalYaw = yaw;
    }

    public void markBlock(Offset at) {
        this.flaw = new Flaw(at, null);
    }

    public void markFigment(UUID figment) {
        this.flaw = new Flaw(null, figment);
    }

    /** Clears the Flaw if it is this block, and says whether it was. */
    public boolean clearIfFlaw(Offset at) {
        if (flaw != null && at.equals(flaw.block())) {
            flaw = null;
            return true;
        }
        return false;
    }

    /** Clears the Flaw if it is this figment, and says whether it was. */
    public boolean clearIfFlaw(UUID figment) {
        if (flaw != null && figment.equals(flaw.figment())) {
            flaw = null;
            return true;
        }
        return false;
    }
}
