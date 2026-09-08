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

    /**
     * The most a skill's mana cost or cooldown can be talked down, whatever is spent or dumped.
     *
     * <p>Both multipliers are sums of signed terms, so without a floor they go negative long before
     * a stat reaches its limit and the cost collapses onto its absolute minimum. Crucible at -11
     * damage and -11 size resolved to 4 mana on an eight-tick cooldown, down from 96 and twenty
     * seconds - which made weakening a skill a way to buy a cheap one rather than a real cost.
     *
     * <p>Neither factor of the mana scale is allowed to go negative first, because two negatives
     * multiply back into a positive: dumped stats plus maxed efficiency used to come out dearer than
     * dumped stats alone, so buying efficiency raised the bill. Clamped at zero they stay ordered,
     * and the floor then applies to the product - not to each factor, or the two would compound into
     * a discount far past three quarters.
     */
    private static final float SCALE_FLOOR = 0.25F;

    public MagicSkillResolvedStats resolve(MagicSkillTuning tuning) {
        float damageScale = 1.0F + tuning.damage() * 0.22F;
        float speedScale = 1.0F + tuning.speed() * 0.17F;
        float sizeScale = 1.0F + tuning.size() * 0.18F;
        float durationScale = 1.0F + tuning.duration() * 0.18F;
        float efficiencyScale = Math.max(0.0F, 1.0F - tuning.efficiency() * 0.12F);

        float damage = Math.max(0.5F, baseDamage * damageScale);
        float speed = Math.max(0.15F, baseSpeed * speedScale);
        float size = Math.max(0.35F, baseSize * sizeScale);
        float manaScale = Math.max(0.0F, 1.0F + tuning.damage() * 0.14F + tuning.speed() * 0.11F + tuning.size() * 0.12F + tuning.duration() * 0.1F) * efficiencyScale;
        float cooldownScale = 1.0F + tuning.damage() * 0.12F + tuning.size() * 0.1F + tuning.duration() * 0.08F - tuning.speed() * 0.06F;
        int manaCost = Math.max(4, Math.round(baseManaCost * Math.max(SCALE_FLOOR, manaScale)));
        int cooldown = Math.max(8, Math.round(baseCooldownTicks * Math.max(SCALE_FLOOR, cooldownScale)));
        int duration = Math.max(6, Math.round(baseDurationTicks * durationScale));
        int barrier = Math.max(0, Math.round(barrierRestore * (1.0F + tuning.efficiency() * 0.18F + tuning.size() * 0.1F)));
        float knockback = Math.max(0.0F, baseKnockback * (1.0F + tuning.damage() * 0.14F + tuning.size() * 0.1F));
        return new MagicSkillResolvedStats(this, tuning, damage, speed, size, manaCost, cooldown, duration, knockback, barrier);
    }
}
