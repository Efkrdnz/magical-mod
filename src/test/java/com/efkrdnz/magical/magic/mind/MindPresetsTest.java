package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MindPresetsTest {
    @Test
    void everyPresetIsAWholeReverieWithinTheBudgetOfWhatItNeeds() {
        for (String name : MindPresets.NAMES) {
            Reverie reverie = MindPresets.named(name);
            assertNotNull(reverie, name);
            assertFalse(reverie.isEmpty(), name);
            Lexicon lexicon = new Lexicon();
            MindPresets.impressions(reverie).forEach(key -> lexicon.learn(key, 5));
            assertTrue(reverie.size() <= lexicon.budget(), name + " is over the budget its own impressions buy");
        }
        assertNull(MindPresets.named("nonsense"));
    }

    @Test
    void theCatIsAGuardWithAVoice() {
        Reverie cat = MindPresets.named("cat");
        assertEquals(Set.of("creature:minecraft:cat"), MindPresets.impressions(cat));
        assertEquals(Stance.GUARD, cat.figments().get(0).script().stance());
        assertTrue(cat.figments().get(0).senses().contains(Sense.SOUND));
    }
}
