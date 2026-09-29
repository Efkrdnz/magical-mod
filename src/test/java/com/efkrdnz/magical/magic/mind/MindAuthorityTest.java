package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MindAuthorityTest {
    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theAuthorityGrantsItsFourSkillsAndNothingElse() {
        assertEquals(List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id(), MagicContent.INSIST.id(), MagicContent.LULL.id()),
                AuthorityContent.get(AuthorityContent.MIND).skillIds());
        for (var skill : List.of(MagicContent.DAYDREAM, MagicContent.UNVEIL, MagicContent.INSIST, MagicContent.LULL)) {
            assertTrue(MagicContent.AUTHORITY_SKILLS.contains(skill.id()), skill.id() + " is not an authority skill");
            assertEquals(-6, skill.tier());
        }
        assertTrue(AuthorityContent.commandIds().contains("authority_of_mind"));
    }

    @Test
    void everyStringTheStageDrawsIsInTheLanguageFile() throws IOException {
        String lang = readLang();
        List<String> keys = new ArrayList<>(List.of(
                "authority.magical.authority_of_mind", "authority.magical.authority_of_mind.desc",
                "skill.magical.daydream", "skill.magical.daydream.desc",
                "skill.magical.unveil", "skill.magical.unveil.desc",
                "message.magical.daydream_hold", "message.magical.unveil_empty",
                "message.magical.unveil_too_many", "message.magical.unveil_nowhere",
                "message.magical.gaze_learned", "message.magical.gaze_studied",
                "message.magical.reverie_saved", "message.magical.reverie_refused",
                "screen.magical.playbill", "screen.magical.playbill.slot",
                "screen.magical.playbill.plausibility", "screen.magical.playbill.senses",
                "screen.magical.playbill.certain_in", "screen.magical.playbill.never",
                "screen.magical.playbill.cost", "screen.magical.playbill.becomes_real",
                "screen.magical.playbill.save",
                "screen.magical.playbill.done", "screen.magical.playbill.empty",
                "screen.magical.playbill.stance", "screen.magical.playbill.reaction",
                "screen.magical.playbill.cluster", "mind.magical.daydream.hint",
                "skill.magical.insist", "skill.magical.insist.desc", "message.magical.insist_nothing",
                "message.magical.manifested", "message.magical.unmanifested",
                "skill.magical.lull", "skill.magical.lull.desc", "message.magical.lull_nobody",
                "message.magical.lull_unsure", "message.magical.lull_no_flaw", "message.magical.lull_no_dream",
                "message.magical.lull_dreaming", "message.magical.lull_not_grounded", "message.magical.lull_asleep", "message.magical.dream_enter", "message.magical.dream_woke",
                "message.magical.dream_flaw_marked", "mind.magical.dream.hint", "mind.magical.dream.status",
                "entity.magical.sleeper"));
        for (String term : List.of("unsupported", "context", "alien", "fidelity", "habitat", "like_kind", "unlike_kind", "size")) {
            keys.add("mind.magical.term." + term);
        }
        for (Stance stance : Stance.values()) {
            keys.add("mind.magical.stance." + stance.name().toLowerCase(Locale.ROOT));
        }
        for (Reaction reaction : Reaction.values()) {
            keys.add("mind.magical.reaction." + reaction.name().toLowerCase(Locale.ROOT));
        }
        for (Sense sense : Sense.values()) {
            keys.add("mind.magical.sense." + sense.name().toLowerCase(Locale.ROOT));
        }
        for (Reverie.Refusal refusal : Reverie.Refusal.values()) {
            if (refusal != Reverie.Refusal.NONE) {
                keys.add("mind.magical.refusal." + refusal.name().toLowerCase(Locale.ROOT));
            }
        }
        for (String brush : List.of("point", "line", "wall", "box")) {
            keys.add("mind.magical.brush." + brush);
        }
        List<String> missing = keys.stream().filter(key -> !lang.contains('"' + key + '"')).toList();
        assertTrue(missing.isEmpty(), "missing lang keys: " + missing);
    }

    private static String readLang() throws IOException {
        try (InputStream stream = MindAuthorityTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(stream, "could not find " + LANG_PATH + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
