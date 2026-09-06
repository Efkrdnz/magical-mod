package com.efkrdnz.magical.magic.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Hoist / pin an entity at a controlled position every tick (telekinetic grasp, sunder grip
 * stagger, compression lock, rigid frame). One entry per entity; the newest holder wins.
 */
public final class HeldEntityService {
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private HeldEntityService() {}

    public static final class Hold {
        public Vec3 position;
        public int remaining;
        public boolean noGravity;
        public boolean lockMotion;
        public UUID holder;
        public String tag;

        private Hold(Vec3 position, int remaining, boolean noGravity, boolean lockMotion, UUID holder, String tag) {
            this.position = position;
            this.remaining = remaining;
            this.noGravity = noGravity;
            this.lockMotion = lockMotion;
            this.holder = holder;
            this.tag = tag;
        }
    }

    public static Hold hold(LivingEntity target, Vec3 position, int ticks, boolean noGravity, Entity holder, String tag) {
        Hold hold = new Hold(position, Math.max(1, ticks), noGravity, true, holder != null ? holder.getUUID() : null, tag);
        HOLDS.put(target.getUUID(), hold);
        if (noGravity) {
            target.setNoGravity(true);
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return hold;
    }

    public static Hold get(Entity entity) {
        return HOLDS.get(entity.getUUID());
    }

    public static boolean isHeld(Entity entity) {
        return HOLDS.containsKey(entity.getUUID());
    }

    public static void release(Entity entity) {
        Hold hold = HOLDS.remove(entity.getUUID());
        if (hold != null && hold.noGravity) {
            entity.setNoGravity(false);
        }
    }

    public static void releaseAllBy(UUID holder, String tag) {
        for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Hold> e = it.next();
            if (holder.equals(e.getValue().holder) && (tag == null || tag.equals(e.getValue().tag))) {
                it.remove();
            }
        }
    }

    /** Server tick: enforce every hold, expire finished ones. */
    public static void tick(ServerLevel level) {
        if (HOLDS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Hold> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            Hold hold = e.getValue();
            if (entity == null || !entity.isAlive() || entity.level() != level) {
                if (entity != null) {
                    it.remove();
                    if (hold.noGravity) {
                        entity.setNoGravity(false);
                    }
                }
                continue;
            }
            if (--hold.remaining <= 0) {
                it.remove();
                if (hold.noGravity) {
                    entity.setNoGravity(false);
                }
                continue;
            }
            if (hold.lockMotion) {
                entity.setPos(hold.position.x, hold.position.y, hold.position.z);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.fallDistance = 0.0F;
                entity.hurtMarked = true;
            }
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
    }
}
