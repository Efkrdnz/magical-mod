package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.FusionGeometry;

import net.minecraft.util.Mth;

/**
 * The three colours a forged strike is drawn in, taken straight from the entity's synced element
 * colours. Nothing here reads the element kind: the palette is only about hue, and the shape
 * differences between elements live in {@link ForgeElementAccent}.
 */
public record ForgePalette(int primary, int secondary, int edge) {

    private static final int WHITE = 0xFFFFFF;

    /** Normalised components, for the places that have to blend two colours rather than pick one. */
    public float[] rgb(int color) {
        return new float[] {
                FusionGeometry.red(color) / 255.0f,
                FusionGeometry.green(color) / 255.0f,
                FusionGeometry.blue(color) / 255.0f};
    }

    /** A packed colour {@code t} of the way from {@code from} to {@code to}. */
    public int mix(int from, int to, float t) {
        float[] a = rgb(from);
        float[] b = rgb(to);
        float f = Mth.clamp(t, 0.0f, 1.0f);
        return pack(Mth.lerp(f, a[0], b[0]), Mth.lerp(f, a[1], b[1]), Mth.lerp(f, a[2], b[2]));
    }

    /** The edge colour pushed toward white: the bloom and glint accents sit on top of the edge. */
    public int bloom() {
        return mix(edge, WHITE, 0.55f);
    }

    /** The primary colour turned inside out, for VOID's collapsing core. */
    public int inverted() {
        float[] c = rgb(primary);
        return pack(1.0f - c[0], 1.0f - c[1], 1.0f - c[2]);
    }

    private static int pack(float r, float g, float b) {
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float value) {
        return Mth.clamp(Math.round(value * 255.0f), 0, 255);
    }
}
