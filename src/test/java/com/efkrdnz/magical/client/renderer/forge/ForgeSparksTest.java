package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.chain.ForgeGrade;
import org.junit.jupiter.api.Test;

/**
 * The debris a forged strike throws off.
 *
 * <p>Every part of a strike was a smooth ribbon, and a picture made only of smooth ribbons reads as
 * fog however bright it is: there is nothing small in it, so there is no sense of scale and no sense
 * of force. Sparks are the small thing. They are also where the grade shows in a way brightness
 * cannot - a divine blade throws a shower and a crude one throws a few - so the two together say
 * more than either.
 *
 * <p>They are drawn from a seed rather than simulated, because a strike lives three ticks and a
 * client that dropped a frame must not see a different shower than its neighbour.
 */
class ForgeSparksTest {

    private static final float EPSILON = 1.0E-5f;
    private static final int SEED = 0x5F3A21;

    @Test
    void aBetterBladeThrowsMoreOfThem() {
        int last = -1;
        for (ForgeGrade grade : ForgeGrade.values()) {
            int count = ForgeSparks.count(grade.ordinal());
            assertTrue(count > last, grade + " throws no more sparks than the grade below it");
            last = count;
        }
    }

    @Test
    void evenTheWorstBladeThrowsSomething() {
        assertTrue(ForgeSparks.count(ForgeGrade.CRUDE.ordinal()) >= 2, "a crude strike throws nothing at all");
        assertTrue(ForgeSparks.count(ForgeGrade.DIVINE.ordinal()) <= 24,
                "a divine strike throws more sparks than a strike can afford to draw");
    }

    @Test
    void anUnknownGradeStillThrowsAMiddlingShower() {
        // The ordinal comes over the wire, and a client on another build must draw a strike rather
        // than a bare ribbon.
        int fallback = ForgeSparks.count(-3);
        assertEquals(ForgeSparks.count(404), fallback);
        assertTrue(fallback > ForgeSparks.count(ForgeGrade.CRUDE.ordinal()));
        assertTrue(fallback < ForgeSparks.count(ForgeGrade.DIVINE.ordinal()));
    }

    @Test
    void theSameStrikeThrowsTheSameSparksEveryFrame() {
        for (int index = 0; index < 16; index++) {
            for (int salt = 0; salt < 4; salt++) {
                assertEquals(ForgeSparks.unit(SEED, index, salt), ForgeSparks.unit(SEED, index, salt), EPSILON,
                        "spark " + index + " moved between two reads of the same frame");
            }
        }
    }

    @Test
    void everySparkGetsItsOwnPath() {
        // Drawn from one seed, so a weak hash would stack them all on top of each other and the
        // shower would read as one fat spark.
        boolean[] bucket = new boolean[8];
        for (int index = 0; index < 24; index++) {
            float where = ForgeSparks.unit(SEED, index, 1);
            bucket[Math.min(7, (int) (where * 8.0f))] = true;
        }
        int filled = 0;
        for (boolean hit : bucket) {
            filled += hit ? 1 : 0;
        }
        assertTrue(filled >= 6, "twenty-four sparks landed in only " + filled + " eighths of the arc");
    }

    @Test
    void aSparkNeverLeavesTheUnitInterval() {
        for (int seed = -5; seed < 40; seed++) {
            for (int index = 0; index < 24; index++) {
                float value = ForgeSparks.unit(seed * 7919, index, index % 5);
                assertTrue(value >= 0.0f && value < 1.0f, "spark " + index + " of seed " + seed + " gave " + value);
            }
        }
    }

    @Test
    void aSparkIsBornAfterTheCutAndIsSpentByTheEndOfIt() {
        assertEquals(0.0f, ForgeSparks.life(0.0f, 0.3f), EPSILON, "a spark flew before the blade reached it");
        assertEquals(0.0f, ForgeSparks.life(0.3f, 0.3f), EPSILON, "a spark flew at the instant of its birth");
        assertEquals(1.0f, ForgeSparks.life(1.0f, 0.3f), EPSILON, "a spark was still in the air when it ended");
        float last = -1.0f;
        for (int i = 0; i <= 20; i++) {
            float life = ForgeSparks.life(i / 20.0f, 0.3f);
            assertTrue(life >= last, "a spark flew backwards at progress " + i / 20.0f);
            last = life;
        }
    }

    @Test
    void aSparkThatIsBornAtTheVeryEndDoesNotDivideByNothing() {
        float life = ForgeSparks.life(1.0f, 1.0f);
        assertTrue(Float.isFinite(life), "a spark born on the last frame gave " + life);
        assertTrue(life >= 0.0f && life <= 1.0f, "a spark born on the last frame gave " + life);
    }

    @Test
    void sparksNeverOutrunTheStrikeThatThrewThem() {
        // Debris may leave the steel, but it may not fly so far that the picture teaches a reach
        // the hit shape will not honour. Everything here is a fraction of the arc's own radius.
        for (int index = 0; index < 24; index++) {
            for (int i = 0; i <= 20; i++) {
                float reach = ForgeSparks.reach(SEED, index, i / 20.0f);
                assertTrue(reach >= 0.0f, "spark " + index + " flew inside out: " + reach);
                assertTrue(reach <= 0.5f, "spark " + index + " outran its own strike: " + reach);
            }
        }
    }
}
