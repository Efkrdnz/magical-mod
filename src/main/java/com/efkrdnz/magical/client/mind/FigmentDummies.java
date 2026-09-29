package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** A stand-in of the imagined kind for each figment, posed like it; only ever rendered, never added. */
public final class FigmentDummies {
    private static final Map<Integer, LivingEntity> DUMMIES = new HashMap<>();

    private FigmentDummies() {}

    public static LivingEntity posed(FigmentEntity figment) {
        LivingEntity dummy = DUMMIES.computeIfAbsent(figment.getId(), id -> create(figment));
        if (dummy == null) {
            DUMMIES.remove(figment.getId());
            return null;
        }
        dummy.setPos(figment.getX(), figment.getY(), figment.getZ());
        dummy.xo = figment.xo;
        dummy.yo = figment.yo;
        dummy.zo = figment.zo;
        dummy.xOld = figment.xOld;
        dummy.yOld = figment.yOld;
        dummy.zOld = figment.zOld;
        dummy.setYRot(figment.getYRot());
        dummy.yRotO = figment.yRotO;
        dummy.setXRot(figment.getXRot());
        dummy.xRotO = figment.xRotO;
        dummy.yBodyRot = figment.yBodyRot;
        dummy.yBodyRotO = figment.yBodyRotO;
        dummy.yHeadRot = figment.yHeadRot;
        dummy.yHeadRotO = figment.yHeadRotO;
        dummy.attackAnim = figment.attackAnim;
        dummy.oAttackAnim = figment.oAttackAnim;
        if (dummy.tickCount != figment.tickCount) {
            dummy.tickCount = figment.tickCount;
            dummy.walkAnimation.update(figment.walkAnimation.speed(), 1.0F, 1.0F);
        }
        return dummy;
    }

    private static LivingEntity create(FigmentEntity figment) {
        return EntityType.byString(figment.creatureId())
                .map(type -> type.create(figment.level(), EntitySpawnReason.LOAD))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .orElse(null);
    }

    public static void clear() {
        DUMMIES.clear();
    }
}
