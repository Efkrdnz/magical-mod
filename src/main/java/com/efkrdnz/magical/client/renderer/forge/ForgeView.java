package com.efkrdnz.magical.client.renderer.forge;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where the viewer is standing, in whatever frame a piece of a strike is being drawn in.
 *
 * <p>{@code ForgeStrikeRenderer.orient} turns local +Z into the strike's aim, which is what lets
 * every form be written facing forward and know nothing about the compass. The cost of that was
 * that nothing inside the geometry knew where the viewer was either, so nothing could turn to face
 * them - and a flat arc seen along its own plane has no projected area at all. An overhead chop
 * thrown away from the camera came out as a twelve-pixel scratch above the wielder's head.
 *
 * <p>An entity's pose stack starts as a pure translation from the camera to the entity, which is
 * the same fact every billboard in this mod leans on when it multiplies in
 * {@code cameraOrientation()}. So the eye sits at the camera-relative origin, and running the pose
 * backwards from there gives its position in local terms - through the renderer's aim rotation and
 * through whatever a form has pushed on top of it. Reading it off the matrix rather than
 * recomputing it per form is the point: a form that had to account for its own lift and tilt by
 * hand would get it wrong the first time one of them changed.
 */
public final class ForgeView {

    private ForgeView() {}

    /**
     * The viewer's position in the local frame {@code pose} draws into.
     *
     * <p>Every pose in this path is a rigid motion with at most a uniform scale, so it always
     * inverts; a degenerate one would come back as zeroes rather than as a NaN that would erase
     * every quad it touched.
     */
    public static float[] eye(Matrix4f pose) {
        Vector3f eye = new Matrix4f(pose).invert().transformPosition(new Vector3f());
        if (!Float.isFinite(eye.x) || !Float.isFinite(eye.y) || !Float.isFinite(eye.z)) {
            return new float[] {0.0f, 0.0f, 0.0f};
        }
        return new float[] {eye.x, eye.y, eye.z};
    }
}
