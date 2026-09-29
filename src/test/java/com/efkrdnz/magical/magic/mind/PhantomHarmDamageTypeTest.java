package com.efkrdnz.magical.magic.mind;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Phantom harm is vanilla indirect_magic plus no knockback. The type and its tags are datapack JSON,
 * which nothing else checks until a wielder is online and their lava shoves somebody.
 */
class PhantomHarmDamageTypeTest {

    private static final String TYPE = "magical:phantom_harm";

    private static String datapackFile(String path) throws IOException {
        try (InputStream in = PhantomHarmDamageTypeTest.class.getResourceAsStream("/data/" + path)) {
            if (in == null) {
                throw new IOException("data/" + path + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theTypeReadsAsIndirectMagic() throws IOException {
        String json = datapackFile("magical/damage_type/phantom_harm.json");
        assertTrue(json.contains("\"message_id\": \"indirectMagic\""), "vanilla's death messages");
        assertTrue(json.contains("\"exhaustion\": 0.0"), "indirect_magic's exhaustion");
        assertTrue(json.contains("\"scaling\": \"when_caused_by_living_non_player\""), "indirect_magic's scaling");
    }

    @Test
    void itIsInEveryTagIndirectMagicIsInAndPushesNobody() throws IOException {
        for (String tag : new String[] {"bypasses_armor", "bypasses_wolf_armor", "panic_causes",
                "witch_resistant_to", "no_knockback"}) {
            String json = datapackFile("minecraft/tags/damage_type/" + tag + ".json");
            assertTrue(json.contains(TYPE), tag + " must list phantom harm");
            assertTrue(json.contains("\"replace\": false"), tag + " must add to vanilla's list, not replace it");
        }
    }
}
