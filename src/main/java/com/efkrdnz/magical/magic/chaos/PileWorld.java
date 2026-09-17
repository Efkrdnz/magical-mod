package com.efkrdnz.magical.magic.chaos;

import java.util.List;

/**
 * Everything {@link Pile} needs to know about the world, and nothing else.
 *
 * <p>The avalanche is arithmetic. Keeping the level behind this interface is what lets the whole of
 * the Authority of Chaos be pinned by unit tests on exact values rather than watched in a dev
 * client and hoped about.
 */
public interface PileWorld {

    /** How much this site holds before it gives way, decided by what the site <em>is</em>. */
    int capacity(PileSite site);

    /** Whatever this site would spill into, in a stable order - ties are broken by this order. */
    List<PileSite> neighbours(PileSite site);

    /** True for a body, false for a block. {@link Fault#HUNT} walks past everything else. */
    boolean living(PileSite site);

    /** Used by {@link Fault#SLUMP} alone, so a landslide knows which way is down. */
    double height(PileSite site);

    /** False once a body is dead or a block is out of a loaded chunk; the Pile then forgets it. */
    boolean present(PileSite site);

    /** Stress leaving the system as force and harm. Conservation: it is spent here, never lost. */
    void shed(PileSite site, int amount);
}
