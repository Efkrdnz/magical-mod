package com.efkrdnz.magical.magic.incantation;

/** What the edit payload will carry, pinned next to the core's caps so the two cannot drift. */
public final class SetIncantationPayloadCaps {
    public static final int MAX_IDS = ReciteCaps.MAX_VERSES;
    /** {@code magical:needle_twin_latch} is 25; a namespace and a path together never need more. */
    public static final int MAX_ID_LENGTH = 64;

    private SetIncantationPayloadCaps() {
    }
}
