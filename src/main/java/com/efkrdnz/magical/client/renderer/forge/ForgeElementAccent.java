package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.forge.ForgeElementKind;

import org.joml.Matrix4f;

/**
 * What makes a FROST cut read as frost and not merely blue. The palette carries the hue; this
 * carries the rest: which row of the smear atlas the blade is painted from, whether its lip gives
 * off light, how opaque the body is, which way its wake runs, and the one or two elements whose
 * silhouette is itself different.
 *
 * <p>Frost's shards, storm's zigzag, venom's drips and earth's broken slabs used to be geometry
 * laid over the edge. They are texture now - the rime, filament, run and grit rows - and the
 * matter the element throws is real particles, so what is left here is only shape: a second blade
 * for gale, a wide soft one for the radiant family.
 */
public final class ForgeElementAccent {

    /** A second silhouette laid over a finished blade, for the elements whose shape differs. */
    public enum Ornament { NONE, BLOOM, TWIN }

    /**
     * How an element bends a strike: body opacity, wake direction, the second silhouette, the atlas
     * row it is painted from and whether its lip glints.
     */
    public record Accent(float bodyAlpha, boolean invertTrail, Ornament ornament, ForgeSmear.Row row,
            boolean glint) {}

    private static final float TWIN_SHIFT = 9.0f;
    private static final float TWIN_SCALE = 1.12f;
    private static final float TWIN_ALPHA = 0.45f;
    private static final float BLOOM_SPREAD = 2.2f;
    private static final float BLOOM_ALPHA = 0.35f;

    private ForgeElementAccent() {}

    /** The single element table. Everything else in this class reads from what it returns. */
    public static Accent of(ForgeElementKind kind) {
        return switch (kind) {
            case FIRE -> accent(kind, 1.0f, false, Ornament.NONE);
            case FROST -> accent(kind, 1.0f, false, Ornament.NONE);
            case STORM -> accent(kind, 1.0f, false, Ornament.NONE);
            case VOID -> accent(kind, 0.75f, true, Ornament.NONE);
            case RADIANT -> accent(kind, 1.0f, false, Ornament.BLOOM);
            case VENOM -> accent(kind, 1.0f, false, Ornament.NONE);
            case TERRA -> accent(kind, 1.0f, false, Ornament.NONE);
            case GALE -> accent(kind, 1.0f, false, Ornament.TWIN);
            // Darker than void, and broken rather than whole: the wake comes apart behind the blow.
            case DARK -> accent(kind, 0.70f, true, Ornament.NONE);
            case BLOOD -> accent(kind, 1.0f, false, Ornament.NONE);
            // Compounds read as the parent they most look like.
            case BLACK_FLAME -> accent(kind, 0.75f, true, Ornament.NONE);
            case EXPLOSION -> accent(kind, 1.0f, false, Ornament.BLOOM);
            case RIME_GALE -> accent(kind, 1.0f, false, Ornament.TWIN);
            case PLASMA -> accent(kind, 1.0f, false, Ornament.NONE);
            case MAGMA -> accent(kind, 1.0f, false, Ornament.NONE);
            case HAILSTORM -> accent(kind, 1.0f, false, Ornament.NONE);
            case ECLIPSE -> accent(kind, 0.8f, true, Ornament.BLOOM);
            case BLIGHT -> accent(kind, 0.9f, false, Ornament.NONE);
            case VERDIGRIS -> accent(kind, 1.0f, false, Ornament.NONE);
            case CORRUPTION -> accent(kind, 0.8f, true, Ornament.NONE);
            case MARTYR -> accent(kind, 1.0f, false, Ornament.BLOOM);
            case CLOT -> accent(kind, 0.9f, false, Ornament.NONE);
        };
    }

    private static Accent accent(ForgeElementKind kind, float bodyAlpha, boolean invertTrail, Ornament ornament) {
        return new Accent(bodyAlpha, invertTrail, ornament, ForgeSmear.of(kind), ForgeSmear.glints(kind));
    }

    /** Lays the element's second silhouette over a blade that has already been drawn. */
    public static void draw(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha,
            Accent accent) {
        if (alpha <= 0.0f) {
            return;
        }
        switch (accent.ornament()) {
            case BLOOM -> bloom(stroke, pose, sweep, palette, alpha);
            case TWIN -> ForgeRibbon.arc(stroke.dull(), pose, sweep.scaled(TWIN_SCALE).shifted(TWIN_SHIFT), palette,
                    alpha * TWIN_ALPHA);
            case NONE -> { }
        }
    }

    /** RADIANT: a wide soft blade sitting behind the edge, so the cut trails light. */
    private static void bloom(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        Sweep wide = new Sweep(sweep.plane(), sweep.radius(), sweep.thickness() * BLOOM_SPREAD,
                sweep.fromDegrees(), sweep.toDegrees());
        ForgeRibbon.arc(stroke.dull(), pose, wide,
                new ForgePalette(palette.bloom(), palette.secondary(), palette.bloom()), alpha * BLOOM_ALPHA);
    }
}
