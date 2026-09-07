package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.strike.ShapeMath;

/**
 * The hit volume of one form family. Implementations take nothing but doubles and a
 * {@link ShapeMath.Basis} and delegate all their arithmetic to {@link ShapeMath}, so
 * {@code ForgeStrikeEntity} never has to know what a cone or a capsule is.
 *
 * <p>Targets are tested by their bounding-box centre, so every shape but {@link WaveShape} (whose
 * inflation already accounts for it) pads its extent by half the target's width. Without that a
 * wide mob standing exactly at reach would be missed by a strike that visibly overlaps it.</p>
 */
public sealed interface HitShape permits SlashShape, CleaveShape, ThrustShape, SpinShape, SlamShape, WaveShape {

    boolean hits(Query query);

    /** How far the broad-phase search box is grown around the strike before {@link #hits} narrows it. */
    default Inflation broadInflation(double halfWidth, double reach) {
        double pad = reach + 1.0;
        return new Inflation(pad, pad, pad);
    }

    /**
     * Everything a shape may look at. {@code o} is the strike origin this tick and {@code p} the
     * origin last tick (they are equal for every family that does not travel); {@code t} is the
     * target's bounding-box centre, with its feet height and width alongside.
     */
    record Query(double ox, double oy, double oz, double px, double py, double pz, ShapeMath.Basis basis,
            double tx, double ty, double tz, double targetFeetY, double targetWidth,
            double reach, double halfWidth, double arcDegrees) {

        public double targetRadius() {
            return targetWidth * 0.5;
        }
    }

    record Inflation(double x, double y, double z) {}
}
