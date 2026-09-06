package com.efkrdnz.magical.magic.service;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for every anti-projectile skill. */
public final class SpellIntercept {
    private SpellIntercept() {}

    /** Hostile projectiles (vanilla or mod) within radius that are not owned by {@code owner}. */
    public static List<Entity> hostileProjectiles(ServerLevel level, Vec3 centre, double radius, Entity owner) {
        List<Entity> out = new ArrayList<>();
        AABB box = new AABB(centre, centre).inflate(radius);
        for (Entity entity : level.getEntities(owner, box, e -> e.isAlive() && isProjectile(e) && !ownedBy(e, owner))) {
            if (entity.position().distanceToSqr(centre) <= radius * radius) {
                out.add(entity);
            }
        }
        return out;
    }

    public static boolean isProjectile(Entity entity) {
        return entity instanceof Projectile || entity instanceof InterceptableSpell;
    }

    public static boolean ownedBy(Entity entity, Entity owner) {
        if (owner == null) {
            return false;
        }
        if (entity instanceof Projectile projectile) {
            return projectile.getOwner() == owner;
        }
        if (entity instanceof InterceptableSpell spell) {
            return spell.spellOwner() == owner;
        }
        return false;
    }

    public static Entity ownerOf(Entity entity) {
        if (entity instanceof Projectile projectile) {
            return projectile.getOwner();
        }
        if (entity instanceof InterceptableSpell spell) {
            return spell.spellOwner();
        }
        return null;
    }

    public static void erase(Entity entity) {
        entity.discard();
    }

    /** Rotate the velocity toward a point by at most maxDegrees, keeping speed. */
    public static void bendToward(Entity entity, Vec3 target, float maxDegrees) {
        Vec3 v = entity.getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-4D) {
            return;
        }
        Vec3 dir = v.scale(1.0D / speed);
        Vec3 want = target.subtract(entity.position());
        if (want.lengthSqr() < 1.0E-6D) {
            return;
        }
        want = want.normalize();
        double cos = Math.max(-1.0D, Math.min(1.0D, dir.dot(want)));
        double angle = Math.acos(cos);
        double max = Math.toRadians(maxDegrees);
        Vec3 nd;
        if (angle <= max) {
            nd = want;
        } else {
            double t = max / angle;
            nd = dir.scale(1.0D - t).add(want.scale(t)).normalize();
        }
        entity.setDeltaMovement(nd.scale(speed));
        entity.hurtMarked = true;
    }

    /** Mirror the velocity across a plane normal, optionally re-owning a vanilla projectile. */
    public static void reflect(Entity entity, Vec3 normal, double speedScale, Entity newOwner) {
        Vec3 v = entity.getDeltaMovement();
        Vec3 n = normal.normalize();
        Vec3 r = v.subtract(n.scale(2.0D * v.dot(n))).scale(speedScale);
        entity.setDeltaMovement(r);
        entity.hurtMarked = true;
        if (newOwner != null) {
            if (entity instanceof Projectile projectile) {
                projectile.setOwner(newOwner);
            } else if (entity instanceof InterceptableSpell spell) {
                spell.onRedirected(newOwner);
            }
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-r.x, r.z));
        float pitch = (float) Math.toDegrees(Math.atan2(r.y, Math.sqrt(r.x * r.x + r.z * r.z)));
        entity.setYRot(yaw);
        entity.setXRot(pitch);
    }
}
