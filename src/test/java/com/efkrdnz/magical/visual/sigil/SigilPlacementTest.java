package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.visual.sigil.SigilPlacement;
import com.efkrdnz.magical.magic.visual.sigil.SigilPlacement.Spawn;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SigilPlacementTest {
    private static final double EPS = 1e-9;

    @Test
    void aCrownRingsTheHeadWhereLongDebtWroteIt() {
        // an iron golem: 1.4 wide, 2.7 tall
        List<Spawn> crown = SigilPlacement.crown(10.0, 64.0, -5.0, 1.4, 2.7, 8, 8);
        assertEquals(8, crown.size());
        for (int i = 0; i < 8; i++) {
            Spawn s = crown.get(i);
            assertEquals(64.0 + 2.7 + 0.25, s.y(), EPS);
            assertEquals(0.8, Math.hypot(s.x() - 10.0, s.z() + 5.0), EPS);
            double angle = Math.PI * 2.0 * i / 8;
            assertEquals(Math.cos(angle) * 0.8, s.x() - 10.0, EPS);
            assertEquals(Math.sin(angle) * 0.8, s.z() + 5.0, EPS);
            assertEquals(0.0, s.vx(), EPS);
            assertEquals(0.0, s.vy(), EPS);
            assertEquals(0.0, s.vz(), EPS);
        }
    }

    @Test
    void aPartCrownWritesOnlyItsFirstSlotsInTheirOwnPlaces() {
        List<Spawn> full = SigilPlacement.crown(0.0, 0.0, 0.0, 0.6, 1.8, 8, 8);
        List<Spawn> part = SigilPlacement.crown(0.0, 0.0, 0.0, 0.6, 1.8, 8, 3);
        assertEquals(3, part.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(full.get(i), part.get(i));
        }
        // a narrow body still gets a ring clear of its head
        assertEquals(0.55, Math.hypot(full.get(0).x(), full.get(0).z()), EPS);
        assertEquals(0, SigilPlacement.crown(0.0, 0.0, 0.0, 0.6, 1.8, 8, 0).size());
        assertEquals(8, SigilPlacement.crown(0.0, 0.0, 0.0, 0.6, 1.8, 8, 20).size());
    }

    @Test
    void aPopRisesFromJustOverTheHead() {
        Spawn pop = SigilPlacement.pop(1.0, 70.0, 2.0, 1.8);
        assertEquals(1.0, pop.x(), EPS);
        assertEquals(2.0, pop.z(), EPS);
        assertEquals(70.0 + 1.8 + SigilPlacement.POP_GAP, pop.y(), EPS);
        assertEquals(SigilPlacement.POP_LIFT, pop.vy(), EPS);
        assertTrue(pop.vy() > 0.0);
    }

    @Test
    void aHaloCirclesTheChestEvenly() {
        List<Spawn> halo = SigilPlacement.halo(0.0, 0.0, 0.0, 0.6, 1.8, 6, 0.3);
        assertEquals(6, halo.size());
        for (int i = 0; i < 6; i++) {
            Spawn s = halo.get(i);
            assertEquals(1.8 * SigilPlacement.HALO_HEIGHT, s.y(), EPS);
            double ring = 0.3 + SigilPlacement.HALO_GAP;
            assertEquals(ring, Math.hypot(s.x(), s.z()), EPS);
            double angle = 0.3 + Math.PI * 2.0 * i / 6;
            assertEquals(Math.cos(angle) * ring, s.x(), EPS);
            assertEquals(Math.sin(angle) * ring, s.z(), EPS);
        }
    }

    @Test
    void aBurstGoesEveryWayAtItsSpeed() {
        List<Spawn> burst = SigilPlacement.burst(0.0, 0.0, 0.0, 24, 0.2);
        assertEquals(24, burst.size());
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        Set<String> directions = new HashSet<>();
        for (Spawn s : burst) {
            assertEquals(0.2, Math.sqrt(s.vx() * s.vx() + s.vy() * s.vy() + s.vz() * s.vz()), 1e-9);
            sx += s.vx();
            sy += s.vy();
            sz += s.vz();
            directions.add(Math.round(s.vx() * 1000) + "," + Math.round(s.vy() * 1000) + "," + Math.round(s.vz() * 1000));
        }
        assertEquals(24, directions.size());
        assertTrue(Math.sqrt(sx * sx + sy * sy + sz * sz) < 0.2, "a burst leans nowhere");
    }

    @Test
    void aRiseStaysInsideItsDiscAndGoesUp() {
        List<Spawn> rise = SigilPlacement.rise(5.0, 64.0, 5.0, 3.0, 50, new Random(7));
        assertEquals(50, rise.size());
        for (Spawn s : rise) {
            assertTrue(Math.hypot(s.x() - 5.0, s.z() - 5.0) <= 3.0 + EPS);
            assertEquals(64.0, s.y(), EPS);
            assertEquals(SigilPlacement.RISE_SPEED, s.vy(), EPS);
        }
    }

    @Test
    void aWallStandsSquareToTheLookAndCentredOnItsPoint() {
        List<Spawn> wall = SigilPlacement.wall(3.0, 70.0, -2.0, 0.6, 0.8, 5, 3, 0.5);
        assertEquals(15, wall.size());
        double mx = 0.0;
        double my = 0.0;
        double mz = 0.0;
        for (Spawn s : wall) {
            mx += s.x() / 15;
            my += s.y() / 15;
            mz += s.z() / 15;
            // in the plane through the centre square to the look
            assertEquals(0.0, (s.x() - 3.0) * 0.6 + (s.z() + 2.0) * 0.8, 1e-9);
        }
        assertEquals(3.0, mx, 1e-9);
        assertEquals(70.0, my, 1e-9);
        assertEquals(-2.0, mz, 1e-9);
        // row-major, a spacing apart along a row, a spacing down between rows
        assertEquals(0.5, Math.hypot(wall.get(1).x() - wall.get(0).x(), wall.get(1).z() - wall.get(0).z()), 1e-9);
        assertEquals(-0.5, wall.get(5).y() - wall.get(0).y(), 1e-9);
    }
}
