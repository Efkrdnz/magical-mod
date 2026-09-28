package com.efkrdnz.magical.magic.visual.sigil;

import java.util.List;
import java.util.Objects;

/**
 * A combination: which symbols, in which inks, moving how, at what size. A placement hands its
 * {@code i}-th spawn {@link #sigilAt(int) sigilAt(i)} and {@link #inkAt(int) inkAt(i)}, each
 * cycling through its list, so a ring of eight in two inks alternates and slot {@code i} always
 * wears the same symbol - a crown reprinted every second lands each glyph where it was.
 *
 * <pre>{@code
 * SigilMark.of(Sigil.HOURGLASS).ink(SigilInk.GOLD)
 * SigilMark.of(Sigil.runes()).ink(SigilInk.from(profileBase)).scale(1.5F)
 * SigilMark.of(Sigil.EYE, Sigil.KEY).inks(SigilInk.GOLD, SigilInk.VIOLET).motion(SigilMotion.DRIFT)
 * }</pre>
 */
public record SigilMark(List<Sigil> sigils, List<SigilInk> inks, SigilMotion motion, float scale) {

    public SigilMark {
        sigils = List.copyOf(sigils);
        inks = List.copyOf(inks);
        Objects.requireNonNull(motion, "motion");
        if (sigils.isEmpty()) {
            throw new IllegalArgumentException("a sigil mark needs at least one symbol");
        }
        if (inks.isEmpty()) {
            throw new IllegalArgumentException("a sigil mark needs at least one ink");
        }
    }

    public static SigilMark of(Sigil... sigils) {
        return of(List.of(sigils));
    }

    public static SigilMark of(List<Sigil> sigils) {
        return new SigilMark(sigils, List.of(SigilInk.VIOLET), SigilMotion.RISE, 1.0F);
    }

    public SigilMark ink(SigilInk ink) {
        return new SigilMark(sigils, List.of(ink), motion, scale);
    }

    public SigilMark inks(SigilInk... inks) {
        return new SigilMark(sigils, List.of(inks), motion, scale);
    }

    public SigilMark motion(SigilMotion motion) {
        return new SigilMark(sigils, inks, motion, scale);
    }

    public SigilMark scale(float scale) {
        return new SigilMark(sigils, inks, motion, scale);
    }

    public Sigil sigilAt(int i) {
        return sigils.get(Math.floorMod(i, sigils.size()));
    }

    public SigilInk inkAt(int i) {
        return inks.get(Math.floorMod(i, inks.size()));
    }
}
