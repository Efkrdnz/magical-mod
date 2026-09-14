package com.efkrdnz.magical.client.renderer.eldritch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The motion the code owns: a chain that reaches what it grabs, grows from its base and stays
 * within what a tentacle can bend; jaws that open, snap and chatter; a gaze that rolls.
 */
class EldritchPoseTest {

    private static final int N = EldritchPose.SEGMENTS;
    private static final float L = 4.0F;

    private static float[] chainTo(float forward, float up, float age) {
        float[] pitch = new float[N];
        EldritchPose.chain(N, L, forward, up, true, age, 0.0F, pitch);
        return pitch;
    }

    private static float distanceToTarget(float[] pitch, float forward, float up) {
        float[] tip = new float[2];
        EldritchPose.tip(L, pitch, N, tip);
        return (float) Math.hypot(tip[0] - forward, tip[1] - up);
    }

    @Test
    void theTipLandsOnATargetWithinReach() {
        for (float[] target : new float[][] {{10, 8}, {3, 20}, {-6, 12}, {16, 2}}) {
            float[] pitch = chainTo(target[0], target[1], 0.0F);
            assertTrue(distanceToTarget(pitch, target[0], target[1]) < 0.5F,
                    "tip misses (" + target[0] + "," + target[1] + ") by " + distanceToTarget(pitch, target[0], target[1]));
        }
    }

    @Test
    void aTargetBeyondReachIsPointedAtAlongAStraightChain() {
        float[] pitch = chainTo(40.0F, 30.0F, 0.0F);
        float[] tip = new float[2];
        EldritchPose.tip(L, pitch, N, tip);
        float reach = N * L;
        assertEquals(reach, (float) Math.hypot(tip[0], tip[1]), 0.05F, "fully extended");
        assertEquals(Math.atan2(40.0, 30.0), Math.atan2(tip[0], tip[1]), 0.02D, "toward the target");
    }

    @Test
    void noJointBendsFurtherThanATentacleCan() {
        for (float[] target : new float[][] {{2, -5}, {-10, -3}, {0, 1}, {12, 12}}) {
            for (float pitch : chainTo(target[0], target[1], 7.0F)) {
                assertTrue(Math.abs(pitch) <= EldritchPose.MAX_BEND + 1.0E-4F, "bend " + pitch);
            }
        }
    }

    @Test
    void thePoseIsContinuousInTime() {
        float[] before = chainTo(10.0F, 8.0F, 3.0F);
        float[] after = chainTo(10.0F, 8.0F, 3.05F);
        for (int i = 0; i < N; i++) {
            assertTrue(Math.abs(after[i] - before[i]) < 0.02F, "joint " + i + " jumped " + (after[i] - before[i]));
        }
        float[] idle = new float[N];
        EldritchPose.chain(N, L, 0.0F, 0.0F, false, 3.0F, 0.3F, idle);
        float[] idleLater = new float[N];
        EldritchPose.chain(N, L, 0.0F, 0.0F, false, 3.05F, 0.3F, idleLater);
        for (int i = 0; i < N; i++) {
            assertTrue(Math.abs(idleLater[i] - idle[i]) < 0.02F, "idle joint " + i + " jumped");
        }
    }

    @Test
    void aChainGrowsFromItsBaseOneSegmentAfterAnother() {
        assertEquals(0.0F, EldritchPose.grown(0.0F, 12.0F, N));
        assertEquals(N, EldritchPose.grown(12.0F, 12.0F, N));
        assertEquals(N, EldritchPose.grown(40.0F, 12.0F, N), "and stays grown");
        float half = EldritchPose.grown(6.0F, 12.0F, N);
        assertEquals(1.0F, EldritchPose.segmentScale(half, 0), "the base is whole");
        assertEquals(1.0F, EldritchPose.segmentScale(half, 2));
        assertEquals(0.0F, EldritchPose.segmentScale(half, 5), "the tip is not there yet");
        assertEquals(0.5F, EldritchPose.segmentScale(2.5F, 2), 1.0E-6F, "the growing one is partly there");
    }

    @Test
    void aConstructDissolvesOverItsLastTicksAndNotBefore() {
        assertEquals(1.0F, EldritchPose.dissolve(10.0F, 100.0F, 20.0F));
        assertEquals(1.0F, EldritchPose.dissolve(80.0F, 100.0F, 20.0F));
        assertEquals(0.5F, EldritchPose.dissolve(90.0F, 100.0F, 20.0F), 1.0E-6F);
        assertEquals(0.0F, EldritchPose.dissolve(100.0F, 100.0F, 20.0F));
        assertEquals(1.0F, EldritchPose.dissolve(500.0F, 0.0F, 20.0F), "unbounded life never dissolves");
    }

    @Test
    void jawsOpenOverTheWindupSnapShutInThreeTicksAndChatter() {
        assertEquals(0.0F, EldritchPose.jaws(0.0F, 20.0F, 0.0F));
        assertEquals(0.5F, EldritchPose.jaws(10.0F, 20.0F, 0.0F), 1.0E-6F);
        assertEquals(1.0F, EldritchPose.jaws(30.0F, 20.0F, 0.0F), "held open while it waits");
        assertEquals(1.0F, EldritchPose.jaws(30.0F, 20.0F, 30.0F), 1.0E-6F, "the snap begins where it was");
        assertEquals(0.0F, EldritchPose.jaws(33.0F, 20.0F, 30.0F), 1.0E-6F, "and is shut three ticks later");
        float chatter = EldritchPose.jaws(35.0F, 20.0F, 30.0F);
        assertTrue(chatter >= 0.0F && chatter < 0.2F, "a small chatter after: " + chatter);
        assertTrue(EldritchPose.jaws(60.0F, 20.0F, 30.0F) < 0.02F, "which dies away");
    }

    @Test
    void aGazeRollsTheShortWayRoundAndAPupilContractsOnItsMark() {
        assertEquals(0.1F, EldritchPose.ease(0.0F, 1.0F, 0.1F), 1.0E-6F);
        float across = EldritchPose.ease(3.0F, -3.0F, 0.5F);
        assertTrue(across > 3.0F || across < -3.0F, "from 3 to -3 the short way is through pi, not zero: " + across);
        assertTrue(EldritchPose.pupil(true, 5.0F) < EldritchPose.pupil(false, 5.0F));
    }
}
