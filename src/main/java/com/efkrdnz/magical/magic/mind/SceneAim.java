package com.efkrdnz.magical.magic.mind;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** The nearest of some boxes along a ray. Imagined blocks are not in the world, so no level clip finds them. */
public final class SceneAim {
    public record Hit(int index, double distance) {}

    private SceneAim() {}

    public static Hit nearest(Vec3 from, Vec3 to, List<AABB> boxes) {
        Hit best = null;
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            double distance;
            if (box.contains(from)) {
                distance = 0.0;
            } else {
                Optional<Vec3> at = box.clip(from, to);
                if (at.isEmpty()) {
                    continue;
                }
                distance = from.distanceTo(at.get());
            }
            if (best == null || distance < best.distance()) {
                best = new Hit(i, distance);
            }
        }
        return best;
    }
}
