package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The crater is a bowl, the cone narrows to a vent, the plate is a disc - and none lists a cell twice. */
class ShapesTest {

    private static Set<String> keys(List<int[]> cells) {
        Set<String> out = new HashSet<>();
        for (int[] c : cells) {
            assertTrue(out.add(c[0] + "," + c[1] + "," + c[2]), "a cell listed twice");
        }
        return out;
    }

    @Test
    void theCraterIsDeepestInTheMiddle() {
        Set<String> hollow = keys(Shapes.crater(3, 3));
        assertTrue(hollow.contains("0,0,0"));
        assertTrue(hollow.contains("0,-2,0"), "three deep in the middle");
        assertFalse(hollow.contains("0,-3,0"));
        assertFalse(hollow.contains("3,-1,0"), "the rim is shallow");
        for (int[] lining : Shapes.craterLining(3, 3)) {
            assertFalse(hollow.contains(lining[0] + "," + lining[1] + "," + lining[2]), "the lining is under the hollow, not in it");
            assertTrue(hollow.contains(lining[0] + "," + (lining[1] + 1) + "," + lining[2]), "the lining sits right under a carved cell");
        }
    }

    @Test
    void theConeNarrowsToAVent() {
        Shapes.Cone cone = Shapes.cone(2, 4);
        Set<String> body = keys(cone.body());
        assertEquals("0,3,0", cone.vent()[0] + "," + cone.vent()[1] + "," + cone.vent()[2]);
        assertFalse(body.contains("0,3,0"), "the vent is not body");
        assertTrue(body.contains("2,0,0"), "the foot is as wide as the base");
        assertFalse(body.contains("2,2,0"), "the upper layers are narrower");
        int foot = 0;
        int second = 0;
        for (int[] c : cone.body()) {
            if (c[1] == 0) {
                foot++;
            } else if (c[1] == 1) {
                second++;
            }
        }
        assertTrue(foot > second);
    }

    @Test
    void thePlateIsADiscTwoDeep() {
        List<int[]> plate = Shapes.plate(2, 2);
        Set<String> cells = keys(plate);
        assertTrue(cells.contains("2,0,0"));
        assertTrue(cells.contains("0,-1,0"));
        assertFalse(cells.contains("2,0,2"), "the corners are cut");
        assertFalse(cells.contains("0,-2,0"));
        assertTrue(plate.size() <= 48, "a plate must fit in one mass entity");
    }
}
