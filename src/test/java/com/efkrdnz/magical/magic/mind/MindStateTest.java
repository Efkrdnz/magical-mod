package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.PlayerMagicState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MindStateTest {
    private static PlayerMagicState written() {
        PlayerMagicState state = new PlayerMagicState();
        state.mind().lexicon().learn("block:minecraft:stone", 6);
        state.mind().reverie(1).setName("Wall");
        state.mind().reverie(1).addBlock(new Offset(0, 0, 0), "minecraft:stone", state.mind().lexicon());
        state.mind().setActiveSlot(1);
        state.mind().belt().set(3, "block:minecraft:stone");
        return state;
    }

    @Test
    void theMindRidesThePlayerSave() {
        PlayerMagicState back = PlayerMagicState.load(written().save());
        assertEquals(6, back.mind().lexicon().gazes("block:minecraft:stone"));
        assertEquals("Wall", back.mind().active().name());
        assertEquals(1, back.mind().active().size());
        assertEquals("block:minecraft:stone", back.mind().belt().get(3));
    }

    @Test
    void aCopyForDeathKeepsItAndItIsIndependent() {
        PlayerMagicState state = written();
        PlayerMagicState copy = state.copy();
        state.mind().clear();
        assertEquals(1, copy.mind().reverie(1).size());
        assertEquals("block:minecraft:stone", copy.mind().belt().get(3));
        assertEquals(0, state.mind().lexicon().size());
    }

    @Test
    void losingTheAuthorityForgetsTheMind() {
        PlayerMagicState state = written();
        state.clearAuthority();
        assertEquals(0, state.mind().lexicon().size());
        assertTrue(state.mind().reverie(1).isEmpty());
        assertEquals(0, state.mind().activeSlot());
        assertNull(state.mind().belt().get(3));
    }

    @Test
    void aSlotOutOfRangeIsClamped() {
        MindState mind = new MindState();
        mind.setActiveSlot(9);
        assertEquals(2, mind.activeSlot());
        assertSame(mind.reverie(2), mind.reverie(-1 + 3));
        assertSame(mind.reverie(0), mind.reverie(-5));
    }

    @Test
    void aSaveFromBeforeTheBeltFillsItWithWhatIsKnown() {
        PlayerMagicState state = written();
        net.minecraft.nbt.CompoundTag tag = state.save();
        tag.getCompound("mind").remove("belt");
        PlayerMagicState back = PlayerMagicState.load(tag);
        assertEquals("block:minecraft:stone", back.mind().belt().get(0));
    }

    @Test
    void aBeltEmptiedByHandStaysEmpty() {
        PlayerMagicState state = written();
        state.mind().belt().clear();
        PlayerMagicState back = PlayerMagicState.load(state.save());
        for (int i = 0; i < Belt.SIZE; i++) {
            assertNull(back.mind().belt().get(i));
        }
    }
}
