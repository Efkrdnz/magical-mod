package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import org.junit.jupiter.api.Test;

/**
 * Where a held formation hangs, which the server and the renderer both work out.
 *
 * <p>The server works it out from the wielder's position on the tick and the renderer from the
 * wielder's position between ticks, which is what keeps a formation on a walking wielder smooth -
 * so the two must be the same formula, or the drawn swords and the ones that hit part company.
 */
class ArrayPoseTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    void aBodyStanceHangsFromTheChestAndALookStanceFromTheEye() {
        double[] body = ArrayPose.heldOrigin(SwordStance.Anchor.BODY, 10.0D, 64.0D, -3.0D, 65.62D);
        assertArrayEquals(new double[] {10.0D, 64.0D + ArrayPose.BODY_CENTRE, -3.0D}, body, EPSILON);
        double[] look = ArrayPose.heldOrigin(SwordStance.Anchor.LOOK, 10.0D, 64.0D, -3.0D, 65.62D);
        assertArrayEquals(new double[] {10.0D, 65.62D, -3.0D}, look, EPSILON);
    }

    /**
     * Crown and Coil spin on their own clock, and a frame that also swung round whenever the
     * wielder turned the camera was two rotations fighting - so an orbit, once placed, keeps its
     * bearing while its wielder turns. Every other stance still comes round.
     */
    @Test
    void onlyTheOrbitsHoldTheirBearingWhileTheWielderTurns() {
        for (SwordStance stance : SwordStance.values()) {
            float next = ArrayPose.nextYaw(stance, 30.0F, 120.0F, 1.0D);
            if (stance == SwordStance.CROWN || stance == SwordStance.COIL) {
                assertEquals(30.0F, next, 1.0E-6F, stance + " turned with its wielder");
            } else {
                assertTrue(next > 30.0F && next <= 120.0F, stance + " did not come round: " + next);
            }
        }
    }

    /** Called fresh, an orbit is placed in its wielder's frame and holds from there. */
    @Test
    void anOrbitTakesItsWielderBearingOnceWhenItIsCalled() {
        assertEquals(120.0F, ArrayPose.nextYaw(SwordStance.CROWN, 30.0F, 120.0F, 0.0D), 1.0E-6F);
        assertEquals(120.0F, ArrayPose.nextYaw(SwordStance.COIL, 30.0F, 120.0F, 0.0D), 1.0E-6F);
    }

    /** Only a formation on the body follows the body: one set down or sunk stays where it was put. */
    @Test
    void onlyAHeldOrRiddenFrameFollowsTheWielder() {
        assertTrue(Bind.HELD.followsTheWielder());
        assertTrue(Bind.RIDDEN.followsTheWielder(), "a rider is pinned to their own frame");
        assertFalse(Bind.SET.followsTheWielder());
        assertFalse(Bind.SUNK.followsTheWielder());
    }
}
