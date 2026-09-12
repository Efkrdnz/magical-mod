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

    default int counterTier() {
        MagicSkillDefinition incoming = MagicContent.get(counterSkillId());
        return incoming == null ? 0 : incoming.tier();
    }

    /** Optional display label for a boss strike that borrows a spell's attribute identity. */
    default String counterNameKey() { return ""; }

    /** Validate response-time requirements before spending resources; offering a prompt ignores aim. */
    default boolean validateCounterResponse(ServerPlayer defender) { return true; }
    default int minimumCounterWindowTicks() { return 3; }
    default int counterDisplayWindowTicks(int serverWindow) { return serverWindow; }

    default int counterThreatId() {
        return counterEntity().getId();
    }

    default boolean canBeCounteredBy(LivingEntity defender) {
        Entity owner = counterOwner();
        return defender.isAlive() && defender != owner;
    }

    void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition);

    /**
     * Answering a prompt that required no counter skill, only the key.
     *
     * <p>Separate from {@link #onCountered} because that one is handed the skill that paid for the
     * counter, and a forced prompt has none. Defaults to the same outcome every other threat has:
     * a clash, and the threat gone.
     */
    default void onForcedCounter(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        MagicSkillDefinition incoming = MagicContent.get(counterSkillId());
        int incomingColor = incoming == null ? 0xFFFFFF : incoming.color();
        MagicCounterService.spawnClash(level, clashPosition, incomingColor, 0xFFD166);
        counterEntity().discard();
    }

    default void onGluttonyCountered(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        MagicSkillDefinition incoming = MagicContent.get(counterSkillId());
        int incomingColor = incoming == null ? 0xFFFFFF : incoming.color();
        MagicCounterService.spawnClash(level, clashPosition, incomingColor, MagicPassiveContent.SIN_GLUTTONY.color());
        counterEntity().discard();
    }
}
