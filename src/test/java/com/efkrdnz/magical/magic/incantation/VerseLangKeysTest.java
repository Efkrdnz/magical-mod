package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every verse in the catalogue has a name and a description in the language file; every message the service sends has a line. */
class VerseLangKeysTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";
    private static final List<String> MESSAGES = List.of(
            "message.magical.incantation_empty", "message.magical.incantation_next", "message.magical.incantation_rest",
            "message.magical.incantation_frayed", "message.magical.incantation_rejected", "message.magical.incantation_written",
            "message.magical.incantation_known", "message.magical.incantation_unknown_verse", "message.magical.incantation_preview",
            "entity.magical.verse_body");

    @Test
    void everyVerseHasANameAndADescription() throws IOException {
        String lang = read();
        List<String> missing = new ArrayList<>();
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            String key = "verse.magical." + verse.path();
            if (!lang.contains('"' + key + '"')) {
                missing.add(key);
            }
            if (!lang.contains('"' + key + ".desc\"")) {
                missing.add(key + ".desc");
            }
        }
        assertTrue(missing.isEmpty(), "verses without a line: " + missing);
    }

    @Test
    void everyMessageTheServiceSendsHasALine() throws IOException {
        String lang = read();
        List<String> missing = new ArrayList<>();
        for (String key : MESSAGES) {
            if (!lang.contains('"' + key + '"')) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "messages without a line: " + missing);
    }

    private static String read() throws IOException {
        try (InputStream in = VerseLangKeysTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(in, "missing language file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
