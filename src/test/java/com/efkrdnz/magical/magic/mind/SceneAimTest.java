package com.efkrdnz.magical.magic.mind;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneAimTest {
    private static final Vec3 EYE = new Vec3(0.0, 0.5, 0.5);
    private static final Vec3 FAR = new Vec3(10.0, 0.5, 0.5);

    @Test
    void theNearestBoxAlongTheRayIsTheOneAimedAt() {
        List<AABB> boxes = List.of(new AABB(5, 0, 0, 6, 1, 1), new AABB(2, 0, 0, 3, 1, 1), new AABB(0, 5, 0, 1, 6, 1));
        SceneAim.Hit hit = SceneAim.nearest(EYE, FAR, boxes);
        assertNotNull(hit);
        assertEquals(1, hit.index());
        assertEquals(2.0, hit.distance(), 1.0E-6);
    }

    @Test
    void aRayThatMeetsNothingAimsAtNothing() {
        assertNull(SceneAim.nearest(EYE, FAR, List.of(new AABB(0, 5, 0, 1, 6, 1))));
        assertNull(SceneAim.nearest(EYE, FAR, List.of()));
        assertNull(SceneAim.nearest(EYE, new Vec3(1.5, 0.5, 0.5), List.of(new AABB(2, 0, 0, 3, 1, 1))), "the ray stops short");
    }

    @Test
    void aBoxTheEyeIsInsideIsAimedAtFromNoDistanceAtAll() {
        SceneAim.Hit hit = SceneAim.nearest(new Vec3(0.5, 0.5, 0.5), FAR,
                List.of(new AABB(5, 0, 0, 6, 1, 1), new AABB(0, 0, 0, 1, 1, 1)));
        assertNotNull(hit);
        assertEquals(1, hit.index());
        assertEquals(0.0, hit.distance(), 1.0E-9);
    }
}
