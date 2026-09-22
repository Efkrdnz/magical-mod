package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import net.minecraft.nbt.IntArrayTag;
import org.junit.jupiter.api.Test;

/**
 * What a wielder owns, which is now two facts: a posture, and whether the steel is out.
 *
 * <p>This file used to pin three arithmetic invariants over a lattice of authored bearings. All of
 * that went with the lattice. What is left is the part that can still go silently wrong: a rung
 * that opens a stance it should not, a save that comes back as a different posture than it went in
 * as, and the load order that would quietly re-clamp a Sword God's stance to a Summoner's on every
 * login.
 */
class SwordArrayTest {

    @Test
    void aFreshArrayIsTheFirstStanceSheathed() {
        SwordArray array = new SwordArray();
        assertEquals(SwordStance.first(), array.stance());
        assertFalse(array.drawn(), "a wielder who has never pressed anything has no steel out");
        assertEquals(SwordRules.SUMMONER.swords(), array.swords(),
                "an Array with no class on it must still be legal at the base rung");
    }

    @Test
    void eachRungOpensExactlyTheStancesItIsMeantTo() {
        assertOpens(SwordRules.SUMMONER, SwordStance.GUARD, SwordStance.VANGUARD);
        assertOpens(SwordRules.RIDER, SwordStance.GUARD, SwordStance.VANGUARD, SwordStance.CROWN,
                SwordStance.WINGS);
        assertOpens(SwordRules.SAINT, SwordStance.values());
        assertOpens(SwordRules.GOD, SwordStance.values());
    }

    /**
     * The count-off-the-front is the cheapest way to say "this rung opens the first N", and the
     * only way it can go wrong is somebody reordering the enum. This is what would say so.
     */
    @Test
    void theStanceCountMatchesTheEnumAtTheTop() {
        assertEquals(SwordStance.count(), SwordRules.GOD.stances(),
                "the apex rung must open every stance there is, or one of them is unreachable");
        for (SwordStance stance : SwordStance.values()) {
            assertTrue(SwordRules.forRung(stance.rung()).allows(stance),
                    stance + " says it opens at rung " + stance.rung()
                            + ", but that rung's stance count does not reach it - the enum has"
                            + " been reordered without moving the counts on SwordRules");
        }
    }

    @Test
    void aLockedStanceIsRefusedAndTheWielderKeepsTheOneTheyHad() {
        SwordArray array = new SwordArray();
        array.setRules(SwordRules.SUMMONER);
        assertTrue(array.setStance(SwordStance.VANGUARD));
        assertFalse(array.setStance(SwordStance.RAIN), "Rain is a Saint's stance");
        assertEquals(SwordStance.VANGUARD, array.stance(),
                "a refused stance must leave the wielder in the one they were already holding");
    }

    @Test
    void takingTheSameStanceTwiceIsNotAChange() {
        SwordArray array = new SwordArray();
        assertFalse(array.setStance(SwordStance.first()),
                "re-taking the stance already held must report no change, or the picker re-flies"
                        + " every sword for a release that chose nothing");
    }

    /**
     * A rung dropping out from under a stance is nobody's mistake - a reset does it - so it falls
     * back rather than refusing. A wielder must always be standing in some posture.
     */
    @Test
    void loweringTheRungBelowTheStanceFallsBackRatherThanRefusing() {
        SwordArray array = new SwordArray();
        array.setRules(SwordRules.GOD);
        assertTrue(array.setStance(SwordStance.RAIN));
        array.setRules(SwordRules.SUMMONER);
        assertEquals(SwordStance.first(), array.stance());
    }

    @Test
    void aStanceAndADrawnFlagSurviveASaveAndALoad() {
        for (SwordStance stance : SwordStance.values()) {
            for (boolean drawn : new boolean[] {false, true}) {
                SwordArray out = new SwordArray();
                out.setRules(SwordRules.GOD);
                out.setStance(stance);
                out.setDrawn(drawn);

                SwordArray in = new SwordArray();
                in.setRules(SwordRules.GOD);
                in.load(out.save());
                assertEquals(stance, in.stance(), "stance did not survive the round trip");
                assertEquals(drawn, in.drawn(), "the drawn flag did not survive the round trip");
            }
        }
    }

    /**
     * The migration, and it is the <em>absence</em> of migration code that is being checked.
     *
     * <p>A version 1 tag was a packed list of lattice stations, and there is no honest reading of
     * one as a stance. {@code load}'s standing rule - an unknown version is dropped whole rather
     * than guessed at - does the whole job, and the result is one keypress from right.
     */
    @Test
    void aSaveFromTheLatticeLoadsAsTheDefaultPostureSheathed() {
        SwordArray array = new SwordArray();
        array.setRules(SwordRules.GOD);
        array.setStance(SwordStance.COIL);
        array.setDrawn(true);
        // Version 1, then four packed stations. Exactly what the old save() wrote.
        array.load(new IntArrayTag(new int[] {1, 197123, 263041, 328975, 394513}));
        assertEquals(SwordStance.first(), array.stance());
        assertFalse(array.drawn());
    }

    @Test
    void aHandEditedSaveCannotNameAStanceThatDoesNotExist() {
        SwordArray array = new SwordArray();
        array.setRules(SwordRules.GOD);
        array.load(new IntArrayTag(new int[] {SwordArray.SAVE_VERSION, 99, 1}));
        assertEquals(SwordStance.first(), array.stance(),
                "an ordinal off the end must clamp, not throw inside a player's login");
        assertTrue(array.drawn(), "and the rest of the tag is still good");
    }

    @Test
    void aSaveWrittenAtAHigherRungIsClampedWhenItIsReadAtALowerOne() {
        SwordArray god = new SwordArray();
        god.setRules(SwordRules.GOD);
        god.setStance(SwordStance.RAIN);

        SwordArray summoner = new SwordArray();
        summoner.setRules(SwordRules.SUMMONER);
        summoner.load(god.save());
        assertEquals(SwordStance.first(), summoner.stance(),
                "a Summoner cannot stand in Rain, however the tag got there");
    }

    @Test
    void copyFromTakesEverythingIncludingTheRung() {
        SwordArray from = new SwordArray();
        from.setRules(SwordRules.SAINT);
        from.setStance(SwordStance.COIL);
        from.setDrawn(true);

        SwordArray to = new SwordArray();
        to.copyFrom(from);
        assertEquals(SwordStance.COIL, to.stance());
        assertTrue(to.drawn());
        assertEquals(SwordRules.SAINT, to.rules(),
                "copyFrom that left the rung behind would clamp the stance it just copied on the"
                        + " next setRules");
        assertEquals(SwordStance.COIL.swords(SwordRules.SAINT.swords()), to.swords(),
                "and the count is the copied stance's cap over the copied rung, not either alone");
    }

    private static void assertOpens(SwordRules rules, SwordStance... open) {
        for (SwordStance stance : SwordStance.values()) {
            boolean expected = false;
            for (SwordStance one : open) {
                expected |= one == stance;
            }
            assertEquals(expected, rules.allows(stance),
                    rules + " " + (expected ? "must" : "must not") + " open " + stance);
        }
    }
}
