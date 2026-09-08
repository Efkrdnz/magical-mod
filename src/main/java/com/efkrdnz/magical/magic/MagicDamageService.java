package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class MagicDamageService {
    private static final int HIT_PROFICIENCY_COOLDOWN_TICKS = 20;
    private static final Map<HitProficiencyKey, Long> LAST_HIT_PROFICIENCY_AT = new HashMap<>();

    private MagicDamageService() {}

    public static void hurt(LivingEntity target, DamageSource source, float amount) {
        hurt(target, source, amount, null);
    }

    public static void hurt(LivingEntity target, DamageSource source, float amount, ResourceLocation skillId) {
        hurt(target, source, amount, skillId, true);
    }

    public static void hurt(LivingEntity target, DamageSource source, float amount, ResourceLocation skillId, boolean triggerSkillReactions) {
        float finalAmount = adjustForCaster(target, source, skillId, amount);
        if (finalAmount > 0.0F) {
            // The one place that still knows which skill this damage is. The DamageSource beneath it
            // does not - SkillTargets builds indirectMagic(owner, owner) - so anything downstream
            // that has to weigh the spell's tier, every apex ward included, reads it from here.
            ResourceLocation previous = TierFive.beginAttribution(skillId);
            try {
                target.hurt(source, finalAmount);
            } finally {
                TierFive.endAttribution(previous);
            }
            grantHitProficiency(target, source, skillId, triggerSkillReactions);
        }
    }

    /**
     * Every skill in the mod funnels its damage through here, so this is the one place a passive can
     * make damage depend on the target: how hurt it is, how far away it is, what is already wrong
     * with it. Without this each such passive would need its own branch in every skill that could
     * benefit from it.
     */
    private static float adjustForCaster(LivingEntity target, DamageSource source, ResourceLocation skillId, float amount) {
        if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer caster) || caster == target) {
            return amount;
        }
        PlayerMagicState state = caster.getData(MagicalAttachments.MAGIC_STATE);
        return ClassPassiveEffects.outgoingSpellDamage(caster, state, target, skillId, amount);
    }

    public static void hurt(Entity target, DamageSource source, float amount) {
        hurt(target, source, amount, null);
    }

    public static void hurt(Entity target, DamageSource source, float amount, ResourceLocation skillId) {
        if (target instanceof LivingEntity living) {
            hurt(living, source, amount, skillId);
            return;
        }
        target.hurt(source, amount);
    }

    private static void grantHitProficiency(LivingEntity target, DamageSource source, ResourceLocation skillId, boolean triggerSkillReactions) {
        if (!triggerSkillReactions || skillId == null || !(source.getEntity() instanceof ServerPlayer caster) || caster == target) {
            return;
        }
        MagicSkillDefinition skill = MagicContent.get(skillId);
        if (skill == null || skill.baseDamage() <= 0.0F || skill.type() == MagicSkillType.BARRIER) {
            return;
        }
        long gameTime = caster.serverLevel().getGameTime();
        HitProficiencyKey key = new HitProficiencyKey(caster.getUUID(), target.getUUID(), skillId);
        long last = LAST_HIT_PROFICIENCY_AT.getOrDefault(key, Long.MIN_VALUE);
        if (gameTime - last < HIT_PROFICIENCY_COOLDOWN_TICKS) {
            return;
        }
        LAST_HIT_PROFICIENCY_AT.put(key, gameTime);
        if (LAST_HIT_PROFICIENCY_AT.size() > 2048) {
            LAST_HIT_PROFICIENCY_AT.entrySet().removeIf(entry -> gameTime - entry.getValue() > 20L * 60L);
        }
        PlayerMagicState state = caster.getData(MagicalAttachments.MAGIC_STATE);
        state.addProficiency(caster, hitProficiencyAmount(skill));
        state.sync(caster);
    }

    private static int hitProficiencyAmount(MagicSkillDefinition skill) {
        return Math.max(1, Math.min(4, 1 + Math.max(0, skill.tier()) / 2));
    }

    private record HitProficiencyKey(UUID casterId, UUID targetId, ResourceLocation skillId) {}
}
