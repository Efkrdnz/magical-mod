package com.efkrdnz.magical.client.model.eldritch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The glow layer is drawn over the body with ordinary alpha blending (the eyes render type is not
 * additive in 1.21.4), so it has to be transparent wherever the creature does not glow: an opaque
 * dark pixel there paints the body black. The shipped placeholders are the contract the artist
 * copies, so they are held to it.
 */
class EldritchGlowTexturesTest {
    private static final String FOLDER = "/assets/magical/textures/entity/eldritch/";
    private static final String[] NAMES = {"tentacle", "eye", "maw"};
    /** Below this sum of channels an opaque pixel reads as dark. */
    private static final int DARK = 96;

    @Test
    void glowLayersAreTransparentWhereTheyDoNotGlow() throws IOException {
        for (String name : NAMES) {
            BufferedImage glow = read(FOLDER + name + "_glow.png");
            int opaqueDark = 0;
            int lit = 0;
            for (int y = 0; y < glow.getHeight(); y++) {
                for (int x = 0; x < glow.getWidth(); x++) {
                    int argb = glow.getRGB(x, y);
                    int alpha = argb >>> 24;
                    int brightness = ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
                    if (alpha > 0 && brightness < DARK) {
                        opaqueDark++;
                    } else if (alpha > 0) {
                        lit++;
                    }
                }
            }
            assertEquals(0, opaqueDark, name + "_glow.png has opaque dark pixels that would paint the body black");
            assertTrue(lit > 0, name + "_glow.png glows nowhere");
        }
    }

    @Test
    void glowLayersShareTheirBodySize() throws IOException {
        for (String name : NAMES) {
            BufferedImage body = read(FOLDER + name + ".png");
            BufferedImage glow = read(FOLDER + name + "_glow.png");
            assertEquals(body.getWidth(), glow.getWidth(), name + " glow width");
            assertEquals(body.getHeight(), glow.getHeight(), name + " glow height");
        }
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = EldritchGlowTexturesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not shipped");
            return ImageIO.read(in);
        }
    }
}
