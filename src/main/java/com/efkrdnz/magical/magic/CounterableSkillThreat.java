package com.efkrdnz.magical.magic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface CounterableSkillThreat {
    Entity counterEntity();

    ResourceLocation counterSkillId();

    MagicAttribute counterAttribute();

    Entity counterOwner();

    default int counterThreatId() {
        return counterEntity().getId();
    }

    default boolean canBeCounteredBy(LivingEntity defender) {
        Entity owner = counterOwner();
        return defender.isAlive() && defender != owner;
    }

    void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition);

    default void onGluttonyCountered(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        MagicSkillDefinition incoming = MagicContent.get(counterSkillId());
        int incomingColor = incoming == null ? 0xFFFFFF : incoming.color();
        MagicCounterService.spawnClash(level, clashPosition, incomingColor, MagicPassiveContent.SIN_GLUTTONY.color());
        counterEntity().discard();
    }
}
