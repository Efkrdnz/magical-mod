package com.efkrdnz.magical.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.passive.RacePassives;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Race is the one decision in the mod that can never be revisited, so the rules around it are
 * pinned rather than trusted: that it is taken once, that it pays out immediately, that it survives
 * a save, and that it cannot be switched off afterwards.
 */
class MagicalRaceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allSixRacesAreWellFormedAndDistinct() {
        assertEquals(6, MagicalRaces.all().size(), "the design document names six races");
        Set<ResourceLocation> skills = new HashSet<>();
        Set<ResourceLocation> passives = new HashSet<>();
        for (MagicalRace race : MagicalRaces.all()) {
            assertNotNull(MagicContent.get(race.starterSkill()), race.id() + " names a skill that does not exist");
            assertNotNull(MagicPassiveContent.get(race.passiveId()), race.id() + " names a passive that does not exist");
            assertTrue(MagicPassiveContent.isRacePassive(race.passiveId()),
                    race.id() + " has a passive that is not registered as a race passive");
            assertFalse(race.affinities().isEmpty(), race.id() + " has no affinity to show");
            assertTrue(skills.add(race.starterSkill()), race.starterSkill() + " is the starter of two races");
            assertTrue(passives.add(race.passiveId()), race.passiveId() + " is the passive of two races");
        }
    }

    @Test
    void choosingARaceGrantsItsSkillAndPassiveAtOnce() {
        // The point of choosing before the awakening: the decision is already paid out by the time
        // the player reaches the class chooser behind it.
        PlayerMagicState state = new PlayerMagicState();
        assertFalse(state.hasRace());

        assertTrue(state.chooseRace(MagicalRaces.DWARF.id()));
        assertTrue(state.hasUnlocked(MagicalRaces.DWARF.starterSkill()), "the starter skill was not granted");
        assertTrue(state.hasPassive(MagicalRaces.DWARF.passiveId()), "the race passive was not granted");
        assertEquals(MagicalRaces.DWARF, state.race());
    }

    @Test
    void aRaceIsTakenOnceAndNeverAgain() {
        PlayerMagicState state = new PlayerMagicState();
        assertTrue(state.chooseRace(MagicalRaces.ELF.id()));
        assertFalse(state.chooseRace(MagicalRaces.DEMON.id()), "a second choice must be refused");
        assertEquals(MagicalRaces.ELF, state.race(), "and must not have taken effect");
        assertFalse(state.hasPassive(MagicalRaces.DEMON.passiveId()), "nor granted anything");
    }

    @Test
    void anUnknownRaceIsRefusedRatherThanStored() {
        PlayerMagicState state = new PlayerMagicState();
        assertFalse(state.chooseRace(ResourceLocation.fromNamespaceAndPath("magical", "dragonborn")));
        assertFalse(state.hasRace());
        assertNull(state.race());
    }

    @Test
    void aRacePassiveHasNoOffSwitch() {
        // Every other non-curse passive has a checkbox in the codex. This one is identity: turning
        // it off would be a free respec of the only permanent decision in the mod.
        PlayerMagicState state = new PlayerMagicState();
        state.chooseRace(MagicalRaces.CELESTIAL.id());
        ResourceLocation blessed = MagicalRaces.CELESTIAL.passiveId();
        assertTrue(state.isPassiveEnabled(blessed));

        state.togglePassive(blessed);
        assertTrue(state.isPassiveEnabled(blessed), "toggling a race passive must do nothing");
    }

    @Test
    void raceSurvivesASaveAndAnUnknownOneIsDroppedOnLoad() {
        PlayerMagicState state = new PlayerMagicState();
        state.chooseRace(MagicalRaces.BEASTKIN.id());
        assertEquals(MagicalRaces.BEASTKIN, PlayerMagicState.load(state.save()).race());

        // A save written by a version that had a race this build does not: better raceless, which
        // re-opens the chooser, than holding an id nothing can resolve.
        CompoundTag tag = state.save();
        tag.putString("raceId", "magical:dragonborn");
        assertFalse(PlayerMagicState.load(tag).hasRace());
    }

    @Test
    void adaptableLiftsTheTuningLadderWithoutLiftingItsCeiling() {
        // Human's whole identity. It must buy an earlier choice, never a bigger one - eleven is
        // still eleven, or the budget rule stops meaning anything.
        PlayerMagicState human = new PlayerMagicState();
        human.chooseRace(MagicalRaces.HUMAN.id());
        PlayerMagicState elf = new PlayerMagicState();
        elf.chooseRace(MagicalRaces.ELF.id());

        int[] xp = {0, 70, 170, 300, 480, 720};
        int[] expectedHuman = {4, 6, 8, 10, 11, 11};
        int[] expectedOther = {3, 5, 7, 9, 11, 11};
        for (int level = 0; level < xp.length; level++) {
            human.setProficiencyXp(xp[level]);
            elf.setProficiencyXp(xp[level]);
            assertEquals(expectedHuman[level], human.tuningLimit(), "human at proficiency " + level);
            assertEquals(expectedOther[level], elf.tuningLimit(), "elf at proficiency " + level);
        }
        assertEquals(RacePassives.ADAPTABLE_BONUS_POINTS, expectedHuman[0] - expectedOther[0]);
    }

    @Test
    void corruptionResistanceSlowsSinWithoutSlowingItsRelease() {
        PlayerMagicState demon = new PlayerMagicState();
        demon.chooseRace(MagicalRaces.DEMON.id());
        PlayerMagicState human = new PlayerMagicState();
        human.chooseRace(MagicalRaces.HUMAN.id());

        demon.addWrath(100);
        human.addWrath(100);
        assertTrue(demon.wrathGauge() < human.wrathGauge(), "a demon must gather wrath more slowly");
        assertEquals(Math.round(100 * RacePassives.CORRUPTION_SIN_SCALE), demon.wrathGauge());

        // Clearing is not damped: resistance must never make a gauge harder to shed than normal.
        int gathered = demon.wrathGauge();
        demon.addWrath(-40);
        assertEquals(gathered - 40, demon.wrathGauge());
    }

    @Test
    void theAwakeningHandsOverTheRaceSkillRatherThanARandomOne() {
        // This is what makes the choice felt on turn one; it replaced a die roll.
        PlayerMagicState state = new PlayerMagicState();
        state.chooseRace(MagicalRaces.DEMON.id());
        state.unlockStarterAwakening(null);

        assertTrue(state.hasUnlocked(MagicContent.STARTER_SKILL), "everyone still gets the universal starter");
        assertTrue(state.hasUnlocked(MagicalRaces.DEMON.starterSkill()), "and their own race skill");
        for (MagicalRace other : MagicalRaces.all()) {
            if (other != MagicalRaces.DEMON && !other.starterSkill().equals(MagicContent.STARTER_SKILL)) {
                assertFalse(state.hasUnlocked(other.starterSkill()),
                        "a demon must not wake up holding the starter of " + other.id());
            }
        }
    }

    @Test
    void aRacePoolBonusReachesTheClientRatherThanStayingOnTheServer() {
        // The bug this pins: the bonus arrives through the passive handlers, which only run on the
        // server, and the two fields it lands in were carried by neither copy() nor save(). The HUD
        // therefore drew the base ceiling while the server spent the real one - so a spell paid for
        // out of the invisible top of the pool looked like it cost nothing at all.
        PlayerMagicState state = new PlayerMagicState();
        state.chooseRace(MagicalRaces.ELF.id());
        int base = state.maxMana();
        state.setClassPoolBonuses(MagicalRaces.ELF.bonusMaxMana(), MagicalRaces.ELF.bonusMaxBarrier());

        assertTrue(MagicalRaces.ELF.bonusMaxMana() > 0, "this test is worthless if the elf gains nothing");
        assertEquals(base + MagicalRaces.ELF.bonusMaxMana(), state.maxMana());

        // save() is the wire format, and ClientMagicState rebuilds through copy(): both have to
        // carry it, and the client cannot re-derive it because it never runs a passive handler.
        assertEquals(state.maxMana(), PlayerMagicState.load(state.save()).maxMana(), "lost over the wire");
        assertEquals(state.maxMana(), state.copy().maxMana(), "lost in copy()");
        assertEquals(state.maxBarrier(), PlayerMagicState.load(state.save()).copy().maxBarrier(),
                "the client applies load() then copy(), so the barrier must survive both");
    }

    @Test
    void everyRacePoolBonusIsReportedByTheHandlerThatOwnsIt() {
        // RaceSelectScreen advertises these numbers before the choice is made, so nothing may
        // promise a bonus that the handler does not then hand out.
        RacePassives handler = new RacePassives();
        for (MagicalRace race : MagicalRaces.all()) {
            PlayerMagicState state = new PlayerMagicState();
            state.chooseRace(race.id());
            assertEquals(race.bonusMaxMana(), handler.bonusMaxMana(null, state), race.id() + " mana");
            assertEquals(race.bonusMaxBarrier(), handler.bonusMaxBarrier(null, state), race.id() + " barrier");
        }
    }

    @Test
    void aStateWrittenBeforeThePoolFieldsExistedStillLoads() {
        PlayerMagicState state = new PlayerMagicState();
        state.chooseRace(MagicalRaces.DWARF.id());
        CompoundTag legacy = state.save();
        legacy.remove("classMaxManaBonus");
        legacy.remove("classMaxBarrierBonus");

        PlayerMagicState loaded = PlayerMagicState.load(legacy);
        assertEquals(new PlayerMagicState().maxMana(), loaded.maxMana(),
                "an old save reads as no bonus until the next slow tick, not as a crash");
        assertEquals(MagicalRaces.DWARF.id(), loaded.race().id());
    }
}
