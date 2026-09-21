package com.efkrdnz.magical.magic.sword;

/**
 * Everything the Array needs to know about a level, and it is as little as could be arranged.
 *
 * <p>Note what is <b>not</b> here: a per-blade distance query. {@code SwordArray.settle} takes one
 * {@code int strain}, which the adapter worked out from one scalar through
 * {@link ArrayPose#boundScale}. That single decision is why every invariant in this package is
 * testable in arithmetic with no world mock at all - a version of this interface with a question
 * per blade would have made {@code SettleTest} a fake level instead of a table of numbers.
 *
 * <p>{@code LevelSwordWorld} is the only implementation that knows what a {@code Level} is, and
 * it answers in a stable order, because a tie in a farthest-first shed has to be deterministic.
 */
public interface SwordWorld {

    /** How far the bound body is from the wielder, or -1 when the body is gone. */
    double distanceToBound(int entityId, double[] wielder);

    /** Whether that body is still there to be bound to. A bind releases when it is not. */
    boolean bodyAlive(int entityId);

    /** Whether a blade would be inside something at this point. */
    boolean solidAt(double x, double y, double z);

    /** The first surface under this point within the search, or NaN when there is none. */
    double surfaceBelow(double x, double y, double z, int searchBlocks);

    /** The game time, for the recovery clocks and the lying blades' lifetimes. */
    long now();
}
