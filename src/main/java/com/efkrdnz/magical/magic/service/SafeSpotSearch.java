package com.efkrdnz.magical.magic.service;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Standable-spot search and forced placement shared by every skill that moves something through
 * space (transposition, retrograde, crease, diaspora, rigid frame slots, arcane grasp drops).
 */
public final class SafeSpotSearch {
    private SafeSpotSearch() {}

    /** A body of the given size standing with its feet at {@code feet} touches no block and no lava. */
    public static boolean fits(Level level, Vec3 feet, float width, float height) {
        double h = Math.max(0.1D, width * 0.5D);
        AABB box = new AABB(feet.x - h, feet.y + 0.01D, feet.z - h, feet.x + h, feet.y + Math.max(0.2D, height), feet.z + h);
        return level.noCollision(box) && !level.getFluidState(BlockPos.containing(feet)).is(FluidTags.LAVA);
    }

    /** {@link #fits} plus something solid directly under the feet. */
    public static boolean standable(Level level, Vec3 feet, float width, float height) {
        BlockPos below = BlockPos.containing(feet).below();
        return fits(level, feet, width, height) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /** Nearest standable feet position in the column around {@code wanted}, scanning up and down alternately. */
    public static Vec3 standableNear(Level level, Vec3 wanted, int up, int down, float width, float height) {
        int baseY = Mth.floor(wanted.y);
        for (int i = 0; i <= Math.max(up, down); i++) {
            if (i <= up) {
                Vec3 c = new Vec3(wanted.x, baseY + i, wanted.z);
                if (standable(level, c, width, height)) {
                    return c;
                }
            }
            if (i > 0 && i <= down) {
                Vec3 c = new Vec3(wanted.x, baseY - i, wanted.z);
                if (standable(level, c, width, height)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** The position itself when clear, else lifted in half-block steps up to {@code maxUp}; null when nothing clears. */
    public static Vec3 liftClear(Level level, Vec3 pos, float width, float height, double maxUp) {
        for (double dy = 0.0D; dy <= maxUp + 1.0E-6D; dy += 0.5D) {
            Vec3 c = pos.add(0.0D, dy, 0.0D);
            if (fits(level, c, width, height)) {
                return c;
            }
        }
        return null;
    }

    /** Move a living entity (players through the teleport path so the client follows), keeping or clearing its velocity. */
    public static void place(LivingEntity entity, Vec3 feet, float yaw, float pitch, boolean keepVelocity) {
        if (entity instanceof ServerPlayer player) {
            Set<Relative> relatives = keepVelocity ? EnumSet.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z) : EnumSet.noneOf(Relative.class);
            player.teleportTo(player.serverLevel(), feet.x, feet.y, feet.z, relatives, yaw, pitch, false);
        } else {
            Vec3 v = entity.getDeltaMovement();
            entity.setPos(feet.x, feet.y, feet.z);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.yBodyRot = yaw;
            entity.yHeadRot = yaw;
            entity.setDeltaMovement(keepVelocity ? v : Vec3.ZERO);
            entity.hurtMarked = true;
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
        entity.fallDistance = 0.0F;
    }
}
