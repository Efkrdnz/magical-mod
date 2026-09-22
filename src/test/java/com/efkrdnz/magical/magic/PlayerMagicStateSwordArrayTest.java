package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordRules;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Array rides on the state like the Grimoire does: copied, saved, loaded, and emptied with the
 * rest of what the wielder chose.
 *
 * <p>All five sites or it half-works, and each way of half-working is silent. Miss {@code copy()}
 * and the stance saves and loads perfectly on the server and arrives as the default on the client
 * - in multiplayer only, after a resync, with nothing logged. Miss {@code save()} and it never
 * reaches the wire or the disk at all. Nothing else in the build will ever notice either one.
 */
class PlayerMagicStateSwordArrayTest {

    /**
     * Rain: the last of the six and one only the top rung opens, so every clamp this file can
     * trip is in play. Guard would pass {@link SwordArray#setStance} at any rung and prove
     * nothing about the rules travelling with the stance.
     */
    private static final SwordStance CHOSEN = SwordStance.RAIN;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState written() {
        PlayerMagicState state = new PlayerMagicState();
        // The rung first: setStance refuses a locked stance rather than clamping it, which is the
        // whole reason PlayerMagicState.load reads the class progress before the Array.
        state.swordArray().setRules(SwordRules.GOD);
        assertTrue(state.swordArray().setStance(CHOSEN), "the apex rung opens every stance");
        assertTrue(state.swordArray().setDrawn(true), "and the steel is out");
        return state;
    }

    @Test
    void aCopyKeepsTheStanceAndTheSteel() {
        SwordArray copied = written().copy().swordArray();
        assertEquals(CHOSEN, copied.stance());
        assertTrue(copied.drawn());
        assertEquals(SwordRules.GOD, copied.rules(), "the rung travels with it or the stance is illegal");
    }

    @Test
    void aSaveAndLoadKeepsTheStanceAndTheSteel() {
        CompoundTag tag = written().save();
        int[] packed = tag.getIntArray("swordArray");
        assertEquals(3, packed.length, "the version, the stance ordinal and the drawn flag");
        assertEquals(SwordArray.SAVE_VERSION, packed[0]);
        assertEquals(CHOSEN.ordinal(), packed[1]);
        assertEquals(1, packed[2]);

        // Loaded onto a state with no class progress, so the rung falls back to the base rung and
        // Rain is clamped away. That is the documented behaviour and it is what makes the load
        // order in PlayerMagicState.load load-bearing: refreshRung cannot put back a stance the
        // load already threw away.
        SwordArray loaded = PlayerMagicState.load(tag).swordArray();
        assertEquals(SwordStance.first(), loaded.stance(),
                "a rung that has not opened Rain reads Guard, and reads it consistently");
        assertTrue(loaded.drawn(), "but the steel is out either way: that is not a rung's business");
    }

    @Test
    void aVersionOneTagIsDroppedWholeRatherThanGuessedAt() {
        // A v1 tag was a packed list of lattice stations and there is no honest way to read one
        // as a stance. load()'s existing rule handles the whole migration: a version this build
        // does not know is dropped, so the wielder comes back in the default stance, sheathed -
        // which is one keypress from right and cannot be a wrong shape.
        CompoundTag old = new PlayerMagicState().save();
        old.putIntArray("swordArray", new int[] {1, 12345, 6789});
        SwordArray loaded = PlayerMagicState.load(old).swordArray();
        assertEquals(SwordStance.first(), loaded.stance());
        assertFalse(loaded.drawn());
    }

    @Test
    void clearingWipesTheArray() {
        PlayerMagicState state = written();
        state.clearAuthority();
        assertEquals(SwordStance.first(), state.swordArray().stance());
        assertFalse(state.swordArray().drawn(), "the steel went with everything else the wielder held");
        assertFalse(state.save().contains("swordArray"), "and a wiped Array is off the wire again");
    }

    @Test
    void anUntouchedArrayIsNotOnTheWire() {
        // This tag rides every sync for every player and the overwhelming majority of them will
        // never find the rite, so an untouched Array has to cost nothing at all.
        assertFalse(new PlayerMagicState().save().contains("swordArray"), "the key is absent, not empty");
        assertTrue(written().save().contains("swordArray"), "and present the moment anything is chosen");
        CompoundTag empty = new PlayerMagicState().save();
        assertTrue(PlayerMagicState.load(empty).swordArray().isDefault(),
                "and a tag without the key loads as the default");
    }
}
