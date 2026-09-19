package com.efkrdnz.magical.entity.verse;

/**
 * Where each body of one shot points, in degrees of yaw off the aim, as §9 of the design lays it
 * down: below 180 the fan is inclusive, {@code -p..+p} in N-1 steps, so Column (90, three bodies)
 * is -90, 0, +90; at 180 and above it is the full circle exclusive starting ahead, so Mirror (180,
 * two) is 0 and 180 and Hexad (180, six) is every 60. A lone body, or no pattern, points straight.
 * The fan turns about the vertical axis, the plane a first-person caster reads. Pure, so a test
 * can hold every row of the table without a level.
 */
public final class VerseFan {

    /** At this pattern and above the fan is the whole circle rather than an arc. */
    public static final double FULL_CIRCLE = 180.0D;

    private VerseFan() {
    }

    public static double[] yaws(int count, double patternDegrees) {
        if (count <= 0) {
            return new double[0];
        }
        double[] yaws = new double[count];
        if (count == 1 || patternDegrees <= 0.0D) {
            return yaws;
        }
        if (patternDegrees >= FULL_CIRCLE) {
            double step = 360.0D / count;
            for (int i = 0; i < count; i++) {
                yaws[i] = i * step;
            }
            return yaws;
        }
        double step = 2.0D * patternDegrees / (count - 1);
        for (int i = 0; i < count; i++) {
            yaws[i] = -patternDegrees + i * step;
        }
        return yaws;
    }

    /** A deviation within half the spread either way, from a 0..1 roll the caller supplies, so the random stays outside. */
    public static double deviation(double spreadDegrees, double roll01) {
        if (spreadDegrees <= 0.0D) {
            return 0.0D;
        }
        return (roll01 - 0.5D) * spreadDegrees;
    }
}
