package com.efkrdnz.magical.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The backdrop behind the title, the chip, the XP bar and the tabs is one small tile repeated over
 * the whole panel: {@code blit} is handed the tile size as the texture size and the panel size as
 * the drawn size, so u and v run past 1 and the sampler wraps.
 *
 * <p>Two things about the file make that work, and neither of them fails loudly.
 *
 * <p>The size the code names has to be the size the file is. Name a different one and every
 * repeat is stretched or cropped by the same wrong amount, which looks like a deliberate texture
 * rather than a mistake and is only visible by launching the game.
 *
 * <p>The tile has to meet itself. A tile whose last column does not continue into its first paints
 * a grid of lines across the panel at the repeat interval. That is measurable without a render
 * context: the wrap seam is held to the worst seam the tile already carries inside itself, so a
 * replacement tile that is merely noisy passes and one with an edge in it does not.
 */
class ScreenBackdropTextureTest {
    private static final String PATH = "/assets/magical/textures/gui/screen_backdrop.png";

    @Test
    void theTileIsTheSizeTheCodeRepeatsIt() throws IOException {
        BufferedImage tile = read();
        assertEquals(MagicalGuiStyle.BACKDROP_TILE, tile.getWidth(), "tile width against the size blit is told");
        assertEquals(MagicalGuiStyle.BACKDROP_TILE, tile.getHeight(), "tile height: one number is passed for both axes");
    }

    @Test
    void theTileIsOpaque() throws IOException {
        BufferedImage tile = read();
        for (int y = 0; y < tile.getHeight(); y++) {
            for (int x = 0; x < tile.getWidth(); x++) {
                assertEquals(0xFF, tile.getRGB(x, y) >>> 24,
                        "the backdrop is the back of the screen, so a hole in it is a hole through to the world");
            }
        }
    }

    @Test
    void theTileMeetsItself() throws IOException {
        BufferedImage tile = read();
        int w = tile.getWidth();
        int h = tile.getHeight();

        double worstColumn = 0.0;
        for (int x = 0; x + 1 < w; x++) {
            worstColumn = Math.max(worstColumn, columnStep(tile, x, x + 1));
        }
        assertTrue(columnStep(tile, w - 1, 0) <= worstColumn,
                "the left edge does not continue the right one, so the repeat draws vertical lines");

        double worstRow = 0.0;
        for (int y = 0; y + 1 < h; y++) {
            worstRow = Math.max(worstRow, rowStep(tile, y, y + 1));
        }
        assertTrue(rowStep(tile, h - 1, 0) <= worstRow,
                "the top edge does not continue the bottom one, so the repeat draws horizontal lines");
    }

    /** Mean per-channel distance between two columns, in levels of 255. */
    private static double columnStep(BufferedImage tile, int left, int right) {
        long sum = 0;
        for (int y = 0; y < tile.getHeight(); y++) {
            sum += channelDistance(tile.getRGB(left, y), tile.getRGB(right, y));
        }
        return sum / (double) (tile.getHeight() * 3);
    }

    /** Mean per-channel distance between two rows, in levels of 255. */
    private static double rowStep(BufferedImage tile, int top, int bottom) {
        long sum = 0;
        for (int x = 0; x < tile.getWidth(); x++) {
            sum += channelDistance(tile.getRGB(x, top), tile.getRGB(x, bottom));
        }
        return sum / (double) (tile.getWidth() * 3);
    }

    private static int channelDistance(int first, int second) {
        return Math.abs(((first >> 16) & 0xFF) - ((second >> 16) & 0xFF))
                + Math.abs(((first >> 8) & 0xFF) - ((second >> 8) & 0xFF))
                + Math.abs((first & 0xFF) - (second & 0xFF));
    }

    private static BufferedImage read() throws IOException {
        try (InputStream in = ScreenBackdropTextureTest.class.getResourceAsStream(PATH)) {
            assertNotNull(in, PATH + " is not shipped");
            return ImageIO.read(in);
        }
    }
}
