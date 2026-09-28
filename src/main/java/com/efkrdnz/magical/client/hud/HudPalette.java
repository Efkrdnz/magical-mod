package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.SpaceRuleChange;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.VisualProfile;

/**
 * The only class allowed to hold a HUD colour.
 *
 * <p>Almost nothing here is a literal. School colours come from {@link SchoolMaterial} through
 * {@link Palette#derive}, the same rules the spell circles use, so a fire mage's sigil is the fire
 * circle's orange and not a second orange chosen by hand. Text comes from {@link MagicalGuiStyle},
 * so the HUD and the codex are one surface; the chrome is the codex panel's lit edge and its
 * plate mid-tone. The two literals that remain are the two inks, because the ink register needs a
 * body colour no school owns.
 */
public final class HudPalette {
    /** The lit register's plate body, and the dark track under every corner-block bar. */
    public static final int CHROME_INK = 0x070A12;
    /** The ink register's body: violet-black, so the reflection reads as something else. */
    public static final int FORBIDDEN_INK = 0x0B0612;
    /** The lit top edge of the codex screens; the HUD's chrome rim is the same line. */
    public static final int CHROME_RIM = 0x31435F;
    /** The mid-tone of the codex panel gradient; the HUD's plates are this colour. */
    public static final int PLATE = 0x141C2E;
    public static final int TEXT_PRIMARY = MagicalGuiStyle.TEXT_PRIMARY;
    public static final int TEXT_MUTED = MagicalGuiStyle.TEXT_MUTED;
    public static final int DANGER = MagicalGuiStyle.ACCENT_BLOOD & 0xFFFFFF;
    public static final int VIOLET = MagicalGuiStyle.ACCENT_VIOLET & 0xFFFFFF;
    /** Fixed on purpose: the barrier has to read the same whatever school tints the mana. */
    public static final int BARRIER = 0xC8F0F4;

    public static final int ALPHA_PLATE = 0xD2;

    /** Text on a plate must clear this luminance gap or it is lifted toward white. */
    private static final float MIN_TEXT_LUMINANCE = 0.5F;
    /** A card tint darker than this is unreadable at sixteen pixels; the next palette slot is tried. */
    private static final float MIN_CARD_LUMINANCE = 0.28F;

    private HudPalette() {}

    /** The mana bar's palette for a school: the bar fills in its {@code bright}. */
    public static Palette mana(MagicSchool school) {
        SchoolMaterial material = SchoolMaterial.of(school);
        return Palette.derive(material.variantColor(0), material.variantColor(1));
    }

    /**
     * A card's tint: the first of the profile's bright, hot and the material's second variant that
     * is light enough to read. Never {@code definition.color()} - Black Flames is nearly black.
     */
    public static int cardTint(VisualProfile profile) {
        int[] candidates = {profile.palette().bright(), profile.palette().hot(), profile.material().variantColor(1)};
        for (int candidate : candidates) {
            if (Palette.luminance(candidate) >= MIN_CARD_LUMINANCE) {
                return candidate;
            }
        }
        return lift(profile.palette().bright(), 0.5F) & 0xFFFFFF;
    }

    /** Control statuses are violet, silence-like ones muted, the two named ones their school's. */
    public static int status(MagicStatus status) {
        return switch (status) {
            case FACING_PINNED, PUPPETED, ROOTED, REACH_CLAMPED, IMMOVABLE, TAUNTED -> VIOLET;
            case EXILED -> SchoolMaterial.SPATIAL.variantColor(0);
            case DAZZLED -> SchoolMaterial.LIGHT.variantColor(1);
            case BRANDED, INFECTED, GAZE -> DANGER;
            default -> TEXT_MUTED;
        };
    }

    /**
     * The rule flash's tint for a kind of change: warm for more, cool for less, blood for gone,
     * violet for reversed, steel for pinned, hot for a surge, green for a heading, and the
     * Authority of Space's own light when a rule is cleared.
     */
    public static int change(SpaceRuleChange change) {
        return switch (change) {
            case RAISE -> 0xFFC76B;
            case LOWER -> 0x5EC8FF;
            case ZERO -> 0xFF5D6C;
            case FLIP -> 0xC77DFF;
            case LOCK -> 0x9FB6D9;
            case SURGE -> 0xFF8A5B;
            case AIM -> 0x7CFFB2;
            case RESTORE -> AuthorityContent.AUTHORITY_OF_SPACE.color() & 0xFFFFFF;
        };
    }

    /** Text colour: the colour itself if it is light enough on a plate, otherwise lifted. */
    public static int textTint(int rgb) {
        return Palette.luminance(rgb) < MIN_TEXT_LUMINANCE ? Palette.mix(rgb, 0xFFFFFF, 0.45F) : rgb;
    }

    // ---- the readouts' colours ------------------------------------------------------------------

    /** Vanilla draws a text shadow at a quarter of the text's colour. */
    private static final float VANILLA_SHADOW = 0.25F;
    /** How far {@link #ink} lifts toward white per step; the smallest step that still reads is taken. */
    private static final float INK_STEP = 0.05F;
    /**
     * What the HUD's own muted text reads at over the worst background there is, with vanilla's
     * shadow under it. Every colour the corner block writes in clears this; see
     * {@code HudLegibilityTest}.
     */
    public static final double TEXT_FLOOR = worstOverAnyBackground(TEXT_MUTED, scale(TEXT_MUTED, VANILLA_SHADOW));

    /**
     * The trouble red: the blood accent lifted just far enough to read as well as muted text. Raw
     * {@link #DANGER} is a shade under, and a warning that is the faintest thing on screen is not a
     * warning. Only the part in trouble wears it - a number, a preset's name.
     */
    public static final int TROUBLE = ink(DANGER);

    /**
     * A colour lifted toward white in twentieths until, with vanilla's shadow under it, it reads
     * at least as well as the HUD's muted text over any background. It is the smallest lift that
     * does, so the hue survives: Wrath stays red, where {@link #textTint} would make it salmon.
     */
    public static int ink(int rgb) {
        for (int step = 0; step <= 20; step++) {
            int candidate = Palette.mix(rgb, 0xFFFFFF, step * INK_STEP);
            if (worstOverAnyBackground(candidate, scale(candidate, VANILLA_SHADOW)) >= TEXT_FLOOR) {
                return candidate;
            }
        }
        return 0xFFFFFF;
    }

    /**
     * Whose a resource reading is: the colour its school's mana bar fills with, as text, lifted
     * the last step {@link #ink} asks where it still falls short. A Vessel is blood's rose,
     * Corruption dark's lavender, Notice eldritch's teal.
     */
    public static int owner(MagicSchool school) {
        return ink(textTint(mana(school).bright()));
    }

    private static int scale(int rgb, float factor) {
        int r = Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * How well a mark over its shadow reads over the worst background there is: where the world is
     * bright the shadow carries it, where it is dark the mark does, and the worst is where the two
     * carry equally - the square root of the mark's contrast with its shadow.
     */
    private static double worstOverAnyBackground(int mark, int shadow) {
        return Math.sqrt(Palette.contrast(mark, shadow));
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Mixes a colour toward white, keeping its alpha. */
    public static int lift(int color, float amount) {
        int out = color & 0xFF000000;
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = (color >> shift) & 0xFF;
            out |= Math.round(channel + (255 - channel) * t) << shift;
        }
        return out;
    }
}
