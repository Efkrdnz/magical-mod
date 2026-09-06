package com.efkrdnz.magical.magic.visual;

/**
 * One glyph layer of a magic circle. Radii are fractions of the circle radius. Band kinds occupy
 * the annulus r0..r1; planar kinds are drawn on a square of half-size r1.
 *
 * @param importance 0 = always drawn (identity), 1..3 = dropped progressively by detail level / LOD
 * @param inkDelay ticks after the circle starts before this layer begins inking in
 * @param inkTicks ticks the ink-on takes
 * @param stampOrEmblem atlas cell (STAMP_BAND stamp id / EMBLEM cell id), else 0
 * @param orbitCount if > 0 the layer is instanced this many times on an orbit of radius orbitRadius
 * @param arcSweepDeg if < 360 only this angular fraction of a band is emitted (partial arcs)
 * @param mirrorAxes if > 0 the partial arc is mirrored across this many axes
 */
public record CircleLayer(
        GlyphKind kind,
        float r0,
        float r1,
        int count,
        int paramB,
        float spinDegPerTick,
        ColorRole role,
        float intensity,
        int importance,
        float inkDelay,
        float inkTicks,
        int stampOrEmblem,
        int orbitCount,
        float orbitRadius,
        float orbitSpeed,
        float arcStartDeg,
        float arcSweepDeg,
        int mirrorAxes) {

    public CircleLayer withSpin(float spin) {
        return new CircleLayer(kind, r0, r1, count, paramB, spin, role, intensity, importance, inkDelay, inkTicks, stampOrEmblem, orbitCount, orbitRadius, orbitSpeed, arcStartDeg, arcSweepDeg, mirrorAxes);
    }

    public CircleLayer withInk(float delay, float ticks) {
        return new CircleLayer(kind, r0, r1, count, paramB, spinDegPerTick, role, intensity, importance, delay, ticks, stampOrEmblem, orbitCount, orbitRadius, orbitSpeed, arcStartDeg, arcSweepDeg, mirrorAxes);
    }

    public CircleLayer withImportance(int value) {
        return new CircleLayer(kind, r0, r1, count, paramB, spinDegPerTick, role, intensity, value, inkDelay, inkTicks, stampOrEmblem, orbitCount, orbitRadius, orbitSpeed, arcStartDeg, arcSweepDeg, mirrorAxes);
    }

    public CircleLayer withRole(ColorRole value) {
        return new CircleLayer(kind, r0, r1, count, paramB, spinDegPerTick, value, intensity, importance, inkDelay, inkTicks, stampOrEmblem, orbitCount, orbitRadius, orbitSpeed, arcStartDeg, arcSweepDeg, mirrorAxes);
    }

    public CircleLayer scaled(float factor) {
        return new CircleLayer(kind, r0 * factor, r1 * factor, count, paramB, spinDegPerTick, role, intensity, importance, inkDelay, inkTicks, stampOrEmblem, orbitCount, orbitRadius * factor, orbitSpeed, arcStartDeg, arcSweepDeg, mirrorAxes);
    }

    public boolean isPartialArc() {
        return arcSweepDeg < 359.9F;
    }
}
