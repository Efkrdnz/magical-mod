package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class MagicCounterService {
    public static final int QTE_WINDOW_TICKS = 18;

    /**
     * Stands in for the counter skill on a prompt that needs none.
     *
     * <p>Not a registered skill, and deliberately so: it names the absence of one. Both the client
     * HUD and {@link #respond} branch on it before they try to resolve a definition.
     */
    public static final ResourceLocation FORCED_COUNTER =
            ResourceLocation.fromNamespaceAndPath("magical", "brace");

    private static final Map<UUID, CounterPrompt> ACTIVE_PROMPTS = new HashMap<>();
    private static final Map<UUID, Set<Integer>> RESOLVED_THREATS = new HashMap<>();
    private static final Map<UUID, Long> AEGIS_COUNTER_COOLDOWNS = new HashMap<>();

    private MagicCounterService() {}

    public static boolean offerCounter(ServerPlayer defender, CounterableSkillThreat threat, Vec3 clashPosition) {
        return offerCounter(defender, threat, clashPosition, QTE_WINDOW_TICKS);
    }

    public static boolean offerCounter(ServerPlayer defender, CounterableSkillThreat threat, Vec3 clashPosition, int windowTicks) {
        if (!(defender.level() instanceof ServerLevel level) || !threat.canBeCounteredBy(defender)) {
            return false;
        }
        int threatId = threat.counterThreatId();
        UUID defenderId = defender.getUUID();
        CounterPrompt active = ACTIVE_PROMPTS.get(defenderId);
        long now = level.getGameTime();
        if (active != null && active.threatId == threatId) {
            if (now <= active.deadlineTick) {
                return true;
            }
            clearPrompt(defender, threatId);
            return false;
        }
        if (resolvedThreats(defenderId).contains(threatId)) {
            return false;
        }

        PlayerMagicState state = defender.getData(MagicalAttachments.MAGIC_STATE);
        if (isValidGluttonyCounter(state)) {
            int safeWindowTicks = Math.max(3, windowTicks);
            long deadline = now + safeWindowTicks;
            ACTIVE_PROMPTS.put(defenderId, new CounterPrompt(threatId, threat.counterSkillId(), MagicPassiveContent.SIN_GLUTTONY.id(), deadline, clashPosition));
            MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(), MagicPassiveContent.SIN_GLUTTONY.id(), deadline, safeWindowTicks);
            return true;
        }

        Optional<MagicSkillDefinition> counter = bestCounterSkill(defender, state, threat);
        if (counter.isEmpty()) {
            return false;
        }

        MagicSkillDefinition counterSkill = counter.get();
        int safeWindowTicks = Math.max(3, windowTicks);
        long deadline = now + safeWindowTicks;
        ACTIVE_PROMPTS.put(defenderId, new CounterPrompt(threatId, threat.counterSkillId(), counterSkill.id(), deadline, clashPosition));
        MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(), counterSkill.id(), deadline, safeWindowTicks);
        return true;
    }

    /**
     * Prompts unconditionally: no attribute match, no tier floor, no counter skill required.
     *
     * <p>{@link #offerCounter} only prompts when the defender happens to own a skill whose
     * attribute beats the incoming one, and a tier-4 incoming additionally demands a tier-4 answer
     * off cooldown. That is the right shape for relief from another player's spell - you get out of
     * it with something you built for. It is the wrong shape for a boss telegraph, which most
     * players would simply never be shown.
     *
     * <p>So this one asks for the key alone. The existing rules are untouched; five shipped threats
     * depend on them.
     */
    public static boolean offerForcedCounter(ServerPlayer defender, CounterableSkillThreat threat,
            Vec3 clashPosition, int windowTicks) {
        if (!(defender.level() instanceof ServerLevel level) || !threat.canBeCounteredBy(defender)) {
            return false;
        }
        int threatId = threat.counterThreatId();
        UUID defenderId = defender.getUUID();
        long now = level.getGameTime();
        CounterPrompt active = ACTIVE_PROMPTS.get(defenderId);
        if (active != null && active.threatId == threatId) {
            if (now <= active.deadlineTick) {
                return true;
            }
            clearPrompt(defender, threatId);
            return false;
        }
        if (resolvedThreats(defenderId).contains(threatId)) {
            return false;
        }
        int safeWindowTicks = Math.max(3, windowTicks);
        long deadline = now + safeWindowTicks;
        ACTIVE_PROMPTS.put(defenderId,
                new CounterPrompt(threatId, threat.counterSkillId(), FORCED_COUNTER, deadline, clashPosition));
        MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(), FORCED_COUNTER,
                deadline, safeWindowTicks);
        return true;
    }

    public static boolean hasActivePrompt(ServerPlayer defender, CounterableSkillThreat threat) {
        if (!(defender.level() instanceof ServerLevel level)) {
            return false;
        }
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defender.getUUID());
        return prompt != null && prompt.threatId == threat.counterThreatId() && level.getGameTime() <= prompt.deadlineTick;
    }

    public static void expirePrompt(ServerPlayer defender, CounterableSkillThreat threat) {
        clearPrompt(defender, threat.counterThreatId());
    }

    public static void respond(ServerPlayer defender, int threatId) {
        if (!(defender.level() instanceof ServerLevel level)) {
            return;
        }
        UUID defenderId = defender.getUUID();
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defenderId);
        if (prompt == null || prompt.threatId != threatId || level.getGameTime() > prompt.deadlineTick) {
            clearPrompt(defender, threatId);
            return;
        }

        Entity entity = level.getEntity(threatId);
        if (!(entity instanceof CounterableSkillThreat threat) || !threat.canBeCounteredBy(defender)) {
            clearPrompt(defender, threatId);
            return;
        }

        if (FORCED_COUNTER.equals(prompt.counterSkillId)) {
            // Nothing to validate and nothing to spend: the press was the whole answer.
            ACTIVE_PROMPTS.remove(defenderId);
            resolvedThreats(defenderId).add(threatId);
            threat.onForcedCounter(level, defender, prompt.clashPosition);
            return;
        }

        PlayerMagicState state = defender.getData(MagicalAttachments.MAGIC_STATE);
        if (MagicPassiveContent.SIN_GLUTTONY.id().equals(prompt.counterSkillId)) {
            if (!isValidGluttonyCounter(state)) {
                clearPrompt(defender, threatId);
                return;
            }
            MagicSkillDefinition incoming = MagicContent.get(threat.counterSkillId());
            int tier = incoming == null ? 1 : Math.max(1, incoming.tier() + 1);
            int manaGain = incoming == null ? 12 : Math.max(8, incoming.baseManaCost() / 2 + tier * 5);
            state.addMana(manaGain);
            state.addManaCharge(Math.min(5, Math.max(1, tier)), 20 * (12 + tier * 8));
            state.setGluttonyCooldown(20 * 16);
            state.sync(defender);
            ACTIVE_PROMPTS.remove(defenderId);
            resolvedThreats(defenderId).add(threatId);
            threat.onGluttonyCountered(level, defender, prompt.clashPosition);
            defender.displayClientMessage(Component.translatable("message.magical.gluttony_devoured", Component.translatable(incoming == null ? "passive.magical.sin_gluttony" : incoming.nameKey())), true);
            return;
        }

        MagicSkillDefinition counterSkill = MagicContent.get(prompt.counterSkillId);
        if (counterSkill == null || !isValidCounter(defender, state, counterSkill, threat)) {
            clearPrompt(defender, threatId);
            return;
        }

        MagicSkillResolvedStats stats = counterSkill.resolve(state.tuningFor(counterSkill.id()));
        if (!state.spendMana(stats.manaCost())) {
            clearPrompt(defender, threatId);
            return;
        }

        if (isAegisProtectionCounter(counterSkill)) {
            AEGIS_COUNTER_COOLDOWNS.put(defenderId, level.getGameTime() + 24L);
        } else {
            state.setSkillCooldown(counterSkill.id(), stats.cooldownTicks());
        }
        state.sync(defender);
        ACTIVE_PROMPTS.remove(defenderId);
        resolvedThreats(defenderId).add(threatId);
        threat.onCountered(level, defender, counterSkill, prompt.clashPosition);
        defender.displayClientMessage(Component.translatable("message.magical.counter_success", Component.translatable(counterSkill.nameKey())), true);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        UUID playerId = player.getUUID();
        CounterPrompt prompt = ACTIVE_PROMPTS.get(playerId);
        if (prompt != null && level.getGameTime() > prompt.deadlineTick) {
            clearPrompt(player, prompt.threatId);
        }
        RESOLVED_THREATS.computeIfPresent(playerId, (id, threats) -> threats.isEmpty() ? null : threats);
    }

    public static void spawnClash(ServerLevel level, Vec3 position, int incomingColor, int counterColor) {
        level.addFreshEntity(SkillClashEffectEntity.create(level, position, incomingColor, counterColor, 24));
        level.playSound(null, position.x, position.y, position.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.75F, 1.45F);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.55F);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    private static Optional<MagicSkillDefinition> bestCounterSkill(ServerPlayer defender, PlayerMagicState state, CounterableSkillThreat threat) {
        return state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> skill != null && isValidCounter(defender, state, skill, threat))
                .max(Comparator
                        .comparingInt(MagicSkillDefinition::tier)
                        .thenComparingInt(skill -> -state.skillCooldown(skill.id()))
                        .thenComparingInt(skill -> -MagicContent.skillIndex(skill.id())));
    }

    private static boolean isValidCounter(ServerPlayer defender, PlayerMagicState state, MagicSkillDefinition skill, CounterableSkillThreat threat) {
        MagicSkillDefinition incoming = MagicContent.get(threat.counterSkillId());
        boolean judgement = MagicContent.GABRIEL_JUDGEMENT.id().equals(threat.counterSkillId());
        boolean aegisProtection = isAegisProtectionCounter(skill);
        if (aegisProtection) {
            if (!canAegisCounterIncoming(incoming) || aegisCounterCooling(defender)) {
                return false;
            }
        } else {
            if (judgement) {
                return false;
            }
            if (!skill.attribute().counters(threat.counterAttribute())) {
                return false;
            }
        }
        if (incoming != null && isTierFive(incoming) && !isTierFive(skill)) {
            return false;
        }
        if (!aegisProtection && state.isSkillOnCooldown(skill.id())) {
            return false;
        }
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        return state.mana() >= stats.manaCost();
    }

    private static boolean isAegisProtectionCounter(MagicSkillDefinition skill) {
        return MagicContent.GABRIEL_ULTIMATE_PROTECTION.id().equals(skill.id());
    }

    private static boolean canAegisCounterIncoming(MagicSkillDefinition incoming) {
        return incoming == null || (!MagicContent.isAuthoritySkill(incoming.id()) && incoming.tier() != -4);
    }

    private static boolean aegisCounterCooling(ServerPlayer defender) {
        Long until = AEGIS_COUNTER_COOLDOWNS.get(defender.getUUID());
        if (until == null) {
            return false;
        }
        if (!(defender.level() instanceof ServerLevel level) || level.getGameTime() < until) {
            return true;
        }
        AEGIS_COUNTER_COOLDOWNS.remove(defender.getUUID());
        return false;
    }

    /** The apex test, kept in one place so the counter rule and the ward rule cannot diverge. */
    private static boolean isTierFive(MagicSkillDefinition skill) {
        return TierFive.is(skill);
    }

    private static boolean isValidGluttonyCounter(PlayerMagicState state) {
        return state.isSinEnabled(MagicPassiveContent.SIN_GLUTTONY.id()) && state.gluttonyCooldownTicks() <= 0;
    }

    private static void clearPrompt(UUID defenderId, int threatId) {
        ACTIVE_PROMPTS.remove(defenderId);
        resolvedThreats(defenderId).add(threatId);
    }

    private static void clearPrompt(ServerPlayer defender, int threatId) {
        clearPrompt(defender.getUUID(), threatId);
        MagicalNetwork.sendCounterClear(defender, threatId);
    }

    private static Set<Integer> resolvedThreats(UUID defenderId) {
        return RESOLVED_THREATS.computeIfAbsent(defenderId, id -> new HashSet<>());
    }

    private record CounterPrompt(int threatId, ResourceLocation incomingSkillId, ResourceLocation counterSkillId, long deadlineTick, Vec3 clashPosition) {}
}
