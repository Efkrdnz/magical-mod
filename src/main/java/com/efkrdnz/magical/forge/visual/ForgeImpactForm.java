package com.efkrdnz.magical.forge.visual;

/**
 * What a forged strike struck, which decides the shape of what it throws there.
 *
 * <p>Sent as an ordinal between the server and a client of the same build, and clamped on arrival
 * because it comes off the wire.
 */
public enum ForgeImpactForm {
    /** A blade into a body: a flash where it bit, the element's matter knocked out of the wound. */
    HIT,
    /** A thrown wave meeting a wall: crumbs of the block, sparks, the element's matter off the face. */
    BLOCK;

    private static final ForgeImpactForm[] VALUES = values();

    public static ForgeImpactForm byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : HIT;
    }
}
