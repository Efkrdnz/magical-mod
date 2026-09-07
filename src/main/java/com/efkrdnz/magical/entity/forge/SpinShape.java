package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The full turn: everything around the wielder out to the spin radius, within about a body's height
 * above or below. Direction is irrelevant — a spin has no front.
 */
public final class SpinShape implements HitShape {

    static final SpinShape INSTANCE = new SpinShape();

    private static final double MAX_DY = 2.0;

    private SpinShape() {}

    @Override
    public boolean hits(Query q) {
        return ShapeMath.ring(q.ox(), q.oy(), q.oz(), q.tx(), q.ty(), q.tz(),
                q.reach() + q.targetRadius(), MAX_DY);
    }
}
