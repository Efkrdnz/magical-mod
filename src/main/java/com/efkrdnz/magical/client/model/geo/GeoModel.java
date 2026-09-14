package com.efkrdnz.magical.client.model.geo;

import java.util.List;

/**
 * One Bedrock entity geometry as Blockbench exports it, in the file's own space: sixteenths of a
 * block, y up, the -Z face the front. Nothing here is converted; the baker does that once.
 */
public record GeoModel(String identifier, int textureWidth, int textureHeight, List<Bone> bones) {

    /** A bone: its pivot and rest rotation in file space, its parent by name, its cubes. */
    public record Bone(String name, String parent, float[] pivot, float[] rotation, boolean mirror, List<Cube> cubes) {
    }

    /**
     * A box. {@code rotation} and {@code pivot} are null unless the cube is rotated on its own,
     * in which case the baker wraps it in a part of its own at that pivot.
     */
    public record Cube(float[] origin, float[] size, float[] uv, float inflate, boolean mirror, float[] rotation, float[] pivot) {
    }

    /** The bone of that name, or null: extras the code does not know are fine, and so are absences. */
    public Bone bone(String name) {
        for (Bone bone : bones) {
            if (bone.name().equals(name)) {
                return bone;
            }
        }
        return null;
    }
}
