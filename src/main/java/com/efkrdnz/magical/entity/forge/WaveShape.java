package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The travelling crescent. The swept box between last tick's position and this one does the
 * catching (see {@link #broadInflation}); this only rejects targets that are behind the wave, so a
 * blade that has already passed through something does not scoop it back up.
 */
public final class WaveShape implements HitShape {

    static final WaveShape INSTANCE = new WaveShape();

    private static final double LATERAL_FRACTION = 0.48;
    private static final double VERTICAL_INFLATION = 0.9;
    private static final double MIN_FORWARD_DOT = 0.12;

    private WaveShape() {}

    @Override
    public Inflation broadInflation(double halfWidth, double reach) {
        double lateral = halfWidth * LATERAL_FRACTION;
        return new Inflation(lateral, VERTICAL_INFLATION, lateral);
    }

    @Override
    public boolean hits(Query q) {
        double dx = q.tx() - q.ox();
        double dy = q.ty() - q.oy();
        double dz = q.tz() - q.oz();
        double length = ShapeMath.length(dx, dy, dz);
        if (length < 1.0E-6) {
            return true;
        }
        return ShapeMath.dot(dx / length, dy / length, dz / length,
                q.basis().fx(), q.basis().fy(), q.basis().fz()) >= MIN_FORWARD_DOT;
    }
}
