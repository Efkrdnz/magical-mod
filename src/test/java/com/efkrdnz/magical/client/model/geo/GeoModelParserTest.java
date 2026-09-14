package com.efkrdnz.magical.client.model.geo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A Blockbench Bedrock export, read the way Bedrock reads it: bones by name with a parent, a
 * pivot and a rotation; cubes as an origin, a size and a Box UV corner. Anything Blockbench
 * writes that the baker does not use is ignored, and the one thing it cannot bake - per-face UV -
 * is refused by name so the fix (export with Box UV) is in the log.
 */
class GeoModelParserTest {

    private static final String EXPORT = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {"identifier": "geometry.tentacle", "texture_width": 64, "texture_height": 64,
                                  "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
                  "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "seg0", "parent": "root", "pivot": [0, 0, 0], "rotation": [-10, 0, 5],
                     "cubes": [{"origin": [-2, 0, -2], "size": [4, 4, 4], "uv": [0, 0], "inflate": 0.25}]},
                    {"name": "seg1", "parent": "seg0", "pivot": [0, 4, 0], "mirror": true,
                     "cubes": [{"origin": [-1.5, 4, -1.5], "size": [3, 4, 3], "uv": [0, 8]},
                               {"origin": [-1, 8, -1], "size": [2, 2, 2], "uv": [16, 8], "rotation": [0, 45, 0], "pivot": [0, 9, 0]}]}
                  ]
                }
              ]
            }
            """;

    @Test
    void bonesComeBackWithTheirParentsPivotsRotationsAndCubes() {
        GeoModel model = GeoModelParser.parse(EXPORT);
        assertEquals("geometry.tentacle", model.identifier());
        assertEquals(64, model.textureWidth());
        assertEquals(3, model.bones().size());
        GeoModel.Bone seg0 = model.bone("seg0");
        assertEquals("root", seg0.parent());
        assertArrayEquals(new float[] {-10, 0, 5}, seg0.rotation());
        assertEquals(1, seg0.cubes().size());
        GeoModel.Cube cube = seg0.cubes().get(0);
        assertArrayEquals(new float[] {-2, 0, -2}, cube.origin());
        assertArrayEquals(new float[] {4, 4, 4}, cube.size());
        assertArrayEquals(new float[] {0, 0}, cube.uv());
        assertEquals(0.25F, cube.inflate());
        assertNull(cube.rotation(), "an unrotated cube has no rotation");
        assertNull(model.bone("root").parent());
        assertNull(model.bone("fin"), "a bone that is not there is null, not an error");
    }

    @Test
    void defaultsAreBedrocksAndARotatedCubeKeepsItsOwnPivot() {
        GeoModel model = GeoModelParser.parse(EXPORT);
        GeoModel.Bone root = model.bone("root");
        assertArrayEquals(new float[] {0, 0, 0}, root.rotation());
        assertTrue(root.cubes().isEmpty());
        assertFalse(root.mirror());
        GeoModel.Bone seg1 = model.bone("seg1");
        assertTrue(seg1.mirror(), "the bone flag");
        assertTrue(seg1.cubes().get(0).mirror(), "is inherited by its cubes");
        GeoModel.Cube rotated = seg1.cubes().get(1);
        assertArrayEquals(new float[] {0, 45, 0}, rotated.rotation());
        assertArrayEquals(new float[] {0, 9, 0}, rotated.pivot());
    }

    @Test
    void aMissingTextureSizeIsSixtyFour() {
        GeoModel model = GeoModelParser.parse("""
                {"minecraft:geometry": [{"description": {"identifier": "geometry.eye"}, "bones": []}]}
                """);
        assertEquals(64, model.textureWidth());
        assertEquals(64, model.textureHeight());
    }

    @Test
    void perFaceUvIsRefusedByBoneName() {
        GeoFormatException refused = assertThrows(GeoFormatException.class, () -> GeoModelParser.parse("""
                {"minecraft:geometry": [{"description": {"identifier": "geometry.maw"}, "bones": [
                  {"name": "jaw_upper", "pivot": [0, 0, 6], "cubes": [{"origin": [0, 0, 0], "size": [1, 1, 1],
                   "uv": {"north": {"uv": [0, 0], "uv_size": [1, 1]}}}]}]}]}
                """));
        assertTrue(refused.getMessage().contains("jaw_upper"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Box UV"), refused.getMessage());
    }

    @Test
    void aFileWithoutGeometryOrABoneWithoutANameIsRefused() {
        assertThrows(GeoFormatException.class, () -> GeoModelParser.parse("{}"));
        assertThrows(GeoFormatException.class, () -> GeoModelParser.parse("""
                {"minecraft:geometry": [{"description": {"identifier": "geometry.x"}, "bones": [{"pivot": [0, 0, 0]}]}]}
                """));
    }
}
