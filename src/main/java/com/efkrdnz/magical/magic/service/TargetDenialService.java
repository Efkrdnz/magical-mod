package com.efkrdnz.magical.magic.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Obscuring volumes (smoke columns, ink ceilings): a mob may not acquire a target inside one, while
 * inside one, or through one. Volumes are re-registered every tick by their behaviours and expire
 * on their own.
 */
public final class TargetDenialService {
    private static final List<Volume> VOLUMES = new ArrayList<>();

    private TargetDenialService() {}

    private record Volume(Level level, Vec3 base, double radius, double height, long expiryTick) {
        boolean contains(Vec3 p) {
            double dx = p.x - base.x;
            double dz = p.z - base.z;
            return p.y >= base.y - 0.5D && p.y <= base.y + height && dx * dx + dz * dz <= radius * radius;
        }

        boolean crosses(Vec3 a, Vec3 b) {
            int steps = (int) Math.ceil(a.distanceTo(b) / 0.5D);
            for (int i = 0; i <= steps; i++) {
                if (contains(a.lerp(b, steps == 0 ? 0.0D : i / (double) steps))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Called every tick by an active volume. */
    public static void register(Level level, Vec3 base, double radius, double height) {
        VOLUMES.add(new Volume(level, base, radius, height, level.getGameTime() + 2L));
    }

    public static boolean deniesTargeting(LivingEntity self, LivingEntity target) {
        if (VOLUMES.isEmpty()) {
            return false;
        }
        long now = self.level().getGameTime();
        Vec3 eye = self.getEyePosition();
        Vec3 targetEye = target.getEyePosition();
        for (Iterator<Volume> it = VOLUMES.iterator(); it.hasNext();) {
            Volume v = it.next();
            if (v.expiryTick < now) {
                it.remove();
                continue;
            }
            if (v.level != self.level()) {
                continue;
            }
            if (v.contains(eye) || v.contains(targetEye) || v.crosses(eye, targetEye)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInside(LivingEntity entity) {
        long now = entity.level().getGameTime();
        Vec3 eye = entity.getEyePosition();
        for (Volume v : VOLUMES) {
            if (v.expiryTick >= now && v.level == entity.level() && v.contains(eye)) {
                return true;
            }
        }
        return false;
    }
}
