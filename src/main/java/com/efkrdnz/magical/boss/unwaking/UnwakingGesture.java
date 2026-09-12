package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server collision and client outline share the same locked coordinate frame. */
public record UnwakingGesture(Vec3 origin, Vec3 forward) {
    public static final int LOCK_TICK = 20;
    public static final int IMPACT_TICK = 32;
    public static final int END_TICK = 76;
    public static final double LENGTH = 14;
    public static final double HALF_WIDTH = 0.75;
    public static final double HALF_HEIGHT = 2;

    public static UnwakingGesture aim(Vec3 origin, Vec3 target) {
        Vec3 direction = target.subtract(origin);
        return new UnwakingGesture(origin, direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize());
    }

    public Vec3 right() {
        Vec3 cross = forward.cross(new Vec3(0, 1, 0));
        return cross.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : cross.normalize();
    }

    public Vec3 up() { return right().cross(forward).normalize(); }

    public Vec3 world(double x, double y, double z) {
        return origin.add(right().scale(x)).add(up().scale(y)).add(forward.scale(z));
    }

    private Vec3 local(Vec3 point) {
        Vec3 relative = point.subtract(origin);
        return new Vec3(relative.dot(right()), relative.dot(up()), relative.dot(forward));
    }

    public boolean intersects(Vec3 previousCenter, Vec3 currentCenter, double width, double height) {
        Vec3 right = right(), up = up();
        double rx = projectedExtent(right, width, height);
        double ry = projectedExtent(up, width, height);
        double rz = projectedExtent(forward, width, height);
        AABB box = new AABB(-HALF_WIDTH - rx, -HALF_HEIGHT - ry, -rz,
                HALF_WIDTH + rx, HALF_HEIGHT + ry, LENGTH + rz);
        Vec3 a = local(previousCenter), b = local(currentCenter);
        return box.contains(a) || box.contains(b) || box.clip(a, b).isPresent();
    }

    private static double projectedExtent(Vec3 axis, double width, double height) {
        return (Math.abs(axis.x) + Math.abs(axis.z)) * width / 2 + Math.abs(axis.y) * height / 2;
    }
}
