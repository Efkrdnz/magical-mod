package com.efkrdnz.magical.magic.skill.sword;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.CircleLayer;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.MagicVisualContent;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What a SWORD cast circle is allowed to put on the screen, measured against the ground it is
 * put on.
 *
 * <p>This exists because the Loose circle shipped with a member that painted <em>opaque black</em>
 * over the terrain, an iron golem and the daylight sky, and every test in the project was green.
 * They were green because nothing in the suite ever asked what colour reaches the framebuffer -
 * the profile was checked for uniqueness, for structure and for bounds, all of which it passed
 * while drawing the one thing no other effect in the mod draws.
 *
 * <p>Both rules below are <b>floors</b>, in the sense the subspace wall taught: a bound on the
 * worst case a player can actually be shown, not a bound on how subtle a thing is allowed to be.
 * A ceiling ("the ring must not be too loud") would have passed the shipped circle too.
 *
 * <p>Pure: a {@link VisualProfile} is plain data by design - that is what makes it safe on a
 * dedicated server - so what the painter will be handed can be read straight off it and the
 * blend arithmetic redone here in integers.
 */
class SwordCircleContrastTest {

    /**
     * The two ends of the range this mod is judged over. Noon sand is the reference bright ground
     * used elsewhere in this codebase (the loadout switcher's contrast numbers are measured on it);
     * the night value is only the other end of the bracket, and its exact number does not matter,
     * because the rule being pinned is "never subtract" rather than "reach some ratio". Having
     * both ends present is what stops the rule from being an artefact of one background.
     */
    private static final int[] NOON_SAND = {220, 209, 165};

    private static final int[] MIDNIGHT = {12, 12, 18};

    /**
     * {@code rendertype_glyph_ink.fsh}, the {@code mode == 1} branch: {@code vec3 body = tint *
     * 0.08;}, emitted through {@code straightOut} onto {@code MagicalFxRenderTypes.glyphVoid()},
     * whose {@code TRANSLUCENT_TRANSPARENCY} shard is {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}. At
     * full coverage that replaces the background with 8% of the palette's ink slot - which for a
     * school whose ink is already the darkest thing it owns is black, whatever the hue was.
     *
     * <p>{@code GlyphCirclePainter} picks that pass off the role alone: {@code boolean dark =
     * layer.role() == ColorRole.INK}. So INK is not a colour in this engine, it is a render mode,
     * and choosing it is choosing to paint over the world.
     */
    private static final float INK_BODY = 0.08F;

    /**
     * How far a facet may lean off the tangent before it stops being a facet.
     *
     * <p>{@code FACET_BAND} draws {@code |lu| + 0.75|c| = 0.85} per cell, where {@code lu} is
     * +/-1 across one cell of the band's circumference and {@code c} is +/-1 across its radial
     * thickness. Following that line from the middle of the band ({@code |c| = 0}) to its edge
     * ({@code |c| = 1}) moves {@code |lu|} by 0.75, so one facet's tangential run against its
     * radial rise is
     *
     * <pre>  run/rise = 1.5 * PI * rMid / (count * (r1 - r0))</pre>
     *
     * <p>which is scale-free - it is the same at any circle radius, because every term is a
     * fraction of it. At 6 facets on the band the builder hands a second layer (0.62..0.71) that
     * comes to 5.8:1, ten degrees off the tangent: the six diamonds stretch until they fuse at
     * their tips into two continuous strands and the band reads as two smooth overlapping
     * circles. That is exactly the picture that was complained about. 3:1 is eighteen degrees,
     * which still reads as a chevron; the lower bound is there because facets shorter than they
     * are tall close up into one solid line, which is a SOLID_RING with extra vertices.
     */
    private static final double MAX_FACET_RUN_PER_RISE = 3.0D;

    private static final double MIN_FACET_RUN_PER_RISE = 0.75D;

    /** Rounding to 8-bit and back through a float multiply; nothing here is near the boundary. */
    private static final double EPSILON = 1.0E-6D;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicVisualContent.init();
    }

    @Test
    void noMemberOfASwordCircleMayPaintDarkerThanTheGroundItSitsOn() {
        List<String> offenders = new ArrayList<>();
        int measured = 0;
        for (VisualProfile profile : swordProfiles()) {
            for (CircleLayer layer : profile.castCircle().layers()) {
                for (int[] background : new int[][] {NOON_SAND, MIDNIGHT}) {
                    int[] painted = darkestOver(background, layer, profile.palette());
                    measured++;
                    if (luminance(painted) < luminance(background) - EPSILON) {
                        offenders.add(profile.skillId() + " " + layer.kind() + ":" + layer.count()
                                + " as " + layer.role() + " paints " + rgb(painted)
                                + " over " + rgb(background));
                    }
                }
            }
        }
        assertTrue(measured > 0, "no SWORD circle layers were measured; the rule became a no-op");
        assertTrue(offenders.isEmpty(), () -> "a SWORD circle darkens the world:\n" + String.join("\n", offenders));
    }

    @Test
    void everyFacetOnASwordCircleReadsAsAFacet() {
        List<String> offenders = new ArrayList<>();
        int measured = 0;
        for (VisualProfile profile : swordProfiles()) {
            for (CircleLayer layer : profile.castCircle().layers()) {
                if (layer.kind() != GlyphKind.FACET_BAND) {
                    continue;
                }
                measured++;
                double ratio = facetRunPerRise(layer);
                if (ratio > MAX_FACET_RUN_PER_RISE) {
                    offenders.add(profile.skillId() + " draws " + layer.count() + " facets at "
                            + round(ratio) + ":1 - too shallow to be facets; they fuse into strands");
                } else if (ratio < MIN_FACET_RUN_PER_RISE) {
                    offenders.add(profile.skillId() + " draws " + layer.count() + " facets at "
                            + round(ratio) + ":1 - too steep to be facets; they close into a line");
                }
            }
        }
        assertTrue(measured > 0,
                "no SWORD circle bands with FACET_BAND any more. Delete this test rather than leave it green.");
        assertTrue(offenders.isEmpty(), () -> String.join("\n", offenders));
    }

    // ---- the arithmetic the painter and the shader will do -----------------------------------

    /**
     * The darkest pixel {@code GlyphCirclePainter} can put on {@code background} for this layer.
     *
     * <p>Two branches, because the painter has two. A non-INK layer goes to
     * {@code MagicalFxRenderTypes.glyphInk()}, whose {@code ADDITIVE_TRANSPARENCY} shard is
     * {@code blendFunc(ONE, ONE)} over {@code additiveOut}'s {@code col * glow * opacity}, and
     * every term of that is non-negative - such a layer can only ever add light, so its worst
     * case is the background untouched and there is nothing to compute. An INK layer replaces the
     * background, so its worst case is the whole of what it writes.
     */
    private static int[] darkestOver(int[] background, CircleLayer layer, Palette palette) {
        if (layer.role() != ColorRole.INK) {
            return background;
        }
        int tint = palette.of(ColorRole.INK);
        return new int[] {
            Math.round(((tint >> 16) & 0xFF) * INK_BODY),
            Math.round(((tint >> 8) & 0xFF) * INK_BODY),
            Math.round((tint & 0xFF) * INK_BODY)
        };
    }

    private static double facetRunPerRise(CircleLayer layer) {
        double mid = (layer.r0() + layer.r1()) / 2.0D;
        double thickness = layer.r1() - layer.r0();
        return 1.5D * Math.PI * mid / (Math.max(1, layer.count()) * thickness);
    }

    private static List<VisualProfile> swordProfiles() {
        List<VisualProfile> out = new ArrayList<>();
        for (VisualProfile profile : VisualProfiles.all().values()) {
            if (profile.material() == SchoolMaterial.SWORD) {
                out.add(profile);
            }
        }
        return out;
    }

    private static double luminance(int[] c) {
        return 0.2126D * c[0] + 0.7152D * c[1] + 0.0722D * c[2];
    }

    private static String rgb(int[] c) {
        return "(" + c[0] + "," + c[1] + "," + c[2] + ")";
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 100.0D) / 100.0D);
    }
}
