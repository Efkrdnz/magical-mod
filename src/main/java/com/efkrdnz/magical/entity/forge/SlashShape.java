package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The forward cutting cone: a sweep of {@code arcDegrees} centred on the look direction. Also the
 * shape flurries and rising cuts use, since all three are "everything in front of me right now".
 * Targets above eye level get a little angular slack so an upward cut still catches a jumping mob.
 */
public final class SlashShape implements HitShape {

    static final SlashShape INSTANCE = new SlashShape();

    private static final double ABOVE_EYE_SLACK = 0.15;

    private SlashShape() {}

    @Override
    public boolean hits(Query q) {
        double slack = q.ty() > q.oy() ? ABOVE_EYE_SLACK : 0.0;
        return ShapeMath.cone(q.ox(), q.oy(), q.oz(), q.basis(), q.tx(), q.ty(), q.tz(),
                q.reach() + q.targetRadius(), q.arcDegrees() * 0.5, slack);
    }
}
