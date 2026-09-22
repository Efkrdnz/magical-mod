package com.efkrdnz.magical.client.renderer.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.sword.SwordArrayRenderer.ThreadGeometry;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.efkrdnz.magical.magic.sword.stance.Slot;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Where a thread is drawn, measured rather than reasoned about.
 *
 * <p>The first capture of the Array showed a white bar running the whole height of the frame out
 * of every blade, and the arithmetic that drew it looked right on its face - which is the point.
 * A thread is three calls, {@code translate}, {@code orientAlong}, {@code beam}, and each one
 * carries an assumption that is invisible at the call site and produces a line to the horizon
 * when it is wrong: that the direction is normalised for you, that the tube starts at the local
 * origin rather than straddling it, and that the length is a whole length and not a half one. So
 * this replays all three - the real {@link FilamentPainter#orientAlong} and the real
 * {@link FxMesh#tube()}, not a restatement of them - and measures where the drawn tip lands.
 *
 * <p>The floor it pins is the one the capture disproved, and it is about the <em>near</em> end:
 * no part of a thread may be drawn inside {@link ThreadGeometry#NEAR_CLEAR} of the camera,
 * because a segment with an endpoint on the camera plane projects to infinity and sweeps the
 * frame. The far end is the regression guard beside it - it was already exactly right, and the
 * cheap way to pass the first test would be to shorten it, which would leave every thread
 * pointing at nothing.
 */
class SwordThreadTest {

    /** The pose is a float matrix, so a blade nine blocks out lands about a ten-thousandth off. */
    private static final double EPSILON = 1.0E-4D;

    /** Half a 1.8-block body: where {@code extractRenderState} puts the chest, in entity space. */
    private static final Vec3 CHEST = new Vec3(0.0D, 0.9D, 0.0D);

    /** The first-person camera, 1.62 up and horizontally coincident with the chest to the digit. */
    private static final Vec3 EYE = new Vec3(0.0D, 1.62D, 0.0D);

    /** A frame off every axis, so no test below can pass on a coincidence of zeroes. */
    private static final Frame FRAME = new Frame(0.0D, 0.0D, 0.0D, 37.0F, -12.0F, 1.4F);

    @Test
    void theTubeAThreadIsDrawnOnStartsAtTheOriginAndRunsAWholeLength() {
        // beam() emits FxMesh.tube() scaled by (halfWidth, halfWidth, length). Everything else
        // here depends on what that mesh's z actually spans: straddle the origin and the thread
        // would reach half its run backwards out of the wielder, and a half-length convention
        // would put the tip halfway to the blade. Both are one constant away at all times.
        float[] tube = FxMesh.tube();
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int i = 0; i + 5 <= tube.length; i += 5) {
            low = Math.min(low, tube[i + 2]);
            high = Math.max(high, tube[i + 2]);
        }
        assertEquals(0.0D, low, 1.0E-9D, "the tube no longer starts at the local origin");
        assertEquals(1.0D, high, 1.0E-9D, "the tube's length parameter is no longer a whole length");
    }

    @Test
    void aThreadEndsAtItsBlade() {
        // The far end, with the camera far enough away that nothing is trimmed: the drawn tip has
        // to land on the slot's own worldOffset, the same doubles the blade is placed with.
        Vec3 camera = new Vec3(30.0D, 2.0D, -18.0D);
        for (Slot slot : spread()) {
            double[] offset = ArrayPose.worldOffset(slot, FRAME);
            Vector3f tip = drawnTip(slot, camera);
            assertNotNull(tip, "no thread at all for " + slot);
            assertEquals(offset[0], tip.x(), EPSILON, "x at " + slot);
            assertEquals(offset[1], tip.y(), EPSILON, "y at " + slot);
            assertEquals(offset[2], tip.z(), EPSILON, "z at " + slot);
        }
    }

    @Test
    void aThreadNeverReachesTheCamera() {
        // The defect, as a number. Before the trim the near end was the chest, which in first
        // person is 0.72 blocks dead below the eye and horizontally on top of it - a point on the
        // camera plane, which the perspective divide sends off the bottom of the frame. Measure
        // the whole drawn segment, not just its ends: a thread to a sword behind the wielder
        // passes closest to a third-person camera somewhere in its middle.
        for (Vec3 camera : new Vec3[] {EYE, new Vec3(0.0D, 1.62D, -4.0D), new Vec3(2.5D, 3.0D, 2.5D)}) {
            for (Slot slot : spread()) {
                Vec3 start = drawnStart(slot, camera);
                if (start == null) {
                    continue;
                }
                double[] offset = ArrayPose.worldOffset(slot, FRAME);
                double near = distanceToSegment(camera, start, new Vec3(offset[0], offset[1], offset[2]));
                assertTrue(near >= ThreadGeometry.NEAR_CLEAR - EPSILON,
                        "a thread for " + slot + " is drawn " + near
                                + " blocks from a camera at " + camera + ", inside the "
                                + ThreadGeometry.NEAR_CLEAR + " it must keep clear of");
            }
        }
    }

    @Test
    void aTrimmedThreadStillEndsAtItsBlade() {
        // The other half of the same rule, and the reason the two tests live together: the cheap
        // way to keep a thread off the camera is to draw less of the far end, which would leave a
        // line stopping in mid-air short of the blade it is about. Only the start may move.
        for (Slot slot : spread()) {
            Vector3f tip = drawnTip(slot, EYE);
            if (tip == null) {
                continue;
            }
            double[] offset = ArrayPose.worldOffset(slot, FRAME);
            assertEquals(offset[0], tip.x(), EPSILON, "x at " + slot);
            assertEquals(offset[1], tip.y(), EPSILON, "y at " + slot);
            assertEquals(offset[2], tip.z(), EPSILON, "z at " + slot);
        }
    }

    @Test
    void nothingIsTakenOffAThreadAnOpponentIsReading() {
        // The trim is a property of where the camera is standing and of nothing else. The reading
        // the threads exist for is taken from across the arena, and at that range every one of
        // them is drawn whole - otherwise the opponent counts fewer blades than there are.
        Vec3 camera = new Vec3(-21.0D, 1.6D, 22.0D);
        for (Slot slot : spread()) {
            double[] offset = ArrayPose.worldOffset(slot, FRAME);
            double from = ThreadGeometry.start(CHEST.x, CHEST.y, CHEST.z,
                    offset[0], offset[1], offset[2], camera.x, camera.y, camera.z);
            assertEquals(0.0D, from, 0.0D, "a thread for " + slot + " was trimmed for a viewer "
                    + camera.distanceTo(CHEST) + " blocks away");
        }
    }

    @Test
    void aBladeInTheCameraLapIsNotThreadedAtAll() {
        // A sword behind the wielder can sit exactly where a third-person camera is. There is no
        // shortened thread that helps there - every point of the run within the clearance is one
        // the viewer is inside - so the answer is to draw none of it.
        for (Slot slot : spread()) {
            double[] offset = ArrayPose.worldOffset(slot, FRAME);
            Vec3 camera = new Vec3(offset[0], offset[1], offset[2]);
            double from = ThreadGeometry.start(CHEST.x, CHEST.y, CHEST.z,
                    offset[0], offset[1], offset[2], camera.x, camera.y, camera.z);
            assertEquals(1.0D, from, 0.0D,
                    "a camera sitting on the blade at " + slot + " still gets a thread drawn to it");
        }
    }

    // ---- the painter, replayed -------------------------------------------------------------------

    /** Where the drawn tube begins, or null when the thread is refused outright. */
    private static Vec3 drawnStart(Slot slot, Vec3 camera) {
        double[] offset = ArrayPose.worldOffset(slot, FRAME);
        double from = ThreadGeometry.start(CHEST.x, CHEST.y, CHEST.z,
                offset[0], offset[1], offset[2], camera.x, camera.y, camera.z);
        if (from >= 1.0D) {
            return null;
        }
        Vec3 run = new Vec3(offset[0], offset[1], offset[2]).subtract(CHEST);
        return CHEST.add(run.scale(from));
    }

    /**
     * The far end of the drawn tube, through the painter's own calls rather than through algebra
     * that agrees with them: translate to the start, {@code orientAlong} the run, then take the
     * tube vertex at z = 1 scaled by the length {@code beam} is handed.
     */
    private static Vector3f drawnTip(Slot slot, Vec3 camera) {
        Vec3 start = drawnStart(slot, camera);
        if (start == null) {
            return null;
        }
        double[] offset = ArrayPose.worldOffset(slot, FRAME);
        Vec3 run = new Vec3(offset[0], offset[1], offset[2]).subtract(CHEST);
        double length = run.length() - start.subtract(CHEST).length();
        PoseStack pose = new PoseStack();
        pose.translate(start.x, start.y, start.z);
        FilamentPainter.orientAlong(pose, run);
        Vector3f tip = new Vector3f(0.0F, 0.0F, (float) length);
        pose.last().pose().transformPosition(tip);
        return tip;
    }

    /**
     * Every stance, at each complement a rung fields, at three phases: 306 places round the
     * wielder.
     *
     * <p>The sweep is over the six because between them they put a sword above the head, out to
     * either side, under the feet and directly behind, and the trim is a different piece of
     * arithmetic in every one of those directions - a spread that only went forward would pass
     * on all of them. The phases are in for the same reason: Coil and Rain turn, so a formation
     * that cleared the camera at phase zero and nowhere else would clear it for an instant in
     * every revolution and be a bar through the frame the rest of the time.
     */
    private static Slot[] spread() {
        int[] counts = {1, 4, 12};
        double[] phases = {0.0D, 37.0D, 113.0D};
        int perStance = 0;
        for (int count : counts) {
            perStance += count;
        }
        Slot[] slots = new Slot[SwordStance.count() * phases.length * perStance];
        int at = 0;
        for (SwordStance stance : SwordStance.values()) {
            for (double phase : phases) {
                for (int count : counts) {
                    for (int i = 0; i < count; i++) {
                        slots[at++] = Formation.place(stance, i, count, phase);
                    }
                }
            }
        }
        return slots;
    }

    /** Closest approach of a point to a segment, which is where a thread passes a camera. */
    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 run = to.subtract(from);
        double lengthSqr = run.lengthSqr();
        if (lengthSqr < 1.0E-12D) {
            return point.distanceTo(from);
        }
        double t = point.subtract(from).dot(run) / lengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(from.add(run.scale(t)));
    }
}
