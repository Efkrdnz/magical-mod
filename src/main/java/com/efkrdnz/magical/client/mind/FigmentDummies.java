package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.MagicalMod;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.HashMap;
import java.util.Map;

/** A stand-in of the imagined kind for each figment, posed like it; only ever rendered, never added. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
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

    public static void remove(int id) {
        DUMMIES.remove(id);
    }

    /** A figment that leaves the client level takes its stand-in with it. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof FigmentEntity figment) {
            remove(figment.getId());
        }
    }

    public static void clear() {
        DUMMIES.clear();
    }
}
