package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The ground shock: a flat disc centred on the impact point. Height is measured against the
 * target's feet rather than its centre, so a slam catches what is standing on the ground it hit and
 * nothing that is sailing over it.
 */
public final class SlamShape implements HitShape {

    static final SlamShape INSTANCE = new SlamShape();

    private static final double MAX_FEET_DY = 1.5;

    private SlamShape() {}

    @Override
    public boolean hits(Query q) {
        return ShapeMath.disc(q.ox(), q.oy(), q.oz(), q.tx(), q.targetFeetY(), q.tz(),
                q.reach() + q.targetRadius(), MAX_FEET_DY);
    }
}
