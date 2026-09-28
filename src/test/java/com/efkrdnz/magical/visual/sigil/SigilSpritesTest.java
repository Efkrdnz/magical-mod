package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The sprite set the sigil particle indexes into. A texture list out of step with {@link Sigil}
 * draws every symbol as its neighbour and fails nothing at runtime, so it is held here.
 */
class SigilSpritesTest {
    private static final int SIDE = 16;
    /** Two glyph pixels at 2x: symbols closer than this are the same drawing at particle size. */
    private static final int MIN_DIFFERENCE = 8;

    @Test
    void theDefinitionListsEveryCoreThenEveryGlowInSigilOrder() throws IOException {
        List<String> expected = new ArrayList<>();
        for (Sigil sigil : Sigil.values()) {
            expected.add("magical:sigil_" + sigil.serializedName());
        }
        for (Sigil sigil : Sigil.values()) {
            expected.add("magical:sigil_" + sigil.serializedName() + "_glow");
        }
        try (InputStream in = resource("/assets/magical/particles/sigil.json")) {
            JsonArray textures = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("textures");
            List<String> actual = new ArrayList<>();
            textures.forEach(element -> actual.add(element.getAsString()));
            assertEquals(expected, actual);
        }
    }

    @Test
    void everyTextureIsSixteenSquarePaleAndGrey() throws IOException {
        for (Sigil sigil : Sigil.values()) {
            for (BufferedImage image : List.of(core(sigil), glow(sigil))) {
                assertEquals(SIDE, image.getWidth(), sigil.serializedName());
                assertEquals(SIDE, image.getHeight(), sigil.serializedName());
                for (int y = 0; y < SIDE; y++) {
                    for (int x = 0; x < SIDE; x++) {
                        int argb = image.getRGB(x, y);
                        if ((argb >>> 24) == 0) {
                            continue;
                        }
                        int r = argb >> 16 & 0xFF;
                        int g = argb >> 8 & 0xFF;
                        int b = argb & 0xFF;
                        assertTrue(r == g && g == b, sigil + " carries a hue at " + x + "," + y);
                        assertTrue(r >= 200, sigil + " is too dark at " + x + "," + y + " for its ink to read");
                    }
                }
            }
        }
    }

    @Test
    void everyCoreHasStrokesAndEveryGlowStaysOffThem() throws IOException {
        for (Sigil sigil : Sigil.values()) {
            boolean[] core = mask(core(sigil));
            boolean[] glow = mask(glow(sigil));
            int strokes = 0;
            int halo = 0;
            for (int i = 0; i < core.length; i++) {
                strokes += core[i] ? 1 : 0;
                halo += glow[i] ? 1 : 0;
                assertTrue(!(core[i] && glow[i]), sigil + " glows over its own stroke at texel " + i);
            }
            assertTrue(strokes > 0, sigil + " has no strokes");
            assertTrue(halo > 0, sigil + " has no glow");
        }
    }

    @Test
    void noTwoSymbolsAreTheSameDrawing() throws IOException {
        Sigil[] all = Sigil.values();
        boolean[][] masks = new boolean[all.length][];
        for (int i = 0; i < all.length; i++) {
            masks[i] = mask(core(all[i]));
        }
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                int differ = 0;
                for (int k = 0; k < masks[i].length; k++) {
                    differ += masks[i][k] != masks[j][k] ? 1 : 0;
                }
                assertTrue(differ >= MIN_DIFFERENCE, all[i] + " and " + all[j] + " differ by " + differ + " texels");
            }
        }
    }

    @Test
    void theRuneSigilsAreTheRunesTheRuneParticleThrows() throws IOException {
        for (Sigil rune : Sigil.runes()) {
            boolean[] core = mask(core(rune));
            BufferedImage small = image("/assets/magical/textures/particle/" + rune.serializedName() + ".png");
            for (int gy = 0; gy < 7; gy++) {
                for (int gx = 0; gx < 7; gx++) {
                    boolean stroke = (small.getRGB(gx, gy) >>> 24) == 0xFF;
                    assertEquals(stroke, core[(1 + 2 * gy) * SIDE + 1 + 2 * gx], rune + " at " + gx + "," + gy);
                }
            }
        }
    }

    private static BufferedImage core(Sigil sigil) throws IOException {
        return image("/assets/magical/textures/particle/sigil_" + sigil.serializedName() + ".png");
    }

    private static BufferedImage glow(Sigil sigil) throws IOException {
        return image("/assets/magical/textures/particle/sigil_" + sigil.serializedName() + "_glow.png");
    }

    private static boolean[] mask(BufferedImage image) {
        boolean[] out = new boolean[SIDE * SIDE];
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                out[y * SIDE + x] = (image.getRGB(x, y) >>> 24) != 0;
            }
        }
        return out;
    }

    private static BufferedImage image(String path) throws IOException {
        try (InputStream in = resource(path)) {
            return ImageIO.read(in);
        }
    }

    private static InputStream resource(String path) {
        InputStream in = SigilSpritesTest.class.getResourceAsStream(path);
        assertNotNull(in, path);
        return in;
    }
}
