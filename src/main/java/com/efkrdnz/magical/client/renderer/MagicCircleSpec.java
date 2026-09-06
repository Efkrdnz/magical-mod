package com.efkrdnz.magical.client.renderer;

public record MagicCircleSpec(
        int outerSides,
        int middleSides,
        int ringSegments,
        int layerCount,
        int spokeCount,
        int glyphCount,
        int starPoints,
        int secondaryStarPoints,
        int glyphBands,
        int orbitingSealCount,
        boolean circularLayers,
        boolean pentagram,
        boolean zigzagRing,
        boolean infinityChain,
        boolean waveRing,
        boolean braidRing,
        boolean diamondMarks) {
    public static MagicCircleSpec divineRestoration() {
        return new MagicCircleSpec(7, 5, 128, 7, 14, 21, 7, 5, 3, 7, true, true, true, true, true, true, true);
    }

    public static MagicCircleSpec mistStep() {
        return new MagicCircleSpec(12, 3, 88, 4, 8, 12, 6, 0, 2, 3, true, false, true, false, true, false, false);
    }

    public static MagicCircleSpec anchorSigil() {
        return new MagicCircleSpec(8, 4, 112, 6, 16, 16, 8, 4, 3, 4, true, false, true, true, true, true, true);
    }

    public static MagicCircleSpec cinderMark() {
        return new MagicCircleSpec(9, 3, 96, 5, 9, 18, 9, 3, 2, 5, true, false, true, false, true, false, true);
    }

    public static MagicCircleSpec prismGuard() {
        return new MagicCircleSpec(6, 6, 112, 6, 12, 12, 6, 12, 3, 6, true, false, false, true, true, true, true);
    }

    public static MagicCircleSpec glacierWave() {
        return new MagicCircleSpec(8, 6, 128, 7, 18, 24, 12, 6, 3, 8, true, false, true, false, true, true, true);
    }

    public static MagicCircleSpec blackFlames() {
        return new MagicCircleSpec(9, 6, 144, 8, 18, 24, 9, 5, 4, 9, true, true, true, true, true, true, true);
    }

    public static MagicCircleSpec soulValley() {
        return new MagicCircleSpec(11, 7, 144, 8, 22, 28, 11, 7, 4, 11, true, true, true, true, true, true, true);
    }

    public MagicCircleSpec simplified(int detail) {
        if (detail >= 3) {
            return this;
        }
        if (detail == 2) {
            return new MagicCircleSpec(outerSides, middleSides, ringSegments, Math.min(layerCount, 4),
                    Math.max(4, spokeCount * 2 / 3), Math.max(6, glyphCount * 2 / 3), starPoints, 0,
                    Math.min(glyphBands, 2), Math.min(orbitingSealCount, 3),
                    circularLayers, false, zigzagRing, false, waveRing, false, diamondMarks);
        }
        if (detail == 1) {
            return new MagicCircleSpec(outerSides, middleSides, Math.min(ringSegments, 96), 3,
                    Math.max(3, spokeCount / 2), Math.max(4, glyphCount / 2), starPoints, 0,
                    1, 0,
                    circularLayers, false, false, false, false, false, false);
        }
        return new MagicCircleSpec(outerSides, 3, Math.min(ringSegments, 80), 1,
                0, Math.max(4, glyphCount / 3), 0, 0,
                1, 0,
                true, false, false, false, false, false, false);
    }
}
