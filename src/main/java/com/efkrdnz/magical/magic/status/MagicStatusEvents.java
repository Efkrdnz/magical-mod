package com.efkrdnz.magical.magic.status;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import com.efkrdnz.magical.magic.service.HeldEntityService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Gameplay consequences of skill statuses, plus the held-entity and conjured-terrain upkeep. */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MagicStatusEvents {
    private MagicStatusEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && !living.level().isClientSide()) {
            MagicStatusService.tick(living);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            HeldEntityService.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ConjuredTerrainService.restoreOrphans(level);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        // an exiled entity can neither be hurt nor hurt anything
        if (MagicStatusService.has(victim, MagicStatus.EXILED)) {
            event.setCanceled(true);
            return;
        }
        if (attacker != null && MagicStatusService.has(attacker, MagicStatus.EXILED)) {
            event.setCanceled(true);
            return;
        }
        if (attacker != null && (MagicStatusService.has(attacker, MagicStatus.ASLEEP) || MagicStatusService.has(attacker, MagicStatus.POLYMORPHED))) {
            event.setCanceled(true);
            return;
        }
        // a planted bulwark restores a little barrier on every hit it takes
        if (victim instanceof ServerPlayer planted) {
            MagicStatusData.Entry stance = MagicStatusService.entry(planted, MagicStatus.IMMOVABLE);
            if (stance != null && com.efkrdnz.magical.magic.MagicContent.LIVING_BULWARK.id().equals(stance.sourceSkill())) {
                var state = planted.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
                state.addBarrier(2);
                state.sync(planted);
            }
        }
        if (attacker instanceof LivingEntity livingAttacker && event.getSource().isDirect()) {
            // pinned facing: attacks outside a 60-degree frontal cone of the frozen bearing do nothing
            MagicStatusData.Entry pin = MagicStatusService.entry(livingAttacker, MagicStatus.FACING_PINNED);
            if (pin != null) {
                double yawRad = Math.toRadians(pin.yaw());
                double fx = -Math.sin(yawRad);
                double fz = Math.cos(yawRad);
                double dx = victim.getX() - livingAttacker.getX();
                double dz = victim.getZ() - livingAttacker.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 1.0E-3D && (dx * fx + dz * fz) / len < Math.cos(Math.toRadians(30.0D))) {
                    event.setCanceled(true);
                    return;
                }
            }
            // clamped reach: melee only lands at point blank
            if (MagicStatusService.has(livingAttacker, MagicStatus.REACH_CLAMPED)) {
                double reach = livingAttacker.getBbWidth() * 0.5D + victim.getBbWidth() * 0.5D + 0.5D;
                if (livingAttacker.distanceTo(victim) > reach) {
                    event.setCanceled(true);
                    return;
                }
            }
            // taunted attackers hit the taunter for 30% less, and their first landed hit sears them
            MagicStatusData.Entry taunt = MagicStatusService.entry(livingAttacker, MagicStatus.TAUNTED);
            if (taunt != null && taunt.source() != null && taunt.source().equals(victim.getUUID())) {
                event.setAmount(event.getAmount() * 0.7F);
                if (taunt.amplifier() == 0 && victim.level() instanceof ServerLevel level) {
                    int remaining = MagicStatusService.remainingTicks(livingAttacker, MagicStatus.TAUNTED);
                    MagicStatusService.apply(livingAttacker, MagicStatus.TAUNTED, remaining, 1, taunt.value(), taunt.sourceSkill(), victim);
                    livingAttacker.igniteForSeconds(3.0F);
                    com.efkrdnz.magical.magic.MagicSkillDefinition definition = com.efkrdnz.magical.magic.MagicContent.get(taunt.sourceSkill());
                    if (definition != null) {
                        com.efkrdnz.magical.magic.service.SkillTargets.hurt(level, victim, livingAttacker, 6.0F, definition, true);
                    }
                }
            }
        }
        // a sleeper wakes when hurt
        if (MagicStatusService.has(victim, MagicStatus.ASLEEP)) {
            MagicStatusService.clear(victim, MagicStatus.ASLEEP);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) {
            return;
        }
        LivingEntity self = event.getEntity();
        if (MagicStatusService.has(self, MagicStatus.ASLEEP) || MagicStatusService.has(self, MagicStatus.DAZZLED) || MagicStatusService.has(self, MagicStatus.EXILED)) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        if (com.efkrdnz.magical.magic.service.TargetDenialService.deniesTargeting(self, newTarget)) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        if (MagicStatusService.has(newTarget, MagicStatus.EXILED)) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        // taunted mobs cannot retarget away from the taunter
        MagicStatusData.Entry taunt = MagicStatusService.entry(self, MagicStatus.TAUNTED);
        if (taunt != null && taunt.source() != null && !taunt.source().equals(newTarget.getUUID()) && self instanceof Mob mob && self.level() instanceof ServerLevel level) {
            Entity source = level.getEntity(taunt.source());
            if (source instanceof LivingEntity living && living.isAlive()) {
                event.setNewAboutToBeSetTarget(living);
            }
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (MagicStatusService.has(event.getEntity(), MagicStatus.UNHALLOWED)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (MagicStatusService.has(event.getEntity(), MagicStatus.IMMOVABLE) || MagicStatusService.has(event.getEntity(), MagicStatus.ROOTED)) {
            event.setCanceled(true);
        }
    }
}
