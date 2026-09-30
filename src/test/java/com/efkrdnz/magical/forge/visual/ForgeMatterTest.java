package com.efkrdnz.magical.forge.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.visual.ForgeMatter.Emission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What each element throws, and how much. A share that rounds to nothing is a kind of matter an
 * element never shows; a count that grows past its cap is a divine flurry that fills the particle
 * engine; and a shower thinned by position rather than within each share loses a whole kind the
 * moment the particle setting is turned down.
 */
class ForgeMatterTest {

    private static final int DIVINE = ForgeGrade.DIVINE.ordinal();

    @Test
    void everyElementThrowsSomethingAndEveryShareIsSane() {
        for (ForgeElementKind kind : ForgeElementKind.values()) {
            List<Emission> swing = ForgeMatter.swing(kind);
            assertFalse(swing.isEmpty(), kind + " throws nothing");
            Set<MatterKind> seen = new HashSet<>();
            for (Emission e : swing) {
                assertTrue(e.weight() > 0, kind + " has a share of nothing");
                assertTrue(e.speed() > 0.0f && e.speed() < 1.0f, kind + " throws " + e.kind() + " at " + e.speed());
                assertTrue(e.cone() >= 0.0f && e.cone() <= 1.0f, kind.name());
                assertTrue(seen.add(e.kind()), kind + " lists " + e.kind() + " twice");
                assertTrue(e.kind() != MatterKind.NICK, kind + " throws the hit flash off a swing");
            }
        }
    }

    @Test
    void aBetterWeaponThrowsMoreAndAHeavyBlowMoreStill() {
        int previous = 0;
        for (ForgeGrade grade : ForgeGrade.values()) {
            int light = ForgeMatter.swingCount(grade.ordinal(), false, false);
            int heavy = ForgeMatter.swingCount(grade.ordinal(), true, false);
            int echo = ForgeMatter.swingCount(grade.ordinal(), false, true);
            assertTrue(light >= previous, grade + " throws less than the grade below it");
            assertTrue(heavy >= light, grade + " heavy throws less than light");
            assertTrue(echo <= (light + 1) / 2, grade + " echo throws more than half");
            assertTrue(echo >= 1, grade + " echo throws nothing");
            previous = light;
        }
        assertEquals(ForgeMatter.FEWEST, ForgeMatter.shower(0));
        assertEquals(ForgeMatter.MOST, ForgeMatter.shower(DIVINE));
    }

    @Test
    void noSwingThrowsPastItsCap() {
        assertTrue(ForgeMatter.swingCount(DIVINE, true, false) <= ForgeMatter.SWING_CAP);
        assertTrue(ForgeMatter.swingCount(Integer.MAX_VALUE, true, false) <= ForgeMatter.SWING_CAP);
    }

    @Test
    void anUnknownGradeStillThrowsSomething() {
        assertTrue(ForgeMatter.shower(-1) >= ForgeMatter.FEWEST);
        assertTrue(ForgeMatter.shower(99) <= ForgeMatter.MOST);
    }

    @Test
    void theSharesAddUpToTheShowerExactly() {
        for (ForgeElementKind kind : ForgeElementKind.values()) {
            List<Emission> swing = ForgeMatter.swing(kind);
            for (int total = 0; total <= ForgeMatter.SWING_CAP; total++) {
                int[] counts = ForgeMatter.split(swing, total);
                int sum = 0;
                for (int count : counts) {
                    sum += count;
                }
                assertEquals(total, sum, kind + " at " + total);
                int[] dealt = ForgeMatter.deal(swing, total);
                assertEquals(total, dealt.length, kind + " at " + total);
                int[] tally = new int[swing.size()];
                for (int index : dealt) {
                    tally[index]++;
                }
                for (int i = 0; i < counts.length; i++) {
                    assertEquals(counts[i], tally[i], kind + " deals a share it was not given");
                }
            }
        }
    }

    @Test
    void aThinnedShowerKeepsSomeOfEveryKindAndAboutHalfOfItAll() {
        for (ForgeElementKind kind : ForgeElementKind.values()) {
            List<Emission> swing = ForgeMatter.swing(kind);
            for (int total = 1; total <= ForgeMatter.SWING_CAP; total++) {
                int[] counts = ForgeMatter.split(swing, total);
                int[] dealt = ForgeMatter.deal(swing, total);
                boolean[] kept = ForgeMatter.keep(dealt, swing.size(), 2);
                int[] survived = new int[swing.size()];
                for (int i = 0; i < dealt.length; i++) {
                    survived[dealt[i]] += kept[i] ? 1 : 0;
                }
                for (int i = 0; i < counts.length; i++) {
                    assertEquals((counts[i] + 1) / 2, survived[i],
                            kind + " at " + total + " thins its " + swing.get(i).kind() + " wrongly");
                }
            }
        }
    }

    @Test
    void aHitThrowsWhatItsSwingThrowsOutOfTheWoundAndLessOfIt() {
        for (ForgeElementKind kind : ForgeElementKind.values()) {
            List<Emission> swing = ForgeMatter.swing(kind);
            List<Emission> hit = ForgeMatter.hit(kind);
            assertEquals(swing.size(), hit.size(), kind.name());
            for (int i = 0; i < swing.size(); i++) {
                assertEquals(swing.get(i).kind(), hit.get(i).kind(), kind + " hits with other matter than it swings");
                assertTrue(hit.get(i).launch() == ForgeMatter.Launch.UP || hit.get(i).launch() == ForgeMatter.Launch.OUT,
                        kind + " throws hit matter along a blade it no longer has");
            }
        }
        for (ForgeGrade grade : ForgeGrade.values()) {
            assertTrue(ForgeMatter.hitCount(grade.ordinal(), false, false)
                    <= ForgeMatter.swingCount(grade.ordinal(), false, false));
            assertTrue(ForgeMatter.hitCount(grade.ordinal(), true, false) <= ForgeMatter.HIT_CAP);
        }
    }

    @Test
    void anUnthinnedShowerKeepsEverything() {
        int[] dealt = ForgeMatter.deal(ForgeMatter.swing(ForgeElementKind.FIRE), 18);
        for (boolean kept : ForgeMatter.keep(dealt, 3, 1)) {
            assertTrue(kept);
        }
    }
}
