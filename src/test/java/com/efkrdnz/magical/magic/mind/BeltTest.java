package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeltTest {
    @Test
    void offerFillsTheFirstEmptySlotAndNeverTwice() {
        Belt belt = new Belt();
        belt.set(0, "block:minecraft:stone");
        assertTrue(belt.offer("block:minecraft:dirt"));
        assertEquals("block:minecraft:dirt", belt.get(1));
        assertFalse(belt.offer("block:minecraft:dirt"), "an impression already on the belt was offered twice");
        for (int i = 2; i < Belt.SIZE; i++) {
            assertTrue(belt.offer("block:minecraft:b" + i));
        }
        assertFalse(belt.offer("block:minecraft:overflow"), "a full belt took one more");
    }

    @Test
    void blankClearsAndOutOfRangeIsIgnored() {
        Belt belt = new Belt();
        belt.set(3, "creature:minecraft:cow");
        belt.set(3, "");
        assertNull(belt.get(3));
        belt.set(9, "block:minecraft:stone");
        belt.set(-1, "block:minecraft:stone");
        assertNull(belt.get(9));
        assertEquals(-1, belt.indexOf("block:minecraft:stone"));
    }

    @Test
    void saveAndLoadKeepEverySlotAndDropWhatIsNotAnImpression() {
        Belt belt = new Belt();
        belt.set(0, "block:minecraft:stone");
        belt.set(4, "creature:minecraft:cow");
        var tag = belt.save();
        tag.set(8, net.minecraft.nbt.StringTag.valueOf("creature:minecraft:player"));
        Belt back = new Belt();
        back.load(tag);
        assertEquals("block:minecraft:stone", back.get(0));
        assertNull(back.get(1));
        assertEquals("creature:minecraft:cow", back.get(4));
        assertNull(back.get(8), "a player was loaded onto the belt");
    }
}
