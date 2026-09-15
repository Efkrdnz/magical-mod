package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The exchange rate, and the five ways a blood price gets past everything that would otherwise
 * soften it.
 *
 * <p>The rate and the bypasses are one decision and move together. A cheaper bill that armour could
 * still absorb would make blood magic free, and an expensive one the barrier pays is not a bill at
 * all.
 */
class BloodPriceTest {

    /**
     * Off the classpath rather than off a source path: the test task's working directory is not the
     * project root, and reading it the way the game does also proves the file is packaged.
     */
    private static String datapackFile(String path) throws IOException {
        try (InputStream in = BloodPriceTest.class.getResourceAsStream("/data/" + path)) {
            if (in == null) {
                throw new IOException("data/" + path + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void tenHeartsAreFiveHundredBlood() {
        assertEquals(25, BloodService.COST_PER_HEALTH, "the rate the whole school is priced at");
        assertEquals(500, 20 * BloodService.COST_PER_HEALTH, "a full bar, in blood");
    }

    @Test
    void theTypeExistsAndScalesWithNothing() throws IOException {
        String json = datapackFile("magical/damage_type/blood_price.json");
        assertTrue(json.contains("\"scaling\": \"never\""), "difficulty must not discount a price");
        assertTrue(json.contains("magical.blood_price"), "its death message id");
    }

    @Test
    void thePriceIsPastEveryVanillaSoftener() throws IOException {
        for (String tag : new String[] {"bypasses_armor", "bypasses_effects", "bypasses_enchantments",
                "bypasses_resistance", "bypasses_cooldown"}) {
            String json = datapackFile("minecraft/tags/damage_type/" + tag + ".json");
            assertTrue(json.contains("magical:blood_price"), tag + " must list the blood price");
        }
    }

    @Test
    void thePriceStaysOutOfTheOneTagThatWouldLetItKillTheUnkillable() throws IOException {
        // Creative and genuinely invulnerable players are already handled by MagicPrice.waived, so
        // bypassing the flag would buy nothing and could kill someone the game says cannot die.
        InputStream in = BloodPriceTest.class.getResourceAsStream(
                "/data/minecraft/tags/damage_type/bypasses_invulnerability.json");
        if (in == null) {
            return;
        }
        try (in) {
            assertTrue(!new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("magical:blood_price"),
                    "a price must not ignore invulnerability");
        }
    }
}
