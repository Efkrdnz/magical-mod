package com.efkrdnz.magical.magic;

/**
 * A skill's numbers with one allocation of points applied.
 *
 * @param costScale the factor the points put on the price of a cast: points spent on the other
 *                  stats raise it, efficiency lowers it, and it never falls below a quarter. Mana
 *                  is {@code base * costScale} floored at four; blood is billed through the same
 *                  factor by {@code BloodService}, so the budget pulls on both currencies alike
 */
public record MagicSkillResolvedStats(
        MagicSkillDefinition definition,
        MagicSkillTuning tuning,
        float damage,
        float speed,
        float size,
        int manaCost,
        int cooldownTicks,
        int durationTicks,
        float knockback,
        int barrierRestore,
        float costScale) {
}
