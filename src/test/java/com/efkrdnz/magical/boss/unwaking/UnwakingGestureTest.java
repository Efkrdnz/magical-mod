package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class UnwakingGestureTest {
    @Test void stationaryPlayerInsideIsHitAndOutsideIsSafe() {
        UnwakingGesture gesture = UnwakingGesture.aim(Vec3.ZERO, new Vec3(0, 0, 10));
        assertTrue(gesture.intersects(new Vec3(0, 0, 6), new Vec3(0, 0, 6), 0.6, 1.8));
        assertFalse(gesture.intersects(new Vec3(3, 0, 6), new Vec3(3, 0, 6), 0.6, 1.8));
        assertFalse(gesture.intersects(new Vec3(0, 4, 6), new Vec3(0, 4, 6), 0.6, 1.8));
    }

    @Test void fastMovementAcrossTheLockedPlaneDoesNotTunnel() {
        UnwakingGesture gesture = UnwakingGesture.aim(Vec3.ZERO, new Vec3(0, 0, 10));
        assertTrue(gesture.intersects(new Vec3(-8, 0, 6), new Vec3(8, 0, 6), 0.6, 1.8));
        assertFalse(gesture.intersects(new Vec3(-8, 4, 6), new Vec3(8, 4, 6), 0.6, 1.8));
    }

    @Test void verticalAndZeroLengthAimingProduceFiniteOrthonormalFrames() {
        for (Vec3 target : new Vec3[] { Vec3.ZERO, new Vec3(0, 10, 0), new Vec3(3, 4, 5) }) {
            UnwakingGesture gesture = UnwakingGesture.aim(Vec3.ZERO, target);
            assertEquals(1, gesture.forward().length(), 1e-8);
            assertEquals(1, gesture.right().length(), 1e-8);
            assertEquals(1, gesture.up().length(), 1e-8);
            assertEquals(0, gesture.forward().dot(gesture.right()), 1e-8);
            assertTrue(gesture.intersects(gesture.world(0, 0, 6), gesture.world(0, 0, 6), 0.6, 1.8));
        }
    }

    @Test void theHitboxIncludesPlayerWidthButNotTheExtraWarningMargin() {
        UnwakingGesture gesture = UnwakingGesture.aim(Vec3.ZERO, new Vec3(0, 0, 10));
        assertTrue(gesture.intersects(new Vec3(1, 0, 6), new Vec3(1, 0, 6), 0.6, 1.8));
        assertFalse(gesture.intersects(new Vec3(1.2, 0, 6), new Vec3(1.2, 0, 6), 0.6, 1.8));
    }
}
