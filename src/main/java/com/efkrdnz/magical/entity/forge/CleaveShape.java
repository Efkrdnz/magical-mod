package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The overhead chop: a narrow vertical slab straight ahead, tall enough to catch a target standing
 * a body above or below the wielder but unforgiving about lateral aim.
 */
public final class CleaveShape implements HitShape {

    static final CleaveShape INSTANCE = new CleaveShape();

    private static final double LATERAL_FRACTION = 0.35;
    private static final double MIN_UP = -1.0;
    private static final double MAX_UP = 2.5;

    private CleaveShape() {}

    @Override
    public boolean hits(Query q) {
        return ShapeMath.verticalPlane(q.ox(), q.oy(), q.oz(), q.basis(), q.tx(), q.ty(), q.tz(),
                q.halfWidth() * LATERAL_FRACTION + q.targetRadius(), q.reach() + q.targetRadius(), MIN_UP, MAX_UP);
    }
}
