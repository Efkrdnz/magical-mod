package com.efkrdnz.magical.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Per-form/element special behaviour triggered on a forged-weapon strike. */
@FunctionalInterface
public interface StrikeSpecial {
    void apply(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon, StrikeContext context);
}
