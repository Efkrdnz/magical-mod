package com.efkrdnz.magical.client.renderer.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * Where the viewer is standing, in whatever frame a piece of a strike is being drawn in.
 *
 * <p>A strike is written facing forward and the renderer turns local +Z into the aim, which is what
 * lets every form know nothing about the compass. The cost is that nothing inside the geometry knew
 * where the viewer was either, so nothing could turn to face them - and a flat arc seen along its
 * own plane has no projected area at all. An overhead chop thrown away from the camera came out as
 * a twelve-pixel scratch above the wielder's head.
 *
 * <p>An entity's pose stack starts as a pure translation from the camera to the entity, which is
 * the same fact every billboard in the mod leans on when it multiplies in
 * {@code cameraOrientation()}. So the eye sits at the camera-relative origin, and running the pose
 * backwards from there says where it is in local terms - through the renderer's own aim rotation
 * and through whatever a form has pushed on top of it, without any form having to account for its
 * own transforms by hand.
 */
class ForgeViewTest {

    private static final float EPSILON = 1.0E-4f;

    @Test
    void anUntouchedPoseLeavesTheViewerOnTheOrigin() {
        assertArrayEqualish(new float[] {0.0f, 0.0f, 0.0f}, ForgeView.eye(new Matrix4f()));
    }

    @Test
    void aStrikeDrawnFourBlocksAheadOfTheCameraSeesItFourBlocksBehind() {
        Matrix4f pose = new Matrix4f().translate(0.0f, 0.0f, 4.0f);
        assertArrayEqualish(new float[] {0.0f, 0.0f, -4.0f}, ForgeView.eye(pose));
    }

    @Test
    void aStrikeAimedEastSeesTheSameViewerInTheSamePlace() {
        // The renderer turns local +Z into the aim. A swing thrown east with the viewer at its back
        // must be drawn exactly as one thrown north with the viewer at its back, or every form
        // would need to know the compass.
        Matrix4f north = new Matrix4f().translate(0.0f, 0.0f, 4.0f);
        Matrix4f east = new Matrix4f().translate(4.0f, 0.0f, 0.0f).rotateY((float) (Math.PI * 0.5));
        assertArrayEqualish(ForgeView.eye(north), ForgeView.eye(east));
    }

    @Test
    void aFormsOwnLiftIsAccountedForWithoutTheFormSayingSo() {
        // Several forms push a translation of their own before they draw - a chop is lifted, a
        // rising cut climbs. Whatever they push has to move the viewer with it, or the glow round
        // the blade faces a point the viewer is not at.
        Matrix4f lifted = new Matrix4f().translate(0.0f, 0.0f, 6.0f).translate(0.0f, 1.5f, 0.0f);
        assertArrayEqualish(new float[] {0.0f, -1.5f, -6.0f}, ForgeView.eye(lifted));
    }

    @Test
    void theViewerKeepsItsDistanceWhicheverWayTheStrikeIsAimed() {
        // A rigid motion and nothing else. If this drifted, a glow sized off the distance would be
        // sized off a number that is not the real one.
        Matrix4f[] poses = {
                new Matrix4f().translate(2.5f, -1.25f, 6.0f),
                new Matrix4f().translate(2.5f, -1.25f, 6.0f).rotateY(0.9f),
                new Matrix4f().translate(2.5f, -1.25f, 6.0f).rotateY(0.9f).rotateX(-0.4f),
                new Matrix4f().translate(2.5f, -1.25f, 6.0f).rotateZ(2.2f).rotateX(1.1f)};
        double expected = Math.sqrt(2.5 * 2.5 + 1.25 * 1.25 + 6.0 * 6.0);
        for (Matrix4f pose : poses) {
            float[] eye = ForgeView.eye(pose);
            double length = Math.sqrt(eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2]);
            assertEquals(expected, length, 1.0E-3, "the viewer moved for a turn it should not care about");
        }
    }

    @Test
    void aViewerInTheSamePlaceAsTheStrikeIsStillANumber() {
        // First person, standing inside your own spin. It has to come back finite: a NaN here would
        // erase every quad the glow touches rather than one band of it.
        float[] eye = ForgeView.eye(new Matrix4f().rotateY(1.3f));
        for (float axis : eye) {
            assertTrue(Float.isFinite(axis), "a viewer at the origin produced " + axis);
        }
    }

    private static void assertArrayEqualish(float[] expected, float[] actual) {
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], EPSILON, "axis " + i);
        }
    }
}
