package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * What {@code scripts/forge-sprites.py} draws, held to the shape the renderer reads it in. Every one
 * of these failures draws something at runtime and fails nothing: a coloured texel fights the
 * element tint, a lip on the wrong line puts the bright edge inside the blade, a filled bottom line
 * bleeds into the next row, and a seam across u draws a line across every spin.
 */
class ForgeSpritesTest {

    private static final Set<ForgeSmear.Row> BLADE_ROWS = EnumSet.range(ForgeSmear.Row.STEEL, ForgeSmear.Row.GUST);

    @Test
    void theAtlasIsTheSizeTheContractAssumes() throws IOException {
        BufferedImage atlas = atlas();
        assertEquals(ForgeSmear.ATLAS_WIDTH, atlas.getWidth());
        assertEquals(ForgeSmear.ATLAS_HEIGHT, atlas.getHeight());
    }

    @Test
    void everyTexelIsGreySoTheElementIsTheOnlyHue() throws IOException {
        BufferedImage atlas = atlas();
        for (int y = 0; y < atlas.getHeight(); y++) {
            for (int x = 0; x < atlas.getWidth(); x++) {
                int argb = atlas.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                assertTrue(r == g && g == b, "a coloured texel at " + x + "," + y);
            }
        }
    }

    @Test
    void everyRowEndsOnAClearLine() throws IOException {
        BufferedImage atlas = atlas();
        for (ForgeSmear.Row row : ForgeSmear.Row.values()) {
            int y = row.ordinal() * ForgeSmear.ROW_HEIGHT + ForgeSmear.ROW_HEIGHT - 1;
            for (int x = 0; x < atlas.getWidth(); x++) {
                assertEquals(0, atlas.getRGB(x, y) >>> 24, row + " bleeds into the next row at x " + x);
            }
        }
    }

    @Test
    void everyBladeCarriesItsLipOnTheCuttingSide() throws IOException {
        BufferedImage atlas = atlas();
        for (ForgeSmear.Row row : BLADE_ROWS) {
            int top = row.ordinal() * ForgeSmear.ROW_HEIGHT;
            int lip = 0;
            int inner = 0;
            for (int x = 0; x < atlas.getWidth(); x++) {
                lip += coverage(atlas, x, top + 13) + coverage(atlas, x, top + 14);
                inner += coverage(atlas, x, top) + coverage(atlas, x, top + 1);
            }
            assertTrue(lip > 0, row + " has no lip");
            assertTrue(lip > inner, row + " is heavier on its inner side than on its cutting edge");
        }
    }

    @Test
    void everyRowAnElementWearsHasSomethingOnIt() throws IOException {
        BufferedImage atlas = atlas();
        for (ForgeSmear.Row row : ForgeSmear.Row.values()) {
            int top = row.ordinal() * ForgeSmear.ROW_HEIGHT;
            int drawn = 0;
            for (int y = top; y < top + ForgeSmear.ROW_HEIGHT; y++) {
                for (int x = 0; x < atlas.getWidth(); x++) {
                    drawn += coverage(atlas, x, y) > 0 ? 1 : 0;
                }
            }
            assertTrue(drawn > atlas.getWidth(), row + " is all but empty");
        }
    }

    /**
     * u wraps, so the last column meets the first. That seam may be no worse than the worst step
     * between two neighbouring columns inside the row, or it is a line drawn across every ring.
     */
    @Test
    void everyRowTilesAlongTheBlade() throws IOException {
        BufferedImage atlas = atlas();
        for (ForgeSmear.Row row : ForgeSmear.Row.values()) {
            int top = row.ordinal() * ForgeSmear.ROW_HEIGHT;
            int worstInside = 0;
            for (int x = 0; x + 1 < atlas.getWidth(); x++) {
                worstInside = Math.max(worstInside, step(atlas, top, x, x + 1));
            }
            int seam = step(atlas, top, atlas.getWidth() - 1, 0);
            assertTrue(seam <= worstInside, row + " shows a seam where u wraps: " + seam + " > " + worstInside);
        }
    }

    @Test
    void theSheathPeaksOnItsCentrelineAndFadesToBothSides() throws IOException {
        BufferedImage atlas = atlas();
        int top = ForgeSmear.Row.SHEATH.ordinal() * ForgeSmear.ROW_HEIGHT;
        int[] line = new int[ForgeSmear.ROW_HEIGHT];
        for (int y = 0; y < ForgeSmear.ROW_HEIGHT; y++) {
            for (int x = 0; x < atlas.getWidth(); x++) {
                line[y] += coverage(atlas, x, top + y);
            }
        }
        int peak = 0;
        for (int y = 1; y < line.length; y++) {
            peak = line[y] > line[peak] ? y : peak;
        }
        assertTrue(peak >= 6 && peak <= 8, "the sheath peaks at line " + peak);
        assertTrue(line[0] < line[peak] / 3 && line[14] < line[peak] / 3, "the sheath does not fade at its rims");
    }

    /** Summed alpha difference down one column pair of a row. */
    private static int step(BufferedImage atlas, int top, int a, int b) {
        int sum = 0;
        for (int y = top; y < top + ForgeSmear.ROW_HEIGHT; y++) {
            sum += Math.abs(coverage(atlas, a, y) - coverage(atlas, b, y));
        }
        return sum;
    }

    private static int coverage(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    static BufferedImage atlas() throws IOException {
        return image("/assets/magical/textures/effect/forge_smear.png");
    }

    static BufferedImage image(String path) throws IOException {
        try (InputStream in = ForgeSpritesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
