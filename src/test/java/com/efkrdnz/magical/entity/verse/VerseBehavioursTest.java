package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The flight arithmetic behind the behaviours, held without a level: gravity bends the flight,
 * a Seeker turns toward its target without changing speed, Errant turns only on its interval and
 * always the same way for the same seed, Serpentine rides beside the line and comes back to it,
 * a Gyre keeps its distance and grows it, a bounce reflects, twins part symmetrically, and every
 * behaviour fits the bitmask the entity syncs.
 */
class VerseBehavioursTest {

    private static final double EPS = 1.0E-9D;
    private static final Vec3 AHEAD = new Vec3(0.0D, 0.0D, 1.0D);

    @Test
    void gravityTakesFromTheHeightAndNothingElse() {
        Vec3 v = VerseBehaviours.steer(AHEAD, 0.04D, null, false, false, 1, 0);
        assertEquals(0.0D, v.x, EPS);
        assertEquals(-0.04D, v.y, EPS);
        assertEquals(1.0D, v.z, EPS);
    }

    @Test
    void aSeekerTurnsTowardItsTargetAndKeepsItsSpeed() {
        Vec3 v = VerseBehaviours.steer(AHEAD.scale(1.6D), 0.0D, new Vec3(5.0D, 0.0D, 0.0D), true, false, 1, 0);
        assertTrue(v.x > 0.0D, "it turned toward +x: " + v);
        assertTrue(v.z > 0.0D, "and still mostly flies ahead: " + v);
        assertEquals(1.6D, v.length(), 1.0E-6D);
    }

    @Test
    void errantTurnsOnItsIntervalOnlyAndIsDecidedByTheSeed() {
        Vec3 still = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL - 1, 7);
        assertEquals(0.0D, AHEAD.distanceTo(still), EPS);
        Vec3 turned = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL, 7);
        assertNotEquals(0.0D, AHEAD.distanceTo(turned), "it turns on the interval");
        assertEquals(1.0D, turned.length(), 1.0E-6D);
        Vec3 again = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL, 7);
        assertEquals(0.0D, turned.distanceTo(again), EPS);
        Vec3 other = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL, 8);
        assertNotEquals(0.0D, turned.distanceTo(other), "another seed turns another way");
    }

    @Test
    void aRollIsWithinOneEitherWayAndRepeatable() {
        for (int tick = 0; tick < 200; tick++) {
            double roll = VerseBehaviours.roll(3, tick, 1);
            assertTrue(roll >= -1.0D && roll <= 1.0D, "roll " + roll);
            assertEquals(roll, VerseBehaviours.roll(3, tick, 1), EPS);
        }
    }

    @Test
    void serpentineRidesBesideTheLineAndReturnsToItEveryPeriod() {
        Vec3 sum = Vec3.ZERO;
        for (int tick = 1; tick <= (int) VerseBehaviours.SERPENTINE_PERIOD_TICKS; tick++) {
            Vec3 offset = VerseBehaviours.serpentineOffset(AHEAD, tick);
            assertEquals(0.0D, offset.dot(AHEAD), EPS, "the offset is beside the line");
            sum = sum.add(offset);
        }
        assertEquals(0.0D, sum.length(), 1.0E-6D, "a whole period nets nothing");
        assertNotEquals(0.0D, VerseBehaviours.serpentineOffset(AHEAD, 2).length(), "and it does move");
    }

    @Test
    void aGyreKeepsItsDistanceAndWidensIt() {
        Vec3 first = VerseBehaviours.gyreOffset(AHEAD, 0);
        Vec3 later = VerseBehaviours.gyreOffset(AHEAD, 10);
        assertEquals(VerseBehaviours.GYRE_RADIUS, first.horizontalDistance(), EPS);
        assertEquals(VerseBehaviours.GYRE_RADIUS + 10 * VerseBehaviours.GYRE_CLIMB, later.horizontalDistance(), EPS);
        assertNotEquals(0.0D, first.subtract(later).horizontalDistance(), "it has moved round");
    }

    @Test
    void aBounceReflectsOffTheFaceAndKeepsTheSpeed() {
        Vec3 v = VerseBehaviours.bounce(new Vec3(1.0D, -1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
        assertEquals(1.0D, v.x, EPS);
        assertEquals(1.0D, v.y, EPS);
        assertEquals(0.0D, v.z, EPS);
    }

    @Test
    void twinsPartSymmetricallyAndStayUnit() {
        Vec3 left = VerseBehaviours.twin(AHEAD, true);
        Vec3 right = VerseBehaviours.twin(AHEAD, false);
        assertEquals(1.0D, left.length(), 1.0E-6D);
        assertEquals(-left.x, right.x, EPS);
        assertEquals(left.z, right.z, EPS);
        assertNotEquals(0.0D, left.x, "they do part");
    }

    @Test
    void everyBehaviourFitsTheMaskAndComesBackOut() {
        int mask = VerseBehaviours.mask(List.of(Behaviour.values()));
        for (Behaviour behaviour : Behaviour.values()) {
            assertTrue(VerseBehaviours.has(mask, behaviour), behaviour + " is in the mask");
        }
        int two = VerseBehaviours.mask(EnumSet.of(Behaviour.SEEKER, Behaviour.NAUGHT));
        assertTrue(VerseBehaviours.has(two, Behaviour.SEEKER));
        assertTrue(VerseBehaviours.has(two, Behaviour.NAUGHT));
        assertFalse(VerseBehaviours.has(two, Behaviour.PUNCTURE));
        assertTrue(Behaviour.values().length <= 31, "the mask is an int");
    }

    @Test
    void theSideOfAVerticalFlightIsStillASide() {
        Vec3 side = VerseBehaviours.sideOf(new Vec3(0.0D, 1.0D, 0.0D));
        assertEquals(1.0D, side.length(), 1.0E-6D);
        assertEquals(0.0D, side.y, EPS);
    }
}
