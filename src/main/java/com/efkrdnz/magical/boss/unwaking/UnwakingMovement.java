package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Local collision checks, never a combat-radius leash. */
final class UnwakingMovement {
    private UnwakingMovement() {}
    static boolean move(UnwakingGodEntity body, Vec3 destination) {
        if (!clear(body,destination)) return false;
        body.place(destination); return true;
    }
    static boolean clear(Entity entity, Vec3 position) {
        BlockPos pos=BlockPos.containing(position);
        if (position.y < entity.level().getMinY() + 4) return false;
        if (!entity.level().getWorldBorder().isWithinBounds(pos) || !entity.level().hasChunkAt(pos)) return false;
        AABB box=entity.getBoundingBox().move(position.subtract(entity.position()));
        return entity.level().noCollision(entity,box) && !entity.level().containsAnyLiquid(box);
    }
    static Vec3 destination(UnwakingGodEntity body, ServerPlayer target, int variant) {
        Vec3 facing=UnwakingHazard.unit(new Vec3(target.getLookAngle().x,0,target.getLookAngle().z));
        double distance=variant==3?2.8:8;
        for(int step=0;step<8;step++) {
            double angle=(step+variant)*Math.PI/4;
            Vec3 offset=UnwakingHazard.rotate(facing,new Vec3(0,1,0),angle).scale(distance);
            for(double up:new double[]{0,2,4,-2}) {
                Vec3 point=target.position().add(offset).add(0,up,0);
                if(clear(body,point)) return point;
            }
        }
        return null;
    }
    static boolean canEscape(ServerPlayer player, Vec3 delta) {
        for(int i=1;i<=12;i++) {
            Vec3 point=player.position().add(delta.scale(i/12D));
            if(!clear(player,point)) return false;
            if(!player.getAbilities().flying && player.level().getBlockState(BlockPos.containing(point).below()).isAir()) return false;
        }
        return true;
    }
}
