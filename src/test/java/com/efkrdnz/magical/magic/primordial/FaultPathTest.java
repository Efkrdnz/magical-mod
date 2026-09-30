package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A crack walks away from the caster, never under them, two wide, each column once, in order. */
class FaultPathTest {

    @Test
    void aCrackEastIsTwoColumnsWide() {
        List<FaultPath.Cell> cells = FaultPath.columns(0.5D, 0.5D, 1.0D, 0.0D, 10, 2);
        Set<Integer> zs = new HashSet<>();
        for (FaultPath.Cell cell : cells) {
            zs.add(cell.z());
            assertTrue(cell.x() >= 1, "never under the caster's feet");
            assertTrue(cell.x() <= 12);
        }
        assertEquals(Set.of(0, 1), zs);
    }

    @Test
    void eachColumnOnceInTheOrderReached() {
        List<FaultPath.Cell> cells = FaultPath.columns(3.2D, -7.9D, 0.6D, 0.8D, 20, 2);
        Set<Long> seen = new HashSet<>();
        int lastStep = 0;
        for (FaultPath.Cell cell : cells) {
            assertTrue(seen.add(((long) cell.x() << 32) ^ (cell.z() & 0xFFFFFFFFL)), "a column listed twice");
            assertTrue(cell.step() >= lastStep, "the crack never goes back");
            lastStep = cell.step();
        }
        assertTrue(lastStep >= 19 && lastStep <= 20, "the crack runs its whole length, got " + lastStep);
    }

    @Test
    void theCasterColumnIsNeverOpened() {
        for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += 0.3D) {
            List<FaultPath.Cell> cells = FaultPath.columns(10.5D, 10.5D, Math.cos(angle), Math.sin(angle), 8, 2);
            for (FaultPath.Cell cell : cells) {
                assertFalse(cell.x() == 10 && cell.z() == 10, "a crack opened under its caster at angle " + angle);
            }
        }
    }
}
