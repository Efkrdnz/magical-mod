package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.MagicVisualContent;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The corner block has no plate behind anything, so whether it can be read is a property of its
 * colours and nothing else - and that is a number, not a taste. These are floors on contrast
 * over the worst background in the game, in WCAG's terms, rather than claims about how it looks.
 *
 * <p>A mark drawn over a dark shadow is legible on any background: where the world is bright the
 * shadow carries it, where the world is dark the mark does. The worst background is the one
 * where both carry equally, and there the contrast has a closed form, {@code sqrt((La + 0.05) /
 * (Lb + 0.05))} for a mark of luminance {@code La} over a shadow of {@code Lb}. Vanilla's own
 * text is the yardstick: a glyph must be at least as legible as the HUD's muted text is with the
 * shadow vanilla gives it.
 */
class HudLegibilityTest {

    /** Vanilla draws a text shadow at a quarter of the text's colour. */
    private static final float VANILLA_SHADOW = 0.25F;
    /** A bar's fill against its own track must clear this everywhere, which is WCAG's floor for graphics. */
    private static final double BAR_FLOOR = 3.0;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicVisualContent.init();
    }

    /** WCAG relative luminance of an sRGB colour. */
    static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double a, double b) {
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** The worst case, over every background, of the better of a mark and its shadow. */
    static double worstOverAnyBackground(double mark, double shadow) {
        return Math.sqrt(contrast(mark, shadow));
    }

    private static int scale(int rgb, float factor) {
        int r = Math.round((rgb >> 16 & 0xFF) * factor);
        int g = Math.round((rgb >> 8 & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    /** {@code top} painted over {@code under} at {@code alpha}. */
    private static int over(int top, float alpha, int under) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = (top >> shift & 0xFF) * alpha + (under >> shift & 0xFF) * (1.0F - alpha);
            out |= Math.round(c) << shift;
        }
        return out;
    }

    private static int mix(int rgb, float t) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = rgb >> shift & 0xFF;
            out |= Math.round(c * (1.0F - t) + 255 * t) << shift;
        }
        return out;
    }

    private static final MagicPassiveDefinition[] SINS = {
            MagicPassiveContent.SIN_PRIDE, MagicPassiveContent.SIN_GREED, MagicPassiveContent.SIN_LUST,
            MagicPassiveContent.SIN_ENVY, MagicPassiveContent.SIN_GLUTTONY, MagicPassiveContent.SIN_WRATH,
            MagicPassiveContent.SIN_SLOTH};

    /** Every hue a reading is written or stamped in: each school's, each sin's, a rested Sloth's. */
    private static List<Integer> readingInks() {
        List<Integer> inks = new ArrayList<>();
        for (MagicSchool school : MagicSchool.values()) {
            inks.add(HudPalette.owner(school));
        }
        for (MagicPassiveDefinition sin : SINS) {
            inks.add(HudPalette.ink(sin.color()));
        }
        inks.add(HudPalette.ink(HudPalette.lift(MagicPassiveContent.SIN_SLOTH.color(), 0.35F) & 0xFFFFFF));
        return inks;
    }

    private static double mutedTextFloor() {
        int muted = HudPalette.TEXT_MUTED;
        return worstOverAnyBackground(luminance(muted), luminance(scale(muted, VANILLA_SHADOW)));
    }

    @Test
    void everySkillsGlyphIsAtLeastAsLegibleAsTheHudsOwnMutedText() {
        double floor = mutedTextFloor();
        List<String> faint = new ArrayList<>();
        for (MagicSkillDefinition skill : MagicContent.allSkills()) {
            int ink = HudPalette.textTint(HudPalette.cardTint(VisualProfiles.of(skill.id())));
            double worst = worstOverAnyBackground(luminance(ink), 0.0);
            if (worst < floor) {
                faint.add(skill.id() + String.format(" %.2f", worst));
            }
        }
        assertTrue(faint.isEmpty(), "glyphs below the muted-text floor of " + String.format("%.2f", floor) + ": " + faint);
    }

    @Test
    void everyBarFillStandsOffItsTrackOnTheBrightestGround() {
        // The track is lightest where the world under it is white.
        int track = over(HudPalette.CHROME_INK, HudKind.BAR_TRACK_ALPHA, 0xFFFFFF);
        List<String> faint = new ArrayList<>();
        List<Integer> fills = new ArrayList<>(List.of(HudPalette.BARRIER, HudPalette.DANGER));
        List<String> names = new ArrayList<>(List.of("barrier", "danger"));
        for (MagicSchool school : MagicSchool.values()) {
            fills.add(HudPalette.textTint(HudPalette.mana(school).bright()));
            names.add("mana " + school);
        }
        for (int i = 0; i < fills.size(); i++) {
            double c = contrast(luminance(fills.get(i)), luminance(track));
            if (c < BAR_FLOOR) {
                faint.add(names.get(i) + String.format(" %.2f", c));
            }
        }
        assertTrue(faint.isEmpty(), "bar fills below " + BAR_FLOOR + ":1 against their track: " + faint);
    }

    /** Every word and number in the readouts, and the counts, reads as well as the HUD's own muted text. */
    @Test
    void everyReadingInkClearsTheMutedTextFloor() {
        double floor = mutedTextFloor();
        assertEquals(floor, HudPalette.TEXT_FLOOR, 1.0E-9, "the palette measures its floor differently");
        List<Integer> inks = new ArrayList<>(readingInks());
        inks.add(HudPalette.BARRIER);
        inks.add(HudPalette.TROUBLE);
        inks.add(HudPalette.TEXT_PRIMARY);
        List<String> faint = new ArrayList<>();
        for (int ink : inks) {
            double worst = worstOverAnyBackground(luminance(ink), luminance(scale(ink, VANILLA_SHADOW)));
            if (worst < floor) {
                faint.add(String.format("%06X %.2f", ink, worst));
            }
        }
        assertTrue(faint.isEmpty(), "reading inks below the muted-text floor of " + String.format("%.2f", floor) + ": " + faint);
    }

    /** The lift that makes a sin read is the smallest there is: four keep their colour exactly, and Wrath stays red. */
    @Test
    void theSinsKeepTheirHues() {
        for (MagicPassiveDefinition sin : new MagicPassiveDefinition[] {
                MagicPassiveContent.SIN_PRIDE, MagicPassiveContent.SIN_GREED, MagicPassiveContent.SIN_ENVY, MagicPassiveContent.SIN_GLUTTONY}) {
            assertEquals(sin.color() & 0xFFFFFF, HudPalette.ink(sin.color()), sin.id() + " was lifted although it already reads");
        }
        for (MagicPassiveDefinition sin : SINS) {
            int ink = HudPalette.ink(sin.color());
            boolean small = false;
            for (int step = 0; step <= 3; step++) {
                small |= ink == mix(sin.color() & 0xFFFFFF, step * 0.05F);
            }
            assertTrue(small, sin.id() + " was lifted further than three twentieths: " + String.format("%06X", ink));
        }
        int wrath = HudPalette.ink(MagicPassiveContent.SIN_WRATH.color());
        assertTrue((wrath >> 16 & 0xFF) - (wrath >> 8 & 0xFF) > 120, "Wrath is no longer red: " + String.format("%06X", wrath));
    }

    /** The trouble red is the blood accent lifted just enough: raw, it is the one colour that would not read. */
    @Test
    void troubleIsDangerLiftedJustEnough() {
        double floor = mutedTextFloor();
        assertTrue(worstOverAnyBackground(luminance(HudPalette.DANGER), luminance(scale(HudPalette.DANGER, VANILLA_SHADOW))) < floor,
                "DANGER reads on its own; TROUBLE need not exist");
        assertTrue(worstOverAnyBackground(luminance(HudPalette.TROUBLE), luminance(scale(HudPalette.TROUBLE, VANILLA_SHADOW))) >= floor);
        boolean smallest = false;
        for (int step = 1; step <= 4; step++) {
            int lifted = mix(HudPalette.DANGER, step * 0.05F);
            if (worstOverAnyBackground(luminance(lifted), luminance(scale(lifted, VANILLA_SHADOW))) >= floor) {
                smallest = lifted == HudPalette.TROUBLE;
                break;
            }
        }
        assertTrue(smallest, "TROUBLE is not the smallest lift of DANGER that reads");
    }

    /** A stamp is drawn whole in its reading's hue over a hard black shadow, and reads over any ground. */
    @Test
    void everyStampReadsOverItsShadow() {
        double floor = mutedTextFloor();
        List<String> faint = new ArrayList<>();
        for (int ink : readingInks()) {
            double worst = worstOverAnyBackground(luminance(ink), 0.0);
            if (worst < floor) {
                faint.add(String.format("%06X %.2f", ink, worst));
            }
        }
        assertTrue(faint.isEmpty(), "stamps below the muted-text floor: " + faint);
    }

    /** A cooling glyph is greyed out, not faded out: its grey rows read over their shadow on any ground, a night sky included. */
    @Test
    void aCoolingGlyphStillReadsAtNight() {
        int grey = Math.round(HudKind.SLOT_GHOST_GREY * 255.0F);
        int ghost = grey << 16 | grey << 8 | grey;
        assertTrue(worstOverAnyBackground(luminance(ghost), 0.0) >= mutedTextFloor(),
                String.format("the cooling grey reads at %.2f", worstOverAnyBackground(luminance(ghost), 0.0)));
    }

    /** No reading wears another's mark. */
    @Test
    void everyReadingWearsAMarkOfItsOwn() {
        Set<Integer> seen = new HashSet<>();
        for (int cell : HudState.readoutCells()) {
            assertTrue(cell >= 0 && cell < 256, "cell " + cell + " is off the atlas");
            assertTrue(seen.add(cell), "two readings share atlas cell " + cell);
        }
    }

    @Test
    void theTrackTheShaderPaintsIsThePalettesInk() throws IOException {
        String fragment;
        try (InputStream in = HudLegibilityTest.class.getResourceAsStream("/assets/magical/shaders/core/rendertype_hud_sigil.fsh")) {
            assertNotNull(in, "missing the sigil shader");
            fragment = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("const vec3 TRACK_RGB = vec3\\(([0-9.]+), ([0-9.]+), ([0-9.]+)\\);").matcher(fragment);
        assertTrue(m.find(), "TRACK_RGB is not declared in the shader");
        int ink = HudPalette.CHROME_INK;
        int[] channels = {ink >> 16 & 0xFF, ink >> 8 & 0xFF, ink & 0xFF};
        for (int i = 0; i < 3; i++) {
            assertEquals(channels[i] / 255.0, Double.parseDouble(m.group(i + 1)), 1.0 / 255.0, "TRACK_RGB channel " + i + " is not CHROME_INK");
        }
    }
}
