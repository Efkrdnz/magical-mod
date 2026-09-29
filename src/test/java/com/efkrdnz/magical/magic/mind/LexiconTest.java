package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LexiconTest {
    @Test
    void aKeyNamesItsKindAndItsId() {
        assertEquals("block:minecraft:grass_block", Impression.block("minecraft:grass_block").key());
        Impression back = Impression.parse("creature:minecraft:villager");
        assertEquals(Impression.Kind.CREATURE, back.kind());
        assertEquals("minecraft:villager", back.id());
        assertNull(Impression.parse("nonsense"));
        assertNull(Impression.parse("creature:minecraft:player"), "a player is never an impression");
        assertNull(Impression.parse("creature:magical:sleeper"), "a sleeping body is a player, and never an impression");
    }

    @Test
    void fidelityClimbsAtOneFiveAndTwentyGazes() {
        Lexicon lexicon = new Lexicon();
        String key = "block:minecraft:stone";
        assertEquals(0, lexicon.fidelity(key));
        lexicon.gaze(key);
        assertEquals(1, lexicon.fidelity(key));
        lexicon.learn(key, 4);
        assertEquals(1, lexicon.fidelity(key), "learn never lowers and never adds");
        lexicon.learn(key, 5);
        assertEquals(2, lexicon.fidelity(key));
        lexicon.learn(key, 20);
        assertEquals(3, lexicon.fidelity(key));
        lexicon.learn(key, 2);
        assertEquals(20, lexicon.gazes(key));
    }

    @Test
    void theBudgetIsSixteenPlusTwoPerImpressionCappedAt128() {
        Lexicon lexicon = new Lexicon();
        assertEquals(16, lexicon.budget());
        for (int i = 0; i < 10; i++) {
            lexicon.gaze("block:minecraft:b" + i);
        }
        assertEquals(36, lexicon.budget());
        for (int i = 10; i < 100; i++) {
            lexicon.gaze("block:minecraft:b" + i);
        }
        assertEquals(128, lexicon.budget());
    }

    @Test
    void itSurvivesASaveAndACopy() {
        Lexicon lexicon = new Lexicon();
        lexicon.learn("creature:minecraft:cat", 7);
        CompoundTag tag = lexicon.save();
        Lexicon loaded = new Lexicon();
        loaded.load(tag);
        assertEquals(7, loaded.gazes("creature:minecraft:cat"));
        Lexicon copy = new Lexicon();
        copy.copyFrom(loaded);
        loaded.clear();
        assertEquals(1, copy.size());
        assertEquals(0, loaded.size());
    }
}
