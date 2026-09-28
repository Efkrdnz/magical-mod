package com.efkrdnz.magical.magic.visual.sigil;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The symbols a sigil particle can wear. Declaration order is texture order:
 * {@code particles/sigil.json} lists every core in this order and then every glow, and
 * {@code SigilSpritesTest} fails the moment the two disagree. Add a symbol at the end of its
 * family's run, draw it in {@code scripts/particle-sprites.py} and rerun the script.
 *
 * <p>Where the HUD already has a stamp for an idea - the sins, the statuses - the sigil of the
 * same name is the same idea, so a passive can wear one vocabulary on the HUD and in the world.
 */
public enum Sigil {
    RUNE_0(Family.RUNE),
    RUNE_1(Family.RUNE),
    RUNE_2(Family.RUNE),
    RUNE_3(Family.RUNE),
    RUNE_4(Family.RUNE),
    RUNE_5(Family.RUNE),
    RUNE_6(Family.RUNE),
    RUNE_7(Family.RUNE),
    FLAME(Family.ELEMENT),
    DROP(Family.ELEMENT),
    SNOWFLAKE(Family.ELEMENT),
    LEAF(Family.ELEMENT),
    WAVE(Family.ELEMENT),
    SUN(Family.ELEMENT),
    MOON(Family.ELEMENT),
    STAR(Family.ELEMENT),
    SPROUT(Family.ELEMENT),
    EYE(Family.CREATURE),
    SKULL(Family.CREATURE),
    BONE(Family.CREATURE),
    FANG(Family.CREATURE),
    HEART(Family.CREATURE),
    PAW(Family.CREATURE),
    FEATHER(Family.CREATURE),
    KEY(Family.OBJECT),
    HOURGLASS(Family.OBJECT),
    CROWN(Family.OBJECT),
    SHIELD(Family.OBJECT),
    COIN(Family.OBJECT),
    SWORD(Family.OBJECT),
    HAMMER(Family.OBJECT),
    FLASK(Family.OBJECT),
    ANCHOR(Family.OBJECT),
    GEAR(Family.OBJECT),
    LINK(Family.OBJECT),
    PLUS(Family.MARK),
    ARROW(Family.MARK),
    CHEVRON(Family.MARK),
    DIAMOND(Family.MARK),
    TRIANGLE(Family.MARK),
    RING(Family.MARK),
    SPIRAL(Family.MARK),
    THORN(Family.MARK);

    /** What kind of thing a symbol is; only for browsing, nothing draws by family. */
    public enum Family {
        /** The eight glyphs {@code MagicalParticles.RUNE} throws, drawn from the same table. */
        RUNE,
        ELEMENT,
        CREATURE,
        OBJECT,
        MARK
    }

    public static final int COUNT = values().length;

    private static final List<Sigil> RUNES = Arrays.stream(values()).filter(s -> s.family == Family.RUNE).toList();

    private static final Map<String, Sigil> BY_NAME =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Sigil::serializedName, Function.identity()));

    private final Family family;

    Sigil(Family family) {
        this.family = family;
    }

    public Family family() {
        return family;
    }

    /** The lower-case name, as {@code /particle} and the preview command spell it. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Sigil> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /** The eight runes in order, for a mark that wants "any rune" in fixed slots. */
    public static List<Sigil> runes() {
        return RUNES;
    }
}
