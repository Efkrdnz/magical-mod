package com.efkrdnz.magical.magic.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The sandpile, with no world under it.
 *
 * <p>Every rule the Authority of Chaos has is in here, which is the point of {@link Pile} taking a
 * {@link PileWorld} rather than a level: the avalanche is arithmetic and can be pinned exactly.
 * There is no random number anywhere in this Authority, so every one of these assertions is on an
 * exact value rather than a range.
 */
class PileTest {

    /** A line of sites, so "lowest neighbour" and "most-loaded neighbour" are unambiguous. */
    private static final class FakeWorld implements PileWorld {
        final Map<PileSite, Integer> capacity = new HashMap<>();
        final Map<PileSite, List<PileSite>> neighbours = new HashMap<>();
        final Set<PileSite> living = new HashSet<>();
        final Map<PileSite, Double> height = new HashMap<>();
        final List<String> shed = new ArrayList<>();

        @Override
        public int capacity(PileSite site) {
            return capacity.getOrDefault(site, 4);
        }

        @Override
        public List<PileSite> neighbours(PileSite site) {
            return neighbours.getOrDefault(site, List.of());
        }

        @Override
        public boolean living(PileSite site) {
            return living.contains(site);
        }

        @Override
        public double height(PileSite site) {
            return height.getOrDefault(site, 0.0D);
        }

        @Override
        public boolean present(PileSite site) {
            return true;
        }

        @Override
        public void shed(PileSite site, int amount) {
            shed.add(site + "=" + amount);
        }
    }

    private static PileSite block(int x) {
        return PileSite.of(new BlockPos(x, 0, 0));
    }

    private static Fracture only(Fault fault) {
        Fracture fracture = new Fracture();
        for (int i = 0; i < Fracture.LENGTH; i++) {
            fracture.set(i, fault);
        }
        return fracture;
    }

    // ---- the grain that does nothing -------------------------------------------------------

    @Test
    void aSiteUnderItsCapacityDoesNothingAtAll() {
        FakeWorld world = new FakeWorld();
        Pile pile = new Pile(world);
        pile.add(block(0), 4, 0L);
        assertEquals(4, pile.stressAt(block(0)));
        assertFalse(pile.unstable(block(0)), "four on a capacity of four is full, not over");
        assertEquals(0, pile.settle(only(Fault.BLOOM), 64, 0L), "nothing was armed, so nothing gives");
    }

    @Test
    void stressCanOnlyEverBeAddedAndNeverTakenBack() {
        FakeWorld world = new FakeWorld();
        Pile pile = new Pile(world);
        pile.add(block(0), 3, 0L);
        assertFalse(pile.add(block(0), -2, 0L), "a negative burden is refused, not applied");
        assertEquals(3, pile.stressAt(block(0)), "entropy has a direction and the Pile has no CLEAR");
    }

    // ---- the seven faults -------------------------------------------------------------------

    @Test
    void slumpSendsEverythingToTheLowestNeighbour() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1), block(2)));
        // Room to hold what they are given, so this pins the direction rather than the depth.
        world.capacity.put(block(1), 99);
        world.capacity.put(block(2), 99);
        world.height.put(block(1), 5.0D);
        world.height.put(block(2), -3.0D);
        Pile pile = new Pile(world);
        pile.add(block(0), 5, 0L);
        pile.settle(only(Fault.SLUMP), 64, 0L);

        assertEquals(0, pile.stressAt(block(0)), "the site that gave way keeps nothing");
        assertEquals(0, pile.stressAt(block(1)), "the high neighbour gets none of it");
        assertEquals(5, pile.stressAt(block(2)), "a landslide runs downhill");
    }

    @Test
    void heapSendsEverythingToWhicheverNeighbourAlreadyHoldsMost() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1), block(2)));
        world.capacity.put(block(1), 99);
        world.capacity.put(block(2), 99);
        Pile pile = new Pile(world);
        pile.add(block(1), 1, 0L);
        pile.add(block(2), 3, 0L);
        pile.add(block(0), 5, 0L);
        pile.settle(only(Fault.HEAP), 64, 0L);

        assertEquals(1, pile.stressAt(block(1)));
        assertEquals(8, pile.stressAt(block(2)), "rich gets richer, so a heap converges to one point");
    }

    @Test
    void bloomSplitsEvenlyAndGivesTheRemainderToTheFirstNeighbour() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1), block(2), block(3)));
        Pile pile = new Pile(world);
        pile.add(block(0), 8, 0L);
        pile.settle(only(Fault.BLOOM), 64, 0L);

        assertEquals(3, pile.stressAt(block(1)), "eight across three is 3/3/2, remainder first");
        assertEquals(3, pile.stressAt(block(2)));
        assertEquals(2, pile.stressAt(block(3)));
    }

    @Test
    void huntIgnoresBlocksAndFeedsOnlyLivingNeighbours() {
        FakeWorld world = new FakeWorld();
        PileSite body = PileSite.of(41);
        world.neighbours.put(block(0), List.of(block(1), body, block(2)));
        world.capacity.put(body, 99);
        world.living.add(body);
        Pile pile = new Pile(world);
        pile.add(block(0), 6, 0L);
        pile.settle(only(Fault.HUNT), 64, 0L);

        assertEquals(0, pile.stressAt(block(1)), "a hunting cascade walks past the stone");
        assertEquals(0, pile.stressAt(block(2)));
        assertEquals(6, pile.stressAt(body));
    }

    @Test
    void huntWithNothingAliveToFeedShedsWhereItStandsRatherThanVanishing() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        Pile pile = new Pile(world);
        pile.add(block(0), 6, 0L);
        pile.settle(only(Fault.HUNT), 64, 0L);

        assertEquals(0, pile.stressAt(block(1)), "nothing living, so nothing is passed on");
        assertEquals(List.of(block(0) + "=6"), world.shed, "stress is conserved: it is spent, not lost");
    }

    @Test
    void recoilSendsItBackTheWayItCame() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        world.neighbours.put(block(1), List.of(block(0), block(2)));
        world.capacity.put(block(1), 0);
        Pile pile = new Pile(world);
        // 0 gives way into 1 (BLOOM), then 1 recoils back into 0.
        Fracture fracture = new Fracture();
        fracture.set(0, Fault.BLOOM);
        fracture.set(1, Fault.RECOIL);
        pile.add(block(0), 5, 0L);
        pile.settle(fracture, 64, 0L);

        assertEquals(5, pile.stressAt(block(0)), "the cascade ate its own source");
        assertEquals(0, pile.stressAt(block(1)));
        assertEquals(0, pile.stressAt(block(2)));
    }

    @Test
    void recoilAtTheOriginHasNowhereToGoBackToAndShedsInstead() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        Pile pile = new Pile(world);
        pile.add(block(0), 7, 0L);
        pile.settle(only(Fault.RECOIL), 64, 0L);

        assertEquals(List.of(block(0) + "=7"), world.shed);
    }

    @Test
    void shedSpendsTheStressWhereItStandsAndPassesNothingOn() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        Pile pile = new Pile(world);
        pile.add(block(0), 9, 0L);
        pile.settle(only(Fault.SHED), 64, 0L);

        assertEquals(0, pile.stressAt(block(1)), "a terminator passes nothing on");
        assertEquals(List.of(block(0) + "=9"), world.shed);
    }

    @Test
    void rootKeepsTheStressAndRaisesItsOwnCapacitySoTheCascadeStopsThere() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        Pile pile = new Pile(world);
        pile.add(block(0), 5, 0L);
        pile.settle(only(Fault.ROOT), 64, 0L);

        assertEquals(5, pile.stressAt(block(0)), "a root swallows what it was given");
        assertEquals(0, pile.stressAt(block(1)));
        assertTrue(pile.capacityAt(block(0), 0L) >= 5, "and it is no longer over its own capacity");
        assertFalse(pile.unstable(block(0)), "so it does not give way again next tick");
        assertTrue(world.shed.isEmpty(), "a root is the quiet terminator, not the loud one");
    }

    // ---- the Fracture is what makes a cascade change character -------------------------------

    @Test
    void theFractureChangesTheFaultAsTheCascadeDeepens() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        world.neighbours.put(block(1), List.of(block(2), block(3)));
        world.capacity.put(block(1), 0);
        world.capacity.put(block(2), 99);
        world.capacity.put(block(3), 99);

        Fracture fracture = new Fracture();
        fracture.set(0, Fault.BLOOM);   // generation 1: 0 -> 1
        fracture.set(1, Fault.SHED);    // generation 2: 1 spends it

        Pile pile = new Pile(world);
        pile.add(block(0), 5, 0L);
        pile.settle(fracture, 64, 0L);

        assertEquals(0, pile.stressAt(block(2)), "generation two shed rather than bloomed");
        assertEquals(0, pile.stressAt(block(3)));
        assertEquals(List.of(block(1) + "=5"), world.shed);
    }

    @Test
    void aFractureShorterThanTheCascadeClampsToItsLastFault() {
        Fracture fracture = new Fracture();
        fracture.set(0, Fault.SLUMP);
        fracture.set(1, Fault.HEAP);
        fracture.set(2, Fault.SHED);
        fracture.set(3, Fault.SHED);
        fracture.set(4, Fault.SHED);
        assertSame(Fault.SLUMP, fracture.at(1));
        assertSame(Fault.HEAP, fracture.at(2));
        assertSame(Fault.SHED, fracture.at(5));
        assertSame(Fault.SHED, fracture.at(99), "a deep cascade keeps the last thing you told it");
    }

    // ---- pressure, budget, slack --------------------------------------------------------------

    @Test
    void aCascadeIsBudgetedSoItRollsAcrossSeveralTicksInsteadOfOneFrame() {
        FakeWorld world = new FakeWorld();
        for (int x = 0; x < 6; x++) {
            world.neighbours.put(block(x), List.of(block(x + 1)));
            world.capacity.put(block(x), 0);
        }
        world.capacity.put(block(6), 99);
        Pile pile = new Pile(world);
        pile.add(block(0), 3, 0L);

        assertEquals(2, pile.settle(only(Fault.BLOOM), 2, 0L), "two give-ways is all the budget allows");
        assertTrue(pile.settling(), "the rest of the avalanche is still queued");
        pile.settle(only(Fault.BLOOM), 64, 1L);
        assertFalse(pile.settling());
        assertEquals(3, pile.stressAt(block(6)), "and it arrives all the same");
    }

    @Test
    void aSiteThatGaveWayGoesSlackAndRefusesFurtherStress() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        Pile pile = new Pile(world);
        pile.add(block(0), 5, 0L);
        pile.settle(only(Fault.BLOOM), 64, 0L);

        assertTrue(pile.slack(block(0), 10L));
        assertFalse(pile.add(block(0), 3, 10L), "you cannot dig the same trap twice");
        assertEquals(0, pile.stressAt(block(0)));
        assertFalse(pile.slack(block(0), Pile.SLACK_TICKS + 1L), "but the ground recovers eventually");
        assertTrue(pile.add(block(0), 3, Pile.SLACK_TICKS + 1L));
    }

    @Test
    void loweringTheGroundArmsEverythingThatWasAlreadyStanding() {
        FakeWorld world = new FakeWorld();
        world.neighbours.put(block(0), List.of(block(1)));
        world.capacity.put(block(1), 99);
        Pile pile = new Pile(world);
        pile.add(block(0), 4, 0L);
        assertFalse(pile.unstable(block(0)), "four on four is stable");

        pile.lowerGround(1, 200L, 0L);
        assertEquals(3, pile.capacityAt(block(0), 0L), "criticality drops the floor out");
        assertTrue(pile.settling(), "and everything standing on it is now armed");
        pile.settle(only(Fault.BLOOM), 64, 0L);
        assertEquals(4, pile.stressAt(block(1)));

        assertEquals(4, pile.capacityAt(block(0), 300L), "the ground comes back up after the window");
    }

    // ---- the whole thesis ----------------------------------------------------------------------

    @Test
    void theSameStartingPileAlwaysProducesExactlyTheSameCascade() {
        Fracture fracture = new Fracture();
        fracture.set(0, Fault.SLUMP);
        fracture.set(1, Fault.HEAP);
        fracture.set(2, Fault.BLOOM);

        Map<PileSite, Integer> first = run(fracture);
        Map<PileSite, Integer> second = run(fracture);
        assertEquals(first, second, "there is no random number anywhere in this Authority");
    }

    private static Map<PileSite, Integer> run(Fracture fracture) {
        FakeWorld world = new FakeWorld();
        for (int x = 0; x < 8; x++) {
            world.neighbours.put(block(x), List.of(block(x + 1), block(x + 2)));
            world.height.put(block(x), (double) -x);
            world.capacity.put(block(x), 1 + (x % 3));
        }
        Pile pile = new Pile(world);
        pile.add(block(0), 9, 0L);
        for (int tick = 0; tick < 40 && pile.settling(); tick++) {
            pile.settle(fracture, 3, tick);
        }
        Map<PileSite, Integer> snapshot = new HashMap<>();
        for (PileSite site : pile.sites()) {
            snapshot.put(site, pile.stressAt(site));
        }
        return snapshot;
    }
}
