package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The music notes the Authority of Sound throws: six drawings, each pale grey so the tint is its
 * colour, a white body inside a darker rim so a tinted note keeps an edge of its own hue over
 * daylight. {@code scripts/note-sprites.py} draws them; the flying Riff notes wear the same files.
 */
class NoteSpritesTest {

    private static final int NOTES = 6;

    @Test
    void theDefinitionListsEveryNoteInOrder() throws IOException {
        try (InputStream in = NoteSpritesTest.class.getResourceAsStream("/assets/magical/particles/note.json")) {
            assertNotNull(in, "particles/note.json is missing");
            JsonArray textures = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("textures");
            assertEquals(NOTES, textures.size());
            for (int i = 0; i < NOTES; i++) {
                assertEquals("magical:note_" + i, textures.get(i).getAsString());
            }
        }
    }

    @Test
    void everyNoteIsSixteenSquareGreyWithAWhiteBodyAndADarkerRim() throws IOException {
        Set<Integer> drawings = new HashSet<>();
        for (int i = 0; i < NOTES; i++) {
            BufferedImage image = sprite(i);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            int body = 0;
            int rim = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    int r = (argb >> 16) & 0xFF;
                    assertTrue(r == ((argb >> 8) & 0xFF) && r == (argb & 0xFF), "note_" + i + " has a coloured texel");
                    if (r == 255) {
                        body++;
                    } else {
                        rim++;
                    }
                }
            }
            assertTrue(body >= 12, "note_" + i + " has almost no body");
            assertTrue(rim >= body / 2, "note_" + i + " has no rim round it");
            assertTrue(drawings.add(Arrays.hashCode(image.getRGB(0, 0, 16, 16, null, 0, 16))), "note_" + i + " is drawn twice");
        }
    }

    private static BufferedImage sprite(int index) throws IOException {
        try (InputStream in = NoteSpritesTest.class.getResourceAsStream("/assets/magical/textures/particle/note_" + index + ".png")) {
            assertNotNull(in, "note_" + index + " is missing");
            return ImageIO.read(in);
        }
    }
}
