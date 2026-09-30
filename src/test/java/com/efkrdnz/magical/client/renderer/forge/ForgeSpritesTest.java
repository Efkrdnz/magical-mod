package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.visual.MatterKind;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
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

    /**
     * The one sprite list every piece of forge matter indexes into, by {@link MatterKind#offset()}.
     * Out of step with the enum, every kind is drawn in another kind's frames.
     */
    @Test
    void theMatterListIsEveryKindsFramesInEnumOrder() throws IOException {
        List<String> expected = new ArrayList<>();
        for (MatterKind kind : MatterKind.values()) {
            for (int frame = 0; frame < kind.frames(); frame++) {
                expected.add(kind.sprite(frame));
            }
        }
        assertEquals(expected, textures("/assets/magical/particles/forge_matter.json"));
        assertEquals(MatterKind.SPRITES, expected.size());
    }

    @Test
    void theDecalListIsTheThreeCracks() throws IOException {
        assertEquals(List.of("magical:forge/crack_0", "magical:forge/crack_1", "magical:forge/crack_2"),
                textures("/assets/magical/particles/forge_decal.json"));
        for (int i = 0; i < 3; i++) {
            BufferedImage crack = image("/assets/magical/textures/particle/forge/crack_" + i + ".png");
            assertEquals(32, crack.getWidth());
            assertEquals(32, crack.getHeight());
            assertGrey(crack, "crack_" + i);
        }
    }

    /** Every frame exists, is square at its size, and is grey so the element is its only hue. */
    @Test
    void everyMatterFrameIsGreyAndTheSizeItIsDrawnAt() throws IOException {
        for (MatterKind kind : MatterKind.values()) {
            for (int frame = 0; frame < kind.frames(); frame++) {
                BufferedImage sprite = image("/assets/magical/textures/particle/" + kind.texture(frame) + ".png");
                int side = kind == MatterKind.NICK ? 16 : 8;
                assertEquals(side, sprite.getWidth(), kind.texture(frame));
                assertEquals(side, sprite.getHeight(), kind.texture(frame));
                if (kind.texture(frame).startsWith("forge/")) {
                    assertGrey(sprite, kind.texture(frame));
                }
            }
        }
    }

    /** A kind that picks one frame for its life has frames worth picking between. */
    @Test
    void theFramesOfEveryForgeKindDiffer() throws IOException {
        for (MatterKind kind : MatterKind.values()) {
            if (!kind.texture(0).startsWith("forge/")) {
                continue;
            }
            List<int[]> frames = new ArrayList<>();
            for (int frame = 0; frame < kind.frames(); frame++) {
                BufferedImage sprite = image("/assets/magical/textures/particle/" + kind.texture(frame) + ".png");
                int[] pixels = sprite.getRGB(0, 0, sprite.getWidth(), sprite.getHeight(), null, 0, sprite.getWidth());
                for (int[] earlier : frames) {
                    assertTrue(!Arrays.equals(earlier, pixels), kind + " frame " + frame + " repeats an earlier one");
                }
                frames.add(pixels);
            }
        }
    }

    private static void assertGrey(BufferedImage image, String name) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                assertTrue(r == g && g == b, name + " has a coloured texel at " + x + "," + y);
            }
        }
    }

    private static List<String> textures(String path) throws IOException {
        try (InputStream in = ForgeSpritesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            List<String> textures = new ArrayList<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("textures").forEach(element -> textures.add(element.getAsString()));
            return textures;
        }
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
