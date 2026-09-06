package com.efkrdnz.magical.magic.visual;

/** Six colour slots derived from one base colour with the shared bright/hot/dim rules. */
public record Palette(int base, int bright, int hot, int dim, int ink, int accent) {

    public static Palette derive(int base) {
        return derive(base, base);
    }

    public static Palette derive(int base, int accent) {
        return new Palette(base, shift(base, 36, 28, 68), shift(base, 78, 66, 98), shift(base, -28, -40, -28), shift(base, -150, -160, -140), accent);
    }

    public int of(ColorRole role) {
        return switch (role) {
            case BASE -> base;
            case BRIGHT -> bright;
            case HOT -> hot;
            case DIM -> dim;
            case INK -> ink;
            case ACCENT -> accent;
        };
    }

    public static int shift(int color, int dr, int dg, int db) {
        int r = clamp(((color >> 16) & 0xFF) + dr);
        int g = clamp(((color >> 8) & 0xFF) + dg);
        int b = clamp((color & 0xFF) + db);
        return (r << 16) | (g << 8) | b;
    }

    public static int mix(int a, int b, float t) {
        float u = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round(((a >> 16) & 0xFF) * (1.0F - u) + ((b >> 16) & 0xFF) * u);
        int g = Math.round(((a >> 8) & 0xFF) * (1.0F - u) + ((b >> 8) & 0xFF) * u);
        int bl = Math.round((a & 0xFF) * (1.0F - u) + (b & 0xFF) * u);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    public static float luminance(int color) {
        return (0.2126F * ((color >> 16) & 0xFF) + 0.7152F * ((color >> 8) & 0xFF) + 0.0722F * (color & 0xFF)) / 255.0F;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
