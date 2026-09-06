package com.efkrdnz.magical;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MagicalConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_MANA = BUILDER
            .comment("Maximum mana available to a player.")
            .defineInRange("maxMana", 100, 20, 500);

    public static final ModConfigSpec.IntValue MANA_REGEN_INTERVAL_TICKS = BUILDER
            .comment("How often mana regenerates, in ticks.")
            .defineInRange("manaRegenIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue MANA_PER_REGEN = BUILDER
            .comment("How much mana is restored every regen interval.")
            .defineInRange("manaPerRegen", 3, 1, 50);

    public static final ModConfigSpec.IntValue MAX_BARRIER = BUILDER
            .comment("Maximum permanent magic barrier available to a player.")
            .defineInRange("maxBarrier", 60, 10, 300);

    public static final ModConfigSpec.IntValue BARRIER_REFILL_MANA_COST = BUILDER
            .comment("Mana spent by the manual barrier refill action.")
            .defineInRange("barrierRefillManaCost", 12, 1, 100);

    public static final ModConfigSpec.IntValue BARRIER_PER_REFILL = BUILDER
            .comment("Barrier restored by the manual barrier refill action.")
            .defineInRange("barrierPerRefill", 10, 1, 100);

    public static final ModConfigSpec.BooleanValue STARTER_UNLOCK_ON_LOGIN = BUILDER
            .comment("Whether players automatically unlock starter arcane components and a high-tier starter skill on first login.")
            .define("starterUnlockOnLogin", true);

    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS = BUILDER
            .comment("Whether debug arcane commands should be available.")
            .define("debugCommands", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MagicalConfig() {}
}
