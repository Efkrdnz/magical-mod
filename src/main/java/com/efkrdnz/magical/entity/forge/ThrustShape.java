package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The lunge: a thin capsule running from the wielder's eye out along the look direction, so a
 * thrust reaches far but only hits what is genuinely lined up with the point.
 */
public final class ThrustShape implements HitShape {

    static final ThrustShape INSTANCE = new ThrustShape();

    private static final double RADIUS_FRACTION = 0.4;

    private ThrustShape() {}

    @Override
    public boolean hits(Query q) {
        double distance = ShapeMath.capsuleDistance(
                q.ox(), q.oy(), q.oz(),
                q.ox() + q.basis().fx() * q.reach(),
                q.oy() + q.basis().fy() * q.reach(),
                q.oz() + q.basis().fz() * q.reach(),
                q.tx(), q.ty(), q.tz());
        return distance <= q.halfWidth() * RADIUS_FRACTION + q.targetRadius();
    }
}
