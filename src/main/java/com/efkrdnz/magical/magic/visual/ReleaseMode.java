package com.efkrdnz.magical.magic.visual;

/** How the cast circle collapses on release. */
public enum ReleaseMode {
    /** Radius x1.4 in 4 ticks plus a slam flash under it: self-bursts, ground slams. */
    SLAM,
    /** Shrinks toward 0.25R tilted toward the aim vector: projectiles, beams, lances. */
    FUNNEL,
    /** Rises 1.2 blocks and turns to face the aim vector: summons, pillars, walls. */
    LIFT
}
