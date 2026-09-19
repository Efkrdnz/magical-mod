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
            "hud.magical.wheel", "hud.magical.active", "hud.magical.no_preset",
            "message.magical.space_rule_applied",
            // The Ledger kit of the Authority of Mana, replaced by the incantations.
            "skill.magical.open_ledger", "skill.magical.writ", "skill.magical.mana_form",
            "message.magical.writ_silenced", "message.magical.ledger_opened", "message.magical.mana_form_entered",
            // The codex creator branch and its picker, replaced by the Spell Creator screen.
            "screen.magical.spell_creator_hint", "screen.magical.fusion_slot_one", "screen.magical.fusion_slot_two",
            "screen.magical.fusion_empty_slot", "screen.magical.fusion_incomplete", "screen.magical.fusion_pick_hint",
            "screen.magical.fusion_no_formula", "screen.magical.fusion_try_other_pair", "screen.magical.fusion_select_prompt",
            "screen.magical.fusion_select_slot", "screen.magical.fusion_no_inputs", "screen.magical.fusion_output",
            // The codex title strip readout, replaced by the level chip and the XP bar.
            "screen.magical.proficiency",
            // The five blood actives the kit replaced, and the one message only one of them printed.
            "skill.magical.crimson_tithe", "skill.magical.hemorrhage", "skill.magical.scarlet_lance",
            "skill.magical.second_heart", "skill.magical.exsanguinate", "message.magical.second_heart_already");

    private static final List<String> PRESENT = List.of(
            "hud.magical.gauge.pride", "hud.magical.gauge.greed", "hud.magical.gauge.envy", "hud.magical.gauge.gluttony",
            "hud.magical.gauge.wrath", "hud.magical.gauge.sloth", "hud.magical.gauge.sloth_rested", "hud.magical.gauge.charge", "hud.magical.gauge.vault",
            "hud.magical.level_chip", "hud.magical.pool", "hud.magical.vault_chip",
            "hud.magical.arcane_line", "hud.magical.corruption_next", "hud.magical.corruption_line", "hud.magical.vessel_line", "hud.magical.notice_line", "hud.magical.debug",
            "hud.magical.rule.caption",
            // The codex builds a skill kind line from the MagicSkillType name.
            "skilltype.magical.projectile", "skilltype.magical.burst", "skilltype.magical.barrier");

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
