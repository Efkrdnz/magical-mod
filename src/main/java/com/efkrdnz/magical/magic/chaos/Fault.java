package com.efkrdnz.magical.magic.chaos;

import java.util.Locale;

/**
 * Which way a site gives when it gives.
 *
 * <p>Seven answers to one question, and the wielder holds five of them in an order. A Fault is not
 * an effect and not a rule about anybody: it says nothing about who is hurt, what it costs, or when
 * it may happen. It is the geometry of a collapse, and it is the whole of what the Authority of
 * Chaos authors.
 */
public enum Fault {
    /** All of it to the lowest neighbour. A landslide that runs downhill and pools. */
    SLUMP,
    /** All of it to whichever neighbour already holds most. Rich gets richer, so a cascade converges. */
    HEAP,
    /** Split evenly among every neighbour. A front that widens. */
    BLOOM,
    /** Only to living neighbours, walking past stone. A cascade that seeks bodies. */
    HUNT,
    /** Back the way it came. The cascade eats its own source. */
    RECOIL,
    /** Passes nothing on, and spends it as force where it stands. The loud terminator. */
    SHED,
    /** Keeps it and raises its own capacity. The quiet terminator: a sink that swallows a cascade. */
    ROOT;

    public String translationKey() {
        return "fault.magical." + name().toLowerCase(Locale.ROOT);
    }
}
