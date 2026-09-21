package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.PlantResult;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Array rides on the state like the Grimoire does: copied, saved, loaded, and emptied with the
 * rest of what the wielder authored.
 *
 * <p>All five sites or it half-works, and each way of half-working is silent. Miss {@code copy()}
 * and the shape saves and loads perfectly on the server and arrives empty on the client - in
 * multiplayer only, after a resync, with nothing logged. Miss {@code save()} and it never reaches
 * the wire or the disk at all. Nothing else in the build will ever notice either one.
 */
class PlayerMagicStateSwordArrayTest {

    /** Two bearings at the base rung: bill {@code 3*2 + 2*3 = 12} of a draw of 24, five Edge bound. */
    private static final Station NORTH = new Station(0, 0, 3, 2);
    private static final Station WEST_HIGH = new Station(6, 1, 2, 3);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState written() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(PlantResult.PLANTED, state.swordArray().plant(NORTH, 0), "the first bearing is written");
        assertEquals(PlantResult.PLANTED, state.swordArray().plant(WEST_HIGH, 0), "and the second clears the separation");
        assertEquals(12, state.swordArray().bill(), "the shape the rest of this test is about");
        return state;
    }

    @Test
    void aCopyKeepsTheArray() {
        SwordArray copied = written().copy().swordArray();
        assertEquals(2, copied.size());
        assertEquals(NORTH, copied.station(0), "insertion order is load-bearing and survives the copy");
        assertEquals(WEST_HIGH, copied.station(1));
        assertEquals(12, copied.bill());
        assertEquals(5, copied.bound());
    }

    @Test
    void aSaveAndLoadKeepsTheArray() {
        CompoundTag tag = written().save();
        int[] packed = tag.getIntArray("swordArray");
        assertEquals(3, packed.length, "the version and one packed int per station");
        assertEquals(SwordArray.SAVE_VERSION, packed[0]);

        SwordArray loaded = PlayerMagicState.load(tag).swordArray();
        assertEquals(2, loaded.size());
        assertEquals(NORTH, loaded.station(0));
        assertEquals(WEST_HIGH, loaded.station(1));
        assertEquals(12, loaded.bill());
        assertEquals(5, loaded.bound());
    }

    @Test
    void clearingWipesTheArray() {
        PlayerMagicState state = written();
        state.clearAuthority();
        assertTrue(state.swordArray().isEmpty(), "the shape went with everything else the wielder authored");
        assertEquals(0, state.swordArray().bill());
        assertFalse(state.save().contains("swordArray"), "and a wiped Array is off the wire again");
    }

    @Test
    void anEmptyArrayIsNotOnTheWire() {
        // This tag rides every sync for every player and the overwhelming majority of them will
        // never find the rite, so an empty Array has to cost nothing at all - not an empty list.
        assertFalse(new PlayerMagicState().save().contains("swordArray"), "the key is absent, not empty");
        assertTrue(written().save().contains("swordArray"), "and present the moment there is a bearing");
        CompoundTag empty = new PlayerMagicState().save();
        assertTrue(PlayerMagicState.load(empty).swordArray().isEmpty(), "and a tag without the key loads empty");
    }
}
