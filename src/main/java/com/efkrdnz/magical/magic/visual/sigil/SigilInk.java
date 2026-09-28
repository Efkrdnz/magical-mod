package com.efkrdnz.magical.magic.visual.sigil;

import com.efkrdnz.magical.magic.visual.Palette;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Two colours for a sigil: the core colours its strokes, the glow the one-texel halo round them.
 *
 * <p><b>Readability is a number.</b> A mark outlined in a second colour reads over any background
 * at least as well as the square root of the contrast between its two colours: wherever the
 * background defeats one layer the other carries it, and the worst ground is the one that sits
 * exactly between them (the argument {@code HudPalette} makes for a glyph over its shadow). So
 * {@link #readability()} is that square root and every ink here holds {@link #READABLE}. It is
 * what failed for a white core in a pale cyan halo (1.23), which vanished into a daylight sky, and
 * for gold with an orange halo (1.31), which read on a blue sky only by hue and would have vanished
 * on noon sand; the presets were tuned to it by deepening the halo, never by darkening the core.
 */
public record SigilInk(int core, int glow) {

    /** The least {@link #readability()} any ink may have: a core-to-glow contrast of 3.24:1. */
    public static final double READABLE = 1.8D;

    /** How far toward white a derived ink's core is mixed from the colour it was given. */
    private static final float DERIVED_CORE_LIFT = 0.85F;

    /** How far toward black each step deepens a derived glow that does not yet read. */
    private static final float DERIVED_GLOW_STEP = 0.05F;

    private static final Map<String, SigilInk> NAMED = new LinkedHashMap<>();

    public static final SigilInk VIOLET = named("violet", 0xF4E8FF, 0x985AFA);
    public static final SigilInk GOLD = named("gold", 0xFFF6D0, 0xB86E00);
    public static final SigilInk EMBER = named("ember", 0xFFEFA8, 0xE64717);
    public static final SigilInk CRIMSON = named("crimson", 0xFFD9DC, 0xE0213B);
    public static final SigilInk ROSE = named("rose", 0xFFE4F1, 0xD64286);
    public static final SigilInk AZURE = named("azure", 0xE4F4FF, 0x2C7EF0);
    public static final SigilInk FROST = named("frost", 0xFFFFFF, 0x1E88C8);
    public static final SigilInk TEAL = named("teal", 0xD9FFF4, 0x0F947B);
    public static final SigilInk VERDANT = named("verdant", 0xEAFFDB, 0x2C993A);
    public static final SigilInk LIME = named("lime", 0xFBFFD2, 0x669000);
    public static final SigilInk UMBRA = named("umbra", 0xFFDFFF, 0xC42ADB);
    /** A pale core in a near-black halo: the halo carries it over daylight, the core at night. */
    public static final SigilInk SHADOW = named("shadow", 0xE6DDF2, 0x3B2156);
    public static final SigilInk STEEL = named("steel", 0xFFFFFF, 0x7E8C9C);
    public static final SigilInk BONE = named("bone", 0xFFFBEA, 0x90856D);
    /** The inverse of the rest: a dark core in a pale halo. */
    public static final SigilInk NIGHT = named("night", 0x2A1840, 0xE2D6FF);

    public SigilInk {
        core &= 0xFFFFFF;
        glow &= 0xFFFFFF;
    }

    private static SigilInk named(String name, int core, int glow) {
        SigilInk ink = new SigilInk(core, glow);
        NAMED.put(name, ink);
        return ink;
    }

    /** The fifteen presets by name, in the order they are declared. */
    public static Map<String, SigilInk> named() {
        return Collections.unmodifiableMap(NAMED);
    }

    public static Optional<SigilInk> byName(String name) {
        return Optional.ofNullable(NAMED.get(name));
    }

    /**
     * An ink from any one colour - a passive's own, a profile's base. The core is the colour mixed
     * most of the way to white; the glow is the colour itself, deepened toward black a step at a
     * time until the pair reads. The core is always light, so black always gets there.
     */
    public static SigilInk from(int rgb) {
        int core = Palette.mix(rgb, 0xFFFFFF, DERIVED_CORE_LIFT);
        int glow = rgb & 0xFFFFFF;
        float deepened = 0.0F;
        while (Math.sqrt(Palette.contrast(core, glow)) < READABLE && deepened < 1.0F) {
            deepened = Math.min(1.0F, deepened + DERIVED_GLOW_STEP);
            glow = Palette.mix(rgb, 0x000000, deepened);
        }
        return new SigilInk(core, glow);
    }

    /** How well the mark reads over the worst background there is; see the class note. */
    public double readability() {
        return Math.sqrt(Palette.contrast(core, glow));
    }
}
