package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Keys the HUD builds by concatenation, which the literal-key scan cannot see, pinned by hand -
 * and the keys of the panel this one replaced, pinned absent so they do not come back.
 */
class HudLangKeysTest {

    private static final List<String> RETIRED = List.of(
            "hud.magical.mana", "hud.magical.mana_vault", "hud.magical.barrier", "hud.magical.proficiency",
            "hud.magical.wheel", "hud.magical.active", "hud.magical.no_preset");

    private static final List<String> PRESENT = List.of(
            "hud.magical.gauge.pride", "hud.magical.gauge.greed", "hud.magical.gauge.envy", "hud.magical.gauge.gluttony",
            "hud.magical.gauge.wrath", "hud.magical.gauge.sloth", "hud.magical.gauge.sloth_rested", "hud.magical.gauge.charge", "hud.magical.gauge.vault",
            "hud.magical.level_chip", "hud.magical.pool", "hud.magical.vault_chip",
            "hud.magical.arcane_line", "hud.magical.corruption_next", "hud.magical.corruption_line", "hud.magical.vessel_line", "hud.magical.debug");

    @Test
    void everyAnnouncementKindHasALine() throws IOException {
        String lang = read();
        for (HudAnnouncer.Kind kind : HudAnnouncer.Kind.values()) {
            String key = "hud.magical.announce." + kind.name().toLowerCase(Locale.ROOT);
            assertTrue(lang.contains('"' + key + '"'), "language file is missing " + key);
        }
    }

    @Test
    void theKeysTheHudBuildsByHandArePresent() throws IOException {
        String lang = read();
        for (String key : PRESENT) {
            assertTrue(lang.contains('"' + key + '"'), "language file is missing " + key);
        }
    }

    @Test
    void theRetiredPanelsKeysStayGone() throws IOException {
        String lang = read();
        for (String key : RETIRED) {
            assertFalse(lang.contains('"' + key + '"'), key + " came back; nothing draws it");
        }
    }

    private static String read() throws IOException {
        try (InputStream in = HudLangKeysTest.class.getResourceAsStream("/assets/magical/lang/en_us.json")) {
            assertNotNull(in, "missing language file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
