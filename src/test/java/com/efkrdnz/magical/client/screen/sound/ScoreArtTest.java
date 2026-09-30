package com.efkrdnz.magical.client.screen.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The Score's atlas and the table that reads it. The script that draws the atlas and the class that
 * blits from it each carry the coordinates; a sprite moved in one and not the other draws a slice of
 * its neighbour and nothing fails, so this holds them together, and holds every sprite to having ink.
 */
class ScoreArtTest {

    private static final Pattern ENTRY = Pattern.compile("\"(\\w+)\": \\((\\d+), (\\d+)\\)");

    @Test
    void everySpriteIsWhereTheScriptPutIt() throws IOException {
        String script = Files.readString(script());
        String table = script.substring(script.indexOf("LAYOUT = {"), script.indexOf("}", script.indexOf("LAYOUT = {")));
        Map<String, int[]> placed = new HashMap<>();
        Matcher matcher = ENTRY.matcher(table);
        while (matcher.find()) {
            placed.put(matcher.group(1), new int[] {Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))});
        }
        assertEquals(placed.size(), ScoreArt.ALL.length, "the script and the class list different sprites");
        for (ScoreArt.Sprite sprite : ScoreArt.ALL) {
            int[] at = placed.get(sprite.name());
            assertNotNull(at, sprite.name() + " is not in the script");
            assertEquals(at[0], sprite.u(), sprite.name() + " u");
            assertEquals(at[1], sprite.v(), sprite.name() + " v");
        }
    }

    /** The test JVM starts wherever Gradle puts it, so the script is found by walking up to the project. */
    private static Path script() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("scripts").resolve("score-art.py");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("scripts/score-art.py is not above " + Path.of("").toAbsolutePath());
    }

    @Test
    void everySpriteHasInkAndStaysInTheAtlas() throws IOException {
        BufferedImage atlas;
        try (InputStream in = ScoreArtTest.class.getResourceAsStream("/assets/magical/textures/gui/score/atlas.png")) {
            assertNotNull(in, "the score atlas is missing");
            atlas = ImageIO.read(in);
        }
        assertEquals(ScoreArt.ATLAS_SIZE, atlas.getWidth());
        assertEquals(ScoreArt.ATLAS_SIZE, atlas.getHeight());
        for (ScoreArt.Sprite sprite : ScoreArt.ALL) {
            assertTrue(sprite.u() + sprite.w() <= ScoreArt.ATLAS_SIZE && sprite.v() + sprite.h() <= ScoreArt.ATLAS_SIZE, sprite.name());
            int ink = 0;
            for (int y = sprite.v(); y < sprite.v() + sprite.h(); y++) {
                for (int x = sprite.u(); x < sprite.u() + sprite.w(); x++) {
                    if ((atlas.getRGB(x, y) >>> 24) > 0) {
                        ink++;
                    }
                }
            }
            assertTrue(ink >= 6, sprite.name() + " is blank");
        }
    }
}
