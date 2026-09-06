package com.efkrdnz.magical.magic;

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
        int barrierRestore) {
}
