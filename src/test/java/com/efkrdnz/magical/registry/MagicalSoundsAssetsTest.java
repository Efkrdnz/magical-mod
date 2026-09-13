package com.efkrdnz.magical.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.SpaceRuleChange;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A sound event is three things that have to agree: a registered id, an entry in sounds.json,
 * and an .ogg on disk. A miss in any of the three is silence at runtime and nothing in the log,
 * so this reads all three the way the game would.
 */
class MagicalSoundsAssetsTest {

    private static final String SOUNDS_JSON = "/assets/magical/sounds.json";
    private static final String SUBTITLE = "subtitles.magical.rule_flash";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRuleCueIsRegisteredWiredAndOnDisk() throws IOException {
        JsonObject sounds = JsonParser.parseString(read(SOUNDS_JSON)).getAsJsonObject();
        List<DeferredHolder<SoundEvent, SoundEvent>> cues = new ArrayList<>();
        cues.add(MagicalSounds.RULE_STAMP);
        for (SpaceRuleChange change : SpaceRuleChange.values()) {
            cues.add(MagicalSounds.cue(change));
        }
        Set<String> ids = new HashSet<>();
        for (DeferredHolder<SoundEvent, SoundEvent> cue : cues) {
            String path = cue.getId().getPath();
            assertTrue(ids.add(path), "two cues share the id " + path);
            assertTrue(path.startsWith("rule."), path + " is not under the rule namespace");
            JsonObject entry = sounds.getAsJsonObject(path);
            assertNotNull(entry, "sounds.json has no entry for " + path);
            assertEquals(SUBTITLE, entry.get("subtitle").getAsString(), path + " has the wrong subtitle");
            for (JsonElement sound : entry.getAsJsonArray("sounds")) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                assertTrue(name.startsWith("magical:rule/"), path + " points outside sounds/rule: " + name);
                String file = "/assets/magical/sounds/" + name.substring("magical:".length()) + ".ogg";
                try (InputStream in = MagicalSoundsAssetsTest.class.getResourceAsStream(file)) {
                    assertNotNull(in, path + " points at a missing file " + file);
                    assertTrue(in.readAllBytes().length > 1000, file + " is too small to be a cue");
                }
            }
        }
        assertEquals(1 + SpaceRuleChange.values().length, ids.size());
    }

    @Test
    void theCreationCueIsWiredAndOnDisk() throws IOException {
        JsonObject sounds = JsonParser.parseString(read(SOUNDS_JSON)).getAsJsonObject();
        String path = MagicalSounds.CREATION.getId().getPath();
        JsonObject entry = sounds.getAsJsonObject(path);
        assertNotNull(entry, "sounds.json has no entry for " + path);
        assertEquals("subtitles.magical.creation", entry.get("subtitle").getAsString());
        assertTrue(read("/assets/magical/lang/en_us.json").contains("\"subtitles.magical.creation\""), "the creation subtitle is untranslated");
        for (JsonElement sound : entry.getAsJsonArray("sounds")) {
            String name = sound.getAsJsonObject().get("name").getAsString();
            String file = "/assets/magical/sounds/" + name.substring("magical:".length()) + ".ogg";
            try (InputStream in = MagicalSoundsAssetsTest.class.getResourceAsStream(file)) {
                assertNotNull(in, path + " points at a missing file " + file);
                assertTrue(in.readAllBytes().length > 1000, file + " is too small to be a cue");
            }
        }
    }

    @Test
    void everyRegisteredSoundHasAnEntry() throws IOException, IllegalAccessException {
        JsonObject sounds = JsonParser.parseString(read(SOUNDS_JSON)).getAsJsonObject();
        int holders = 0;
        for (Field field : MagicalSounds.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !DeferredHolder.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            DeferredHolder<?, ?> holder = (DeferredHolder<?, ?>) field.get(null);
            assertTrue(sounds.has(holder.getId().getPath()), field.getName() + " is registered but sounds.json never mentions " + holder.getId());
            holders++;
        }
        assertEquals(sounds.size(), holders, "sounds.json has entries no holder registers");
    }

    @Test
    void theCueSubtitleIsTranslated() throws IOException {
        assertTrue(read("/assets/magical/lang/en_us.json").contains('"' + SUBTITLE + '"'), "language file is missing " + SUBTITLE);
    }

    private static String read(String path) throws IOException {
        try (InputStream in = MagicalSoundsAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
