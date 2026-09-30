package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.forge.ForgeElementKind;

/**
 * Where on the smear atlas a forged blade is painted from: the contract between
 * {@code scripts/forge-sprites.py}, which draws {@code textures/effect/forge_smear.png}, and the
 * geometry that samples it.
 *
 * <p>The atlas is sixteen rows of sixteen texels, one row per way a blade can look. u runs along the
 * arc and tiles; v runs across the blade, the inner side at the top of its row and the cutting lip
 * two texels from the bottom, with the bottom line of every row left clear so a blade ends on an
 * edge and never on the next row's first line.
 *
 * <p>Pure: no clock, no noise, no Minecraft. A strike moves because its geometry moves and its
 * texture is anchored to the blade's head, not because anything scrolls.
 */
public final class ForgeSmear {

    /** One row of the atlas, in the order the generator lays them down. */
    public enum Row { STEEL, EMBER, RIME, FILAMENT, TATTER, RUN, GRIT, GUST, SHEATH, LIP }

    public static final int ATLAS_WIDTH = 128;
    public static final int ATLAS_HEIGHT = 256;
    public static final int ROW_HEIGHT = 16;

    /**
     * How many blocks of blade one width of the atlas covers: 128 texels at 16 a block, which is
     * vanilla's own texel density, so a smear reads like a vanilla sprite rather than a filter.
     */
    public static final float TILE_BLOCKS = 8.0f;

    /**
     * What the owner's own first-person view draws its strike at. Everything a swing draws starts
     * at the wielder's eye, and at full strength the smear of a wide cut covers the thing being cut.
     */
    public static final float OWN_VIEW_OPACITY = 0.7f;

    /** The tail of an opening arc against its head, and how far along the head reaches full. */
    private static final float TAIL = 0.15f;
    private static final float HEAD_FROM = 0.8f;
    /** How much of a lance, from the hilt, is left clear. */
    private static final float LANCE_CLEAR = 0.25f;
    /** Where across a row the lip sits: the centre of the second texel from the bottom. */
    public static final float LIP = 1.0f;
    /** A sweep this wide is a closed ring and has no head. */
    private static final float CLOSED = 359.9f;

    private ForgeSmear() {}

    /** The row a blade of this element is painted with. */
    public static Row of(ForgeElementKind kind) {
        return switch (kind) {
            case FIRE, EXPLOSION, BLACK_FLAME, MAGMA -> Row.EMBER;
            case FROST, HAILSTORM, RIME_GALE, CLOT -> Row.RIME;
            case STORM, PLASMA -> Row.FILAMENT;
            case VOID, DARK, ECLIPSE, CORRUPTION -> Row.TATTER;
            case VENOM, BLOOD, BLIGHT -> Row.RUN;
            case TERRA, VERDIGRIS -> Row.GRIT;
            case GALE -> Row.GUST;
            case RADIANT, MARTYR -> Row.STEEL;
        };
    }

    /**
     * Whether a blade of this element gives off light, and so gets its hot lip drawn a second time
     * on the additive glint pass. Everything else is matter only: a void cut or a venom one is a
     * dark or wet thing, and lighting it made every element the same glowing ribbon.
     */
    public static boolean glints(ForgeElementKind kind) {
        return switch (kind) {
            case FIRE, EXPLOSION, MAGMA, STORM, PLASMA, RADIANT, MARTYR, FROST, HAILSTORM, RIME_GALE -> true;
            default -> false;
        };
    }

    /**
     * The v of a point {@code across} a blade in {@code row}: 0 on the inner side, 1 on the lip.
     *
     * <p>Texel centres from the first to the fifteenth line of the row, never the sixteenth: with
     * nearest filtering a v on a texel centre cannot sample the row below it.
     */
    public static float v(Row row, float across) {
        float a = Math.max(0.0f, Math.min(1.0f, across));
        return (row.ordinal() * ROW_HEIGHT + 0.5f + a * (ROW_HEIGHT - 2)) / ATLAS_HEIGHT;
    }

    /**
     * The u of a point {@code t} along an arc {@code length} blocks long, anchored to the head at
     * {@code t = 1}. As the arc opens its head moves and the texture moves with it, so the streaks
     * travel with the blade instead of sliding under it.
     */
    public static float head(float t, float length, float offset) {
        return offset - (1.0f - t) * length / TILE_BLOCKS;
    }

    /**
     * The u of a point {@code t} round a closed ring {@code circumference} blocks round: a whole
     * number of tiles, so the ring meets itself without a seam.
     */
    public static float ring(float t, float circumference) {
        return t * ringRepeats(circumference);
    }

    public static int ringRepeats(float circumference) {
        return Math.max(1, Math.round(circumference / TILE_BLOCKS));
    }

    public static boolean closed(float spanDegrees) {
        return Math.abs(spanDegrees) >= CLOSED;
    }

    /** How opaque an opening arc is at {@code t}: faint at its tail, full from most of the way in. */
    public static float headRamp(float t) {
        float x = Math.max(0.0f, Math.min(1.0f, t / HEAD_FROM));
        return TAIL + (1.0f - TAIL) * x * x * (3.0f - 2.0f * x);
    }

    /**
     * How opaque a lance is at {@code t} from hilt to point: nothing at the hilt, full from most of
     * the way out. A thrust is drawn from the wielder's own eye, so any of its hilt that shows is a
     * slab across the bottom of their view; what they should see is the point going away from them.
     */
    public static float lanceRamp(float t) {
        float x = Math.max(0.0f, Math.min(1.0f, (t - LANCE_CLEAR) / (HEAD_FROM - LANCE_CLEAR)));
        return x * x * (3.0f - 2.0f * x);
    }

    /** A strike's own place on the tile, so two strikes side by side do not wear the same streaks. */
    public static float offset(int seed) {
        return ForgeSparks.unit(seed, 0, 7);
    }
}
