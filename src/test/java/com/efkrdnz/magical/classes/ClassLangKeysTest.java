package com.efkrdnz.magical.classes;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A class with no lang key is not a crash either. It is a node in the Paths of Power reading
 * {@code class.magical.sword_saint}, a tooltip whose first line is the same string and a footer
 * pool labelled with it - all of which read as placeholder art rather than as a bug, so they
 * survive review the way a missing skill key did until {@code ContentLangKeysTest} went up.
 *
 * <p>Nothing has ever checked these. {@code ContentLangKeysTest} walks skills and authorities and
 * stops there; {@code MagicalTooltipAssetsTest} explicitly skips keys built by concatenation, and
 * {@link MagicalClassDefinition#nameKey()} is exactly that, and it never reads this package at
 * all. Fifty-two classes have been going unchecked, so the net goes up with the fifty-third.</p>
 */
class ClassLangKeysTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Every class, secret or not. A hidden class still needs its strings: the moment a wielder
     * finds the rite the whole chain is drawn, and a reveal that opens on four raw resource paths
     * is a worse first impression than no reveal.
     */
    @Test
    void everyClassHasANameAndADescription() throws IOException {
        String lang = readLang();
        List<String> missing = new ArrayList<>();
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            if (!lang.contains('"' + definition.nameKey() + '"')) {
                missing.add(definition.nameKey());
            }
            if (!lang.contains('"' + definition.descriptionKey() + '"')) {
                missing.add(definition.descriptionKey());
            }
        }
        assertTrue(missing.isEmpty(), "the language file is missing " + missing.size() + " class keys: " + missing);
    }

    private static String readLang() throws IOException {
        try (InputStream stream = ClassLangKeysTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(stream, "could not find " + LANG_PATH + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
