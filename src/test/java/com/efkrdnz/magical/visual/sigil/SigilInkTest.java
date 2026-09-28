package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.sigil.SigilInk;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SigilInkTest {

    @Test
    void contrastIsTheWcagRatio() {
        assertEquals(21.0, Palette.contrast(0xFFFFFF, 0x000000), 1e-6);
        assertEquals(1.0, Palette.contrast(0x9B5CFF, 0x9B5CFF), 1e-9);
        assertEquals(Palette.contrast(0x123456, 0xABCDEF), Palette.contrast(0xABCDEF, 0x123456), 1e-12);
    }

    @Test
    void thereAreFifteenNamedInksAndTheirNamesReadBack() {
        Map<String, SigilInk> named = SigilInk.named();
        assertEquals(15, named.size());
        named.forEach((name, ink) -> {
            assertEquals(name.toLowerCase(Locale.ROOT), name);
            assertEquals(ink, SigilInk.byName(name).orElseThrow());
        });
        assertEquals(SigilInk.VIOLET, named.get("violet"));
        assertEquals(SigilInk.NIGHT, named.get("night"));
        assertTrue(SigilInk.byName("no_such_ink").isEmpty());
    }

    @Test
    void everyNamedInkReadsOverAnyBackground() {
        SigilInk.named().forEach((name, ink) ->
                assertTrue(ink.readability() >= SigilInk.READABLE, name + " reads at " + ink.readability()));
    }

    @Test
    void aDerivedInkAlwaysReads() {
        for (int r = 0; r <= 255; r += 51) {
            for (int g = 0; g <= 255; g += 51) {
                for (int b = 0; b <= 255; b += 51) {
                    int rgb = r << 16 | g << 8 | b;
                    SigilInk ink = SigilInk.from(rgb);
                    assertTrue(ink.readability() >= SigilInk.READABLE, Integer.toHexString(rgb) + " -> " + ink);
                    assertTrue(Palette.relativeLuminance(ink.core()) > Palette.relativeLuminance(ink.glow()),
                            Integer.toHexString(rgb) + ": a derived core is the light half");
                }
            }
        }
    }

    @Test
    void aDerivedInkKeepsAColourThatAlreadyReads() {
        assertEquals(0x8A1CC8, SigilInk.from(0x8A1CC8).glow());
        assertEquals(Palette.mix(0x8A1CC8, 0xFFFFFF, 0.85F), SigilInk.from(0x8A1CC8).core());
    }

    @Test
    void aPaleColourIsDeepenedUntilItReads() {
        SigilInk ink = SigilInk.from(0xFFFF80);
        assertTrue(Palette.relativeLuminance(ink.glow()) < Palette.relativeLuminance(0xFFFF80));
        assertTrue(ink.readability() >= SigilInk.READABLE);
    }
}
