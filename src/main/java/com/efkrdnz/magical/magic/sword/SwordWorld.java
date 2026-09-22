package com.efkrdnz.magical.magic.sword;

/**
 * Everything the formation needs to know about a level, and it is as little as could be arranged.
 *
 * <p>Note what is <b>not</b> here: anything per sword. Every rule the kit has is either pure
 * geometry in {@code magic.sword.stance} or a single query against the world, which is what keeps
 * the interesting half testable with no world mock at all.
 *
 * <p>{@code LevelSwordWorld} is the only implementation that knows what a {@code Level} is, and it
 * answers in a stable order, because a tie between two bodies has to break the same way twice.
 */
public interface SwordWorld {

    /** Whether that body is still there. A ride, a fall and a follow-up all release when it is not. */
    boolean bodyAlive(int entityId);

    /** Whether a sword would be inside something at this point. */
    boolean solidAt(double x, double y, double z);

    /** The first surface under this point within the search, or NaN when there is none. */
    double surfaceBelow(double x, double y, double z, int searchBlocks);

    /** The game time, for the return clocks and the lying swords' lifetimes. */
    long now();
}
