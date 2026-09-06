package com.efkrdnz.magical.magic;

import net.minecraft.resources.ResourceLocation;

public record MagicSkillDefinition(
        ResourceLocation id,
        MagicSchool school,
        MagicSkillType type,
        int tier,
        int requiredLevel,
        float baseDamage,
        float baseSpeed,
        float baseSize,
        int baseManaCost,
        int baseCooldownTicks,
        int baseDurationTicks,
        float baseKnockback,
        int barrierRestore,
        int color,
        MagicAttribute attribute) {

    public MagicSkillDefinition(
            ResourceLocation id,
            MagicSchool school,
            MagicSkillType type,
            int tier,
            int requiredLevel,
            float baseDamage,
            float baseSpeed,
            float baseSize,
            int baseManaCost,
            int baseCooldownTicks,
            int baseDurationTicks,
            float baseKnockback,
            int barrierRestore,
            int color) {
        this(id, school, type, tier, requiredLevel, baseDamage, baseSpeed, baseSize, baseManaCost, baseCooldownTicks, baseDurationTicks, baseKnockback, barrierRestore, color, MagicAttribute.fromSchool(school));
    }

    public MagicSkillDefinition {
        if (attribute == null) {
            attribute = MagicAttribute.fromSchool(school);
        }
    }

    public String nameKey() {
        return "skill.magical." + id.getPath();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    public MagicSkillResolvedStats resolve(MagicSkillTuning tuning) {
        float damageScale = 1.0F + tuning.damage() * 0.22F;
        float speedScale = 1.0F + tuning.speed() * 0.17F;
        float sizeScale = 1.0F + tuning.size() * 0.18F;
        float durationScale = 1.0F + tuning.duration() * 0.18F;
        float efficiencyScale = 1.0F - tuning.efficiency() * 0.12F;

        float damage = Math.max(0.5F, baseDamage * damageScale);
        float speed = Math.max(0.15F, baseSpeed * speedScale);
        float size = Math.max(0.35F, baseSize * sizeScale);
        int manaCost = Math.max(4, Math.round(baseManaCost * (1.0F + tuning.damage() * 0.14F + tuning.speed() * 0.11F + tuning.size() * 0.12F + tuning.duration() * 0.1F) * efficiencyScale));
        int cooldown = Math.max(8, Math.round(baseCooldownTicks * (1.0F + tuning.damage() * 0.12F + tuning.size() * 0.1F + tuning.duration() * 0.08F - tuning.speed() * 0.06F)));
        int duration = Math.max(6, Math.round(baseDurationTicks * durationScale));
        int barrier = Math.max(0, Math.round(barrierRestore * (1.0F + tuning.efficiency() * 0.18F + tuning.size() * 0.1F)));
        float knockback = Math.max(0.0F, baseKnockback * (1.0F + tuning.damage() * 0.14F + tuning.size() * 0.1F));
        return new MagicSkillResolvedStats(this, tuning, damage, speed, size, manaCost, cooldown, duration, knockback, barrier);
    }
}
