package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BeliefTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void gainClosesAFractionOfTheDistanceLeft() {
        Belief belief = new Belief();
        assertEquals(0.02F * 0.5F, belief.gain(1, 0, 0.5F, 1.0F, 1.0F, 1.0F), EPSILON);
        belief.set(1, 0, 0.5F);
        assertEquals(0.5F + 0.02F * 0.8F * 1.25F * 1.3F * 0.5F, belief.gain(1, 0, 0.8F, 1.25F, 1.3F, 1.0F), EPSILON);
    }

    @Test
    void decayIsSteadyAndNeverShatters() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.004F);
        belief.decay(1, 0);
        assertEquals(0.002F, belief.get(1, 0), EPSILON);
        belief.decay(1, 0);
        belief.decay(1, 0);
        assertEquals(0.0F, belief.get(1, 0), EPSILON);
        assertFalse(belief.shattered(1, 0));
    }

    @Test
    void aTouchBreaksAWeakBeliefAndDentsAStrongOne() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.65F);
        assertTrue(belief.contradict(1, 0, Contradiction.TOUCH), "0.65 - 0.60 is under 0.1");
        assertTrue(belief.shattered(1, 0));
        assertEquals(0.0F, belief.gain(1, 0, 1.0F, 1.0F, 1.0F, 1.0F), EPSILON, "a shattered element is not seen again");

        belief.set(2, 0, 0.9F);
        assertFalse(belief.contradict(2, 0, Contradiction.WITNESS));
        assertEquals(0.70F, belief.get(2, 0), EPSILON);
        assertFalse(belief.contradict(2, 0, Contradiction.PROJECTILE));
        assertEquals(0.35F, belief.get(2, 0), EPSILON);
        assertFalse(belief.contradict(2, 0, Contradiction.WITNESS));
        assertTrue(belief.contradict(2, 0, Contradiction.HOLLOW_STRIKE), "0.15 - 0.25");
    }

    @Test
    void aViewerWhoNeverBelievedHasNothingToShatter() {
        Belief belief = new Belief();
        assertFalse(belief.contradict(5, 0, Contradiction.TOUCH));
        assertFalse(belief.shattered(5, 0));
    }

    @Test
    void aFirstHandBlowShattersAViewerWhoNeverBelieved() {
        Belief belief = new Belief();
        assertTrue(belief.expose(5, 0, Contradiction.TOUCH), "walking through a wall you never saw is seeing through it");
        assertTrue(belief.shattered(5, 0));
        assertEquals(0.0F, belief.gain(5, 0, 1.0F, 1.0F, 1.0F, 1.0F), EPSILON, "and it never fades in afterwards");
        assertFalse(belief.expose(5, 0, Contradiction.TOUCH), "a shattered element cannot shatter twice");

        belief.set(6, 0, 0.9F);
        assertFalse(belief.expose(6, 0, Contradiction.HOLLOW_STRIKE), "a strong belief is only dented, as by contradict");
        assertEquals(0.65F, belief.get(6, 0), EPSILON);
    }

    @Test
    void scepticismForgetsEverythingWhenCleared() {
        Scepticism scepticism = new Scepticism();
        Set<String> wall = Set.of("block:minecraft:stone");
        scepticism.seenThrough("u", wall, 0L);
        scepticism.clear();
        assertEquals(1.0F, scepticism.novelty("u", wall, 10L), EPSILON);
    }

    @Test
    void convincedIsAtOneHalf() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.49F);
        assertFalse(belief.convinced(1, 0));
        belief.set(1, 0, 0.5F);
        assertTrue(belief.convinced(1, 0));
    }

    @Test
    void theWorkedZombieIsCertainInAboutTwoPointSevenSeconds() {
        float senses = Sense.multiplier(EnumSet.of(Sense.SOUND, Sense.SCENT));
        int ticks = Belief.ticksToReach(Belief.SURE, 0.82F, senses, 1.3F, 1.0F);
        assertEquals(55, ticks);
        assertEquals(-1, Belief.ticksToReach(Belief.SURE, 0.82F, senses, 0.0F, 1.0F), "the wielder never believes");
    }

    @Test
    void forgettingAViewerDropsOnlyItsRows() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.3F);
        belief.set(1, 1, 0.3F);
        belief.set(2, 0, 0.3F);
        belief.forget(1);
        List<Belief.Row> rows = belief.rows();
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).viewer());
    }

    @Test
    void theSameTrickTwiceIsHalfATrick() {
        Scepticism scepticism = new Scepticism();
        Set<String> wall = Set.of("block:minecraft:stone");
        assertEquals(1.0F, scepticism.novelty("u", wall, 0L), EPSILON);
        scepticism.seenThrough("u", wall, 0L);
        assertEquals(0.5F, scepticism.novelty("u", wall, 100L), EPSILON);
        scepticism.seenThrough("u", wall, 100L);
        assertEquals(0.25F, scepticism.novelty("u", Set.of("block:minecraft:stone", "block:minecraft:dirt"), 200L), EPSILON);
        assertEquals(1.0F, scepticism.novelty("u", wall, 100L + Scepticism.MEMORY_TICKS + 1), EPSILON, "the memory fades");
        assertEquals(1.0F, scepticism.novelty("someone else", wall, 200L), EPSILON);
    }

    @Test
    void anUnveilCostsTenAMarkPerBlockFivePerFigmentAndTenPerSenseLayer() {
        Lexicon lexicon = new Lexicon();
        lexicon.gaze("block:minecraft:grass_block");
        lexicon.gaze("creature:minecraft:villager");
        Reverie reverie = new Reverie();
        for (int i = 0; i < 5; i++) {
            reverie.addBlock(new Offset(i, 0, 0), "minecraft:grass_block", lexicon);
        }
        reverie.setClusterSenses(new Offset(0, 0, 0), EnumSet.of(Sense.SHADOW));
        reverie.addFigment(new Offset(0, 1, 2), "minecraft:villager", lexicon);
        reverie.setFigmentSenses(0, EnumSet.of(Sense.SOUND, Sense.SHADOW));
        assertEquals(10 + 5 + 5 + 30, UnveilCost.of(reverie));
    }

    @Test
    void consensusSumsOnlyTheConvincedEachByItsWeight() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.6F);
        belief.set(2, 0, 0.49F);
        belief.set(3, 0, 1.0F);
        belief.set(1, 1, 0.8F);
        belief.set(1, 5, 0.9F);
        float[] sums = belief.consensus(2, viewer -> viewer == 3 ? 3.0 : 1.0);
        assertEquals(2, sums.length);
        assertEquals(0.6F + 3.0F, sums[0], EPSILON);
        assertEquals(0.8F, sums[1], EPSILON);
    }

    @Test
    void aNudgeMovesBeliefWithoutEverShatteringIt() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.3F);
        belief.nudge(1, 0, 0.01F);
        assertEquals(0.31F, belief.get(1, 0), EPSILON);
        belief.set(1, 0, 0.05F);
        belief.nudge(1, 0, -0.01F);
        assertEquals(0.04F, belief.get(1, 0), EPSILON);
        assertFalse(belief.shattered(1, 0));
        belief.nudge(1, 0, -0.5F);
        assertEquals(0.0F, belief.get(1, 0), EPSILON);
        assertFalse(belief.shattered(1, 0), "doubt pushed to nothing is not a contradiction");
        belief.nudge(2, 0, -0.01F);
        assertEquals(0.0F, belief.get(2, 0), EPSILON);
        belief.nudge(2, 0, 0.10F);
        assertEquals(0.10F, belief.get(2, 0), EPSILON);
        belief.set(3, 0, 0.95F);
        belief.nudge(3, 0, 0.10F);
        assertEquals(1.0F, belief.get(3, 0), EPSILON);
    }

    @Test
    void aShatteredRowIsDeafToANudge() {
        Belief belief = new Belief();
        assertTrue(belief.expose(4, 0, Contradiction.TOUCH));
        belief.nudge(4, 0, 0.10F);
        assertTrue(belief.shattered(4, 0));
        assertEquals(0.0F, belief.get(4, 0), EPSILON);
    }
}
